// 造数据 8c：重建旧库（v1，带 store）并写入 legacy 记录
//
// 前置：页面刚 reload（清掉上一条挂死的 deleteDatabase 队列），所以此刻没有未决的 IDB 请求。
// 用「先删（仅在无连接时能成）→ 以 v1 重开 + onupgradeneeded 建 store」得到与真实旧安装
// 一致的库形态；删除被阻塞时退回「升到 v2 建 store」，并在结果里如实标注。
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const steps = [];

const openWithStore = (name, version) => new Promise((resolve, reject) => {
    const req = version ? indexedDB.open(name, version) : indexedDB.open(name);
    req.onblocked = () => reject(new Error('BLOCKED ' + name + ' v' + version));
    req.onerror = () => reject(req.error);
    req.onupgradeneeded = (e) => {
        const db = e.target.result;
        if (!db.objectStoreNames.contains('store')) db.createObjectStore('store');
    };
    req.onsuccess = () => resolve(req.result);
});
const deleteDb = (name) => new Promise((resolve) => {
    const req = indexedDB.deleteDatabase(name);
    req.onsuccess = () => resolve('deleted');
    req.onerror = () => resolve('error:' + (req.error && req.error.name));
    req.onblocked = () => resolve('blocked');
    setTimeout(() => resolve('timeout'), 4000);
});
const putAll = async (db, entries) => {
    await new Promise((resolve, reject) => {
        const tx = db.transaction(['store'], 'readwrite');
        const store = tx.objectStore('store');
        entries.forEach(([k, v]) => store.put(v, k));
        tx.oncomplete = () => resolve();
        tx.onerror = () => reject(tx.error);
        tx.onabort = () => reject(tx.error);
    });
};

const legacyUuid = '11111111-2222-3333-4444-555555555555';
const legacyOnlyUuid = '66666666-7777-8888-9999-aaaaaaaaaaaa';

// ---- 1) 旧库 ----
const del = await deleteDb('SillyTavernDB');
steps.push('delete=' + del);
await sleep(400);
let legacyDb = null;
let legacyVersion = null;
try {
    legacyDb = await openWithStore('SillyTavernDB', 1);
    legacyVersion = legacyDb.version;
} catch (e) {
    steps.push('v1-open-failed=' + e.message);
    legacyDb = await openWithStore('SillyTavernDB', 2);
    legacyVersion = legacyDb.version;
}
steps.push('legacyDb v' + legacyVersion + ' stores=[' + Array.from(legacyDb.objectStoreNames).join(',') + ']');

if (legacyDb) {
    await putAll(legacyDb, [
        ['silly_tavern_characters', [{
            name: '仅旧库角色',
            description: '只写在旧库里的角色，用来验证「两库合并」。',
            first_mes: '（这页只在旧库里。）',
            avatar: '',
            personality: '',
            mes_example: '',
            uuid: legacyOnlyUuid,
            createdAt: 1600000000000,
            uiTemplates: [],
        }]],
        ['silly_tavern_chat_' + legacyOnlyUuid, [
            { role: 'assistant', name: '仅旧库角色', content: '（这页只在旧库里。）' },
            { role: 'user', name: '旧用户', content: '你在哪一年？', isSelf: true },
        ]],
        ['silly_tavern_worldinfo', [{
            comment: '仅旧库的世界书', keys: ['旧库'], content: '这条只存在于旧库。',
            constant: false, enabled: true, position: 'global_note', depth: 4, order: 100,
            probability: 100, useProbability: true, scope: 'character',
        }]],
    ]);
    steps.push('legacy-db written: 3 keys');
}

// ---- 2) 主库里的 legacy 前缀记录 ----
const mainDb = await openWithStore('RPHubDB');
await putAll(mainDb, [
    ['silly_tavern_characters', [{
        name: '旧档角色',
        description: '旧版本写入的角色卡（legacy 前缀）。',
        first_mes: '（旧书店的灯坏了，只有柜台上的一支蜡烛。）',
        avatar: '',
        personality: '寡言。',
        mes_example: '',
        uuid: legacyUuid,
        createdAt: 1700000000000,
        uiTemplates: [],
    }]],
    ['silly_tavern_chat_' + legacyUuid, [
        { role: 'assistant', name: '旧档角色', content: '（旧书店的灯坏了，只有柜台上的一支蜡烛。）' },
        { role: 'user', name: '旧用户', content: '蜡烛够用一晚上吗？', isSelf: true, shouldAnimate: true, skipReveal: true, avatar: '' },
        { role: 'assistant', name: '旧档角色', content: '「够。」她把烛台往里推了推，「你要是怕黑，就坐到里侧来。」' },
    ]],
    ['silly_tavern_settings', { theme: 'classic', themeMode: 'light', fontSize: 14, model: 'legacy-model' }],
    ['silly_tavern_last_active_char', 0],
]);
steps.push('main-db legacy written: 4 keys');

// ---- 3) 复核 ----
const keysOf = (db) => new Promise((resolve) => {
    const tx = db.transaction(['store'], 'readonly').objectStore('store').getAllKeys();
    tx.onsuccess = () => resolve(tx.result.map(String));
});
const read = (db, k) => new Promise((resolve) => {
    const tx = db.transaction(['store'], 'readonly').objectStore('store').get(k);
    tx.onsuccess = () => resolve(tx.result);
});
await sleep(1500);
const mainKeys = await keysOf(mainDb);
return JSON.stringify({
    steps,
    legacyVersion,
    legacyKeys: await keysOf(legacyDb),
    mainKeys,
    charCount: ((await read(mainDb, 'rp_hub_characters')) || []).length,
    profiles: ((await read(mainDb, 'rp_hub_user_profiles')) || []).length,
    lastActiveChar: await read(mainDb, 'rp_hub_last_active_char'),
    activeProfileId: await read(mainDb, 'rp_hub_active_profile_id'),
}, null, 1);
