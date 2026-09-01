// Shared API endpoint helpers used by the main app and the novel page.
(function () {
    const buildApiEndpoint = (baseUrl, path) => {
        const root = String(baseUrl || '').replace(/\/+$/, '');
        const apiRoot = /\/v1$/i.test(root) ? root : `${root}/v1`;
        return `${apiRoot}/${String(path || '').replace(/^\/+/, '')}`;
    };

    window.RPHubApiUtils = Object.freeze({ buildApiEndpoint });
})();
