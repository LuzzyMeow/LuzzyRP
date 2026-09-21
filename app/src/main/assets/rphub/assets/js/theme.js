// Shared by RP Hub, the character workshop and the novel editor. The square is not themed.
(function () {
    const key = 'rphub-appearance';
    const root = document.documentElement;
    const valid = value => value === 'dark' || value === 'light';
    const localFrame = frame => {
        if (!frame?.src) return false;
        const url = new URL(frame.src, location.href);
        return url.origin === location.origin && /\/(character|novel)\/index\.html$/.test(url.pathname);
    };
    const send = frame => {
        if (!localFrame(frame)) return;
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
        set(theme) {
            if (!valid(theme)) return;
            try { localStorage.setItem(key, theme); } catch (_) { /* Keep switching available without storage. */ }
            apply(theme);
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
