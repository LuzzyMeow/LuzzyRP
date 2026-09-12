// 造数据 1/5：用户资料 + 供应商（内置 DeepSeek 编辑态 override）+ 模型槽位
//
// 全部走应用自身的**真实写入路径**：
//   - user / settings 是 reactive 对象，其深度 watcher（app.js:3114，防抖 1000ms）→ saveData()
//   - 供应商走 editUserApiProvider()（内置商编辑态）→ saveProviderEditor() → apiProviderOverrides
//   - 密钥走 updateProviderKey()（真实字段，同时兼容 legacy settings.apiKey）
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const S = () => document.querySelector('#app').__vue_app__._container._vnode.component.setupState;
const s = S();
const steps = [];

// ---- 1) 用户资料 ----
s.user.name = '林澈';
s.user.person = '第一人称';
s.user.description = '二十六岁的插画师，住在旧城区的顶楼。'
    + '\n喜欢在雨天画画，讨厌吵闹的地方，说话慢但是很直。';
s.user.preferences = '偏好慢节奏的日常对话，不喜欢被催促；描写请留在体感层面。';
steps.push('user');

// ---- 2) 内置 DeepSeek 走「编辑态」写入 override（含手动模型条目）----
s.editUserApiProvider(s.allApiProviders.find((p) => p.id === 'deepseek'));
await sleep(50);
const draft = s.providerEditorDraft;
if (!draft) throw new Error('providerEditorDraft 未打开');
draft.apiUrl = 'https://api.deepseek.com/v1';
draft.protocol = 'openai';
draft.models = [
    {
        id: 'deepseek-chat',
        label: 'DeepSeek Chat',
        contextLength: 65536,
        maxOutput: 8192,
        inputModalities: ['text'],
        extraBody: {},
        extraBodyText: '',
    },
    {
        id: 'deepseek-reasoner',
        label: 'DeepSeek Reasoner',
        contextLength: 65536,
        maxOutput: 8192,
        inputModalities: ['text'],
        extraBody: {},
        extraBodyText: '',
    },
];
s.saveProviderEditor();
await sleep(100);
steps.push('provider:' + (s.settings.apiProviderOverrides ? Object.keys(s.settings.apiProviderOverrides).join(',') : 'none'));

// ---- 3) 密钥 + 激活商 + 模型槽位 ----
s.updateProviderKey('deepseek', '__DEV_API_KEY__');
s.settings.apiProviderId = 'deepseek';
s.settings.model = 'deepseek::deepseek-reasoner';
s.settings.qualityModel = 'deepseek::deepseek-reasoner';
s.settings.balancedModel = 'deepseek::deepseek-chat';
s.settings.stream = true;
s.settings.temperature = 0.85;
s.settings.contextSize = 65536;
s.settings.theme = 'luzzy';
s.settings.themeMode = 'dark';
s.settings.fontFamily = 'luzzy';
s.settings.fontSize = 15;
steps.push('settings:model=' + s.settings.model);

// ---- 4) 等防抖（1000ms）+ saveData 完成 ----
await sleep(2200);

const dbKeys = await new Promise((resolve) => {
    const req = indexedDB.open('RPHubDB', 1);
    req.onsuccess = () => {
        const tx = req.result.transaction(['store'], 'readonly').objectStore('store').getAllKeys();
        tx.onsuccess = () => resolve(tx.result.map(String));
    };
});
return JSON.stringify({ steps, dbKeys }, null, 1);
