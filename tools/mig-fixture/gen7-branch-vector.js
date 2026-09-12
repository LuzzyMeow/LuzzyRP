// 收尾：在「留在书店」分支上再做一次真实向量提取（补齐两条 scope 的向量记忆）
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const S = () => document.querySelector('#app').__vue_app__._container._vnode.component.setupState;
const s = S();
const steps = [];
const keysNow = () => new Promise((resolve) => {
    const req = indexedDB.open('RPHubDB');
    req.onsuccess = () => {
        const tx = req.result.transaction(['store'], 'readonly').objectStore('store').getAllKeys();
        tx.onsuccess = () => resolve(tx.result.map(String));
    };
});
const read = (key) => new Promise((resolve) => {
    const req = indexedDB.open('RPHubDB');
    req.onsuccess = () => {
        let tx;
        try { tx = req.result.transaction(['store'], 'readonly').objectStore('store').get(key); }
        catch (e) { return resolve(undefined); }
        tx.onsuccess = () => resolve(tx.result);
        tx.onerror = () => resolve(undefined);
    };
});
const clearModals = () => {
    const b = Array.from(document.querySelectorAll('button')).find((x) => /取消中断|取消|关闭/.test(x.textContent || ''));
    if (b) b.click();
    return !!b;
};

const branch = s.storyBranches.find((b) => b.id !== 'main');
steps.push('switching-to=' + branch.id + '/' + branch.name);
await s.switchStoryBranch(branch.id);
await sleep(3500);
// 切过去后当前历史应是分支的（3 条：first_mes + 用户 + 回复）
steps.push('chatLen=' + s.chatHistory.length);

s.memorySettings.enabled = true;
s.memorySettings.mode = 'vector';
s.memorySettings.embeddingModel = 'sta1n-embed::gemini-embedding-2';
await sleep(1500);

const branchScope = s.currentCharacter.uuid + '__branch__' + branch.id;
await s.startBatchMemoryExtraction();
await sleep(3000);
let n = -1, stable = 0;
for (let i = 0; i < 45; i++) {
    await sleep(2000);
    clearModals();
    const v = await read('rp_hub_memories_' + branchScope);
    const cur = Array.isArray(v) ? v.length : 0;
    if (cur === n && cur > 0) { stable++; if (stable >= 3) break; } else stable = 0;
    n = cur;
}
const v = await read('rp_hub_memories_' + branchScope);
await sleep(2500);
return JSON.stringify({
    steps,
    branchScope,
    branchVector: Array.isArray(v) ? {
        len: v.length,
        dims: v[0] ? v[0].embeddingDims : null,
        qLen: v[0] ? String(v[0].embeddingQ || '').length : null,
        turns: v.map((m) => m.turn),
    } : 'null',
}, null, 1);
