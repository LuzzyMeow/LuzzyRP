// 造数据 6/6：换成 STA1N gemini-embedding-2（3072 维，真实可用）后重跑向量提取
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const S = () => document.querySelector('#app').__vue_app__._container._vnode.component.setupState;
const s = S();
const steps = [];
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

// ---- 0) 先关掉可能挂着的中断确认弹窗（上一个失败的 BigModel 尝试留下的）----
const clearModals = () => {
    const buttons = Array.from(document.querySelectorAll('button'));
    const cancel = buttons.find((b) => /取消中断|取消|关闭/.test(b.textContent || ''));
    if (cancel) cancel.click();
    return !!cancel;
};
steps.push('modal-cleared=' + clearModals());
await sleep(600);

// ---- 1) 真实新增 STA1N 嵌入供应商（base 已含 /v1，符合 buildApiEndpoint）----
const exists = (s.settings.apiProviders || []).some((p) => p.id === 'sta1n-embed');
if (!exists) {
    s.addUserApiProvider();
    await sleep(120);
    const d = s.providerEditorDraft;
    d.id = 'sta1n-embed';
    d.name = 'STA1N 嵌入';
    d.apiUrl = 'https://cdn.sta1n.cn/v1';
    d.protocol = 'openai';
    d.models = [{
        id: 'gemini-embedding-2', label: 'Gemini Embedding 2', contextLength: 8192, maxOutput: 0,
        inputModalities: ['text'], extraBody: {}, extraBodyText: '',
    }];
    s.saveProviderEditor();
    await sleep(400);
}
s.updateProviderKey('sta1n-embed', '__STA1N_KEY__');
s.memorySettings.enabled = true;
s.memorySettings.mode = 'vector';
s.memorySettings.embeddingModel = 'sta1n-embed::gemini-embedding-2';
await sleep(1800);
steps.push('embeddingModel=' + s.memorySettings.embeddingModel);

// ---- 2) 提取（当前 scope）----
const scopeNow = async () => {
    const keys = await new Promise((resolve) => {
        const req = indexedDB.open('RPHubDB');
        req.onsuccess = () => {
            const tx = req.result.transaction(['store'], 'readonly').objectStore('store').getAllKeys();
            tx.onsuccess = () => resolve(tx.result.map(String));
        };
    });
    return keys;
};
const extractHere = async (label) => {
    await s.startBatchMemoryExtraction();
    await sleep(3000);
    let n = -1, stable = 0;
    for (let i = 0; i < 45; i++) {
        await sleep(2000);
        clearModals();
        const keys = await scopeNow();
        let total = 0;
        for (const k of keys.filter((x) => /^rp_hub_memories_/.test(x))) {
            const v = await read(k);
            if (Array.isArray(v)) total += v.length;
        }
        if (total === n && total > 0) { stable++; if (stable >= 3) break; } else stable = 0;
        n = total;
    }
    const keys = await scopeNow();
    const out = {};
    for (const k of keys.filter((x) => /^rp_hub_memories_/.test(x))) {
        const v = await read(k);
        out[k.replace('rp_hub_memories_', '')] = Array.isArray(v) ? v.length : 'x';
    }
    steps.push(label + ':' + JSON.stringify(out)
        + (n > 0 ? '' : ' (nothing)'));
};
await extractHere('after-sta1n');

// ---- 3) 切到另一条 scope 再提取一次 ----
const activeIsBranch = await (async () => {
    const keys = await scopeNow();
    return keys.some((k) => k.includes('__branch__'));
})();
await s.switchStoryBranch(activeIsBranch ? 'main' : s.storyBranches.find((b) => b.id !== 'main').id);
await sleep(3000);
await extractHere('other-scope');
await sleep(3000);

// ---- 4) 报告 ----
const keys = await scopeNow();
const report = {};
for (const k of keys.filter((x) => /^rp_hub_memories_/.test(x))) {
    const v = await read(k);
    report[k] = Array.isArray(v) ? {
        len: v.length,
        fields: v[0] ? Object.keys(v[0]) : null,
        dims: v[0] ? v[0].embeddingDims : null,
        scale: v[0] ? v[0].embeddingScale : null,
        enc: v[0] ? v[0].embeddingEncoding : null,
        qLen: v[0] ? String(v[0].embeddingQ || '').length : null,
        sample: v[0] ? { turn: v[0].turn, chunkMode: v[0].chunkMode, vectorMemory: v[0].vectorMemory, paragraph: String(v[0].paragraph || '').slice(0, 60) } : null,
    } : 'not-array';
}
return JSON.stringify({ steps, report }, null, 1);
