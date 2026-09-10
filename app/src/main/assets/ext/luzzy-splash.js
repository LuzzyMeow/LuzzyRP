/**
 * LuzzyRP 扩展层 · 开屏「开卷 · 门扉」行为（patch 027 v3，v1.2.3）
 * 职责单一：点击「沉溺」→ 触发眩晕/泡泡/中心放大转场 → 收殓开屏层。
 * 入场淡入 / 进度条 / 按钮浮现均为纯 CSS 时序（见 luzzy-theme.css splash v3 段），
 * 本脚本不参与时序；任一元素缺失即静默退出（扩展层不阻塞主流程）。
 *
 * 注：开屏期的启动性能优化（遮挡期不渲染应用主体）为**纯 CSS**，见
 * luzzy-theme.css「开屏期遮挡渲染抑制」段——不在此脚本内，零 JS 依赖与零卡死风险。
 */
(function () {
    'use strict';
    var splash = document.querySelector('.luzzy-splash');
    var btn = splash && splash.querySelector('.lsp-dive-btn');
    if (!splash || !btn) return;

    /* 入场中段解除「遮挡期渲染抑制」（luzzy-theme.css 同名段）：让被开屏完全遮住的
       应用主体在**开屏仍不透明时**先完成首次绘制（实测该次绘制有 150~350ms 主线程
       尖峰，留到点击「沉溺」那一刻会与转场淡出重叠、露出空白）。此处仅提前解除，
       点击时 `.lsp-dive` 仍会由 CSS 原生解除——JS 计时器失效也不会卡住应用。 */
    setTimeout(function () { splash.classList.add('lsp-warm'); }, 1200);

    var dived = false;
    btn.addEventListener('click', function () {
        if (dived) return;
        dived = true;
        splash.classList.add('lsp-dive');
        var onEnd = function (e) {
            if (e.animationName === 'lspDiveZoom' || e.animationName === 'lspDiveCalm') {
                splash.removeEventListener('animationend', onEnd);
                splash.style.visibility = 'hidden';
            }
        };
        splash.addEventListener('animationend', onEnd);
        setTimeout(function () { splash.style.visibility = 'hidden'; }, 1500); /* 兜底收殓 */
    });
})();