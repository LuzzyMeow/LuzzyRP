// 造数据 7/8：主线经典记忆 + 第二个用户人设 + 两个补充角色 + 手工构造的 legacy 记录
//
// legacy 记录（silly_tavern_* 前缀 / 独立 SillyTavernDB）**无法由现行代码再生**（旧版本才写），
// 故这部分是**手工构造**的，形状照现行字段、只保留旧版存在的字段；夹具元数据里逐键登记。
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const S = () => document.querySelector('#app').__vue_app__._container._vnode.component.setupState;
const s = S();
const steps = [];

const openDb = (name, version) => new Promise((resolve, reject) => {
    const req = version ? indexedDB.open(name, version) : indexedDB.open(name);
    req.onerror = () => reject(req.error);
    req.onblocked = () => reject(new Error('BLOCKED ' + name));
    req.onsuccess = () => resolve(req.result);
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
const read = (key, dbName = 'RPHubDB') => new Promise((resolve) => {
    const req = indexedDB.open(dbName);
    req.onsuccess = () => {
        let tx;
        try { tx = req.result.transaction(['store'], 'readonly').objectStore('store').get(key); }
        catch (e) { return resolve(undefined); }
        tx.onsuccess = () => resolve(tx.result);
        tx.onerror = () => resolve(undefined);
    };
});

// ---------- 1) 主线补经典记忆 ----------
await s.switchStoryBranch('main');
await sleep(3000);
s.memorySettings.enabled = true;
s.memorySettings.mode = 'classic';
s.memorySettings.classicModel = 'deepseek::deepseek-chat';
s.memorySettings.classicConcurrency = 1;
await sleep(1500);
await s.startBatchMemoryExtraction();
await sleep(4000);
let n = -1, stable = 0;
for (let i = 0; i < 45; i++) {
    await sleep(2000);
    const v = await read('rp_hub_classic_memories_' + s.currentCharacter.uuid);
    const cur = Array.isArray(v) ? v.length : 0;
    if (cur === n && cur > 0) { stable++; if (stable >= 3) break; } else stable = 0;
    n = cur;
}
steps.push('main-classic:' + ((await read('rp_hub_classic_memories_' + s.currentCharacter.uuid)) || []).length);
// 记忆模式还原为向量（保留用户配置了嵌入模型的终态）
s.memorySettings.mode = 'vector';
await sleep(1500);

// ---------- 2) 第二个用户人设（真实 createNewProfile）----------
s.createNewProfile();
await sleep(300);
const activeId = await read('rp_hub_active_profile_id');
s.user.name = '周叙';
s.user.person = '第三人称';
s.user.description = '三十岁的电台节目编辑，习惯把话在心里过一遍再说。';
s.user.preferences = '偏好长句与环境的白噪音描写。';
await sleep(1800);
steps.push('profiles=' + s.userProfiles.length + ', active=' + (await read('rp_hub_active_profile_id')));

// ---------- 3) 两个补充角色（真实编辑器路径）----------
const addChar = (name, first) => {
    s.createNewCharacter();
    const d = s.editingCharacter.data;
    d.name = name;
    d.first_mes = first;
    d.description = name + ' 的最小角色卡（夹具用，无对话）。';
    s.saveCharacter();
};
addChar('谢昭', '（会议室里只剩下两个人。）\n\n「方案我看过了。」{{char}} 把纸推到桌子中间。');
addChar('夏梧', '（候车厅的广播在念听不懂的站名。）\n\n{{char}} 把行李箱横过来当凳子坐下。');
await sleep(1800);
steps.push('characters=' + s.characters.length + ': ' + s.characters.map((c) => c.name).join(','));

// 停在 index 1 → last_active_char 是非零索引（迁移器把它当 uuid 用就会错）
await s.selectCharacter(1);
await sleep(2500);
steps.push('last_active_char=' + (await read('rp_hub_last_active_char')));

// ---------- 4) 手工构造 legacy 记录（旧版格式，现行代码不再产生）----------
const legacyUuid = '11111111-2222-3333-4444-555555555555';
const legacyOnlyUuid = '66666666-7777-8888-9999-aaaaaaaaaaaa';
const legacyChar = {
    name: '旧档角色',
    description: '旧版本写入的角色卡（legacy 前缀）。',
    first_mes: '（旧书店的灯坏了，只有柜台上的一支蜡烛。）',
    avatar: '',
    personality: '寡言。',
    mes_example: '',
    uuid: legacyUuid,
    createdAt: 1700000000000,
    uiTemplates: [],
};
const legacyChat = [
    { role: 'assistant', name: '旧档角色', content: '（旧书店的灯坏了，只有柜台上的一支蜡烛。）' },
    { role: 'user', name: '旧用户', content: '蜡烛够用一晚上吗？', isSelf: true, shouldAnimate: true, skipReveal: true, avatar: '' },
    { role: 'assistant', name: '旧档角色', content: '「够。」她把烛台往里推了推，「你要是怕黑，就坐到里侧来。」' },
];
const mainDb = await openDb('RPHubDB');
await putAll(mainDb, [
    ['silly_tavern_characters', [legacyChar]],
    ['silly_tavern_chat_' + legacyUuid, legacyChat],
    ['silly_tavern_settings', { theme: 'classic', themeMode: 'light', fontSize: 14, model: 'legacy-model' }],
    ['silly_tavern_last_active_char', 0],
]);
steps.push('legacy-in-main-db: 4 keys');

// 独立旧库：只在这里存在的角色（迁移器必须合并，且同键时新库优先）
const legacyDb = await openDb('SillyTavernDB', 1);
await putAll(legacyDb, [
    ['silly_tavern_characters', [{
        name: '仅旧库角色',
        description: '只写在 SillyTavernDB 里的角色，用来验证「两库合并」。',
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
steps.push('legacy-db: 3 keys');

await sleep(2500);
const keys = await new Promise((resolve) => {
    const req = indexedDB.open('RPHubDB');
    req.onsuccess = () => {
        const tx = req.result.transaction(['store'], 'readonly').objectStore('store').getAllKeys();
        tx.onsuccess = () => resolve(tx.result.map(String));
    };
});
return JSON.stringify({ steps, keys, legacyUuid, legacyOnlyUuid }, null, 1);
