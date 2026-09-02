/**
 * LuzzyRP 扩展层 · 二创新功能（AGENTS.md §5.3）
 *
 * 本文件在 index.html 尾部加载（patch 005 挂载点），此时上游全局对象
 * （Vue app / RPHub 等）已就绪。访问上游对象必须先做存在性检测；
 * 任何扩展功能必须自带降级路径，不允许因本文件报错导致应用白屏。
 *
 * 功能清单：
 * 1. 桥接自检（开发期诊断；发布期静默）
 * 2. 关于页品牌信息注入
 *
 * [状态 2026-09-01] 旧主题系统（data-theme/data-mode）已随「暖纸书房」方案
 * 整体移除；新主题系统待设计 SKILL 三方向硬门产出并经用户选定后重建。
 * 字体扩展（luzzy 选项）也待新字体方案重新设计后恢复——当前上游
 * fontFamilies 保持原样（modern/serif/system）。
 */
(function () {
    'use strict';

    // ============================================================
    // 1. 桥接自检
    // ============================================================
    function selfCheck() {
        var info = window.Luzzy && window.Luzzy.getVersion ? window.Luzzy.getVersion() : null;
        if (!info) return;
        if (window.console && console.info) {
            console.info(
                '[LuzzyRP] v' + info.versionName +
                ' (code ' + info.versionCode + ') · upstream RP-Hub ' + info.upstream +
                ' · ' + info.device
            );
        }
    }

    // ============================================================
    // 2. 关于页品牌信息注入
    // ============================================================
    function injectAboutBranding() {
        try {
            var containers = document.querySelectorAll('[class*="about"], [class*="About"], [class*="version"], [class*="Version"]');
            var info = window.Luzzy && window.Luzzy.getVersion ? window.Luzzy.getVersion() : null;
            if (!info || containers.length === 0) return;

            if (document.getElementById('luzzy-about-branding')) return;

            var footer = document.createElement('div');
            footer.id = 'luzzy-about-branding';
            footer.style.cssText =
                'margin-top:12px;padding:10px 14px;border-radius:10px;' +
                'background:rgba(127,127,127,0.08);font-size:12px;line-height:1.7;color:#888;' +
                'text-align:center;';
            footer.textContent =
                'LuzzyRP v' + info.versionName +
                ' · 基于 RP-Hub v' + info.upstream + ' 二次开发（CC BY-NC 4.0）';
            var target = containers[containers.length - 1];
            target.appendChild(footer);
        } catch (e) {
            // 注入失败静默降级，不影响上游
        }
    }

    // ============================================================
    // 挂载
    // ============================================================
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', function () {
            selfCheck();
            setTimeout(injectAboutBranding, 800);
        });
    } else {
        selfCheck();
        setTimeout(injectAboutBranding, 800);
    }
})();
// ============================================================
// [LuzzyRP patch 018] 主题快照维护（配合 index.html head 内联脚本防开屏闪蓝）
// luzzy-ext.js 挂载于尾部（DOM 就绪后），用 MutationObserver 跟随
// <html> 的 data-theme/data-mode 变化，把当前主题写入 localStorage 快照；
// 下次冷启动由 head 内联脚本同步读取，开屏首帧即为正确主题色。
// localStorage 不可用时静默降级（内联脚本回退默认 luzzy+light）。
// ============================================================
(function () {
    var writeSnapshot = function () {
        try {
            var root = document.documentElement;
            localStorage.setItem('luzzy_theme_snapshot', JSON.stringify({
                theme: root.dataset.theme === 'classic' ? 'classic' : 'luzzy',
                mode: root.dataset.mode === 'dark' ? 'dark' : 'light'
            }));
        } catch (e) { /* 隐私模式等场景静默降级 */ }
    };
    writeSnapshot();
    try {
        new MutationObserver(writeSnapshot).observe(document.documentElement, {
            attributes: true,
            attributeFilter: ['data-theme', 'data-mode']
        });
    } catch (e) { /* 旧内核降级：仅启动时写一次 */ }
})();
