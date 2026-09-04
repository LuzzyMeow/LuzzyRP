/**
 * LuzzyRP 扩展层 · 开屏「开卷 · 门扉」行为（patch 027 v3，v1.2.3）
 * 职责单一：点击「沉溺」→ 触发眩晕/泡泡/中心放大转场 → 收殓开屏层。
 * 入场淡入 / 进度条 / 按钮浮现均为纯 CSS 时序（见 luzzy-theme.css splash v3 段），
 * 本脚本不参与时序；任一元素缺失即静默退出（扩展层不阻塞主流程）。
 */
(function () {
    'use strict';
    var splash = document.querySelector('.luzzy-splash');
    var btn = splash && splash.querySelector('.lsp-dive-btn');
    if (!splash || !btn) return;
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