// Shared by RP Hub, the character workshop and the novel editor. The square is not themed.
(function () {
    const key = 'rphub-appearance';
    const root = document.documentElement;
    let transition = null;
    let changeId = 0;
    const valid = value => value === 'dark' || value === 'light';
    const localFrame = frame => {
        if (!frame?.src) return false;
        const url = new URL(frame.src, location.href);
        return url.origin === location.origin && /\/(character|novel)\/index\.html$/.test(url.pathname);
    };
    const send = frame => {
        if (!localFrame(frame)) return;
        // Update accessible embedded pages before capturing the new theme.
        try {
            if (frame.contentWindow?.RPHubTheme) {
                frame.contentWindow.RPHubTheme.set(root.dataset.appTheme);
                return;
            }
        } catch (_) { /* Local-file frames may still require postMessage. */ }
        frame.contentWindow?.postMessage(
            { type: 'RPHUB_THEME', theme: root.dataset.appTheme },
            location.origin === 'null' ? '*' : location.origin
        );
    };
    const apply = theme => {
        root.dataset.appTheme = theme;
        document.querySelectorAll('iframe').forEach(send);
        window.dispatchEvent(new CustomEvent('rphub-theme-change', { detail: theme }));
    };
    let saved = 'light';
    try { saved = localStorage.getItem(key) || 'light'; } catch (_) { /* Storage can be blocked in local-file mode. */ }
    apply(valid(saved) ? saved : 'light');
    window.RPHubTheme = Object.freeze({
        get current() { return root.dataset.appTheme; },
        async set(theme, source) {
            if (!valid(theme)) return;
            const id = ++changeId;
            transition?.skipTransition();
            const update = () => {
                if (id !== changeId) return;
                try { localStorage.setItem(key, theme); } catch (_) { /* Keep switching available without storage. */ }
                apply(theme);
            };
            const clearTransition = () => {
                if (id !== changeId) return;
                transition = null;
                delete root.dataset.themeTransition;
                ['--theme-x', '--theme-y', '--theme-radius'].forEach(name => root.style.removeProperty(name));
            };
            if (!source || !document.startViewTransition || window.matchMedia('(prefers-reduced-motion: reduce)').matches || theme === root.dataset.appTheme) {
                clearTransition();
                update();
                return;
            }
            const rect = source.getBoundingClientRect();
            const x = rect.left + rect.width / 2;
            const y = rect.top + rect.height / 2;
            const radius = Math.ceil(Math.hypot(Math.max(x, innerWidth - x), Math.max(y, innerHeight - y)));
            root.style.setProperty('--theme-x', `${x}px`);
            root.style.setProperty('--theme-y', `${y}px`);
            root.style.setProperty('--theme-radius', `${radius}px`);
            root.dataset.themeTransition = theme;
            try {
                transition = document.startViewTransition(async () => {
                    update();
                    await window.Vue?.nextTick();
                });
                transition.ready.catch(() => {}); // Skipping a transition rejects ready, not the theme change.
                await transition.finished;
            } catch (_) {
                update(); // Theme switching must still work if snapshots are unavailable.
            } finally {
                clearTransition();
            }
        }
    });
    window.addEventListener('storage', event => {
        if (event.key === key) apply(valid(event.newValue) ? event.newValue : 'light');
    });
    document.addEventListener('load', event => {
        if (event.target.tagName === 'IFRAME') send(event.target);
    }, true);
    window.addEventListener('message', event => {
        if (event.origin !== location.origin) return;
        if (event.source === window.parent && window.parent !== window && event.data?.type === 'RPHUB_THEME' && valid(event.data.theme)) {
            apply(event.data.theme);
        } else if (event.data?.type === 'RPHUB_THEME_REQUEST') {
            const frame = [...document.querySelectorAll('iframe')].find(item => item.contentWindow === event.source);
            if (frame) send(frame);
        }
    });
    if (window.parent !== window) window.parent.postMessage(
        { type: 'RPHUB_THEME_REQUEST' }, location.origin === 'null' ? '*' : location.origin
    );
})();
