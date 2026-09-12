// 导出夹具：把两个 IndexedDB 的全量记录 + 溯源信息序列化成 JSON
const readDb = (name) => new Promise((resolve) => {
    const req = indexedDB.open(name);
    req.onerror = () => resolve(null);
    req.onblocked = () => resolve(null);
    req.onsuccess = () => {
        const db = req.result;
        if (!db.objectStoreNames.contains('store')) return resolve({ version: db.version, entries: null, note: 'NO_STORE' });
        const tx = db.transaction(['store'], 'readonly').objectStore('store').openCursor();
        const entries = {};
        tx.onsuccess = () => {
            const c = tx.result;
            if (!c) return resolve({ version: db.version, entries });
            entries[String(c.key)] = c.value === undefined ? null : c.value;
            c.continue();
        };
        tx.onerror = () => resolve({ version: db.version, entries, note: 'CURSOR_ERROR' });
    };
});
const main = await readDb('RPHubDB');
const legacy = await readDb('SillyTavernDB');
const summarize = (entries) => {
    const out = {};
    for (const [k, v] of Object.entries(entries || {})) {
        out[k] = Array.isArray(v) ? v.length : (v && typeof v === 'object' ? Object.keys(v).length : typeof v);
    }
    return out;
};
return JSON.stringify({
    fixtureVersion: 1,
    capturedAt: new Date().toISOString(),
    source: {
        appPackage: 'com.luzzymeow.luzzyrp',
        build: 'v2.0.0 (versionCode 13) 未发布构建 · WebView 壳 MainActivity',
        origin: location.href,
        webviewUa: navigator.userAgent,
        generator: '.workbuddy/mig/gen1..gen8c（CDP 驱动前端自身函数）',
    },
    databases: {
        RPHubDB: { version: main ? main.version : null, entries: main ? main.entries : null },
        SillyTavernDB: { version: legacy ? legacy.version : null, entries: legacy ? legacy.entries : null },
    },
    counts: {
        main: summarize(main && main.entries),
        legacy: summarize(legacy && legacy.entries),
    },
}, null, 1);
