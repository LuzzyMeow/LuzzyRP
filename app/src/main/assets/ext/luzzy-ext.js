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
 * 3. 主题系统（data-theme / data-mode 驱动，见 docs/design/theme-tech-plan.md）
 * 4. 字体设置扩展（luzzy 默认字体选项）
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
    // 3. 主题系统（data-theme / data-mode 驱动）
    // ============================================================
    // 主题变量定义在 luzzy-theme.css（扩展层）：
    //   [data-theme="classic"]            → 原版 RP-Hub 色值（默认）
    //   [data-theme="luzzy"][data-mode="light"] → 新主题亮色
    //   [data-theme="luzzy"][data-mode="dark"]  → 新主题暗色
    // 切换逻辑：读取 localStorage 设置 → 设置 documentElement 属性。
    // 上游设置存储键：RP-Hub 用 localStorage 存 settings（键名待确认）。
    var THEME_KEY = 'luzzy_theme';
    var THEME_MODE_KEY = 'luzzy_theme_mode';

    function applyTheme() {
        try {
            var theme = localStorage.getItem(THEME_KEY) || 'luzzy'; // 新用户默认新主题
            var mode = localStorage.getItem(THEME_MODE_KEY) || 'light';
            var root = document.documentElement;
            root.dataset.theme = theme;
            root.dataset.mode = mode;
            // 同步系统栏（通过桥接，见 LuzzyBridge.setSystemBarStyle）
            if (window.LuzzyBridge && window.LuzzyBridge.setSystemBarStyle) {
                window.LuzzyBridge.setSystemBarStyle(theme === 'luzzy' ? mode : 'light');
            }
        } catch (e) {
            // localStorage 不可用时降级为默认
        }
    }

    // ============================================================
    // 4. 字体设置扩展（luzzy 默认字体选项）
    // ============================================================
    // 上游 fontFamilies 选项在 core-utils.js（modern/serif/system）。
    // 扩展层追加 'luzzy' 选项：Alibaba PuHuiTi 3.0 + AlibabaSans（本地打包）。
    // 通过 data-app-font="luzzy" 触发 luzzy-theme.css 中的字体栈覆盖。
    function extendFontOptions() {
        try {
            // 上游 applyFontFamily 只认 modern/serif/system，扩展层补 luzzy
            var origApply = window.RPHubUtils && window.RPHubUtils.applyFontFamily;
            // 若上游暴露了 applyFontFamily，包装它支持 luzzy
            if (window.RPHubUtils && typeof window.RPHubUtils.applyFontFamily === 'function') {
                var orig = window.RPHubUtils.applyFontFamily;
                window.RPHubUtils.applyFontFamily = function (value) {
                    if (value === 'luzzy') {
                        document.documentElement.dataset.appFont = 'luzzy';
                        return;
                    }
                    return orig(value);
                };
            }
        } catch (e) {
            // 降级：不扩展
        }
    }

    // ============================================================
    // 挂载
    // ============================================================
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', function () {
            selfCheck();
            applyTheme();
            extendFontOptions();
            setTimeout(injectAboutBranding, 800);
        });
    } else {
        selfCheck();
        applyTheme();
        extendFontOptions();
        setTimeout(injectAboutBranding, 800);
    }
})();
