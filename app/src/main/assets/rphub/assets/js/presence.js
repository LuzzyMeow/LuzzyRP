// Anonymous online presence. Configure the API URL in index.html.
(function () {
    const { onBeforeUnmount, onMounted, ref } = Vue;
    const apiUrl = String(document.querySelector('meta[name="rphub-presence-api"]')?.content || '')
        .trim()
        .replace(/\/+$/, '');
    const storageKey = 'rphub_presence_client_id';
    const parseVersionId = value => /^\d{5}$/.test(String(value ?? '').trim())
        ? Number(value)
        : null;
    const currentVersionId = parseVersionId(window.RPHubLatestUpdate?.id);
    let notifiedVersionId = 0;

    const createClientId = () => {
        if (typeof crypto?.randomUUID === 'function') return crypto.randomUUID().replaceAll('-', '');
        const bytes = new Uint8Array(16);
        crypto.getRandomValues(bytes);
        return Array.from(bytes, value => value.toString(16).padStart(2, '0')).join('');
    };

    const getClientId = () => {
        try {
            const existing = localStorage.getItem(storageKey);
            if (existing) return existing;
            const created = createClientId();
            localStorage.setItem(storageKey, created);
            return created;
        } catch {
            return createClientId();
        }
    };

    const usePresence = () => {
        const online = ref(null);
        let timer = null;
        const heartbeat = async () => {
            if (!apiUrl || document.hidden) return;
            try {
                const response = await fetch(`${apiUrl}/v1/presence`, {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ clientId: getClientId(), versionId: currentVersionId })
                });
                if (!response.ok) throw new Error(`HTTP ${response.status}`);
                const data = await response.json();
                online.value = Number.isFinite(data.online) ? data.online : null;
                const latestVersionId = parseVersionId(data.latestVersionId);
                if (data.updateAvailable && latestVersionId > currentVersionId && latestVersionId > notifiedVersionId) {
                    notifiedVersionId = latestVersionId;
                    window.dispatchEvent(new CustomEvent('rphub:update-available', {
                        detail: { versionId: latestVersionId }
                    }));
                }
            } catch {
                online.value = null;
            }
        };
        const handleVisibility = () => {
            if (!document.hidden) heartbeat();
        };
        onMounted(() => {
            heartbeat();
            timer = setInterval(heartbeat, 20_000);
            document.addEventListener('visibilitychange', handleVisibility);
        });
        onBeforeUnmount(() => {
            clearInterval(timer);
            document.removeEventListener('visibilitychange', handleVisibility);
        });
        return { online };
    };

    window.RPHubPresence = Object.freeze({ usePresence });
})();
