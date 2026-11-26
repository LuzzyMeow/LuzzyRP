// Cloud backup: characters and all their chats (branches included, summaries not) go to the update service as
// pieces named by the SHA-256 of their JSON, so whatever the server already holds is skipped. The key it returns
// merges them into another device or browser within 15 days. The service is the one in `rphub-update-api`.
(function () {
    const { reactive } = Vue;
    const apiUrl = String(document.querySelector('meta[name="rphub-update-api"]')?.content || '')
        .trim()
        .replace(/\/+$/, '');
    const MB = 1024 * 1024;
    const MAX_BACKUP_BYTES = 1024 * MB;
    const SCOPED_NAMES = ['chat', 'branches'];
    const LAST_BACKUP_STORAGE_KEY = 'rp_hub_cloud_backup';
    const encoder = new TextEncoder();
    const storage = () => window.RPHubStorage;
    const formatMB = bytes => `${(bytes / MB).toFixed(1)} MB`;

    const readError = (status, text) => {
        let message = '';
        try { message = JSON.parse(text).error; } catch (_) { /* Not JSON. */ }
        return new Error(message || `云端服务出错（${status}）`);
    };
    const request = async (path, options = {}) => {
        let response;
        try {
            response = await fetch(`${apiUrl}${path}`, { cache: 'no-store', ...options });
        } catch (_) {
            throw new Error('连不上云端服务，请检查网络后重试');
        }
        if (response.ok) return response;
        throw readError(response.status, await response.text().catch(() => ''));
    };
    const postJson = (path, body) => request(path, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(body)
    }).then(response => response.json());
    // XHR rather than fetch, because only XHR reports how much of an upload has been sent.
    const putPiece = (hash, body, encoding, onFraction) => new Promise((resolve, reject) => {
        const xhr = new XMLHttpRequest();
        xhr.open('PUT', `${apiUrl}/v1/backup/blobs/${hash}`);
        xhr.setRequestHeader('Content-Type', 'application/octet-stream');
        xhr.setRequestHeader('X-Blob-Encoding', encoding);
        xhr.upload.onprogress = event => event.lengthComputable && onFraction(event.loaded / event.total);
        xhr.onload = () => (xhr.status >= 200 && xhr.status < 300 ? resolve() : reject(readError(xhr.status, xhr.responseText)));
        xhr.onerror = () => reject(new Error('连不上云端服务，请检查网络后重试'));
        xhr.send(body);
    });
    // The server sends pieces gzipped and the browser unpacks them, so the bytes counted here match a piece's size.
    const downloadPiece = async (key, hash, onBytes = () => {}) => {
        const reader = (await request(`/v1/backup/${key}/blobs/${hash}`)).body.getReader();
        const chunks = [];
        let received = 0;
        for (;;) {
            const { done, value } = await reader.read();
            if (done) break;
            chunks.push(value);
            received += value.length;
            onBytes(received);
        }
        return JSON.parse(await new Blob(chunks).text());
    };

    const encode = value => encoder.encode(JSON.stringify(value ?? null));
    const sha256 = async bytes => Array.from(
        new Uint8Array(await crypto.subtle.digest('SHA-256', bytes)),
        byte => byte.toString(16).padStart(2, '0')
    ).join('');
    const gzip = bytes => (typeof CompressionStream === 'function'
        ? new Response(new Blob([bytes]).stream().pipeThrough(new CompressionStream('gzip'))).arrayBuffer()
        : Promise.resolve(null));

    // Field order and empty fields (the page adds some to a character when it is opened) do not make cards differ.
    const isEmptyField = value => value === undefined || value === null || value === ''
        || (Array.isArray(value) ? value.length === 0 : typeof value === 'object' && Object.keys(value).length === 0);
    const canonical = (value) => {
        if (Array.isArray(value)) return value.map(canonical);
        if (!value || typeof value !== 'object') return value;
        return Object.fromEntries(Object.keys(value).sort()
            .filter(field => !isEmptyField(value[field]))
            .map(field => [field, canonical(value[field])]));
    };
    const isSameCard = (a, b) => JSON.stringify(canonical(a)) === JSON.stringify(canonical(b));

    // Chats and branch lists of the given characters, by owner; a branch's chat is named "<uuid>__branch__<id>".
    const listScopedEntries = async (uuids) => {
        const { getLegacyDb, getMainDb, getStorageLogicalKey, initDB, readStorageKeys } = storage();
        await initDB();
        const keys = [...await readStorageKeys(getMainDb()), ...await readStorageKeys(getLegacyDb())];
        const owners = new Map(uuids.map(uuid => [uuid, []]));
        new Set(keys.map(getStorageLogicalKey)).forEach((logicalKey) => {
            const name = SCOPED_NAMES.find(item => logicalKey.startsWith(`${item}_`));
            if (!name) return;
            const id = logicalKey.slice(name.length + 1);
            owners.get(window.RPHubStoryBranches.getStoryBranchOwnerId(id))?.push({ name, id });
        });
        return owners;
    };

    // `replaces` is this device's previous key, which the server retires once the new backup exists.
    const createBackup = async (onProgress, replaces) => {
        const { getScopedStoredValue, getStoredValue } = storage();
        const characters = ((await getStoredValue('characters')) || []).filter(character => character?.uuid);
        if (!characters.length) throw new Error('还没有角色卡可以备份');
        const owners = await listScopedEntries(characters.map(character => character.uuid));
        const total = characters.reduce((count, character) => count + 1 + owners.get(character.uuid).length, 0);

        // 1. Every piece is measured and named first, so an oversized backup fails before anything is sent.
        //    `read` fetches it again for sending, so only one piece at a time is held in memory.
        const pieces = [];
        let totalBytes = 0;
        const measure = async (piece, value) => {
            const bytes = encode(value);
            totalBytes += bytes.length;
            if (totalBytes > MAX_BACKUP_BYTES) throw new Error('角色卡和聊天加起来超过 1GB，超出单次备份上限');
            Object.assign(piece, { hash: await sha256(bytes), size: bytes.length });
            pieces.push(piece);
            onProgress({ label: '正在整理数据', percent: pieces.length / total * 100, detail: `${pieces.length} / ${total} 项` });
            return piece;
        };
        const manifest = [];
        for (const character of characters) {
            const entries = [];
            for (const { name, id } of owners.get(character.uuid)) {
                const value = await getScopedStoredValue(name, id);
                if (value !== undefined) entries.push(await measure({ name, id, read: () => getScopedStoredValue(name, id) }, value));
            }
            const name = String(character.name || '').slice(0, 200);
            manifest.push(await measure({ uuid: character.uuid, name, read: () => character, entries }, character));
        }

        // 2. The server says which pieces it lacks; the rest are skipped.
        const hashes = [...new Set(pieces.map(piece => piece.hash))];
        const missing = new Set();
        for (let index = 0; index < hashes.length; index += 1000) {
            const { missing: lacking } = await postJson('/v1/backup/check', { hashes: hashes.slice(index, index + 1000) });
            lacking.forEach(hash => missing.add(hash));
        }
        const skipped = pieces.filter(piece => !missing.has(piece.hash)).length;

        // 3. Only those go up. A chat changed since step 1 goes up as it is now.
        const sizes = new Map(pieces.map(piece => [piece.hash, piece.size]));
        const uploadBytes = [...missing].reduce((sum, hash) => sum + sizes.get(hash), 0);
        const sent = new Set();
        let sentBytes = 0;
        const report = (extra = 0) => onProgress({
            label: '正在上传',
            percent: uploadBytes ? (sentBytes + extra) / uploadBytes * 100 : 100,
            detail: `${formatMB(Math.min(uploadBytes, sentBytes + extra))} / ${formatMB(uploadBytes)}`
        });
        report();
        for (const piece of pieces) {
            if (!missing.has(piece.hash) || sent.has(piece.hash)) continue;
            const planned = piece.size;
            const bytes = encode(await piece.read());
            Object.assign(piece, { hash: await sha256(bytes), size: bytes.length });
            if (!sent.has(piece.hash)) {
                const packed = await gzip(bytes);
                await putPiece(piece.hash, packed || bytes, packed ? 'gzip' : 'identity', fraction => report(fraction * planned));
                sent.add(piece.hash);
            }
            sentBytes += planned;
            report();
        }
        onProgress({ label: '正在生成密钥', percent: 100, detail: '' });
        return { ...await postJson('/v1/backup', { characters: manifest, replaces }), total: pieces.length, skipped };
    };

    // Look-alike letters are read as the digits they resemble, as the server does.
    const normalizeKey = key => String(key || '').toUpperCase().replace(/[^0-9A-Z]/g, '')
        .replace(/O/g, '0').replace(/[IL]/g, '1');

    // Sorts the backup's characters against this browser: a card identical to the local one is skipped with all its
    // chats, the same character with a different card takes the backup's card and chats, the rest are added.
    const fetchBackup = async (rawKey, onProgress) => {
        const key = normalizeKey(rawKey);
        if (key.length !== 16) throw new Error('密钥应为 16 位字母和数字');
        const { characters } = await request(`/v1/backup/${key}`).then(response => response.json());
        const local = new Map(((await storage().getStoredValue('characters')) || []).map(character => [character?.uuid, character]));
        const plan = { key, added: [], replaced: [], skipped: [] };
        for (const [index, item] of characters.entries()) {
            onProgress({ label: '正在比对本机角色卡', percent: index / characters.length * 100, detail: `${index} / ${characters.length} 个` });
            if (!local.has(item.uuid)) {
                plan.added.push(item);
                continue;
            }
            if (await sha256(encode(local.get(item.uuid))) !== item.hash) {
                // Kept for the restore, which needs it if the card does differ.
                item.card = await downloadPiece(key, item.hash);
                if (!isSameCard(local.get(item.uuid), item.card)) {
                    plan.replaced.push(item);
                    continue;
                }
            }
            plan.skipped.push(item);
        }
        return plan;
    };

    const applyBackup = async ({ key, added, replaced }, onProgress) => {
        const {
            deleteScopedStoredValue, getScopedStoredValue, getStoredValue, setScopedStoredValue, setStoredValue, setWritesPaused
        } = storage();
        const items = [...added, ...replaced];
        const entries = items.flatMap(item => item.entries);
        const totalBytes = [...items, ...entries].reduce((sum, piece) => sum + piece.size, 0);
        let doneBytes = 0;
        const report = (extra = 0) => onProgress({
            label: '正在下载',
            percent: totalBytes ? (doneBytes + extra) / totalBytes * 100 : 100,
            detail: `${formatMB(doneBytes + extra)} / ${formatMB(totalBytes)}`
        });
        const finish = (piece) => {
            doneBytes += piece.size;
            report();
        };
        const download = async (piece) => {
            const value = await downloadPiece(key, piece.hash, report);
            finish(piece);
            return value;
        };

        // Cards are held until the end, so an interrupted restore never lists a character whose chats are missing.
        const incoming = new Map();
        for (const item of items) {
            if (item.card) finish(item);
            incoming.set(item.uuid, item.card || await download(item));
        }
        setWritesPaused(true);
        try {
            for (const entry of entries) {
                const local = await getScopedStoredValue(entry.name, entry.id);
                if (local !== undefined && await sha256(encode(local)) === entry.hash) {
                    finish(entry);
                    continue;
                }
                await setScopedStoredValue(entry.name, entry.id, await download(entry), { clone: false, force: true });
                // Summaries were made from the chat this one replaces, so they no longer match it.
                if (entry.name === 'chat') await deleteScopedStoredValue('classic_memories', entry.id);
            }
            // A replaced character keeps nothing the backup lacks, such as a branch made only here.
            const incomingKeys = new Set(entries.map(entry => `${entry.name}_${entry.id}`));
            const owners = await listScopedEntries(replaced.map(item => item.uuid));
            for (const list of owners.values()) {
                for (const { name, id } of list) {
                    if (incomingKeys.has(`${name}_${id}`)) continue;
                    await deleteScopedStoredValue(name, id);
                    if (name === 'chat') await deleteScopedStoredValue('classic_memories', id);
                }
            }
            const localCharacters = (await getStoredValue('characters')) || [];
            const merged = localCharacters.map(character => incoming.get(character?.uuid) || character);
            added.forEach(item => merged.push(incoming.get(item.uuid)));
            await setStoredValue('characters', merged, { clone: false, force: true });
        } catch (error) {
            setWritesPaused(false);
            throw error;
        }
    };

    const useCloudBackup = ({ saveAll, toast }) => {
        const cloudBackup = reactive({
            enabled: Boolean(apiUrl),
            mode: '', // 'upload' or 'download' while its dialog is open
            busy: false,
            progress: null, // { label, percent, detail }
            key: '',
            expiresAt: 0,
            restoreKey: ''
        });
        try {
            const last = JSON.parse(localStorage.getItem(LAST_BACKUP_STORAGE_KEY) || 'null');
            if (last?.expiresAt > Date.now()) Object.assign(cloudBackup, { key: last.key, expiresAt: last.expiresAt });
        } catch (_) { /* Storage can be blocked in local-file mode. */ }

        const setProgress = (progress) => {
            cloudBackup.progress = { ...progress, percent: Math.min(100, Math.max(0, progress.percent)) };
        };
        const run = async (task) => {
            if (cloudBackup.busy) return;
            cloudBackup.busy = true;
            try {
                if (!globalThis.crypto?.subtle) throw new Error('当前网页环境不支持云端备份，请用 https 地址打开');
                await task();
            } catch (error) {
                toast(error?.message || '操作失败', 'error', 5000);
            } finally {
                cloudBackup.busy = false;
                cloudBackup.progress = null;
            }
        };

        const openCloudBackup = (mode) => {
            cloudBackup.mode = mode;
        };
        const closeCloudBackup = () => {
            if (!cloudBackup.busy) cloudBackup.mode = '';
        };

        const startCloudBackup = () => run(async () => {
            setProgress({ label: '正在保存当前数据', percent: 0, detail: '' });
            await saveAll();
            const { key, expiresAt, total, skipped, replaced } = await createBackup(setProgress, cloudBackup.key || undefined);
            Object.assign(cloudBackup, { key, expiresAt });
            try {
                localStorage.setItem(LAST_BACKUP_STORAGE_KEY, JSON.stringify({ key, expiresAt }));
            } catch (_) { /* The key is still shown in the dialog. */ }
            const summary = skipped ? `备份完成：共 ${total} 项，其中 ${skipped} 项云端已有，已跳过` : `备份完成：共 ${total} 项`;
            toast(replaced ? `${summary}；旧密钥已作废` : summary, 'success');
        });

        // Reads the backup and merges it at once; the dialog states the rules, the closing message the counts.
        const startCloudRestore = () => run(async () => {
            setProgress({ label: '正在读取备份', percent: 0, detail: '' });
            const plan = await fetchBackup(cloudBackup.restoreKey, setProgress);
            if (!plan.added.length && !plan.replaced.length) {
                toast('备份里的角色卡本机都有且完全相同，无需恢复', 'info');
                return;
            }
            await applyBackup(plan, setProgress);
            const counts = [`新增 ${plan.added.length} 个`, `替换 ${plan.replaced.length} 个`, `跳过 ${plan.skipped.length} 个`]
                .filter(text => !text.includes(' 0 个'));
            setProgress({ label: '恢复完成，正在刷新页面', percent: 100, detail: counts.join('，') });
            toast(`恢复完成：${counts.join('，')}`, 'success', 3000);
            setTimeout(() => location.reload(), 1500);
            // Keeps the dialog locked until the page reloads.
            await new Promise(() => {});
        });

        const copyCloudBackupKey = () => navigator.clipboard.writeText(cloudBackup.key)
            .then(() => toast('密钥已复制', 'success'), () => toast('复制失败，请手动选中密钥复制', 'error'));
        const formatCloudBackupTime = time => new Date(time).toLocaleString('zh-CN', {
            month: 'numeric', day: 'numeric', hour: '2-digit', minute: '2-digit'
        });

        return {
            cloudBackup, closeCloudBackup, copyCloudBackupKey, formatCloudBackupTime, openCloudBackup,
            startCloudBackup, startCloudRestore
        };
    };

    window.RPHubCloudBackup = Object.freeze({ useCloudBackup });
})();
