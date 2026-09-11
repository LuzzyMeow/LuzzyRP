/**
 * luzzy-chat-native.js 行为回归（Node 直跑，不进 Gradle 测试源集的 Kotlin 编译）
 *
 *   node app/src/test/js/chat-native.test.cjs
 *   退出码 0 = 全过
 *
 * 为什么要有这个文件：`onEvent` 的「先交付再摘处理器」顺序**只能靠行为测**——
 * 它是纯 JS 运行时语义，Kotlin/JVM 侧看不到。曾经写反过一次，后果是终态事件
 * （done / error）被静默丢弃，调用方 Promise 永不 settle，真机上表现为
 * 「一直生成中」。这里用 vm 沙箱加载**真实资产文件**（不是副本）来钉住它。
 */
'use strict';

const assert = require('assert');
const fs = require('fs');
const path = require('path');
const vm = require('vm');

const ASSET = path.join(__dirname, '..', '..', 'main', 'assets', 'ext', 'luzzy-chat-native.js');

/** 在沙箱里加载真实资产；[bridge] 为 null 表示没有原生桥（桌面浏览器场景）。 */
function loadChatNative(bridge) {
    const code = fs.readFileSync(ASSET, 'utf8');
    const sandbox = { window: {}, console: { log: () => {}, warn: () => {}, info: () => {} } };
    if (bridge !== null) sandbox.window.LuzzyBridge = bridge;
    vm.createContext(sandbox);
    vm.runInContext(code, sandbox, { filename: ASSET });
    return sandbox.window.Luzzy.chatNative;
}

/** 齐备三方法的假原生桥。 */
function fakeBridge(overrides) {
    return Object.assign({
        chatStart: () => 'j-echo',
        chatAbort: () => true,
        chatCapabilities: () => '{"available":true,"protocols":["openai","anthropic","gemini"]}',
    }, overrides || {});
}

/** 沙箱是独立 realm：跨 realm 对象的原型不同，deepStrictEqual 会误报。回主 realm 再比。 */
function plain(value) {
    return JSON.parse(JSON.stringify(value));
}

const results = [];
function test(name, fn) {
    try {
        fn();
        results.push(['PASS', name]);
    } catch (error) {
        results.push(['FAIL', name, error.message]);
    }
}

// ---------- 修复回归：终态事件必须先交付、再摘处理器 ----------

test('回归：仅注册 per-job 处理器时，done 必须送达调用方', () => {
    const chatNative = loadChatNative(fakeBridge());
    const seen = [];
    chatNative.setHandler('j-1', (event) => seen.push(event));

    chatNative.onEvent('j-1', '{"type":"done","finishReason":"stop"}');

    assert.strictEqual(seen.length, 1, 'done 被静默丢弃了（先 delete 后 dispatch 的老 bug）');
    assert.strictEqual(seen[0].type, 'done');
    assert.strictEqual(seen[0].finishReason, 'stop');
});

test('回归：仅注册 per-job 处理器时，error 必须送达调用方', () => {
    const chatNative = loadChatNative(fakeBridge());
    const seen = [];
    chatNative.setHandler('j-1', (event) => seen.push(event));

    chatNative.onEvent('j-1', '{"type":"error","message":"boom","retryable":false}');

    assert.strictEqual(seen.length, 1);
    assert.strictEqual(seen[0].type, 'error');
    assert.strictEqual(seen[0].message, 'boom');
});

test('回归：终态事件交付后处理器被摘掉（同一 jobId 不再重复送达）', () => {
    const chatNative = loadChatNative(fakeBridge());
    let calls = 0;
    chatNative.setHandler('j-1', () => { calls++; });

    chatNative.onEvent('j-1', '{"type":"done","finishReason":"stop"}');
    chatNative.onEvent('j-1', '{"type":"done","finishReason":"stop"}');

    assert.strictEqual(calls, 1, '终态后处理器应当已被摘除');
});

test('回归：全局兜底处理器也能收到终态事件', () => {
    const chatNative = loadChatNative(fakeBridge());
    const seen = [];
    chatNative.setHandler((event, jobId) => seen.push([jobId, event.type]));

    chatNative.onEvent('j-9', '{"type":"done","finishReason":"stop"}');

    assert.deepStrictEqual(seen, [['j-9', 'done']]);
});

test('回归：per-job 与全局兜底同时存在时，per-job 优先且兜底不被误删', () => {
    const chatNative = loadChatNative(fakeBridge());
    const perJob = [];
    const fallback = [];
    chatNative.setHandler((event, jobId) => fallback.push([jobId, event.type]));
    chatNative.setHandler('j-1', (event, jobId) => perJob.push([jobId, event.type]));

    chatNative.onEvent('j-1', '{"type":"done","finishReason":"stop"}');
    // 另一个 job 没有 per-job 处理器 → 仍应落到兜底（说明兜底没被上一个终态事件连带删掉）
    chatNative.onEvent('j-2', '{"type":"done","finishReason":"stop"}');

    assert.deepStrictEqual(perJob, [['j-1', 'done']]);
    assert.deepStrictEqual(fallback, [['j-2', 'done']]);
});

test('非终态事件（delta / usage）不摘处理器，可连续送达', () => {
    const chatNative = loadChatNative(fakeBridge());
    const seen = [];
    chatNative.setHandler('j-1', (event) => seen.push(event.type));

    chatNative.onEvent('j-1', '{"type":"delta","content":"你","reasoning":""}');
    chatNative.onEvent('j-1', '{"type":"usage","usage":{"prompt_tokens":1}}');
    chatNative.onEvent('j-1', '{"type":"delta","content":"好","reasoning":""}');
    chatNative.onEvent('j-1', '{"type":"done","finishReason":"stop"}');

    assert.deepStrictEqual(seen, ['delta', 'usage', 'delta', 'done']);
});

test('处理器自身抛错不外溢，后续事件照常送达', () => {
    const chatNative = loadChatNative(fakeBridge());
    const seen = [];
    chatNative.setHandler('j-1', (event) => {
        seen.push(event.type);
        throw new Error('处理器炸了');
    });

    chatNative.onEvent('j-1', '{"type":"delta","content":"a","reasoning":""}');
    chatNative.onEvent('j-1', '{"type":"done","finishReason":"stop"}');

    assert.deepStrictEqual(seen, ['delta', 'done']);
});

test('非法事件串被静默丢弃（不抛、不误摘处理器）', () => {
    const chatNative = loadChatNative(fakeBridge());
    const seen = [];
    chatNative.setHandler('j-1', (event) => seen.push(event.type));

    chatNative.onEvent('j-1', 'not json');
    chatNative.onEvent('j-1', '{"noType":true}');
    assert.deepStrictEqual(seen, []);

    chatNative.onEvent('j-1', '{"type":"done","finishReason":"stop"}');
    assert.deepStrictEqual(seen, ['done'], '非法帧不应把处理器摘掉');
});

test('abort 主动摘掉 per-job 处理器（原生侧中止时不再发终态事件）', () => {
    const chatNative = loadChatNative(fakeBridge());
    let calls = 0;
    chatNative.setHandler('j-1', () => { calls++; });

    assert.strictEqual(chatNative.abort('j-1'), true);
    chatNative.onEvent('j-1', '{"type":"delta","content":"a","reasoning":""}');

    assert.strictEqual(calls, 0);
});

// ---------- 降级面 ----------

test('无原生桥：ready 为 false、start 返回空串、capabilities 形状一致', () => {
    const chatNative = loadChatNative(null);

    assert.strictEqual(chatNative.ready(), false);
    assert.strictEqual(chatNative.start({ jobId: 'j-1' }), '');
    assert.strictEqual(chatNative.abort('j-1'), false);
    assert.deepStrictEqual(plain(chatNative.capabilities()), { available: false, reason: 'no-native-bridge' });
    // 没有处理器时收到事件也不能抛
    chatNative.onEvent('j-1', '{"type":"done","finishReason":"stop"}');
});

test('三方法缺一即视为不可用', () => {
    assert.strictEqual(loadChatNative(fakeBridge({ chatAbort: undefined })).ready(), false);
    assert.strictEqual(loadChatNative(fakeBridge({ chatStart: undefined })).ready(), false);
    assert.strictEqual(loadChatNative(fakeBridge({ chatCapabilities: undefined })).ready(), false);
    assert.strictEqual(loadChatNative(fakeBridge()).ready(), true);
});

test('start 传对象时序列化后交给 chatStart，并原样回传其返回的 jobId', () => {
    let received = null;
    const chatNative = loadChatNative(fakeBridge({
        chatStart: (payload) => { received = payload; return 'j-echo-42'; },
    }));

    assert.strictEqual(chatNative.start({ jobId: 'j-echo-42', protocol: 'openai' }), 'j-echo-42');
    assert.deepStrictEqual(JSON.parse(received), { jobId: 'j-echo-42', protocol: 'openai' });
});

test('start 遇到 chatStart 抛错 / 非字符串返回都降级为空串', () => {
    const throwing = loadChatNative(fakeBridge({ chatStart: () => { throw new Error('boom'); } }));
    assert.strictEqual(throwing.start({ jobId: 'j' }), '');

    const weird = loadChatNative(fakeBridge({ chatStart: () => 42 }));
    assert.strictEqual(weird.start({ jobId: 'j' }), '');
});

test('capabilities 转发原生 JSON 并解析成对象', () => {
    const chatNative = loadChatNative(fakeBridge());
    assert.deepStrictEqual(plain(chatNative.capabilities()), {
        available: true,
        protocols: ['openai', 'anthropic', 'gemini'],
    });

    const broken = loadChatNative(fakeBridge({ chatCapabilities: () => 'not json' }));
    assert.deepStrictEqual(plain(broken.capabilities()), { available: false, reason: 'exception' });
});

// ---------- 汇总 ----------

const failed = results.filter((row) => row[0] === 'FAIL');
for (const row of results) {
    console.log(row[0] === 'PASS' ? `  ok  ${row[1]}` : `  XX  ${row[1]}\n        ${row[2]}`);
}
console.log(`\n${results.length - failed.length}/${results.length} passed`);
process.exit(failed.length === 0 ? 0 : 1);
