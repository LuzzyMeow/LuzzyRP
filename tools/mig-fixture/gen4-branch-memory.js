// 造数据 4/5：剧情分支（真实 createStoryBranch）+ 分支上再生成一轮 + 真实经典记忆提取
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const S = () => document.querySelector('#app').__vue_app__._container._vnode.component.setupState;
const s = S();
const steps = [];
const read = (key) => new Promise((resolve) => {
    const req = indexedDB.open('RPHubDB', 1);
    req.onsuccess = () => {
        const tx = req.result.transaction(['store'], 'readonly').objectStore('store').get(key);
        tx.onsuccess = () => resolve(tx.result);
        tx.onerror = () => resolve(undefined);
    };
});

// ---------- 1) 从第一条生成的 assistant 回复（index 2）开分支 ----------
await s.createStoryBranch(2);
let guard = 0;
while (s.storyBranchSwitching && guard++ < 120) await sleep(500);
steps.push('branch-created:' + s.storyBranches.map((b) => b.id + '/' + b.name).join(' | '));

// 重命名（真实路径：selectStoryBranchNode + saveStoryBranchName）
const newBranch = s.storyBranches.find((b) => b.id !== 'main');
if (newBranch) {
    s.selectStoryBranchNode(newBranch.id);
    s.storyBranchNameDraft = '留在书店';
    await s.saveStoryBranchName();
    await sleep(1200);
    steps.push('branch-renamed:' + s.storyBranches.map((b) => b.name).join(' | '));
}

// ---------- 2) 分支上再真实生成一轮 ----------
s.settings.model = 'deepseek::deepseek-chat';
await sleep(1200);
s.userInput = '我把毛巾搭在椅背上，问你要不要一起喝点什么。';
await s.sendMessage();
let g2 = 0;
while (s.isConversationBusy && g2++ < 360) await sleep(500);
steps.push('branch-round:msgs=' + s.chatHistory.length + ',branch=' + s.activeStoryBranchId);
await sleep(3000);

// ---------- 3) 经典记忆提取（真实 LLM 总结；无嵌入需求）----------
s.memorySettings.enabled = true;
s.memorySettings.mode = 'classic';
s.memorySettings.classicModel = 'deepseek::deepseek-chat';
s.memorySettings.classicConcurrency = 1;
await sleep(1500);
await s.startBatchMemoryExtraction();
// 轮询直到经典记忆落盘条数稳定
const scope = s.currentCharacter.uuid && s.activeStoryBranchId !== 'main'
    ? `${s.currentCharacter.uuid}__branch__${s.activeStoryBranchId}`
    : s.currentCharacter.uuid;
let lastCount = -1, stable = 0;
for (let i = 0; i < 240; i++) {
    await sleep(1000);
    const list = await read('rp_hub_classic_memories_' + scope);
    const n = Array.isArray(list) ? list.length : 0;
    if (n > 0 && n === lastCount) { stable++; if (stable >= 3) break; } else stable = 0;
    lastCount = n;
}
steps.push('classic-memories:' + lastCount + ' @ ' + scope);

// ---------- 4) 落盘 ----------
await sleep(2500);
const classic = await read('rp_hub_classic_memories_' + scope);
const keys = await new Promise((resolve) => {
    const req = indexedDB.open('RPHubDB', 1);
    req.onsuccess = () => {
        const tx = req.result.transaction(['store'], 'readonly').objectStore('store').getAllKeys();
        tx.onsuccess = () => resolve(tx.result.map(String));
    };
});
return JSON.stringify({
    steps,
    keys,
    activeBranchId: s.activeStoryBranchId,
    scope,
    classicCount: Array.isArray(classic) ? classic.length : null,
    classicFirstKeys: Array.isArray(classic) && classic[0] ? Object.keys(classic[0]) : null,
    classicFirst: Array.isArray(classic) && classic[0] ? JSON.parse(JSON.stringify(classic[0])) : null,
}, null, 1);
