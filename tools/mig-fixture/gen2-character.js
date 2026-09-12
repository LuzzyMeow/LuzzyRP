// 造数据 2/5：角色（真实 createNewCharacter → saveCharacter）+ 世界书 + 正则 + 预设
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const S = () => document.querySelector('#app').__vue_app__._container._vnode.component.setupState;
const s = S();
const steps = [];

// ---- 1) 角色（走真实编辑器：createNewCharacter → 改 data → saveCharacter）----
s.createNewCharacter();
const d = s.editingCharacter.data;
d.name = 'Vanio';
d.description = '{{char}} 是常驻在旧书店二楼的女孩，二十四岁，短发，戴细框眼镜。'
    + '\n她说话轻，习惯先想再答；对陌生人保持礼貌的距离，熟悉后会主动递茶。'
    + '\n她知道书店每一本书的位置，也知道哪一层的木地板会响。';
d.personality = '安静、观察力强、不擅表达亲近；被追问时会转移话题。';
d.first_mes = '（雨季的下午，书店里没有别的客人。）\n\n'
    + '{{char}}把最后一摞书放回架上，回头看见你站在楼梯口，手上还带着雨。\n\n'
    + '「……伞放在门口就好。」她从柜台下面抽出一条干毛巾，放在柜台上推过来，'
    + '「二楼的地板会响，你上来的时候我听见了。」';
d.mes_example = '<START>\n{{user}}: 你在看什么？\n{{char}}: 「一本讲潮汐的旧书。」她把书脊转过来给你看，'
    + '「扉页有人写了名字，又划掉了。」';
d.scenario = '（该字段会被 saveCharacter 删除，用来验证迁移器对「不落盘字段」的处理）';
d.creator = 'fixture';
d.character_version = '1.0';
d.tags = ['日常', '书店'];
d.creator_notes = '迁移夹具用的角色卡';
s.saveCharacter();
steps.push('character-created');
await sleep(1600);

// ---- 2) 选中角色（真实路径：装载 branches + chat）----
await s.selectCharacter(0);
await sleep(1500);
steps.push('character-selected:' + (s.currentCharacter && s.currentCharacter.name));

// ---- 3) 世界书：一条 global、一条 character ----
const addWi = (entry) => {
    s.createWorldInfo();
    Object.assign(s.editingWorldInfo.data, entry);
    s.worldInfoKeysText = (entry.keys || []).join('\n');
    s.saveWorldInfo();
};
addWi({
    comment: '旧书店的规矩',
    keys: ['书店', '二楼'],
    content: '书店二楼的木地板第三块会响；雨天不营业，但门不上锁。',
    scope: 'character',
    position: 'global_note',
    depth: 4,
    order: 100,
    probability: 100,
    useProbability: true,
    useRegex: false,
    scanDepth: 2,
    constant: false,
});
addWi({
    comment: '全局：语言风格',
    keys: ['语气', '称呼'],
    content: '对话保留少量停顿（省略号），避免感叹号。',
    scope: 'global',
    position: 'global_note',
    depth: 2,
    order: 60,
    probability: 80,
    useProbability: true,
    useRegex: false,
    scanDepth: 2,
    constant: false,
});
steps.push('worldinfo:' + s.worldInfo.length);

// ---- 4) 正则：一条 character ----
s.createRegex();
Object.assign(s.editingRegex.data, {
    name: '去掉多余空行',
    regex: '/\\n{3,}/g',
    flags: 'g',
    replacement: '\n\n',
    placement: [1, 2],
    scope: 'character',
    markdownOnly: false,
    promptOnly: false,
    runOnEdit: false,
    minDepth: null,
    maxDepth: null,
});
s.saveRegex();
steps.push('regex:' + s.regexScripts.length);

// ---- 5) 预设：一条用户预设 ----
s.createPreset();
Object.assign(s.editingPreset.data, {
    name: '日常慢节奏',
    content: '保持对话的长度与节奏，不要替 {{user}} 决定行动。',
    enabled: true,
    role: 'system',
});
s.savePreset();
steps.push('presets:' + s.presets.length);

// ---- 6) 等防抖落盘 ----
await sleep(2500);

const keys = await new Promise((resolve) => {
    const req = indexedDB.open('RPHubDB', 1);
    req.onsuccess = () => {
        const tx = req.result.transaction(['store'], 'readonly').objectStore('store').getAllKeys();
        tx.onsuccess = () => resolve(tx.result.map(String));
    };
});
return JSON.stringify({ steps, keys, charUuid: s.currentCharacter && s.currentCharacter.uuid }, null, 1);
