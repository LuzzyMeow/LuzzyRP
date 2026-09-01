// Remote version check. Configure the API URL in index.html.
(function () {
    const { onBeforeUnmount, onMounted } = Vue;
    const apiUrl = String(document.querySelector('meta[name="rphub-update-api"]')?.content || '')
        .trim()
        .replace(/\/+$/, '');
    const parseVersionId = value => /^\d{5}$/.test(String(value ?? '').trim())
        ? Number(value)
        : null;
    const currentVersionId = parseVersionId(window.RPHubLatestUpdate?.id);
    let notifiedVersionId = 0;

    const useUpdateCheck = () => {
        let timer = null;
        const check = async () => {
            if (!apiUrl || currentVersionId === null || document.hidden) return;
            try {
                const response = await fetch(`${apiUrl}/v1/version?current=${currentVersionId}`, {
                    cache: 'no-store'
                });
                if (!response.ok) throw new Error(`HTTP ${response.status}`);
                const data = await response.json();
                const latestVersionId = parseVersionId(data.latestVersionId);
                if (data.updateAvailable && latestVersionId > currentVersionId && latestVersionId > notifiedVersionId) {
                    notifiedVersionId = latestVersionId;
                    window.dispatchEvent(new CustomEvent('rphub:update-available', {
                        detail: { versionId: latestVersionId }
                    }));
                }
            } catch {
                // A failed check must not affect normal RP-Hub use.
            }
        };
        const handleVisibility = () => {
            if (!document.hidden) check();
        };
        onMounted(() => {
            check();
            timer = setInterval(check, 20_000);
            document.addEventListener('visibilitychange', handleVisibility);
        });
        onBeforeUnmount(() => {
            clearInterval(timer);
            document.removeEventListener('visibilitychange', handleVisibility);
        });
    };

    window.RPHubUpdateCheck = Object.freeze({ useUpdateCheck });
})();
