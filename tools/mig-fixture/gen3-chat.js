// 造数据 3/5：真实生成两轮（走应用自身 sendMessage → SSE 流 → 真实落盘）
// 角色头像用 canvas 生成的真实 PNG 走 handleAvatarUpload（真实 compressImage）
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const S = () => document.querySelector('#app').__vue_app__._container._vnode.component.setupState;
const s = S();
const steps = [];

// ---------- 1) 角色头像（真实路径：editCharacter → handleAvatarUpload → saveCharacter）----------
s.editCharacter(0);
await sleep(100);
const cv = document.createElement('canvas');
cv.width = 512; cv.height = 512;
const g = cv.getContext('2d');
const grad = g.createLinearGradient(0, 0, 512, 512);
grad.addColorStop(0, '#3d5a80'); grad.addColorStop(1, '#e0a458');
g.fillStyle = grad; g.fillRect(0, 0, 512, 512);
g.fillStyle = '#f6f1e7';
g.beginPath(); g.arc(256, 220, 110, 0, Math.PI * 2); g.fill();
g.fillStyle = '#2b2b2b';
g.fillRect(160, 360, 192, 130);
const pngUrl = cv.toDataURL('image/png');
const avatarBlob = await (await fetch(pngUrl)).blob();
s.handleAvatarUpload({ target: { files: [new File([avatarBlob], 'avatar.png', { type: 'image/png' })] } });
await sleep(1500);
s.saveCharacter();
await sleep(1600);
steps.push('avatar:' + String(s.characters[0].avatar || '').slice(0, 30) + '...len=' + String(s.characters[0].avatar || '').length);

// ---------- 2) 两轮真实生成 ----------
const waitIdle = async (limitMs) => {
    const t0 = Date.now();
    while (Date.now() - t0 < limitMs) {
        if (!s.isConversationBusy) return true;
        await sleep(500);
    }
    return false;
};
const rounds = [
    { model: 'deepseek::deepseek-chat', text: '我在门口把伞收好，走上二楼。' },
    { model: 'deepseek::deepseek-reasoner', text: '我在靠窗的位子坐下，问你今天有没有客人。' },
];
for (let i = 0; i < rounds.length; i++) {
    const r = rounds[i];
    s.settings.model = r.model;
    await sleep(1200);
    s.userInput = r.text;
    const before = s.chatHistory.length;
    await s.sendMessage();
    const idle = await waitIdle(180000);
    const after = s.chatHistory.length;
    steps.push(`round${i + 1}:model=${r.model},msgs ${before}->${after},idle=${idle}`);
    if (!idle) break;
    await sleep(1500);
}

// ---------- 3) 落盘（chat 防抖 300/1500ms）----------
await sleep(3000);

const roles = s.chatHistory.map((m) => `${m.role}${m.name ? '(' + m.name + ')' : ''}${m.reasoningContent ? '+reason' : ''}${m.imageAttachments && m.imageAttachments.length ? '+img' + m.imageAttachments.length : ''}`);
const last = s.chatHistory[s.chatHistory.length - 1];
return JSON.stringify({
    steps,
    msgCount: s.chatHistory.length,
    roles,
    lastMessageKeys: last ? Object.keys(last) : null,
    lastMessagePreview: last ? JSON.parse(JSON.stringify(last)).content?.slice(0, 120) : null,
    usageHistory: Array.isArray(s.tokenUsageHistory) ? s.tokenUsageHistory.length : null,
}, null, 1);
