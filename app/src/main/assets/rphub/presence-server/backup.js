// Cloud backup of RP-Hub characters and their chats. Each character and each chat is stored once, by the SHA-256 of
// its JSON, so anything the disk already holds (from any earlier backup) is skipped. A backup is a manifest of those
// hashes that a 16-character key opens for 15 days. Nothing is encrypted: the key works like a pickup code.
import crypto from 'node:crypto';
import fs from 'node:fs';
import fsp from 'node:fs/promises';
import path from 'node:path';
import { Transform, Writable } from 'node:stream';
import { pipeline } from 'node:stream/promises';
import zlib from 'node:zlib';

const MB = 1024 * 1024;
const HOUR = 3600_000;
const BACKUP_DIR = path.resolve(process.env.BACKUP_DIR || 'backup-data');
const BLOB_DIR = path.join(BACKUP_DIR, 'blobs');
const MANIFEST_DIR = path.join(BACKUP_DIR, 'manifests');
const TMP_DIR = path.join(BACKUP_DIR, 'tmp');
const MAX_BACKUP_BYTES = 1024 * MB;
const MAX_TOTAL_BYTES = (Number(process.env.BACKUP_MAX_TOTAL_GB) || 1000) * 1024 * MB;
const FREE_SPACE_RESERVE = 1024 * MB;
const RETENTION_MS = 15 * 24 * HOUR;
// Pieces uploaded for a backup that was never finished are kept this long before they count as rubbish.
const ORPHAN_GRACE_MS = 6 * HOUR;
const KEY_ALPHABET = '0123456789ABCDEFGHJKMNPQRSTVWXYZ';
const HASH_RE = /^[0-9a-f]{64}$/;
const SCOPED_NAMES = new Set(['chat', 'branches']);
const STATS_FILE = path.join(BACKUP_DIR, 'stats.json');
const ADMIN_PASSWORD = process.env.ADMIN_PASSWORD || '';
const ADMIN_PAGE = new URL('./admin.html', import.meta.url);

let ready = false;
let storedBytes = 0;
let collecting = false;
const manifestCache = new Map();
const buckets = new Map();
// Daily counters for the admin page, by Beijing date, written to disk once a minute and kept for 90 days.
let stats = {};
let statsDirty = false;
// What the last cleanup found: live backups, what they add up to before deduplication, and stored pieces.
let summary = { backups: 0, backupBytes: 0, pieces: 0 };

const httpError = (statusCode, message, extra = {}) => Object.assign(new Error(message), { statusCode, ...extra });
const sha256 = value => crypto.createHash('sha256').update(value).digest('hex');
const blobPath = hash => path.join(BLOB_DIR, hash.slice(0, 2), `${hash}.gz`);
// Look-alike letters are read as the digits they resemble, so a key typed by hand still works.
const normalizeKey = key => String(key || '').toUpperCase().replace(/[^0-9A-Z]/g, '').replace(/O/g, '0').replace(/[IL]/g, '1');
const manifestPath = key => path.join(MANIFEST_DIR, `${sha256(key)}.json`);
const manifestHashes = manifest => (manifest.characters || []).flatMap(item => [item.hash, ...item.entries.map(entry => entry.hash)]);

const getClientIp = request => String(request.headers['x-forwarded-for'] || '').split(',')[0].trim()
    || request.socket.remoteAddress || '';

const overLimit = (name, ip, limit, windowMs, amount = 1) => {
    const key = `${name}:${ip}`;
    const now = Date.now();
    let bucket = buckets.get(key);
    if (!bucket || now - bucket.start > windowMs) {
        bucket = { start: now, used: 0 };
        buckets.set(key, bucket);
    }
    if (bucket.used + amount > limit) return true;
    bucket.used += amount;
    return false;
};

const count = (fields) => {
    const day = new Date(Date.now() + 8 * HOUR).toISOString().slice(0, 10);
    stats[day] ||= {};
    for (const [field, amount] of Object.entries(fields)) stats[day][field] = (stats[day][field] || 0) + amount;
    statsDirty = true;
};

const saveStats = async () => {
    if (!statsDirty) return;
    statsDirty = false;
    stats = Object.fromEntries(Object.entries(stats).sort(([a], [b]) => a.localeCompare(b)).slice(-90));
    const tmp = path.join(TMP_DIR, `stats-${crypto.randomUUID()}`);
    await fsp.writeFile(tmp, JSON.stringify(stats));
    await fsp.rename(tmp, STATS_FILE);
};

const limitBytes = max => {
    let total = 0;
    return new Transform({
        transform(chunk, _encoding, callback) {
            total += chunk.length;
            callback(total > max ? httpError(413, '单项内容超过 1GB') : null, chunk);
        }
    });
};

const readJsonBody = async (request, maxBytes) => {
    const chunks = [];
    let size = 0;
    for await (const chunk of request) {
        size += chunk.length;
        if (size > maxBytes) throw httpError(413, '请求内容过大');
        chunks.push(chunk);
    }
    try {
        return JSON.parse(Buffer.concat(chunks).toString('utf8') || '{}');
    } catch {
        throw httpError(400, '请求格式不正确');
    }
};

const ensureSpace = async (bytes) => {
    if (storedBytes + bytes > MAX_TOTAL_BYTES) throw httpError(507, '云端空间暂时已满，请稍后再试');
    const { bavail, bsize } = await fsp.statfs(BACKUP_DIR);
    if (bavail * bsize < bytes + FREE_SPACE_RESERVE) throw httpError(507, '云端空间暂时已满，请稍后再试');
};

// gzip keeps the uncompressed size in its last four bytes; reading it also marks the piece as just used.
const readBlobSize = async (hash) => {
    const file = await fsp.open(blobPath(hash));
    const buffer = Buffer.alloc(4);
    try {
        const { size } = await file.stat();
        await file.read(buffer, 0, 4, size - 4);
    } finally {
        await file.close();
    }
    const now = new Date();
    await fsp.utimes(blobPath(hash), now, now);
    return buffer.readUInt32LE(0);
};

const receiveBlob = async (request, hash, ip) => {
    const length = Number(request.headers['content-length']) || 0;
    if (length > MAX_BACKUP_BYTES) throw httpError(413, '单项内容超过 1GB');
    if (overLimit('upload', ip, 5 * 1024 * MB, HOUR, length)) throw httpError(429, '上传太频繁，请稍后再试');
    await ensureSpace(length);
    const target = blobPath(hash);
    const tmp = path.join(TMP_DIR, `${hash}-${crypto.randomUUID()}`);
    try {
        const streams = [request, limitBytes(MAX_BACKUP_BYTES)];
        if (request.headers['x-blob-encoding'] !== 'gzip') streams.push(zlib.createGzip());
        await pipeline(...streams, fs.createWriteStream(tmp));
        // The hash names the content for everyone's backups, so it must really be the hash of what was sent.
        const digest = crypto.createHash('sha256');
        await pipeline(fs.createReadStream(tmp), zlib.createGunzip(), limitBytes(MAX_BACKUP_BYTES), new Writable({
            write(chunk, _encoding, callback) {
                digest.update(chunk);
                callback();
            }
        })).catch(error => {
            throw error.statusCode ? error : httpError(400, '上传内容无效');
        });
        if (digest.digest('hex') !== hash) throw httpError(400, '上传内容校验失败');
        await fsp.mkdir(path.dirname(target), { recursive: true });
        await fsp.rename(tmp, target);
        const { size } = await fsp.stat(target);
        storedBytes += size;
        count({ uploads: 1, uploadBytes: size });
    } finally {
        await fsp.rm(tmp, { force: true });
    }
};

const isText = (value, max) => typeof value === 'string' && value.length > 0 && value.length <= max;
const isPiece = item => HASH_RE.test(item?.hash) && Number.isInteger(item.size) && item.size >= 0;

// A new upload from the same device retires the backup before it. Only the old list goes, replaced by a marker that
// lets the old key explain itself until it would have expired; pieces the new backup or anyone else's still uses stay,
// and the hourly cleanup removes the rest.
const retireManifest = async (rawKey, now) => {
    const key = normalizeKey(rawKey);
    if (key.length !== 16) return false;
    const file = manifestPath(key);
    const old = JSON.parse(await fsp.readFile(file, 'utf8').catch(() => 'null'));
    if (!old || old.replacedAt || old.expiresAt <= now) return false;
    const tmp = path.join(TMP_DIR, `manifest-${crypto.randomUUID()}`);
    await fsp.writeFile(tmp, JSON.stringify({ createdAt: old.createdAt, expiresAt: old.expiresAt, replacedAt: now }));
    await fsp.rename(tmp, file);
    manifestCache.delete(file);
    count({ replaced: 1 });
    return true;
};

const createBackup = async (request, ip) => {
    if (overLimit('create', ip, 20, HOUR)) throw httpError(429, '备份太频繁，请稍后再试');
    const { characters, replaces } = await readJsonBody(request, 16 * MB);
    const valid = Array.isArray(characters) && characters.length > 0 && characters.length <= 5000
        && characters.every(item => isText(item?.uuid, 100) && typeof item.name === 'string' && item.name.length <= 200
            && isPiece(item) && Array.isArray(item.entries) && item.entries.length <= 2000
            && item.entries.every(entry => SCOPED_NAMES.has(entry?.name) && isText(entry.id, 300) && isPiece(entry)));
    if (!valid) throw httpError(400, '备份内容格式不正确');
    const pieces = characters.flatMap(item => [item, ...item.entries]);
    const backupBytes = pieces.reduce((total, piece) => total + piece.size, 0);
    if (backupBytes > MAX_BACKUP_BYTES) {
        throw httpError(413, '角色卡和聊天加起来超过 1GB，超出单次备份上限');
    }
    const sizes = new Map(pieces.map(piece => [piece.hash, piece.size]));
    const missing = [];
    for (const [hash, size] of sizes) {
        const actual = await readBlobSize(hash).catch(() => null);
        if (actual === null) missing.push(hash);
        else if (actual !== size) throw httpError(400, '备份内容大小不符');
    }
    if (missing.length) throw httpError(409, '部分内容在上传途中失效，请重新备份', { missing });

    const key = Array.from({ length: 16 }, () => KEY_ALPHABET[crypto.randomInt(KEY_ALPHABET.length)]).join('');
    const now = Date.now();
    const manifest = {
        createdAt: now,
        expiresAt: now + RETENTION_MS,
        characters: characters.map(item => ({
            uuid: item.uuid,
            name: item.name,
            hash: item.hash,
            size: item.size,
            entries: item.entries.map(({ name, id, hash, size }) => ({ name, id, hash, size }))
        }))
    };
    const tmp = path.join(TMP_DIR, `manifest-${crypto.randomUUID()}`);
    await fsp.writeFile(tmp, JSON.stringify(manifest));
    await fsp.rename(tmp, manifestPath(key));
    count({ backups: 1, backupBytes });
    const replaced = await retireManifest(replaces, now);
    return { key: key.match(/.{4}/g).join('-'), expiresAt: manifest.expiresAt, replaced };
};

const loadManifest = async (rawKey, ip) => {
    const key = normalizeKey(rawKey);
    const file = manifestPath(key);
    let manifest = manifestCache.get(file);
    if (!manifest) {
        if (overLimit('lookup', ip, 60, 10 * 60_000)) throw httpError(429, '尝试太频繁，请稍后再试');
        if (key.length !== 16 || [...key].some(char => !KEY_ALPHABET.includes(char))) throw httpError(404, '密钥无效或已过期');
        manifest = JSON.parse(await fsp.readFile(file, 'utf8').catch(() => 'null'));
        if (!manifest) throw httpError(404, '密钥无效或已过期');
        if (manifest.replacedAt && manifest.expiresAt > Date.now()) throw httpError(410, '这份备份已被同一设备的新备份替换，请使用新的密钥');
        manifest.hashes = new Set(manifestHashes(manifest));
        if (manifestCache.size >= 50) manifestCache.delete(manifestCache.keys().next().value);
        manifestCache.set(file, manifest);
    }
    if (manifest.expiresAt <= Date.now()) throw httpError(404, '密钥无效或已过期');
    return manifest;
};

const collectGarbage = async () => {
    if (collecting) return;
    collecting = true;
    try {
        const now = Date.now();
        const referenced = new Set();
        const found = { backups: 0, backupBytes: 0, pieces: 0 };
        for (const name of await fsp.readdir(MANIFEST_DIR)) {
            const file = path.join(MANIFEST_DIR, name);
            const manifest = JSON.parse(await fsp.readFile(file, 'utf8').catch(() => 'null'));
            if (manifest?.expiresAt > now) {
                if (!manifest.replacedAt) found.backups += 1;
                (manifest.characters || []).flatMap(item => [item, ...item.entries]).forEach((piece) => {
                    found.backupBytes += piece.size;
                    referenced.add(piece.hash);
                });
            } else {
                // A retired backup's marker was counted when it was replaced, so only real expiries count here.
                if (manifest && !manifest.replacedAt) count({ expired: 1 });
                await fsp.rm(file, { force: true });
            }
        }
        manifestCache.clear();
        let total = 0;
        for (const dir of await fsp.readdir(BLOB_DIR)) {
            for (const name of await fsp.readdir(path.join(BLOB_DIR, dir))) {
                const file = path.join(BLOB_DIR, dir, name);
                const { size, mtimeMs } = await fsp.stat(file);
                if (!referenced.has(name.slice(0, 64)) && now - mtimeMs > ORPHAN_GRACE_MS) {
                    await fsp.rm(file, { force: true });
                    count({ deletedPieces: 1, freedBytes: size });
                } else {
                    total += size;
                    found.pieces += 1;
                }
            }
        }
        storedBytes = total;
        summary = found;
        for (const [key, bucket] of buckets) {
            if (now - bucket.start > HOUR) buckets.delete(key);
        }
    } catch (error) {
        console.warn('Backup cleanup failed:', error.message);
    } finally {
        collecting = false;
    }
};

// Called on shutdown so a restart or redeploy keeps the last minute of counters.
export const flushBackupStats = () => saveStats().catch(error => console.warn('Saving backup stats failed:', error.message));

export const initBackup = async () => {
    try {
        await fsp.rm(TMP_DIR, { recursive: true, force: true });
        await Promise.all([BLOB_DIR, MANIFEST_DIR, TMP_DIR].map(dir => fsp.mkdir(dir, { recursive: true })));
        stats = JSON.parse(await fsp.readFile(STATS_FILE, 'utf8').catch(() => '{}'));
        await collectGarbage();
        setInterval(collectGarbage, HOUR).unref();
        setInterval(flushBackupStats, 60_000).unref();
        ready = true;
        console.log(`Cloud backup stored in ${BACKUP_DIR}`);
    } catch (error) {
        console.error('Cloud backup disabled:', error.message);
    }
};

// Answers every /v1/backup request. `send` writes a JSON reply with the CORS headers; `cors` holds those headers.
export const handleBackupRequest = async (request, response, url, send, cors) => {
    const ip = getClientIp(request);
    const blobMatch = url.pathname.match(/^\/v1\/backup\/blobs\/([0-9a-f]{64})$/);
    const keyMatch = url.pathname.match(/^\/v1\/backup\/([0-9A-Za-z-]{16,24})(?:\/blobs\/([0-9a-f]{64}))?$/);
    try {
        if (!ready) throw httpError(503, '云端备份暂未开启');

        if (request.method === 'POST' && url.pathname === '/v1/backup/check') {
            if (overLimit('check', ip, 300, 10 * 60_000)) throw httpError(429, '请求太频繁，请稍后再试');
            const { hashes } = await readJsonBody(request, 2 * MB);
            if (!Array.isArray(hashes) || hashes.length > 5000 || !hashes.every(hash => HASH_RE.test(hash))) {
                throw httpError(400, '请求格式不正确');
            }
            const missing = [];
            for (const hash of new Set(hashes)) {
                // Touching what is already here keeps it until the backup that reuses it is finished.
                const now = new Date();
                await fsp.utimes(blobPath(hash), now, now).catch(() => missing.push(hash));
            }
            const checked = new Set(hashes).size;
            count({ checked, hits: checked - missing.length });
            return send(200, { missing });
        }

        if (request.method === 'PUT' && blobMatch) {
            await receiveBlob(request, blobMatch[1], ip);
            return send(200, { ok: true });
        }

        if (request.method === 'POST' && url.pathname === '/v1/backup') {
            return send(200, await createBackup(request, ip));
        }

        if (request.method === 'GET' && keyMatch) {
            const manifest = await loadManifest(keyMatch[1], ip);
            const hash = keyMatch[2];
            if (!hash) {
                const { createdAt, expiresAt, characters } = manifest;
                count({ restores: 1 });
                return send(200, { createdAt, expiresAt, characters });
            }
            if (!manifest.hashes.has(hash)) throw httpError(404, '备份里没有这项内容');
            const file = blobPath(hash);
            const { size } = await fsp.stat(file).catch(() => {
                throw httpError(404, '这项内容已失效');
            });
            // Stored gzipped; the browser unpacks it by itself.
            response.writeHead(200, {
                ...cors,
                'Content-Type': 'application/json; charset=utf-8',
                'Content-Encoding': 'gzip',
                'Content-Length': size,
                'Cache-Control': 'no-store'
            });
            await pipeline(fs.createReadStream(file), response);
            count({ downloads: 1, downloadBytes: size });
            return;
        }

        return send(404, { error: 'Not found' });
    } catch (error) {
        if (response.headersSent) return response.destroy();
        if (!error.statusCode) console.error('Backup request failed:', error);
        return send(error.statusCode || 500, {
            error: error.statusCode ? error.message : '云端服务出错，请稍后再试',
            ...(error.missing ? { missing: error.missing } : {})
        });
    }
};

// The admin page itself is public; the numbers behind it need ADMIN_PASSWORD.
export const handleAdminRequest = async (request, response, url, send) => {
    try {
        if (url.pathname === '/admin') {
            response.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8', 'Cache-Control': 'no-store', 'X-Frame-Options': 'DENY' });
            return response.end(await fsp.readFile(ADMIN_PAGE));
        }
        if (request.method !== 'GET' || url.pathname !== '/v1/admin/stats') return send(404, { error: 'Not found' });
        if (!ADMIN_PASSWORD) return send(503, { error: '后台未开启：请先设置环境变量 ADMIN_PASSWORD' });
        const ip = getClientIp(request);
        const failures = buckets.get(`admin:${ip}`);
        if (failures && Date.now() - failures.start <= 10 * 60_000 && failures.used >= 10) {
            return send(429, { error: '密码错误次数太多，请 10 分钟后再试' });
        }
        const given = String(request.headers.authorization || '').replace(/^Bearer\s+/i, '');
        if (!crypto.timingSafeEqual(Buffer.from(sha256(given), 'hex'), Buffer.from(sha256(ADMIN_PASSWORD), 'hex'))) {
            overLimit('admin', ip, 10, 10 * 60_000);
            return send(401, { error: '密码不正确' });
        }
        if (!ready) return send(503, { error: '云端备份暂未开启' });
        await collectGarbage();
        const { bavail, blocks, bsize } = await fsp.statfs(BACKUP_DIR);
        return send(200, {
            storage: { used: storedBytes, limit: MAX_TOTAL_BYTES, diskFree: bavail * bsize, diskTotal: blocks * bsize },
            live: summary,
            retentionDays: RETENTION_MS / (24 * HOUR),
            days: stats
        });
    } catch (error) {
        console.error('Admin request failed:', error);
        return response.headersSent ? response.destroy() : send(500, { error: '后台出错，请稍后再试' });
    }
};
