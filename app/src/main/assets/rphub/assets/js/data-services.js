// RP-Hub data services: storage, memory, context, branches and UI-template state.

// --- Storage ---
(function () {
    const DB_NAME = 'RPHubDB';
    const LEGACY_DB_NAME = String.fromCharCode(83, 105, 108, 108, 121, 84, 97, 118, 101, 114, 110, 68, 66);
    const STORAGE_PREFIX = 'rp_hub_';
    const LEGACY_STORAGE_PREFIX = String.fromCharCode(115, 105, 108, 108, 121, 95, 116, 97, 118, 101, 114, 110, 95);
    const DB_VERSION = 1;
    let mainDb = null;
    let legacyDb = null;
    let initPromise = null;

    const openAppDB = (name, version = DB_VERSION) => new Promise((resolve, reject) => {
        const request = indexedDB.open(name, version);
        request.onerror = (event) => reject(`DB Error: ${event.target.error}`);
        request.onsuccess = (event) => resolve(event.target.result);
        request.onupgradeneeded = (event) => {
            const database = event.target.result;
            if (!database.objectStoreNames.contains('store')) database.createObjectStore('store');
        };
    });

    const initDB = async () => {
        if (mainDb) return mainDb;
        if (initPromise) return initPromise;
        initPromise = (async () => {
            mainDb = await openAppDB(DB_NAME);
            try {
                const dbList = typeof indexedDB.databases === 'function' ? await indexedDB.databases() : null;
                const legacyInfo = dbList?.find(item => item?.name === LEGACY_DB_NAME);
                const shouldOpenLegacy = !dbList || !!legacyInfo;
                if (shouldOpenLegacy) {
                    const legacyVersion = Math.max(DB_VERSION, Number(legacyInfo?.version) || DB_VERSION);
                    legacyDb = await openAppDB(LEGACY_DB_NAME, legacyVersion);
                }
            } catch (error) {
                console.warn('Legacy DB check failed:', error);
            }
            return mainDb;
        })();
        try {
            return await initPromise;
        } finally {
            initPromise = null;
        }
    };

    const isDatabaseClosingError = (error) => {
        const message = String(error?.message || error || '');
        return /connection is closing|database is closing|close pending/i.test(message);
    };

    const reopenMainDB = async () => {
        try { mainDb?.close(); } catch (_) { }
        mainDb = await openAppDB(DB_NAME);
        return mainDb;
    };

    const unwrapForStorage = (value, seen = new WeakMap()) => {
        if (value === null || typeof value !== 'object') return value;
        const raw = typeof Vue?.toRaw === 'function' ? Vue.toRaw(value) : value;
        if (raw === null || typeof raw !== 'object') return raw;
        if (seen.has(raw)) return seen.get(raw);
        if (raw instanceof Date) return raw.toISOString();
        if (ArrayBuffer.isView(raw)) return Array.from(raw);
        if (raw instanceof ArrayBuffer) return Array.from(new Uint8Array(raw));

        if (Array.isArray(raw)) {
            const result = [];
            seen.set(raw, result);
            raw.forEach((item, index) => {
                const clonedItem = unwrapForStorage(item, seen);
                result[index] = clonedItem === undefined ? null : clonedItem;
            });
            return result;
        }

        const result = {};
        seen.set(raw, result);
        Object.keys(raw).forEach(key => {
            const item = raw[key];
            if (typeof item === 'function' || typeof item === 'undefined') return;
            result[key] = unwrapForStorage(item, seen);
        });
        return result;
    };

    const cloneForStorage = (value) => {
        const plainValue = unwrapForStorage(value);
        if (typeof structuredClone === 'function') {
            try { return structuredClone(plainValue); } catch (_) { }
        }
        return JSON.parse(JSON.stringify(plainValue));
    };

    const storageKey = (name) => `${STORAGE_PREFIX}${name}`;
    const legacyStorageKey = (name) => `${LEGACY_STORAGE_PREFIX}${name}`;
    const scopedStorageKey = (name, id) => `${storageKey(name)}_${id}`;
    const legacyScopedStorageKey = (name, id) => `${legacyStorageKey(name)}_${id}`;

    const dbSetTo = (targetDb, key, value, options = {}) => new Promise((resolve, reject) => {
        if (!targetDb) return reject('DB not initialized');
        const request = targetDb.transaction(['store'], 'readwrite')
            .objectStore('store')
            .put(options.clone === false ? value : cloneForStorage(value), key);
        request.onsuccess = () => resolve();
        request.onerror = (event) => reject(event.target.error);
    });

    const dbSet = async (key, value, options = {}) => {
        if (!mainDb) await initDB();
        try {
            return await dbSetTo(mainDb, key, value, options);
        } catch (error) {
            if (!isDatabaseClosingError(error)) throw error;
            await reopenMainDB();
            return dbSetTo(mainDb, key, value, options);
        }
    };

    const dbGetFrom = (targetDb, key) => new Promise((resolve, reject) => {
        if (!targetDb) return resolve(undefined);
        const request = targetDb.transaction(['store'], 'readonly').objectStore('store').get(key);
        request.onsuccess = () => resolve(request.result);
        request.onerror = (event) => reject(event.target.error);
    });

    const dbGet = async (key) => {
        if (!mainDb) await initDB();
        try {
            return await dbGetFrom(mainDb, key);
        } catch (error) {
            if (!isDatabaseClosingError(error)) throw error;
            await reopenMainDB();
            return dbGetFrom(mainDb, key);
        }
    };

    const dbGetWithLegacy = async (key, oldKey) => {
        const value = await dbGet(key);
        if (value !== undefined || !oldKey || !legacyDb) return value;
        const legacyValue = await dbGetFrom(legacyDb, oldKey);
        if (legacyValue !== undefined) await dbSet(key, legacyValue);
        return legacyValue;
    };

    const dbDeleteFrom = (targetDb, key) => new Promise((resolve, reject) => {
        if (!targetDb) return resolve();
        const request = targetDb.transaction(['store'], 'readwrite').objectStore('store').delete(key);
        request.onsuccess = () => resolve();
        request.onerror = (event) => reject(event.target.error);
    });

    const dbDeleteWithLegacy = async (key, oldKey) => {
        if (!mainDb) await initDB();
        await dbDeleteFrom(mainDb, key);
        if (oldKey && legacyDb) await dbDeleteFrom(legacyDb, oldKey);
    };

    const readStorageKeys = (targetDb) => new Promise((resolve, reject) => {
        if (!targetDb) return resolve([]);
        const request = targetDb.transaction(['store'], 'readonly').objectStore('store').getAllKeys();
        request.onsuccess = () => resolve(request.result.map(key => String(key)));
        request.onerror = () => reject(request.error);
    });

    const scanStorageEntries = (targetDb, source, inspect) => new Promise((resolve, reject) => {
        if (!targetDb) return resolve();
        const request = targetDb.transaction(['store'], 'readonly').objectStore('store').openCursor();
        request.onsuccess = () => {
            const cursor = request.result;
            if (!cursor) return resolve();
            inspect(source, String(cursor.key), cursor.value);
            cursor.continue();
        };
        request.onerror = () => reject(request.error);
    });

    const deleteStorageKeys = (targetDb, keys) => new Promise((resolve, reject) => {
        if (!targetDb || keys.length === 0) return resolve();
        const transaction = targetDb.transaction(['store'], 'readwrite');
        const store = transaction.objectStore('store');
        keys.forEach(key => store.delete(key));
        transaction.oncomplete = () => resolve();
        transaction.onerror = () => reject(transaction.error);
        transaction.onabort = () => reject(transaction.error);
    });

    const getStorageLogicalKey = (key) => {
        const value = String(key || '');
        if (value.startsWith(STORAGE_PREFIX)) return value.slice(STORAGE_PREFIX.length);
        if (value.startsWith(LEGACY_STORAGE_PREFIX)) return value.slice(LEGACY_STORAGE_PREFIX.length);
        return value;
    };

    window.RPHubStorage = Object.freeze({
        cloneForStorage,
        deleteScopedStoredValue: (name, id) => dbDeleteWithLegacy(scopedStorageKey(name, id), legacyScopedStorageKey(name, id)),
        deleteStorageKeys,
        deleteStoredValue: (name) => dbDeleteWithLegacy(storageKey(name), legacyStorageKey(name)),
        getLegacyDb: () => legacyDb,
        getMainDb: () => mainDb,
        getScopedStoredValue: (name, id) => dbGetWithLegacy(scopedStorageKey(name, id), legacyScopedStorageKey(name, id)),
        getStoredValue: (name) => dbGetWithLegacy(storageKey(name), legacyStorageKey(name)),
        getStorageLogicalKey,
        initDB,
        isDatabaseClosingError,
        readStorageKeys,
        scanStorageEntries,
        setScopedStoredValue: (name, id, value, options = {}) => dbSet(scopedStorageKey(name, id), value, options),
        setStoredValue: (name, value, options = {}) => dbSet(storageKey(name), value, options),
        unwrapForStorage
    });
})();

// --- Memory utilities ---
(function () {
    const STORY_TIME_VALUE_PATTERN = /^(\d{1,6})年(\d{1,2})月(\d{1,2})日[ \t]+(\d{1,2})时$/;
    const STORY_TIME_LINE_PATTERN = /^[ \t]*【(\d{1,6})年(\d{1,2})月(\d{1,2})日[ \t]+(\d{1,2})时】[ \t]*(?=\r?\n|$)/;

    const formatStoryTimeMatch = (match) => {
        if (!match) return '';
        const month = Number(match[2]);
        const day = Number(match[3]);
        const hour = Number(match[4]);
        const year = Number(match[1]);
        const leapYear = year % 4 === 0 && (year % 100 !== 0 || year % 400 === 0);
        const maxDay = [31, leapYear ? 29 : 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31][month - 1] || 0;
        if (year <= 0 || day < 1 || day > maxDay || hour < 0 || hour > 23) return '';
        return `${match[1]}年${String(month).padStart(2, '0')}月${String(day).padStart(2, '0')}日 ${String(hour).padStart(2, '0')}时`;
    };

    const normalizeStoryTime = (value) => formatStoryTimeMatch(
        String(value || '').trim().match(STORY_TIME_VALUE_PATTERN)
    );

    const extractStoryTime = (text) => formatStoryTimeMatch(
        String(text || '').match(STORY_TIME_LINE_PATTERN)
    );

    const stripStoryTimeLine = (text) => {
        const source = String(text || '');
        if (!extractStoryTime(source)) return source;
        return source.replace(STORY_TIME_LINE_PATTERN, '').replace(/^\r?\n/, '').trimStart();
    };

    const isEmbeddingLike = (value) => Array.isArray(value) || ArrayBuffer.isView(value);
    const hasVectorEmbedding = (memory) => (
        (isEmbeddingLike(memory?.embedding) && memory.embedding.length > 0)
        || (typeof memory?.embeddingQ === 'string' && memory.embeddingQ.length > 0)
    );
    const isVectorMemory = (memory) => memory?.vectorMemory === true
        && memory.chunkMode === 'paragraph'
        && hasVectorEmbedding(memory);
    const isEnabledVectorMemory = (memory) => isVectorMemory(memory) && memory.enabled !== false;
    const markRuntimeRaw = (value) => {
        if (!value || typeof value !== 'object') return value;
        return typeof Vue?.markRaw === 'function' ? Vue.markRaw(value) : value;
    };

    const bytesToBase64 = (bytes) => {
        const source = bytes instanceof Uint8Array
            ? bytes
            : new Uint8Array(bytes.buffer, bytes.byteOffset || 0, bytes.byteLength);
        let binary = '';
        const chunkSize = 0x8000;
        for (let i = 0; i < source.length; i += chunkSize) {
            binary += String.fromCharCode(...source.subarray(i, i + chunkSize));
        }
        return btoa(binary);
    };

    const base64ToInt8Array = (base64) => {
        const binary = atob(String(base64 || ''));
        const bytes = new Uint8Array(binary.length);
        for (let i = 0; i < binary.length; i++) {
            bytes[i] = binary.charCodeAt(i);
        }
        return new Int8Array(bytes.buffer);
    };

    const quantizeEmbeddingForStorage = (embedding) => {
        if (!isEmbeddingLike(embedding) || embedding.length === 0) return null;
        let maxAbs = 0;
        for (let i = 0; i < embedding.length; i++) {
            const value = Math.abs(Number(embedding[i]) || 0);
            if (value > maxAbs) maxAbs = value;
        }
        if (maxAbs <= 0) return null;

        const quantized = new Int8Array(embedding.length);
        for (let i = 0; i < embedding.length; i++) {
            const scaled = Math.round(((Number(embedding[i]) || 0) / maxAbs) * 127);
            quantized[i] = Math.max(-127, Math.min(127, scaled));
        }

        return {
            embeddingQ: bytesToBase64(new Uint8Array(quantized.buffer)),
            embeddingScale: maxAbs / 127,
            embeddingDims: embedding.length,
            embeddingEncoding: 'int8:maxabs:v1'
        };
    };

    const prepareMemoryForRuntime = (memory) => {
        if (!memory || typeof memory !== 'object') return memory;
        delete memory.depth;
        const storyTime = normalizeStoryTime(memory.storyTime)
            || extractStoryTime(memory.paragraph || memory.sourceText || memory.summary);
        if (storyTime) memory.storyTime = storyTime;
        else delete memory.storyTime;

        if (typeof memory.embeddingQ === 'string' && memory.embeddingQ.length > 0) {
            try {
                memory.embedding = markRuntimeRaw(base64ToInt8Array(memory.embeddingQ));
            } catch (_) {
                memory.embedding = [];
            }
        } else if (isEmbeddingLike(memory.embedding)) {
            const packed = quantizeEmbeddingForStorage(memory.embedding);
            if (packed) {
                Object.assign(memory, packed);
                memory.embedding = markRuntimeRaw(base64ToInt8Array(packed.embeddingQ));
            }
        }
        if (isEmbeddingLike(memory.embedding)) memory.embedding = markRuntimeRaw(memory.embedding);
        return markRuntimeRaw(memory);
    };

    const prepareMemoriesForRuntime = (items) => Array.isArray(items)
        ? items.filter(isVectorMemory).map(prepareMemoryForRuntime)
        : [];

    const normalizeClassicMemoryForRuntime = (memory, includeSources = true) => {
        if (memory?.classicMemory !== true || !String(memory.summary || '').trim()) return null;
        const { storyTime: _storedStoryTime, ...memoryData } = memory;
        const fallbackTurn = Math.max(1, Number(memory.turn) || 1);
        const secondaryCompressed = memory.secondaryCompressed === true;
        const turnStart = secondaryCompressed
            ? Math.max(1, Number(memory.turnStart) || fallbackTurn)
            : fallbackTurn;
        const turnEnd = secondaryCompressed
            ? Math.max(turnStart, Number(memory.turnEnd) || fallbackTurn)
            : fallbackTurn;
        const normalized = {
            ...memoryData,
            turn: secondaryCompressed ? turnEnd : fallbackTurn,
            summary: String(memory.summary || '').trim(),
            sourceUserIds: Array.isArray(memory.sourceUserIds) ? memory.sourceUserIds.filter(Boolean) : [],
            sourceAssistantIds: Array.isArray(memory.sourceAssistantIds) ? memory.sourceAssistantIds.filter(Boolean) : []
        };
        if (secondaryCompressed) {
            normalized.secondaryCompressed = true;
            normalized.turnStart = turnStart;
            normalized.turnEnd = turnEnd;
            normalized.sourceMemories = includeSources && Array.isArray(memory.sourceMemories)
                ? memory.sourceMemories.map(item => normalizeClassicMemoryForRuntime(item, false)).filter(Boolean)
                : [];
        }
        return markRuntimeRaw(normalized);
    };

    const prepareClassicMemoriesForRuntime = (items) => Array.isArray(items)
        ? items.map(memory => normalizeClassicMemoryForRuntime(memory)).filter(Boolean)
        : [];

    const splitLongMemoryParagraph = (paragraph, maxLength = 1800) => {
        const text = String(paragraph || '').trim();
        if (!text) return [];
        if (text.length <= maxLength) return [text];

        const parts = [];
        let remaining = text;
        while (remaining.length > maxLength) {
            const windowText = remaining.slice(0, maxLength);
            const breakAt = Math.max(
                windowText.lastIndexOf('。'),
                windowText.lastIndexOf('！'),
                windowText.lastIndexOf('？'),
                windowText.lastIndexOf('.'),
                windowText.lastIndexOf('!'),
                windowText.lastIndexOf('?'),
                windowText.lastIndexOf('\n')
            );
            const cutAt = breakAt > Math.floor(maxLength * 0.55) ? breakAt + 1 : maxLength;
            parts.push(remaining.slice(0, cutAt).trim());
            remaining = remaining.slice(cutAt).trim();
        }
        if (remaining) parts.push(remaining);
        return parts.filter(Boolean);
    };

    const splitMemoryParagraphs = (text) => {
        const cleanText = String(text || '')
            .replace(/\r\n/g, '\n')
            .replace(/\n{3,}/g, '\n\n')
            .trim();
        if (!cleanText) return [];

        return cleanText
            .split(/\n\s*\n/g)
            .map(paragraph => paragraph.trim())
            .filter(Boolean)
            .flatMap(paragraph => splitLongMemoryParagraph(paragraph));
    };

    const mergeSmallMemoryParagraphs = (paragraphs, maxLength = 400) => {
        const merged = [];
        let current = null;
        const flush = () => {
            if (!current) return;
            merged.push(current);
            current = null;
        };

        paragraphs.forEach((paragraph, index) => {
            const text = String(paragraph || '').trim();
            if (!text) return;
            const paragraphNo = index + 1;
            if (!current) {
                current = { text, start: paragraphNo, end: paragraphNo };
                return;
            }
            const candidateText = `${current.text}\n\n${text}`;
            if (candidateText.length <= maxLength) {
                current.text = candidateText;
                current.end = paragraphNo;
                return;
            }
            flush();
            current = { text, start: paragraphNo, end: paragraphNo };
        });

        flush();
        return merged;
    };

    const trimMemoryText = (text, maxLength = 1800) => {
        const cleanText = String(text || '').replace(/\n{3,}/g, '\n\n').trim();
        return cleanText.length <= maxLength ? cleanText : `${cleanText.slice(0, maxLength)}...`;
    };

    const getClassicMemoryKey = (sourceAssistantIds, turn = 0) => {
        const ids = Array.isArray(sourceAssistantIds) ? sourceAssistantIds.filter(Boolean) : [];
        return ids.length > 0 ? ids.join('|') : `turn:${Number(turn) || 0}`;
    };

    const normalizeEmbedding = (embedding) => {
        const rawVector = isEmbeddingLike(embedding)
            ? embedding
            : (isEmbeddingLike(embedding?.values) ? embedding.values : []);
        return rawVector
            .map(value => Number(value))
            .filter(value => Number.isFinite(value));
    };

    const cosineSimilarity = (a, b) => {
        if (!isEmbeddingLike(a) || !isEmbeddingLike(b) || a.length === 0 || b.length === 0) return -1;
        const length = Math.min(a.length, b.length);
        let dot = 0;
        let normA = 0;
        let normB = 0;
        for (let i = 0; i < length; i++) {
            const av = Number(a[i]) || 0;
            const bv = Number(b[i]) || 0;
            dot += av * bv;
            normA += av * av;
            normB += bv * bv;
        }
        if (normA === 0 || normB === 0) return -1;
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    };

    const normalizeVectorMemoryFingerprintText = (text) => String(text || '')
        .replace(/\s+/g, '')
        .replace(/[，。、“”‘’：；！？,.!?;:"'`~]/g, '');

    const getVectorMemoryContentFingerprint = (text) => {
        const normalized = normalizeVectorMemoryFingerprintText(text);
        return normalized.length >= 80 ? normalized.slice(0, 1000) : '';
    };

    const extractVectorQueryTerms = (text) => {
        const normalized = String(text || '')
            .replace(/[^\p{Script=Han}A-Za-z0-9_]+/gu, ' ')
            .trim();
        if (!normalized) return [];

        const stopTerms = new Set([
            '是不是', '有没有', '为什么', '怎么样', '怎么办', '什么', '这个', '那个',
            '还是', '还在', '还会', '了吗', '吗', '呢', '啊', '吧', '的', '了', '我', '你', '她', '他'
        ]);
        const terms = new Set();
        normalized.split(/\s+/).filter(Boolean).forEach(part => {
            if (/^[A-Za-z0-9_]{2,}$/.test(part)) {
                terms.add(part.toLowerCase());
                return;
            }
            const han = part.replace(/[^\p{Script=Han}]/gu, '');
            if (han.length >= 2) {
                for (let size = Math.min(4, han.length); size >= 2; size--) {
                    for (let index = 0; index <= han.length - size; index++) {
                        const term = han.slice(index, index + size);
                        if (!stopTerms.has(term)) terms.add(term);
                    }
                }
            } else if (han.length === 1 && !stopTerms.has(han)) {
                terms.add(han);
            }
        });
        return [...terms]
            .filter(term => term.length > 0 && !stopTerms.has(term))
            .sort((a, b) => b.length - a.length)
            .slice(0, 20);
    };

    const getVectorLexicalMatch = (memory, queryTerms) => {
        if (!Array.isArray(queryTerms) || queryTerms.length === 0) return { hits: 0, boost: 0, matched: [] };
        const text = String(`${memory?.sourceText || ''}\n${memory?.summary || ''}`).toLowerCase();
        const matched = queryTerms.filter(term => text.includes(String(term).toLowerCase()));
        return {
            hits: matched.length,
            boost: Math.min(0.08, matched.length * 0.015),
            matched
        };
    };

    const sortVectorMemoriesByTime = (items) => {
        const orderNumber = (value, fallback) => {
            if (value === null || value === undefined || value === '') return fallback;
            const number = Number(value);
            return Number.isFinite(number) ? number : fallback;
        };
        return [...(Array.isArray(items) ? items : [])].sort((a, b) => {
            const turnDiff = orderNumber(a?.turn, Number.MAX_SAFE_INTEGER) - orderNumber(b?.turn, Number.MAX_SAFE_INTEGER);
            if (turnDiff !== 0) return turnDiff;
            const sequenceDiff = orderNumber(a?.sequence, 0) - orderNumber(b?.sequence, 0);
            return sequenceDiff !== 0 ? sequenceDiff : (b?.vectorScore || 0) - (a?.vectorScore || 0);
        });
    };

    const getVectorMemoryText = (memory) => String(
        memory?.paragraph || memory?.summary || memory?.sourceText || ''
    ).trim();

    const getVectorMemoryFingerprint = (memory) => (
        getVectorMemoryContentFingerprint(getVectorMemoryText(memory))
        || `${memory?.turn || ''}:${memory?.sequence || ''}:${normalizeVectorMemoryFingerprintText(getVectorMemoryText(memory))}`
    );

    const buildMergedVectorMemoryFallbackText = (items) => {
        const orderedItems = sortVectorMemoriesByTime(items);
        let userBlock = '';
        const roleBlocks = [];
        orderedItems.forEach(memory => {
            const text = getVectorMemoryText(memory);
            if (!text) return;
            const roleMarker = '\n角色卡：';
            const roleIndex = text.indexOf(roleMarker);
            if (roleIndex >= 0) {
                if (!userBlock) userBlock = text.slice(0, roleIndex).trim();
                const roleText = text.slice(roleIndex + roleMarker.length).trim();
                if (roleText) roleBlocks.push(roleText);
            } else if (!roleBlocks.includes(text)) {
                roleBlocks.push(text);
            }
        });
        const roleBlock = roleBlocks.filter(Boolean).join('\n\n').trim();
        return [userBlock, roleBlock ? `角色卡：${roleBlock}` : ''].filter(Boolean).join('\n\n').trim();
    };

    window.RPHubMemoryUtils = Object.freeze({
        buildMergedVectorMemoryFallbackText,
        cosineSimilarity,
        extractStoryTime,
        extractVectorQueryTerms,
        getClassicMemoryKey,
        getVectorMemoryContentFingerprint,
        getVectorMemoryFingerprint,
        getVectorMemoryText,
        getVectorLexicalMatch,
        hasVectorEmbedding,
        isEmbeddingLike,
        isEnabledVectorMemory,
        isVectorMemory,
        markRuntimeRaw,
        mergeSmallMemoryParagraphs,
        normalizeEmbedding,
        normalizeStoryTime,
        prepareClassicMemoriesForRuntime,
        prepareMemoryForRuntime,
        prepareMemoriesForRuntime,
        quantizeEmbeddingForStorage,
        splitLongMemoryParagraph,
        splitMemoryParagraphs,
        sortVectorMemoriesByTime,
        stripStoryTimeLine,
        trimMemoryText
    });
})();

// --- Context utilities ---
(function () {
    const { prompts: BUILTIN_PROMPTS } = window.RPHubBuiltinContent;
    const ROLE_MEMORY_VECTOR_RECALL_TAG = 'role_memory_vector_recall';
    const ROLE_MEMORY_VECTOR_RECALL_OPEN_TAG = `<${ROLE_MEMORY_VECTOR_RECALL_TAG}>`;
    const ROLE_MEMORY_VECTOR_RECALL_CLOSE_TAG = `</${ROLE_MEMORY_VECTOR_RECALL_TAG}>`;

    const escapeXmlAttribute = (value) => String(value ?? '')
        .replace(/&/g, '&amp;')
        .replace(/"/g, '&quot;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;');
    const escapeXmlText = (value) => String(value ?? '')
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;');
    const indentXmlText = (text, spaces = 0) => {
        const prefix = ' '.repeat(Math.max(0, spaces));
        return String(text || '')
            .split(/\r?\n/)
            .map(line => `${prefix}${line}`)
            .join('\n');
    };

    const isVectorMemoryRecallContent = (content) => {
        const text = String(content || '').trimStart();
        return text.startsWith(ROLE_MEMORY_VECTOR_RECALL_OPEN_TAG)
            || text.startsWith('[角色记忆 - 向量召回]');
    };
    const isRoleMemoryContextContent = (content) => {
        const text = String(content || '').trimStart();
        return text.startsWith('[角色记忆') || text.startsWith(ROLE_MEMORY_VECTOR_RECALL_OPEN_TAG);
    };

    const getMessageSourceIndexes = (message, index, trackSources) => {
        const source = message?._sourceIndexes;
        if (!Array.isArray(source)) return trackSources ? [index] : [];
        return [...source];
    };

    const toPlainContextMessage = (message, index, trackSources = false) => {
        const nextMessage = {
            role: message.role,
            name: message.name,
            content: String(message.content || '')
        };
        if (message.id) nextMessage.id = message.id;
        if (Number.isFinite(message._contextFloor)) nextMessage._contextFloor = message._contextFloor;
        if (message._preventContextMerge === true) nextMessage._preventContextMerge = true;
        if (trackSources) nextMessage._sourceIndexes = getMessageSourceIndexes(message, index, true);
        else if (Array.isArray(message?._sourceIndexes)) nextMessage._sourceIndexes = getMessageSourceIndexes(message, index, false);
        if (Array.isArray(message?._worldInfoEntries)) nextMessage._worldInfoEntries = message._worldInfoEntries;
        return nextMessage;
    };

    const mergeConsecutiveRoleMessages = (messages, options = {}) => {
        const {
            mergeRoles = ['user', 'assistant'],
            includeSystem = true,
            trackSources = false
        } = options;
        const mergeRoleSet = new Set(mergeRoles);
        const merged = [];
        (Array.isArray(messages) ? messages : []).forEach((message, index) => {
            if (!message || typeof message !== 'object') return;
            if (!includeSystem && message.role === 'system') return;

            const nextMessage = toPlainContextMessage(message, index, trackSources);
            const previous = merged[merged.length - 1];
            if (previous
                && previous.role === nextMessage.role
                && mergeRoleSet.has(nextMessage.role)
                && previous._preventContextMerge !== true
                && nextMessage._preventContextMerge !== true) {
                previous.content = [previous.content, nextMessage.content].filter(Boolean).join('\n\n');
                if (!previous.name && nextMessage.name) previous.name = nextMessage.name;
                if (Number.isFinite(nextMessage._contextFloor)) {
                    previous._contextFloor = Number.isFinite(previous._contextFloor)
                        ? Math.min(previous._contextFloor, nextMessage._contextFloor)
                        : nextMessage._contextFloor;
                }
                if (trackSources || previous._sourceIndexes || nextMessage._sourceIndexes) {
                    previous._sourceIndexes = [
                        ...(previous._sourceIndexes || []),
                        ...(nextMessage._sourceIndexes || [])
                    ];
                }
                if (previous._worldInfoEntries || nextMessage._worldInfoEntries) {
                    previous._worldInfoEntries = [
                        ...(previous._worldInfoEntries || []),
                        ...(nextMessage._worldInfoEntries || [])
                    ];
                }
                return;
            }
            merged.push(nextMessage);
        });
        return merged;
    };

    const postprocessContextMessages = (messages) => mergeConsecutiveRoleMessages(messages, {
        mergeRoles: ['user', 'assistant'],
        includeSystem: true
    });

    const getPostprocessedChatMessages = (messages, options = {}) => {
        const { includeSystem = false } = options;
        return mergeConsecutiveRoleMessages(messages, {
            mergeRoles: ['user', 'assistant'],
            includeSystem,
            trackSources: true
        });
    };

    const buildConversationTurnSnapshot = (messages, options = {}) => {
        const { includeSystem = false, alreadyPostprocessed = false } = options;
        const processedMessages = alreadyPostprocessed
            ? (Array.isArray(messages) ? messages : [])
                .filter(message => message && typeof message === 'object' && (includeSystem || message.role !== 'system'))
                .map((message, index) => {
                    const nextMessage = toPlainContextMessage(message, index, false);
                    nextMessage._sourceIndexes = getMessageSourceIndexes(message, index, true);
                    return nextMessage;
                })
            : getPostprocessedChatMessages(messages, { includeSystem });

        const turns = [];
        let pendingUser = null;
        processedMessages.forEach((message, messageIndex) => {
            if (!message || message.role === 'system') return;
            const sourceIndexes = Array.isArray(message._sourceIndexes) ? message._sourceIndexes : [messageIndex];
            const sourceStartIndex = sourceIndexes.length ? Math.min(...sourceIndexes) : messageIndex;
            const sourceEndIndex = sourceIndexes.length ? Math.max(...sourceIndexes) : messageIndex;

            if (message.role === 'user') {
                pendingUser = { message, messageIndex, sourceIndexes, sourceStartIndex, sourceEndIndex };
                return;
            }
            if (message.role !== 'assistant' || !pendingUser) return;

            const turn = turns.length + 1;
            turns.push({
                turn,
                user: pendingUser.message,
                assistant: message,
                messages: [pendingUser.message, message],
                messageIndexes: [pendingUser.messageIndex, messageIndex],
                sourceIndexes: [...pendingUser.sourceIndexes, ...sourceIndexes],
                startIndex: pendingUser.sourceStartIndex,
                endIndex: sourceEndIndex
            });
            pendingUser = null;
        });
        return { messages: processedMessages, turns };
    };

    const getConversationTurnAtIndexFromSnapshot = (snapshot, index) => {
        if (!Number.isFinite(index) || index < 0) return null;
        const turns = Array.isArray(snapshot?.turns) ? snapshot.turns : [];
        const matchedTurn = turns.find(turn => (turn.sourceIndexes || []).includes(index));
        if (matchedTurn) return matchedTurn.turn;
        return turns.filter(turn => turn.endIndex < index).length + 1;
    };

    const toNonNegativeNumber = (value, fallback = 0) => {
        const number = Number(value);
        return Number.isFinite(number) ? Math.max(0, number) : fallback;
    };

    const createWorldInfoRegex = (pattern) => {
        let source = String(pattern || '');
        let flags = 'i';
        if (source.startsWith('/') && source.lastIndexOf('/') > 0) {
            const lastSlash = source.lastIndexOf('/');
            const potentialFlags = source.slice(lastSlash + 1);
            if (/^[dgimsuvy]*$/.test(potentialFlags)) {
                source = source.slice(1, lastSlash);
                flags = potentialFlags;
            }
        }
        flags = flags.replace(/g/g, '');
        if (!flags.includes('i')) flags += 'i';
        if (/\\[pP]\{/.test(source) && !flags.includes('u')) flags += 'u';
        return new RegExp(source, flags);
    };

    const worldInfoKeyMatchesText = (entry, key, text) => {
        const rawKey = String(key || '').trim();
        const rawText = String(text || '');
        if (!rawKey || !rawText) return false;
        if (!entry?.useRegex) return rawText.toLowerCase().includes(rawKey.toLowerCase());
        try {
            return createWorldInfoRegex(rawKey).test(rawText);
        } catch (_) {
            console.warn(`Invalid world info regex: ${rawKey}`);
            return false;
        }
    };

    const resolveWorldInfoEntries = (entries, messages, settings = {}, options = {}) => {
        const activeEntries = (Array.isArray(entries) ? entries : []).filter(entry => entry?.enabled !== false);
        const chatMessages = Array.isArray(messages) ? messages : [];
        const random = typeof options.random === 'function' ? options.random : Math.random;
        const probabilityResults = new Map();
        const triggerMap = new Map();

        const passesProbability = (entry) => {
            const probability = Math.min(100, toNonNegativeNumber(entry?.probability, 100));
            if (entry?.useProbability === false || probability >= 100) return true;
            if (!probabilityResults.has(entry)) {
                probabilityResults.set(entry, probability > 0 && random() * 100 < probability);
            }
            return probabilityResults.get(entry) === true;
        };

        activeEntries.forEach(entry => {
            if (entry.constant) {
                triggerMap.set(entry, { score: Infinity, matchedKeys: ['常驻 (Constant)'] });
                return;
            }

            const rawScanDepth = toNonNegativeNumber(entry.scanDepth ?? settings.scanDepth, 0);
            const maxScanDepth = toNonNegativeNumber(settings.maxDepth, 0);
            const scanDepth = maxScanDepth > 0 ? Math.min(rawScanDepth, maxScanDepth) : rawScanDepth;
            const keys = Array.isArray(entry.keys) ? entry.keys : [];
            if (scanDepth === 0 || keys.length === 0 || !passesProbability(entry)) return;

            const scanText = chatMessages.slice(-scanDepth).map(message => message?.content || '').join('\n');
            const matchedKeys = keys
                .map(key => String(key || '').trim())
                .filter(key => key && worldInfoKeyMatchesText(entry, key, scanText));
            if (matchedKeys.length > 0) {
                triggerMap.set(entry, { score: matchedKeys.length, matchedKeys: [...new Set(matchedKeys)] });
            }
        });

        const resolvedEntries = [...triggerMap.keys()].sort((a, b) => {
            if (a.constant && !b.constant) return -1;
            if (!a.constant && b.constant) return 1;
            return (b.order || 0) - (a.order || 0);
        });
        const groups = {
            system_top: [],
            global_note: [],
            before_char: [],
            after_char: [],
            user_top: [],
            assistant_top: [],
            at_depth: []
        };
        resolvedEntries.forEach(entry => {
            const position = Object.prototype.hasOwnProperty.call(groups, entry.position) ? entry.position : 'at_depth';
            groups[position].push(entry);
        });
        Object.values(groups).forEach(group => group.sort((a, b) => (a.order || 0) - (b.order || 0)));

        return { entries: resolvedEntries, groups, triggerMap };
    };

    const buildContextViewerState = ({
        messages,
        budgetedEntries,
        triggeredEntries,
        postprocessedChatHistory,
        worldInfoSettings
    }) => {
        const escapeHtml = (value) => String(value || '')
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;')
            .replace(/'/g, '&#039;');
        const getDisplayName = (entry) => entry.comment || entry.name || '未命名条目';
        const floorInfo = new Map();
        const chatMessages = Array.isArray(postprocessedChatHistory) ? postprocessedChatHistory : [];
        const triggerMap = triggeredEntries instanceof Map ? triggeredEntries : new Map();
        const scanDepth = toNonNegativeNumber(worldInfoSettings?.scanDepth, 2);
        const maxScanDepth = toNonNegativeNumber(worldInfoSettings?.maxDepth, 0);

        triggerMap.forEach((data, entry) => {
            if (!data.matchedKeys) return;
            const rawEntryScanDepth = toNonNegativeNumber(entry.scanDepth ?? scanDepth, 0);
            const entryScanDepth = maxScanDepth > 0 ? Math.min(rawEntryScanDepth, maxScanDepth) : rawEntryScanDepth;
            const entryStart = Math.max(0, chatMessages.length - entryScanDepth);

            data.matchedKeys.forEach(key => {
                if (key === '常驻 (Constant)') return;
                for (let index = entryStart; index < chatMessages.length; index++) {
                    if (!worldInfoKeyMatchesText(entry, key, chatMessages[index].content)) continue;
                    if (!floorInfo.has(key)) floorInfo.set(key, new Set());
                    floorInfo.get(key).add(index + 1);
                }
            });
        });

        const getTriggerText = (entry) => {
            const entryData = triggerMap.get(entry);
            if (!entryData?.matchedKeys) return '关联触发';
            return entryData.matchedKeys.map(key => {
                if (key === '常驻 (Constant)') return '常驻';
                const floors = floorInfo.get(key);
                return floors?.size > 0
                    ? `${key} (${Array.from(floors).map(floor => `F${floor}`).join(', ')})`
                    : key;
            }).join(', ');
        };

        const triggeredWorldInfos = (Array.isArray(budgetedEntries) ? budgetedEntries : []).map(entry => ({
            name: getDisplayName(entry),
            triggers: getTriggerText(entry)
        }));
        let displayedFloor = 0;
        const contextMessages = (Array.isArray(messages) ? messages : []).map(message => {
            const injectedWorldInfos = new Map();
            (Array.isArray(message._worldInfoEntries) ? message._worldInfoEntries : []).forEach(entry => {
                if (entry) injectedWorldInfos.set(getDisplayName(entry), getTriggerText(entry));
            });

            const isMemory = message.role !== 'system' && isRoleMemoryContextContent(message.content);
            if (isMemory) {
                const memoryContent = String(message.content || '');
                const fragmentCount = (memoryContent.match(/<memory_fragment\b/gi) || []).length;
                const closedFragmentCount = (memoryContent.match(/<\/memory_fragment>/gi) || []).length;
                const legacyFragmentCount = memoryContent.split('\n')
                    .filter(line => /^<第\s*.+?次对话_相似度\s+.+>$/.test(line.trim())).length;
                const vectorFragmentCount = fragmentCount > 0
                    ? Math.max(1, closedFragmentCount > 0 ? fragmentCount : Math.ceil(fragmentCount / 2))
                    : legacyFragmentCount;
                const isVectorMemory = isVectorMemoryRecallContent(memoryContent);
                const memoryName = isVectorMemory ? '角色记忆（向量召回）' : '角色记忆';
                const memoryTrigger = isVectorMemory ? `已注入 ${vectorFragmentCount} 个向量分片` : '已注入';
                injectedWorldInfos.set(memoryName, memoryTrigger);
                if (!triggeredWorldInfos.some(item => item.name === memoryName)) {
                    triggeredWorldInfos.push({ name: memoryName, triggers: memoryTrigger });
                }
            }

            let renderedContent = escapeHtml(message.content);
            Array.from(floorInfo.keys()).sort((a, b) => b.length - a.length).forEach(key => {
                if (!key) return;
                const escapedKey = key.replace(/[-\/\\^$*+?.()|[\]{}]/g, '\\$&');
                renderedContent = renderedContent.replace(
                    new RegExp(`(${escapedKey})(?![^<]*>)`, 'gi'),
                    '<mark class="bg-yellow-200/80 text-yellow-900 border-b border-yellow-400 font-bold px-0.5 mx-px rounded shadow-sm">$1</mark>'
                );
            });
            if (isMemory) {
                renderedContent = renderedContent
                    .replace(/&lt;\/?(?:role_memory_vector_recall|memory_fragment)\b[\s\S]*?&gt;/g,
                        '<mark class="bg-purple-200/80 text-purple-900 border-b border-purple-400 font-bold px-1 rounded shadow-sm">$&</mark>')
                    .replace(/\[角色记忆[^\]]*\]/g,
                        '<mark class="bg-purple-200/80 text-purple-900 border-b border-purple-400 font-bold px-1 rounded shadow-sm">$&</mark>')
                    .replace(/\[——[^—]*——\]/g,
                        '<mark class="bg-purple-100/80 text-purple-700 font-semibold px-0.5 rounded">$&</mark>')
                    .replace(/\[向量召回[^\]]*\]/g,
                        '<mark class="bg-teal-100/90 text-teal-800 border-b border-teal-300 font-semibold px-0.5 rounded">$&</mark>');
            }

            return {
                role: message.role,
                name: message.name,
                content: message.content,
                renderedContent,
                floor: Number.isFinite(message._contextFloor) ? ++displayedFloor : null,
                isMemory,
                wiTriggers: Array.from(injectedWorldInfos.entries()).map(([name, triggers]) => ({ name, triggers }))
            };
        });

        return { contextMessages, triggeredWorldInfos };
    };

    const injectContextMessages = ({
        messages,
        worldInfoGroups,
        vectorMemories = [],
        vectorDepth = 4,
        safeTargetLimit = 1
    }) => {
        const groups = worldInfoGroups || {};
        const finalMessages = [...(Array.isArray(messages) ? messages : [])];
        const joinEntries = (entries) => entries
            .map(entry => `[${entry.comment || 'Entry'}]\n${entry.content}`)
            .join('\n\n');
        const findDepthIndex = (depth) => {
            const reversedMessages = [...finalMessages].reverse();
            let countdown = depth;
            let targetIndex = -1;
            for (let index = 0; index < reversedMessages.length; index++) {
                if (reversedMessages[index].role === 'user' || reversedMessages[index].role === 'assistant') countdown--;
                if (countdown < 0) {
                    targetIndex = reversedMessages.length - 1 - index;
                    break;
                }
            }
            return Math.max(targetIndex, safeTargetLimit);
        };

        const depthEntries = Array.isArray(groups.at_depth) ? groups.at_depth : [];
        depthEntries.sort((a, b) => (a.order || 0) - (b.order || 0));
        depthEntries.forEach(entry => {
            finalMessages.splice(findDepthIndex(entry.depth !== undefined ? entry.depth : 4), 0, {
                role: 'user',
                content: `[${entry.comment || 'Entry'}]\n${entry.content}`,
                _worldInfoEntries: [entry]
            });
        });

        if (vectorMemories.length > 0) {
            const memoryContent = vectorMemories.map(memory => {
                const turn = escapeXmlAttribute(memory.turn || '?');
                const score = escapeXmlAttribute(Number.isFinite(memory.vectorScore)
                    ? `${(memory.vectorScore * 100).toFixed(1)}%`
                    : 'unknown');
                const storyTime = escapeXmlAttribute(memory.storyTime || '');
                const fragmentText = indentXmlText(memory.paragraph || memory.summary || '', 4);
                return [
                    `  <memory_fragment turn="${turn}" similarity="${score}" story_time="${storyTime}">`,
                    fragmentText,
                    '  </memory_fragment>'
                ].join('\n');
            }).join('\n\n');
            finalMessages.splice(findDepthIndex(Number(vectorDepth) || 4), 0, {
                role: 'user',
                // [LuzzyRP patch 016] 召回块禁止与相邻用户消息合并：一旦被并入前一条 user 消息，
                // 上下文查看器的「角色记忆（向量召回）」startsWith 标注即失效（v1.2.1）
                _preventContextMerge: true,
                content: [
                    ROLE_MEMORY_VECTOR_RECALL_OPEN_TAG,
                    '  <description>',
                    ...BUILTIN_PROMPTS.vectorMemoryRecallDescription,
                    '  </description>',
                    memoryContent,
                    ROLE_MEMORY_VECTOR_RECALL_CLOSE_TAG
                ].join('\n')
            });
        }

        const userTopEntries = Array.isArray(groups.user_top) ? groups.user_top : [];
        if (userTopEntries.length > 0) {
            const lastUserMessage = finalMessages.slice().reverse().find(message => message.role === 'user');
            if (lastUserMessage) {
                lastUserMessage.content = `${joinEntries(userTopEntries)}\n\n${lastUserMessage.content}`;
                lastUserMessage._worldInfoEntries = [
                    ...(lastUserMessage._worldInfoEntries || []),
                    ...userTopEntries
                ];
            }
        }

        const assistantTopEntries = Array.isArray(groups.assistant_top) ? groups.assistant_top : [];
        if (assistantTopEntries.length > 0) {
            const lastAssistantMessage = finalMessages.slice().reverse().find(message => message?.role === 'assistant');
            if (lastAssistantMessage) {
                lastAssistantMessage.content = `${joinEntries(assistantTopEntries)}\n\n${lastAssistantMessage.content}`;
                lastAssistantMessage._worldInfoEntries = [
                    ...(lastAssistantMessage._worldInfoEntries || []),
                    ...assistantTopEntries
                ];
            }
        }
        return finalMessages;
    };

    window.RPHubContextUtils = {
        ROLE_MEMORY_VECTOR_RECALL_CLOSE_TAG,
        ROLE_MEMORY_VECTOR_RECALL_OPEN_TAG,
        ROLE_MEMORY_VECTOR_RECALL_TAG,
        buildContextViewerState,
        buildConversationTurnSnapshot,
        escapeXmlAttribute,
        escapeXmlText,
        getConversationTurnAtIndexFromSnapshot,
        getPostprocessedChatMessages,
        indentXmlText,
        injectContextMessages,
        isRoleMemoryContextContent,
        isVectorMemoryRecallContent,
        mergeConsecutiveRoleMessages,
        postprocessContextMessages,
        resolveWorldInfoEntries,
        toNonNegativeNumber,
        worldInfoKeyMatchesText
    };
})();

// --- Story branches ---
(function () {
    const STORY_BRANCH_MAIN_ID = 'main';
    const STORY_BRANCH_SCOPE_SEPARATOR = '__branch__';
    const STORY_BRANCH_CHAT_EXPORT_TYPE = 'rp-hub-branch-chat';
    const STORY_BRANCH_CHAT_EXPORT_VERSION = 1;

    const getStoryBranchScopeId = (characterId, branchId = STORY_BRANCH_MAIN_ID) => {
        if (!characterId || !branchId || branchId === STORY_BRANCH_MAIN_ID) return characterId || null;
        return `${characterId}${STORY_BRANCH_SCOPE_SEPARATOR}${branchId}`;
    };

    const getStoryBranchOwnerId = (scopeId) => String(scopeId || '').split(STORY_BRANCH_SCOPE_SEPARATOR)[0];

    const getConversationBodyLength = (history = []) => history.reduce((total, message) => {
        if (!['user', 'assistant'].includes(message?.role)) return total;
        return total + window.RPHubUtils.parseCot(message.content || '').main.length;
    }, 0);

    const formatStoryBranchWordCount = (count) => {
        const units = Math.max(0, Number(count) || 0) / 10000;
        const decimals = units < 1 ? 2 : units < 100 ? 1 : 0;
        const formatted = units.toFixed(decimals);
        return `${decimals > 0 ? formatted.replace(/\.?0+$/, '') : formatted}W`;
    };

    const createMainStoryBranch = (character) => ({
        id: STORY_BRANCH_MAIN_ID,
        name: '主线',
        parentId: null,
        createdAt: Number(character?.createdAt) || Date.now(),
        updatedAt: Date.now(),
        forkFloor: 0,
        floorCount: 0,
        messageCount: 0,
        wordCount: 0
    });

    const normalizeStoryBranches = (character, saved) => {
        const source = Array.isArray(saved?.branches) ? saved.branches : [];
        const seen = new Set();
        const branches = source.map((branch, index) => {
            const id = String(branch?.id || '').trim();
            if (!id || seen.has(id)) return null;
            seen.add(id);
            const fallbackName = id === STORY_BRANCH_MAIN_ID ? '主线' : `分支 ${index + 1}`;
            const name = id === STORY_BRANCH_MAIN_ID
                ? '主线'
                : String(branch?.name || fallbackName).trim().replace(/^路线(?=\s*\d+$)/, '分支');
            return {
                id,
                name: name.slice(0, 30),
                parentId: id === STORY_BRANCH_MAIN_ID ? null : String(branch?.parentId || STORY_BRANCH_MAIN_ID),
                createdAt: Number(branch?.createdAt) || Date.now(),
                updatedAt: Number(branch?.updatedAt) || Number(branch?.createdAt) || Date.now(),
                forkFloor: Math.max(0, Number(branch?.forkFloor) || 0),
                floorCount: Math.max(0, Number(branch?.floorCount) || 0),
                messageCount: Math.max(0, Number(branch?.messageCount) || 0),
                wordCount: Math.max(0, Number(branch?.wordCount) || 0)
            };
        }).filter(Boolean);

        if (!seen.has(STORY_BRANCH_MAIN_ID)) branches.unshift(createMainStoryBranch(character));
        const validIds = new Set(branches.map(branch => branch.id));
        branches.forEach(branch => {
            if (branch.id !== STORY_BRANCH_MAIN_ID && !validIds.has(branch.parentId)) {
                branch.parentId = STORY_BRANCH_MAIN_ID;
            }
        });
        return branches;
    };

    const createStoryRouteMap = ({ branches, activeBranchId, selectedBranchId, activeWordCount, activeFloorCount }) => {
        const NODE_WIDTH = 124;
        const NODE_HEIGHT = 64;
        const HORIZONTAL_GAP = 28;
        const LEVEL_GAP = 70;
        const PADDING_X = 28;
        const PADDING_Y = 30;
        const branchesById = new Map(branches.map(branch => [branch.id, branch]));
        const childrenByParent = new Map();
        branches.forEach(branch => {
            const parentId = branch.parentId || null;
            if (!childrenByParent.has(parentId)) childrenByParent.set(parentId, []);
            childrenByParent.get(parentId).push(branch);
        });
        childrenByParent.forEach(children => children.sort((a, b) => a.createdAt - b.createdAt));

        const positions = new Map();
        const visiting = new Set();
        let leafIndex = 0;
        let maxDepth = 0;
        const placeBranch = (branch, depth) => {
            if (positions.has(branch.id)) return positions.get(branch.id).centerX;
            if (visiting.has(branch.id)) return PADDING_X + NODE_WIDTH / 2;
            visiting.add(branch.id);
            maxDepth = Math.max(maxDepth, depth);
            const childCenters = (childrenByParent.get(branch.id) || [])
                .filter(child => child.id !== branch.id)
                .map(child => placeBranch(child, depth + 1));
            const centerX = childCenters.length
                ? childCenters.reduce((total, value) => total + value, 0) / childCenters.length
                : PADDING_X + NODE_WIDTH / 2 + leafIndex++ * (NODE_WIDTH + HORIZONTAL_GAP);
            const y = PADDING_Y + depth * (NODE_HEIGHT + LEVEL_GAP);
            positions.set(branch.id, { x: centerX - NODE_WIDTH / 2, y, centerX, centerY: y + NODE_HEIGHT / 2 });
            visiting.delete(branch.id);
            return centerX;
        };

        const roots = branches
            .filter(branch => !branch.parentId || !branchesById.has(branch.parentId))
            .sort((a, b) => (a.id === STORY_BRANCH_MAIN_ID ? -1 : b.id === STORY_BRANCH_MAIN_ID ? 1 : a.createdAt - b.createdAt));
        roots.forEach(branch => placeBranch(branch, 0));
        branches.forEach(branch => {
            if (!positions.has(branch.id)) placeBranch(branch, 0);
        });

        const collectRouteIds = (startId) => {
            const ids = new Set();
            let branch = branchesById.get(startId);
            while (branch && !ids.has(branch.id)) {
                ids.add(branch.id);
                branch = branchesById.get(branch.parentId);
            }
            return ids;
        };
        const activeRouteIds = collectRouteIds(activeBranchId);
        const selectedRouteIds = collectRouteIds(selectedBranchId);
        const routeColumns = Math.max(1, leafIndex);
        const naturalWidth = PADDING_X * 2 + routeColumns * NODE_WIDTH + (routeColumns - 1) * HORIZONTAL_GAP;
        const width = Math.max(360, naturalWidth);
        const horizontalOffset = (width - naturalWidth) / 2;
        const naturalHeight = PADDING_Y * 2 + (maxDepth + 1) * NODE_HEIGHT + maxDepth * LEVEL_GAP;
        const height = Math.max(170, naturalHeight);
        const verticalOffset = (height - naturalHeight) / 2;
        const nodes = branches.map(branch => {
            const position = positions.get(branch.id);
            const isActive = branch.id === activeBranchId;
            const wordCount = isActive ? activeWordCount : branch.wordCount;
            return {
                ...branch,
                ...position,
                x: position.x + horizontalOffset,
                y: position.y + verticalOffset,
                centerX: position.centerX + horizontalOffset,
                centerY: position.centerY + verticalOffset,
                isActive,
                isSelected: branch.id === selectedBranchId,
                isOnActiveRoute: activeRouteIds.has(branch.id),
                isOnSelectedRoute: selectedRouteIds.has(branch.id),
                floorCount: isActive ? activeFloorCount : branch.floorCount,
                wordCount,
                wordCountText: formatStoryBranchWordCount(wordCount)
            };
        });
        const links = nodes.filter(node => positions.has(node.parentId)).map(node => {
            const parent = positions.get(node.parentId);
            const startX = parent.centerX + horizontalOffset;
            const startY = parent.y + verticalOffset + NODE_HEIGHT;
            const endX = node.centerX;
            const endY = node.y;
            const middleY = (startY + endY) / 2;
            return {
                id: `${node.parentId}-${node.id}`,
                path: `M ${startX} ${startY} C ${startX} ${middleY}, ${endX} ${middleY}, ${endX} ${endY}`,
                isActive: activeRouteIds.has(node.id),
                isSelected: selectedRouteIds.has(node.id)
            };
        });
        return { nodes, links, width, height };
    };

    window.RPHubStoryBranches = {
        STORY_BRANCH_CHAT_EXPORT_TYPE,
        STORY_BRANCH_CHAT_EXPORT_VERSION,
        STORY_BRANCH_MAIN_ID,
        createStoryRouteMap,
        getConversationBodyLength,
        getStoryBranchOwnerId,
        getStoryBranchScopeId,
        normalizeStoryBranches
    };
})();

// --- UI-template utilities ---
(function () {
    const { generateUUID } = window.RPHubUtils;
    const DEFAULT_HTML = '';
    const DEFAULT_VARIABLES = {};

    const cloneUiObject = (value) => JSON.parse(JSON.stringify(value || {}));
    const cloneUiValue = (value) => value === undefined ? undefined : JSON.parse(JSON.stringify(value));

    const stripUiTemplateCodeFence = (value) => {
        const text = String(value || '').trim();
        const fenced = text.match(/^```[a-zA-Z0-9_-]*\s*\n?([\s\S]*?)\s*```$/);
        return (fenced ? fenced[1] : text).trim();
    };

    const inferInitialUiTemplateState = (template = {}, variableState = null) => {
        if (template.initialVariableState && typeof template.initialVariableState === 'object') {
            return cloneUiObject(template.initialVariableState);
        }
        let baseState = cloneUiObject(variableState || template.variableState || template.variables || DEFAULT_VARIABLES);
        const logs = Array.isArray(template.changeLog) ? [...template.changeLog].sort((a, b) => (a.time || 0) - (b.time || 0)) : [];
        const initializedKeys = new Set();
        logs.forEach(log => {
            Object.entries(log.changes || {}).forEach(([key, change]) => {
                if (!initializedKeys.has(key) && change && Object.prototype.hasOwnProperty.call(change, 'from')) {
                    if (key === '$root') baseState = cloneUiValue(change.from) || {};
                    else baseState[key] = change.from;
                    initializedKeys.add(key);
                }
            });
        });
        return baseState;
    };

    const normalizeUiTemplate = (template = {}) => {
        const variableState = (template.variableState && typeof template.variableState === 'object')
            ? cloneUiObject(template.variableState)
            : (template.variables && typeof template.variables === 'object'
                ? cloneUiObject(template.variables)
                : (template.initialVariableState && typeof template.initialVariableState === 'object'
                    ? cloneUiObject(template.initialVariableState)
                    : { ...DEFAULT_VARIABLES }));
        return {
            id: template.id || generateUUID(),
            name: template.name || 'UI模板',
            enabled: template.enabled !== false,
            scope: template.scope === 'global' ? 'global' : 'character',
            order: Number.isFinite(Number(template.order)) ? Number(template.order) : 100,
            placement: ['top', 'bottom'].includes(template.placement) ? template.placement : 'bottom',
            htmlTemplate: stripUiTemplateCodeFence(template.htmlTemplate || template.template || DEFAULT_HTML),
            initialVariableState: inferInitialUiTemplateState(template, variableState),
            variableState,
            variableSchema: (template.variableSchema && (typeof template.variableSchema === 'object' || typeof template.variableSchema === 'string')) ? template.variableSchema : '',
            changeLog: Array.isArray(template.changeLog) ? template.changeLog : [],
            runtimeByCharacter: (template.runtimeByCharacter && typeof template.runtimeByCharacter === 'object') ? cloneUiObject(template.runtimeByCharacter) : {},
            updateMode: template.updateMode || 'merge'
        };
    };

    const sanitizeUiTemplateImportEntry = (template = {}) => {
        const { changeLog, runtimeByCharacter, variableState, model, version, ...cleanTemplate } = template || {};
        if (!cleanTemplate.initialVariableState && !cleanTemplate.variables && variableState && typeof variableState === 'object') {
            cleanTemplate.initialVariableState = cloneUiObject(variableState);
        }
        return cleanTemplate;
    };

    const isUiTemplateObject = (value) => value !== null && typeof value === 'object';
    const splitUiTemplatePath = (path) => String(path || '')
        .trim()
        .replace(/\[(?:'([^']+)'|"([^"]+)"|([^\]]+))\]/g, (_, single, double, bare) => `.${single ?? double ?? String(bare || '').trim()}`)
        .split('.')
        .map(part => part.trim())
        .filter(Boolean);

    const readUiTemplatePath = (source, path) => {
        const normalizedPath = String(path || '').trim();
        if (!normalizedPath || normalizedPath === 'this' || normalizedPath === '.') return source;
        if (isUiTemplateObject(source) && Object.prototype.hasOwnProperty.call(source, normalizedPath)) {
            return source[normalizedPath];
        }
        return splitUiTemplatePath(normalizedPath).reduce((acc, key) => (
            acc !== undefined && acc !== null && acc[key] !== undefined ? acc[key] : undefined
        ), source);
    };

    const getUiTemplateValue = (source, path, context = null) => {
        const expression = String(path || '').trim();
        if (!expression) return undefined;
        if (context) {
            if (expression === 'this' || expression === '.') return context.current;
            if (expression === '@index') return context.index ?? 0;
            if (expression === '@number') return (context.index ?? 0) + 1;
            if (expression === '@first') return (context.index ?? 0) === 0;
            if (expression === '@last') return (context.index ?? 0) === (context.length ?? 0) - 1;
            if (expression === '@key') return context.key ?? context.index ?? '';
            if (expression.startsWith('root.')) return readUiTemplatePath(context.root, expression.slice(5));
            if (expression === 'root') return context.root;
            if (expression.startsWith('../')) {
                let parentContext = context.parentContext;
                let parentPath = expression;
                while (parentPath.startsWith('../')) {
                    parentPath = parentPath.slice(3);
                    if (parentPath.startsWith('../') && parentContext?.parentContext) {
                        parentContext = parentContext.parentContext;
                    }
                }
                const fallbackParent = { root: context.root, current: context.root, parentContext: null };
                return getUiTemplateValue(context.root, parentPath, parentContext || fallbackParent);
            }
            if (context.alias && (expression === context.alias || expression.startsWith(`${context.alias}.`))) {
                return expression === context.alias
                    ? context.current
                    : readUiTemplatePath(context.current, expression.slice(context.alias.length + 1));
            }
            const localValue = readUiTemplatePath(context.current, expression);
            if (localValue !== undefined) return localValue;
        }
        return readUiTemplatePath(source, expression);
    };

    const setUiTemplateValue = (source, path, value) => {
        const expression = String(path || '').trim();
        if (!expression) return source;
        if (expression === '$root' || expression === 'this' || expression === '.') return cloneUiValue(value);
        const root = isUiTemplateObject(source) ? source : {};
        if (Object.prototype.hasOwnProperty.call(root, expression) || !/[.[\]]/.test(expression)) {
            root[expression] = cloneUiValue(value);
            return root;
        }
        const parts = splitUiTemplatePath(expression);
        if (!parts.length) return root;
        let target = root;
        parts.forEach((part, index) => {
            if (index === parts.length - 1) {
                target[part] = cloneUiValue(value);
                return;
            }
            const nextPart = parts[index + 1];
            if (!isUiTemplateObject(target[part])) target[part] = /^\d+$/.test(nextPart) ? [] : {};
            target = target[part];
        });
        return root;
    };

    const stringifyUiTemplateValue = (value) => {
        if (value === undefined || value === null) return '';
        if (typeof value === 'string') return value;
        if (typeof value === 'object') {
            try {
                return JSON.stringify(value, null, 2);
            } catch (error) {
                return String(value);
            }
        }
        return String(value);
    };

    const escapeUiValue = (value) => stringifyUiTemplateValue(value)
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#39;');

    const createUiTemplateRenderContext = (variables, overrides = {}) => ({
        root: variables,
        current: variables,
        parentContext: null,
        index: 0,
        key: '',
        length: 1,
        alias: '',
        ...overrides
    });

    const renderUiTemplateString = (templateText, variables = {}, context = null) => {
        const activeContext = context || createUiTemplateRenderContext(variables);
        const withArrays = renderUiTemplateEachBlocks(String(templateText || ''), variables, activeContext);
        return withArrays.replace(/\{\{\s*([^{}]+?)\s*\}\}/g, (match, expression) => {
            const key = String(expression || '').trim();
            if (!key || key === 'else' || key.startsWith('#') || key.startsWith('/')) return match;
            return escapeUiValue(getUiTemplateValue(variables, key, activeContext));
        });
    };

    const renderUiTemplateEachBlocks = (templateText, variables = {}, context = null) => {
        let output = String(templateText || '');
        const eachBlockPattern = /\{\{\s*#each\s+([^\s}]+)(?:\s+as\s+([A-Za-z_$][\w$]*))?\s*\}\}((?:(?!\{\{\s*#each\b)[\s\S])*?)\{\{\s*\/each\s*\}\}/g;
        for (let pass = 0; pass < 50; pass++) {
            let replaced = false;
            output = output.replace(eachBlockPattern, (match, path, alias, body) => {
                replaced = true;
                const value = getUiTemplateValue(variables, path, context);
                const [itemTemplate, emptyTemplate = ''] = String(body || '').split(/\{\{\s*else\s*\}\}/i);
                const entries = Array.isArray(value)
                    ? value.map((item, index) => ({ item, key: index, index }))
                    : (isUiTemplateObject(value)
                        ? Object.entries(value).map(([key, item], index) => ({ item, key, index }))
                        : []);
                if (!entries.length) return renderUiTemplateString(emptyTemplate, variables, context);
                return entries.map(({ item, key, index }) => renderUiTemplateString(itemTemplate, variables, createUiTemplateRenderContext(variables, {
                    current: item,
                    parentContext: context,
                    index,
                    key,
                    length: entries.length,
                    alias: alias || ''
                }))).join('');
            });
            if (!replaced) break;
        }
        return output;
    };

    const htmlIframeSandbox = 'allow-scripts allow-forms allow-popups allow-popups-to-escape-sandbox allow-modals allow-same-origin allow-downloads allow-pointer-lock allow-presentation allow-top-navigation-by-user-activation';

    const buildExecutableHtmlDocument = (rawHtml) => {
        const metaViewport = '<meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">';
        const resetStyle = '<style>html,body{margin:0!important;padding:0!important;width:100%!important;height:auto!important;min-height:auto!important;word-wrap:break-word!important;box-sizing:border-box!important;overflow:hidden!important;}::-webkit-scrollbar{display:none;}*,*::before,*::after{box-sizing:inherit!important;}img,video,canvas,svg{max-width:100%!important;height:auto!important;}table{display:block!important;overflow-x:auto!important;max-width:100%!important;}pre{white-space:pre-wrap!important;word-wrap:break-word!important;max-width:100%!important;}.container,.reality-panel,.app-container{max-width:100%!important;width:100%!important;margin:0!important;border-radius:0!important;box-shadow:none!important;border:none!important;height:auto!important;min-height:0!important;}body>div:first-child{margin:0!important;max-width:100%!important;height:auto!important;min-height:0!important;}#app{height:auto!important;min-height:auto!important;}.bottom-safe{display:none!important;height:0!important;min-height:0!important;margin:0!important;padding:0!important;}</style>';
        const jqueryScript = '<script src="https://cdn.jsdelivr.net/npm/jquery@3.7.1/dist/jquery.min.js" defer><\/script>';
        const scriptShim = `
            <script>
                window.triggerSlash = function(text) {
                    if (window.parent && window.parent.triggerSlash) window.parent.triggerSlash(text);
                };

                let lastHeight = 0;
                let isUpdating = false;
                function updateHeight() {
                    if (!window.frameElement || isUpdating) return;
                    isUpdating = true;
                    requestAnimationFrame(function() {
                        var body = document.body;
                        var html = document.documentElement;
                        if (!body || !html) {
                            isUpdating = false;
                            return;
                        }
                        var maxBottom = 0;
                        for (var i = 0; i < body.children.length; i++) {
                            var child = body.children[i];
                            if (child.tagName === 'SCRIPT' || child.tagName === 'STYLE' || child.tagName === 'LINK') continue;
                            var style = window.getComputedStyle(child);
                            if (style.position === 'fixed') continue;
                            var rect = child.getBoundingClientRect();
                            var itemMax = Math.max(rect.bottom, child.offsetTop + child.offsetHeight);
                            if (itemMax > maxBottom) maxBottom = itemMax;
                        }
                        var bodyStyle = window.getComputedStyle(body);
                        var marginBottom = parseFloat(bodyStyle.marginBottom) || 0;
                        var newHeight = Math.max(maxBottom + marginBottom, body.scrollHeight) + 4;
                        if (Math.abs(newHeight - lastHeight) > 0) {
                            lastHeight = newHeight;
                            window.frameElement.style.height = newHeight + 'px';
                        }
                        isUpdating = false;
                    });
                }

                window.addEventListener('load', function() {
                    updateHeight();
                    setTimeout(updateHeight, 200);
                    setTimeout(updateHeight, 1000);
                });
                window.addEventListener('resize', updateHeight);
                window.addEventListener('click', function(event) {
                    var slashTarget = event.target && event.target.closest && event.target.closest('[data-slash]');
                    if (slashTarget) {
                        event.preventDefault();
                        var command = slashTarget.getAttribute('data-slash');
                        if (command) window.triggerSlash(command);
                    }
                    var start = Date.now();
                    var tick = function() {
                        if (Date.now() - start >= 600) return;
                        updateHeight();
                        requestAnimationFrame(tick);
                    };
                    tick();
                });
                window.addEventListener('DOMContentLoaded', function() {
                    document.querySelectorAll('img').forEach(function(img) {
                        img.addEventListener('load', updateHeight);
                    });
                    updateHeight();
                });
                if (window.ResizeObserver) {
                    var ro = new ResizeObserver(updateHeight);
                    if (document.body) ro.observe(document.body);
                } else {
                    setInterval(updateHeight, 1000);
                }
                if (document.readyState === 'complete') updateHeight();
            <\/script>
        `;

        const content = rawHtml || '';
        const trimmed = content.trim();
        if (/^\s*(<!doctype|<html)/i.test(trimmed)) {
            const headRegex = /<head(\s[^>]*)?>/i;
            const htmlRegex = /<html(\s[^>]*)?>/i;
            if (headRegex.test(content)) {
                return content.replace(headRegex, match => match + metaViewport + resetStyle + jqueryScript + scriptShim);
            }
            if (htmlRegex.test(content)) {
                return content.replace(htmlRegex, match => match + '<head>' + metaViewport + resetStyle + jqueryScript + scriptShim + '</head>');
            }
            return metaViewport + resetStyle + jqueryScript + scriptShim + content;
        }

        return `<!DOCTYPE html>
<html>
<head>
${metaViewport}
${resetStyle}
${jqueryScript}
${scriptShim}
</head>
<body>
${content}
</body>
</html>`;
    };

    const createExecutableHtmlIframe = (rawHtml, extraClass = '') => {
        const iframe = document.createElement('iframe');
        iframe.className = `w-full bg-white block executable-html-frame ${extraClass}`.trim();
        iframe.style.height = 'auto';
        iframe.style.overflow = 'hidden';
        iframe.style.transition = 'height 0.2s ease-out';
        iframe.style.margin = '0';
        iframe.style.padding = '0';
        iframe.setAttribute('scrolling', 'no');
        iframe.setAttribute('sandbox', htmlIframeSandbox);
        iframe.setAttribute('allow', 'clipboard-read; clipboard-write; fullscreen; autoplay; encrypted-media; picture-in-picture');
        iframe.onload = function () {
            try {
                setTimeout(() => {
                    if (this.contentWindow && this.contentWindow.document) {
                        const doc = this.contentWindow.document;
                        this.style.height = Math.max(doc.body.scrollHeight, doc.documentElement.scrollHeight) + 'px';
                    }
                }, 100);
            } catch (error) {
                console.warn('Failed to resize iframe:', error);
            }
        };
        iframe.srcdoc = buildExecutableHtmlDocument(rawHtml);
        return iframe;
    };

    const renderExecutableHtmlFrame = (rawHtml, extraClass = '') => {
        const container = document.createElement('div');
        container.className = 'html-card-container ui-template-frame-container';
        container.style.margin = '0';
        container.style.padding = '0';
        container.style.overflow = 'hidden';
        container.appendChild(createExecutableHtmlIframe(rawHtml, extraClass));
        return container.outerHTML;
    };

    const renderUiTemplateHtml = (template) => {
        if (!template || !template.htmlTemplate) return '';
        const variables = template.variableState || {};
        const html = renderUiTemplateString(stripUiTemplateCodeFence(template.htmlTemplate), variables);
        return renderExecutableHtmlFrame(html, 'ui-template-iframe');
    };

    const stringifyUiSchema = (schema) => {
        if (!schema) return '';
        return typeof schema === 'string' ? schema : JSON.stringify(schema, null, 2);
    };

    const findUiTemplateUpdateBlock = (text) => {
        const source = String(text || '');
        const taggedCandidate = window.RPHubCardUtils.findLastUnprotectedMatch(source, /<ui_template_updates\b[^>]*>/i);
        const taggedTail = taggedCandidate ? source.slice(taggedCandidate.index).trimEnd() : '';
        const tagged = taggedTail.match(/^<ui_template_updates\b[^>]*>([\s\S]*?)(?:<\/ui_template_updates>)?$/i);
        if (tagged) {
            const result = [taggedTail, tagged[1]];
            result.index = taggedCandidate.index;
            return result;
        }
        return null;
    };

    const stripUiTemplateUpdateBlock = (text) => {
        const source = String(text || '');
        const match = findUiTemplateUpdateBlock(source);
        return match ? source.slice(0, match.index).trimEnd() : source;
    };

    const parseUiTemplateUpdates = (rawContent) => {
        const normalizedContent = String(rawContent || '')
            .replace(/^<ui_template_updates\b[^>]*>\s*/i, '')
            .replace(/\s*<\/ui_template_updates>$/i, '')
            .trim();
        const lines = normalizedContent ? normalizedContent.split(/\r?\n/) : [];
        const updates = [];
        const parseError = (message, lineNumber) => {
            const error = new SyntaxError(`简化变量格式错误：第 ${lineNumber} 行${message}`);
            error.jsonSource = normalizedContent;
            error.jsonLine = lineNumber;
            throw error;
        };
        const parseVariableLines = (items, startLine) => {
            const variables = {};
            for (let index = 0; index < items.length; index += 1) {
                const item = items[index];
                const lineNumber = item.lineNumber || startLine;
                const text = String(item.text || '').replace(/^(?:&#x20;)+/, '').trim();
                if (!text) continue;
                const separator = text.indexOf('=');
                if (separator <= 0) parseError('应为“路径=值”', lineNumber || startLine);
                const path = text.slice(0, separator).trim();
                if (!path) parseError('缺少变量路径', lineNumber || startLine);
                let rawValue = text.slice(separator + 1).trim();
                if (!rawValue) {
                    variables[path] = '';
                    continue;
                }
                let parsedValue;
                try {
                    parsedValue = JSON.parse(rawValue);
                } catch (error) {
                    if (!/^[\[{]/.test(rawValue)) {
                        variables[path] = rawValue;
                        continue;
                    }
                    let completed = false;
                    for (let nextIndex = index + 1; nextIndex < items.length; nextIndex += 1) {
                        const continuation = String(items[nextIndex].text || '')
                            .replace(/^(?:&#x20;)+/, '')
                            .trim();
                        if (!continuation) continue;
                        rawValue += `\n${continuation}`;
                        try {
                            parsedValue = JSON.parse(rawValue);
                            index = nextIndex;
                            completed = true;
                            break;
                        } catch (continuationError) {
                            // Continue collecting a multi-line JSON array/object.
                        }
                    }
                    if (!completed) parseError('右侧数组或对象没有完整结束，必须写成有效JSON', lineNumber);
                }
                variables[path] = parsedValue;
            }
            return variables;
        };
        let currentId = '';
        let currentLines = [];
        let hasIdSections = false;
        lines.forEach((rawLine, index) => {
            const text = rawLine.trim();
            const lineNumber = index + 1;
            if (!text) return;
            const open = text.match(/^<id\s*=\s*([^>]+)>$/i);
            const close = text.match(/^<\/id\s*=\s*([^>]+)>$/i);
            if (open) {
                if (currentId) parseError('不能嵌套模板ID区块', lineNumber);
                if (!hasIdSections && currentLines.length) parseError('多个模板时所有变量都必须写在模板ID区块内', lineNumber);
                currentId = open[1].trim();
                if (!currentId) parseError('缺少模板ID', lineNumber);
                currentLines = [];
                hasIdSections = true;
                return;
            }
            if (close) {
                if (!currentId) parseError('出现了没有对应开始标签的模板ID结束标签', lineNumber);
                if (close[1].trim() !== currentId) parseError(`模板ID结束标签不匹配，应为“</id=${currentId}>”`, lineNumber);
                updates.push({ id: currentId, variables: parseVariableLines(currentLines, lineNumber) });
                currentId = '';
                currentLines = [];
                return;
            }
            if (hasIdSections && !currentId) parseError('模板ID区块之间只能留空行', lineNumber);
            currentLines.push({ text, lineNumber });
        });
        if (currentId) parseError(`缺少结束标签“</id=${currentId}>”`, lines.length || 1);
        if (hasIdSections) return { updates };
        return { updates: [{ id: '', variables: parseVariableLines(currentLines, 1) }] };
    };

    const normalizeUiTemplateUpdateList = (parsed, expectedTemplates = []) => {
        const isRecord = value => value !== null && typeof value === 'object' && !Array.isArray(value);
        const issues = [];
        const updates = isRecord(parsed) && Array.isArray(parsed.updates) ? parsed.updates : [];
        if (!isRecord(parsed) || !Array.isArray(parsed.updates)) issues.push('变量块缺少有效的更新分段');

        const templatesById = new Map(expectedTemplates.map(template => [String(template.id), template]));
        const receivedById = new Map();
        updates.forEach((update, index) => {
            const location = `第 ${index + 1} 项`;
            if (!isRecord(update)) {
                issues.push(`${location}不是有效对象`);
                return;
            }

            const variables = update.variables;
            if (!Object.prototype.hasOwnProperty.call(update, 'variables')) {
                issues.push(`${location}缺少变量内容`);
            } else if (variables === null || typeof variables !== 'object') {
                issues.push(`${location}的“variables”必须是对象或数组`);
            }
            const unknownFields = Object.keys(update).filter(key => !['id', 'variables'].includes(key));
            if (unknownFields.length) issues.push(`${location}包含未定义字段：${unknownFields.join('、')}`);

            const explicitId = typeof update.id === 'string' ? update.id.trim() : '';
            const id = explicitId || (expectedTemplates.length === 1 ? String(expectedTemplates[0].id) : '');
            if (!id) {
                issues.push(expectedTemplates.length > 1
                    ? `${location}缺少模板ID；多个模板时不能使用无ID简化格式`
                    : `${location}缺少有效模板ID`);
                return;
            }
            if (!templatesById.has(id)) {
                const validIds = [...templatesById.keys()];
                const expected = validIds.length === 1
                    ? `，当前模板ID应为“${validIds[0]}”`
                    : `，可用模板ID：${validIds.map(value => `“${value}”`).join('、')}`;
                issues.push(`${location}使用了未知模板ID“${id}”${expected}`);
                return;
            }
            if (!receivedById.has(id)) receivedById.set(id, []);
            receivedById.get(id).push({ variables });
        });

        receivedById.forEach((received, id) => {
            const template = templatesById.get(id);
            const label = template.name || id;
            const currentVariables = template.variableState || {};
            const schemaText = stringifyUiSchema(template.variableSchema);
            if (received.length > 1) issues.push(`模板“${label}”重复输出了 ${received.length} 次`);

            const variables = received[0].variables;
            if (Array.isArray(currentVariables)) {
                if (!Array.isArray(variables)) issues.push(`模板“${label}”必须完整输出数组变量`);
                return;
            }
            if (!isRecord(variables)) {
                issues.push(`模板“${label}”的变量不是有效对象`);
                return;
            }
            const unknownNames = [];
            const invalidNames = [];
            const dynamicRoots = new Set();
            schemaText.split(/\r?\n/).filter(line => /新增键|新增\s*id|只增添\s*\/\s*修改/.test(line)).forEach(line => {
                Object.keys(currentVariables).forEach(key => {
                    if (line.includes(key)) dynamicRoots.add(key);
                });
            });
            const dynamicPrefixes = [...schemaText.matchAll(/([A-Za-z][A-Za-z0-9_]*?)\{id\}/g)]
                .map(match => match[1]);
            const socialNodes = Array.isArray(variables.social_nodes) ? variables.social_nodes : currentVariables.social_nodes;
            const socialIds = new Set((Array.isArray(socialNodes) ? socialNodes : []).map(node => String(node?.id || '')));
            Object.entries(variables).forEach(([path, value]) => {
                const match = path.match(/^social_nodes(?:\.\d+|\[\d+\])\.id$/);
                if (match && value !== undefined && value !== null && String(value).trim()) {
                    socialIds.add(String(value).trim());
                }
            });
            const findExpectedPath = (expected, path) => {
                let current = expected;
                let appendedArray = null;
                const parts = splitUiTemplatePath(path);
                for (let index = 0; index < parts.length; index += 1) {
                    const part = parts[index];
                    if (Array.isArray(current) && /^\d+$/.test(part) && Number(part) === current.length && current.length > 0) {
                        appendedArray = current;
                        current = current[0];
                        continue;
                    }
                    if ((!isRecord(current) && !Array.isArray(current)) || !Object.prototype.hasOwnProperty.call(current, part)) {
                        if (index === parts.length - 1
                            && appendedArray === currentVariables.social_nodes
                            && ['id', 'name', 'relation', 'group', 'tags'].includes(part)) {
                            return { found: true, value: undefined };
                        }
                        return { found: false, value: undefined };
                    }
                    appendedArray = null;
                    current = current[part];
                }
                return { found: true, value: current };
            };
            const findDynamicExpected = (path) => {
                const parts = splitUiTemplatePath(path);
                const root = parts[0];
                let sample;
                if (parts.length > 1 && dynamicRoots.has(root) && isRecord(currentVariables[root])) {
                    sample = Object.values(currentVariables[root])[0];
                } else if (parts.length > 0) {
                    const prefix = dynamicPrefixes.find(value => root.startsWith(value));
                    const id = prefix ? root.slice(prefix.length) : '';
                    if (!prefix || !id || !socialIds.has(id)) return { found: false };
                    sample = Object.entries(currentVariables).find(([key]) => key.startsWith(prefix))?.[1];
                } else {
                    return { found: false };
                }
                if (parts.length <= (dynamicRoots.has(root) ? 2 : 1)) return { found: true, value: sample };
                return findExpectedPath(sample, parts.slice(dynamicRoots.has(root) ? 2 : 1).join('.'));
            };
            const inspectVariables = (expected, actual, prefix = '') => {
                Object.keys(actual).forEach(name => {
                    const path = prefix ? `${prefix}.${name}` : name;
                    const resolved = findExpectedPath(expected, name);
                    if (!resolved.found) {
                        const dynamic = findDynamicExpected(path);
                        if (!dynamic.found) {
                            unknownNames.push(path);
                        } else if (isRecord(actual[name]) && isRecord(dynamic.value)) {
                            inspectVariables(dynamic.value, actual[name], path);
                        } else if ((Array.isArray(dynamic.value) && !Array.isArray(actual[name]))
                            || (isRecord(dynamic.value) && !isRecord(actual[name]))) {
                            invalidNames.push(path);
                        }
                    } else if (isRecord(actual[name])) {
                        if (isRecord(resolved.value)) inspectVariables(resolved.value, actual[name], path);
                        else invalidNames.push(path);
                    } else if ((Array.isArray(resolved.value) && !Array.isArray(actual[name]))
                        || (isRecord(resolved.value) && !isRecord(actual[name]))) {
                        invalidNames.push(path);
                    }
                });
            };
            inspectVariables(currentVariables, variables);
            if (unknownNames.length) issues.push(`模板“${label}”输出了未定义变量：${unknownNames.join('、')}`);
            if (invalidNames.length) issues.push(`模板“${label}”变量结构错误：${invalidNames.join('、')}`);
        });

        if (issues.length) throw new Error(issues.join('；'));
        return updates;
    };

    const applyUiTemplateUpdateListToTemplate = (template, updates, { model = '', turn = null, source = 'ai' } = {}) => {
        let fieldCount = 0;
        let changed = false;
        updates.forEach(update => {
            if (!template || !update || typeof update !== 'object') return;
            if (update.id && update.id !== template.id) return;
            if (update.variables === null || typeof update.variables !== 'object') return;
            const changes = {};
            const variableEntries = Array.isArray(update.variables)
                ? [['$root', update.variables]]
                : Object.entries(update.variables);
            variableEntries.forEach(([key, value]) => {
                const oldValue = key === '$root'
                    ? template.variableState
                    : getUiTemplateValue(template.variableState || {}, key);
                if (JSON.stringify(oldValue) !== JSON.stringify(value)) {
                    template.variableState = setUiTemplateValue(template.variableState || {}, key, value);
                    changes[key] = { from: oldValue, to: value };
                }
            });
            if (Object.keys(changes).length > 0) {
                if (!Array.isArray(template.changeLog)) template.changeLog = [];
                template.changeLog.unshift({
                    id: generateUUID(),
                    time: Date.now(),
                    source,
                    model,
                    turn,
                    changes
                });
                template.changeLog = template.changeLog.slice(0, 50);
                fieldCount += Object.keys(changes).length;
                changed = true;
            }
        });
        return { changed, fieldCount };
    };

    window.RPHubUiTemplateUtils = {
        applyUiTemplateUpdateListToTemplate,
        buildExecutableHtmlDocument,
        cloneUiObject,
        cloneUiValue,
        createExecutableHtmlIframe,
        findUiTemplateUpdateBlock,
        getUiTemplateValue,
        inferInitialUiTemplateState,
        normalizeUiTemplate,
        normalizeUiTemplateUpdateList,
        parseUiTemplateUpdates,
        renderUiTemplateHtml,
        renderUiTemplateString,
        sanitizeUiTemplateImportEntry,
        setUiTemplateValue,
        stringifyUiSchema,
        stripUiTemplateCodeFence,
        stripUiTemplateUpdateBlock
    };
})();
