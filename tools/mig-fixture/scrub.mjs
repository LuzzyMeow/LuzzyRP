// 夹具脱敏：把夹具里的**密钥类字段**换成等长占位符，并生成脱敏清单。
//
// 为什么必须有这一步：夹具是从真机态页面导出的，settings 里带着开发用 API Key；
// 而夹具要进仓库（公开）当测试资源。脱敏保留「字段存在 + 长度量级」，迁移器照旧能断言字段被搬运。
//
// 用法：node .workbuddy/mig/scrub.mjs <fixture.json>
import fs from 'node:fs';

const path = process.argv[2];
const raw = fs.readFileSync(path, 'utf8');
const j = JSON.parse(raw);
const redacted = [];

const placeholder = (name, value) => `<REDACTED:${name}:len${String(value).length}>`;

/** 精确路径脱敏（不用正则扫全树：避免误伤 worldinfo.keys / 用量 token 统计等正常字段） */
const scrubSettings = (s) => {
    if (!s || typeof s !== 'object') return;
    for (const k of ['apiKey', 'imageGenKey']) {
        if (typeof s[k] === 'string' && s[k]) {
            redacted.push(`rp_hub_settings.${k}`);
            s[k] = placeholder(k, s[k]);
        }
    }
    if (s.apiProviderKeys && typeof s.apiProviderKeys === 'object') {
        for (const id of Object.keys(s.apiProviderKeys)) {
            if (typeof s.apiProviderKeys[id] === 'string' && s.apiProviderKeys[id]) {
                redacted.push(`rp_hub_settings.apiProviderKeys.${id}`);
                s.apiProviderKeys[id] = placeholder('apiProviderKeys', s.apiProviderKeys[id]);
            }
        }
    }
    for (const p of [...(s.apiProviders || []), ...(s.apiProviderOverrides ? Object.values(s.apiProviderOverrides) : [])]) {
        if (p && typeof p.apiKey === 'string' && p.apiKey) {
            redacted.push(`provider(${p.id || '?'}).apiKey`);
            p.apiKey = placeholder('apiKey', p.apiKey);
        }
    }
};

const main = j.databases?.RPHubDB?.entries || {};
scrubSettings(main.rp_hub_settings);

// 全树兜底扫描：任何看起来像密钥的字符串（sk- 前缀 / 超长无分隔串）。
// usageLogKey 是上游用量日志的标识（request_id 或 created_at|model|tokens 拼接），不是密钥 → 白名单。
const BENIGN = [/usageLogKey$/, /contentFingerprint$/, /vectorChunkId$/, /embeddingQ$/, /\.id$/, /uuid$/];
const suspicious = [];
const walk = (node, at) => {
    if (node === null || typeof node !== 'object') return;
    for (const [k, v] of Object.entries(node)) {
        const p = `${at}.${k}`;
        if (typeof v === 'string') {
            if (/^sk-[A-Za-z0-9_-]{10,}$/.test(v)
                || (/^[A-Za-z0-9]{32,}$/.test(v) && !BENIGN.some((re) => re.test(p)))) {
                suspicious.push(`${p} (len ${v.length})`);
            }
        } else walk(v, p);
    }
};
walk(j.databases, 'databases');

j.provenance = {
    ...(j.provenance || {}),
    redaction: {
        at: new Date().toISOString(),
        note: 'settings 内的密钥字段已替换为 <REDACTED:name:lenN> 占位符（保留字段与长度，迁移器照旧断言字段被搬运）',
        fields: redacted,
        suspiciousRemaining: suspicious,
    },
};
fs.writeFileSync(path, JSON.stringify(j, null, 1));
console.log('redacted', redacted.length, 'field(s):');
redacted.forEach((r) => console.log('  -', r));
console.log('suspicious remaining:', suspicious.length);
suspicious.forEach((s) => console.log('  !', s));
