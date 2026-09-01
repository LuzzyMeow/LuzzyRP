import http from 'node:http';

const port = Number(process.env.PORT) || 3000;
const parseVersionId = value => /^\d{5}$/.test(String(value ?? '').trim())
    ? Number(value)
    : null;
const versionSourceUrl = 'https://sta1n156.github.io/RP-Hub/assets/js/built-in-content.js';
const versionRefreshMs = 60_000;
let latestVersionId = 10189;
let nextVersionRefreshAt = 0;
let versionRefreshPromise = null;
const allowedOrigins = String(process.env.ALLOWED_ORIGINS || '*')
    .split(',')
    .map(origin => origin.trim())
    .filter(Boolean);

const refreshLatestVersionId = () => {
    if (versionRefreshPromise) return versionRefreshPromise;
    if (Date.now() < nextVersionRefreshAt) return Promise.resolve();
    nextVersionRefreshAt = Date.now() + versionRefreshMs;
    versionRefreshPromise = fetch(`${versionSourceUrl}?t=${Date.now()}`, {
        cache: 'no-store',
        signal: AbortSignal.timeout(5_000)
    })
        .then(response => {
            if (!response.ok) throw new Error(`HTTP ${response.status}`);
            return response.text();
        })
        .then(source => {
            const versionId = parseVersionId(
                source.match(/window\.RPHubLatestUpdate\s*=\s*Object\.freeze\(\s*\{\s*id\s*:\s*(\d{5})\b/)?.[1]
            );
            if (versionId === null) throw new Error('Version ID not found');
            latestVersionId = Math.max(latestVersionId, versionId);
        })
        .catch(error => console.warn('Latest version check failed:', error.message))
        .finally(() => {
            versionRefreshPromise = null;
        });
    return versionRefreshPromise;
};

const getCorsOrigin = (origin) => {
    if (allowedOrigins.includes('*')) return '*';
    return origin && allowedOrigins.includes(origin) ? origin : '';
};

const sendJson = (response, status, body, corsOrigin = '') => {
    response.writeHead(status, {
        'Content-Type': 'application/json; charset=utf-8',
        'Cache-Control': 'no-store',
        ...(corsOrigin ? { 'Access-Control-Allow-Origin': corsOrigin, Vary: 'Origin' } : {})
    });
    response.end(JSON.stringify(body));
};

const readJson = async (request) => {
    let body = '';
    for await (const chunk of request) {
        body += chunk;
        if (body.length > 2048) throw new Error('Request too large');
    }
    return JSON.parse(body || '{}');
};

const server = http.createServer(async (request, response) => {
    const origin = request.headers.origin || '';
    const corsOrigin = getCorsOrigin(origin);
    const url = new URL(request.url || '/', 'http://localhost');

    if (request.method === 'OPTIONS') {
        if (origin && !corsOrigin) return sendJson(response, 403, { error: 'Origin not allowed' });
        response.writeHead(204, {
            'Access-Control-Allow-Origin': corsOrigin || '*',
            'Access-Control-Allow-Methods': 'GET, POST, OPTIONS',
            'Access-Control-Allow-Headers': 'Content-Type',
            'Access-Control-Max-Age': '86400',
            Vary: 'Origin'
        });
        return response.end();
    }

    if (origin && !corsOrigin) return sendJson(response, 403, { error: 'Origin not allowed' });

    if (request.method === 'GET' && url.pathname === '/health') {
        return sendJson(response, 200, { ok: true }, corsOrigin);
    }

    if (request.method === 'GET' && url.pathname === '/v1/version') {
        const currentVersionId = parseVersionId(url.searchParams.get('current'));
        await refreshLatestVersionId();
        return sendJson(response, 200, {
            latestVersionId,
            updateAvailable: currentVersionId !== null && latestVersionId > currentVersionId
        }, corsOrigin);
    }

    // Keep old pages update-capable without storing client IDs or counting users.
    if (request.method === 'POST' && url.pathname === '/v1/presence') {
        try {
            const currentVersionId = parseVersionId((await readJson(request)).versionId);
            await refreshLatestVersionId();
            return sendJson(response, 200, {
                latestVersionId,
                updateAvailable: currentVersionId !== null && latestVersionId > currentVersionId
            }, corsOrigin);
        } catch {
            return sendJson(response, 400, { error: 'Invalid request' }, corsOrigin);
        }
    }

    return sendJson(response, 404, { error: 'Not found' }, corsOrigin);
});

refreshLatestVersionId();

server.listen(port, '0.0.0.0', () => {
    console.log(`RP-Hub update check service listening on ${port}`);
});

const shutdown = () => server.close(() => process.exit(0));
process.on('SIGTERM', shutdown);
process.on('SIGINT', shutdown);

export { server };
