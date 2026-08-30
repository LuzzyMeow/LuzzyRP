# 完整修复方案：Agentic 多步工具调用（RP 场景优化版）

## 设计哲学

这是 **AI 角色扮演** 场景，不是通用 chatbot。核心目标：

| RP 场景痛点 | 本方案如何解决 |
|---|---|
| 模型"失忆"——不查记忆就编造剧情 | 强制 `tool_choice: 'required'`，每次回复前必须调用工具 |
| 模型不知道世界观细节就瞎编 | 多步循环允许 `world-recall` → `vector-memory` 链式检索 |
| 单次工具调用不够——查了地名还要查人名 | Agentic 循环最多 10 步，模型自主决定何时信息充分 |
| 被动工具（memory-recall/world-recall）干扰主动决策 | 从 `tools` 参数中过滤，仅保留在 system prompt 标注"已自动执行" |
| 续写时工具"消失"——模型看不到工具无法继续检索 | 续写时仍注入 `tools` 参数 + `tool_choice: 'auto'` |

---

## 数据流总览

```
用户消息（如："我去清龙城的集市找那个商人"）
  │
  ▼
[预执行] world-recall 三策略召回 → 注入 <world_recall_result>
[预执行] memory-recall 向量检索 → 注入 <memory_recall_result>
  │
  ▼
[首次 API 请求] tool_choice: 'required'
  │  system prompt 含角色卡 + 世界书 + <tool_protocol> 协议
  │  tools 参数 = 仅主动工具（过滤掉被动工具）
  │  reasoning_content: CoT 思考（分析角色心理/剧情走向）
  │  tool_calls: 模型决定调用 vector-memory:"清龙城 商人"
  │  content: 空（强制工具调用，不输出正文）
  │
  ▼
[执行工具] → 添加 tool_result 消息
  │
  ▼
[续写 1] tool_choice: 'auto'（模型可选择继续调用或输出正文）
  │  模型看到 vector-memory 结果，发现还需查"商人"的具体信息
  │  tool_calls: keyword-search:"商人 集市"
  │
  ▼
[执行工具] → 添加 tool_result 消息
  │
  ▼
[续写 2] tool_choice: 'auto'
  │  信息充分，模型输出 content（角色对话/场景描写）
  │  tool_calls: 无 → 循环结束
  │
  ▼
最终输出：含 CoT 思考 + 正文 + 2 次工具调用记录
```

---

## 逐文件修改清单

### 修改 1：`frontend/app/types/luzzy.ts` — 类型扩展

```typescript
// === 新增 ===

/**
 * 被动触发工具集合
 * 这些工具由系统预执行，不注入 tools 参数，不出现在 agentic 循环中
 */
export const PASSIVE_TOOL_TYPES: ReadonlySet<BuiltinToolType> = new Set([
  'memory-recall',
  'world-recall',
]);

// === 修改 ===

export interface ToolGlobalSettings {
  mode: ToolGlobalMode;
  maxAgentSteps: number;  // 新增：Agentic 循环最大步数
}
```

**改动量**：新增 1 个常量 + 1 个字段。**风险**：低。

---

### 修改 2：`frontend/app/stores/slices/settings-slice.ts` — 默认值 + 强制迁移

```typescript
// === 修改 2a: 默认值 ===
export const DEFAULT_TOOL_GLOBAL_SETTINGS: ToolGlobalSettings = {
  mode: "active",         // 改: "force" → "active"
  maxAgentSteps: 10,      // 新增
};

// === 修改 2b: loadFromStorage 中的强制迁移 ===
// 在加载持久化数据时（约 line 860-865），替换为：
toolGlobalSettings: (() => {
  const saved = data.toolGlobalSettings;
  if (!saved || typeof saved !== 'object') {
    return { ...DEFAULT_TOOL_GLOBAL_SETTINGS };
  }
  return {
    // 强制迁移：force → active
    mode: saved.mode === 'force' ? 'active' : (saved.mode ?? 'active'),
    // 旧数据无 maxAgentSteps 字段时补默认值
    maxAgentSteps: typeof saved.maxAgentSteps === 'number'
      ? Math.max(1, Math.min(20, saved.maxAgentSteps))
      : 10,
  };
})(),

// === 修改 2c: setToolGlobalMode 不变（用户仍可手动切回 force）===
// force 模式保留为向后兼容选项，但不再作为默认值
```

**改动量**：改 1 行默认值 + 新增加载迁移逻辑 ~10 行。**风险**：中（影响所有现有用户默认行为，但 force 模式仍可选）。

---

### 修改 3：`frontend/app/services/apiClient.ts` — 支持 tool_choice: 'required' + 回退

```typescript
// === 修改 3a: ApiRequestBodySettings 新增字段 ===
export interface ApiRequestBodySettings {
  enableThinking?: boolean;
  thinkingDepth?: string;
  customRequestBody?: string;
  activeTools?: Array<{
    type: string;
    callName: string;
    description: string;
    isBuiltin: boolean;
  }>;
  forceToolCall?: boolean;  // 新增: true → tool_choice: 'required'
}

// === 修改 3b: buildApiRequestBody 修改 tool_choice 逻辑 ===
// 替换 line 547-557:
if (settings.activeTools && settings.activeTools.length > 0) {
  result.tools = settings.activeTools.map((tool) => ({
    type: 'function',
    function: {
      name: tool.callName,
      description: tool.description,
      parameters: buildToolSchema(tool.type),
    },
  }));
  result.tool_choice = settings.forceToolCall ? 'required' : 'auto';
}
```

**回退机制**（在 `chat-slice.ts` 的 `callApiWithRetry` 中实现，见修改 5）。

**改动量**：新增 1 字段 + 改 1 行。**风险**：低。

---

### 修改 4：`frontend/app/services/chatService.ts` — 条件协议注入

```typescript
// === 修改 4a: 导出 BUILTIN_TOOL_INFO ===
// 将 `const BUILTIN_TOOL_INFO` 改为 `export const BUILTIN_TOOL_INFO`
// （内容不变，仅加 export 关键字）

// === 修改 4b: BuildContextParams 新增字段 ===
export interface BuildContextParams {
  // ... 现有所有字段不变 ...
  /** 工具模式：控制协议注入方式 */
  toolMode?: ToolGlobalMode;
  /** Agentic 最大步数：写入协议提示词 */
  maxAgentSteps?: number;
}

// === 修改 4c: buildContext 内条件注入 ===
// 替换 line 552-561:
const toolMode = params.toolMode ?? 'active';
const maxSteps = params.maxAgentSteps ?? 10;

if (toolMode === 'force') {
  // force 模式：注入 <available_tools> 文本标签列表（现有逻辑不变）
  const toolDescriptions = buildToolDescriptions(params.builtinToolConfigs, params.activeTools);
  if (toolDescriptions) systemPromptParts.push(toolDescriptions);
} else {
  // active/adaptive 模式：注入原生 function calling 协议指引
  // 不注入 <available_tools>，避免双协议冲突
  systemPromptParts.push(buildNativeToolProtocol(maxSteps));
}

// === 修改 4d: 新增函数 ===
/**
 * 构建 native function calling 协议提示词
 * 在 active/adaptive 模式下注入 system prompt，引导模型使用原生 tool_calls
 */
function buildNativeToolProtocol(maxSteps: number): string {
  return `<tool_protocol>
当前使用原生 function calling 协议。通过 API 的 tools 参数调用工具，禁止输出 <tool_calls> 文本标签。

【强制规则】
1. 每次回复前必须至少调用一个最相关的工具。不调用任何工具就直接回复是严重错误。
2. 可在单次回复中进行多轮工具调用——先调用工具收集信息，基于结果决定是否需要更多工具，直到信息充分后输出正文。
3. 工具调用优先于正文输出。首次响应中仅进行工具调用（reasoning_content 可正常思考），正文在工具结果返回后的续写中输出。
4. 最多可进行 ${maxSteps} 轮工具调用。信息充分后停止调用工具，输出正文。

【查询关键词规则】
- 从上下文中提取具体实体名称（地名、人名、物品、事件、概念）
- query 参数为空格分隔的多个关键词
- 禁止泛化词："当前" "现在" "地点" "设定" "信息" "情况" "背景"
- 正确：query="清龙城 第2区 市集 摊位"
- 错误：query="周围环境场景设定"

【多轮调用策略】
- 第一轮：根据用户消息提取关键词，调用最相关的工具
- 后续轮次：根据前一轮工具结果，补充查询相关实体或换用其他工具
- 信息充分后：输出角色对话或场景描写（正文）
</tool_protocol>`;
}
```

**改动量**：导出 1 个常量 + 新增 2 个字段 + 新增 1 个函数 + 条件分支。**风险**：中。

---

### 修改 5：`frontend/app/stores/slices/chat-slice.ts` — 核心改造

这是最大的修改，分为多个子改动。

#### 5a. 删除 `MAX_CONTINUATIONS`，改为动态读取

```typescript
// 删除 line 70:
// const MAX_CONTINUATIONS = 3;

// 在 generateResponse 内部（约 line 292 附近）新增:
const maxAgentSteps = get().toolGlobalSettings.maxAgentSteps ?? 10;
```

#### 5b. `callApiWithRetry` / `callApiAndUpdate` 签名改为 options 对象 + 回退机制

```typescript
// 替换 line 310-367 (callApiWithRetry):
const callApiWithRetry = async (
  msgId: string,
  contextMsgs: ChatMessage[],
  options: {
    skipToolsInjection?: boolean;
    forceToolCall?: boolean;
  } = {},
): Promise<{
  content: string;
  reasoning: string;
  cot: string;
  toolCalls: Array<{ id: string; function: { name: string; arguments: string } }>;
}> => {
  const maxRetries = 3;
  const baseDelays = [2000, 4000, 8000];
  let lastError: unknown;
  let triedForceToolCallFallback = false;  // 回退标记

  for (let attempt = 0; attempt <= maxRetries; attempt++) {
    if (abortController?.signal.aborted) {
      throw new DOMException('Aborted', 'AbortError');
    }
    try {
      return await callApiAndUpdate(msgId, contextMsgs, options);
    } catch (err) {
      if (err instanceof DOMException && err.name === 'AbortError') throw err;

      const errMessage = err instanceof Error ? err.message : String(err);

      // === 回退机制: tool_choice: 'required' 不支持时回退到 'auto' ===
      if (
        options.forceToolCall &&
        !triedForceToolCallFallback &&
        (errMessage.includes('400') ||
          errMessage.includes('tool_choice') ||
          errMessage.includes('invalid') ||
          errMessage.includes('Bad Request'))
      ) {
        logger.warn('api', 'tool_choice: "required" 不被支持，回退到 "auto"');
        triedForceToolCallFallback = true;
        options = { ...options, forceToolCall: false };
        continue;  // 立即重试，不等待
      }

      // === 现有 429 重试逻辑不变 ===
      const is429 = errMessage.includes('429') ||
                    errMessage.includes('TooManyRequests') ||
                    errMessage.includes('ServerOverloaded') ||
                    errMessage.includes('server overload');

      if (!is429 || attempt === maxRetries) throw err;

      const delay = baseDelays[attempt];
      console.warn(`[ChatSlice] API 429, ${delay / 1000}s 后重试 (${attempt + 1}/${maxRetries})`);
      get().updateMessage(msgId, {
        loading: true,
        error: `服务器繁忙, ${delay / 1000}s 后重试 (${attempt + 1}/${maxRetries})...`,
      });
      await new Promise<void>((resolve, reject) => {
        const timer = setTimeout(resolve, delay);
        const onAbort = (): void => {
          clearTimeout(timer);
          reject(new DOMException('Aborted', 'AbortError'));
        };
        if (abortController?.signal.aborted) { onAbort(); return; }
        abortController?.signal.addEventListener('abort', onAbort, { once: true });
      });
      get().updateMessage(msgId, { error: undefined });
      lastError = err;
    }
  }
  throw lastError;
};

// 替换 line 369-373 (callApiAndUpdate 签名):
const callApiAndUpdate = async (
  msgId: string,
  contextMsgs: ChatMessage[],
  options: {
    skipToolsInjection?: boolean;
    forceToolCall?: boolean;
  } = {},
): Promise<{...}> => {
```

#### 5c. 修复工具描述 + 过滤被动工具（`callApiAndUpdate` 内部）

```typescript
// 替换 line 472-499:
import { PASSIVE_TOOL_TYPES } from "~/types/luzzy";
import { BUILTIN_TOOL_INFO } from "~/services/chatService";

const skipTools = options.skipToolsInjection ?? false;
const forceTool = options.forceToolCall ?? false;

// active/adaptive 模式: 过滤被动工具，用真实描述
const builtinToolsForRequest =
  toolGlobalSettings.mode !== "force" && !skipTools
    ? builtinToolConfigs
        .filter((c) => c.enabled && !PASSIVE_TOOL_TYPES.has(c.type))
        .map((c) => ({
          type: c.type,
          callName: c.type,
          description: BUILTIN_TOOL_INFO[c.type]?.description ?? c.type,
          isBuiltin: true as const,
        }))
    : [];

const activeToolsForRequest =
  toolGlobalSettings.mode !== "force" && !skipTools &&
  (activeTools.length > 0 || builtinToolsForRequest.length > 0)
    ? [
        ...builtinToolsForRequest,
        ...activeTools
          .filter((t) => t.enabled)
          .map((tool) => ({
            type: tool.type,
            callName: tool.callName || tool.name,
            description: tool.description,
            isBuiltin: false as const,
          })),
      ]
    : undefined;
```

#### 5d. 传 `forceToolCall` 到 `buildApiRequestBody`

```typescript
// 替换 line 501-513:
const requestBody = buildApiRequestBody(
  { model: actualModel, messages: apiMessages, stream: get().stream },
  {
    enableThinking: settings.enableThinking,
    thinkingDepth,
    customRequestBody: get().customRequestBody,
    activeTools: activeToolsForRequest,
    forceToolCall: forceTool,  // 新增
  },
);
```

#### 5e. `buildContext` 传 `toolMode`

```typescript
// 替换 line 386-399 区域的 buildContext 调用:
const { apiMessages: rawApiMessages } = await buildContext({
  messages: contextMsgs,
  character: currentCharacter,
  user: activeUser,
  presets,
  worldInfoEntries,
  settings,
  apiProviders: allProviders,
  apiProviderKeys: get().apiProviderKeys,
  vectorMemoryShards,
  memorySettings,
  sessionId: currentSessionId ?? undefined,
  builtinToolConfigs,
  activeTools,
  skipWorldInfoInjection: worldRecallPreExecuted,
  toolMode: toolGlobalSettings.mode,      // 新增
  maxAgentSteps: maxAgentSteps,           // 新增
});
```

#### 5f. 首次 API 请求：`forceToolCall: true`

```typescript
// 替换 line 1216-1217:
const {
  content: cotRawContent,
  reasoning: cotReasoning,
  cot: cotContent,
  toolCalls: nativeToolCallsFromMain,
} = await callApiWithRetry(assistantMessageId, contextMessages, {
  forceToolCall: true,  // 新增: 首次强制工具调用
});
```

#### 5g. 替换原生 tool_calls 的单次续写为 Agentic 循环

这是核心改动。替换 `line 1388-1592` 的整个 `if (nativeToolCalls && nativeToolCalls.length > 0)` 块：

```typescript
if (nativeToolCalls && nativeToolCalls.length > 0) {
  // === Agentic 多步循环 ===
  logger.info("api", `检测到原生 tool_calls（${nativeToolCalls.length} 个），进入 Agentic 循环（最大 ${maxAgentSteps} 步）`);

  const nativeAgentSteps: AgentStep[] = [
    ...(get().messages.find((m) => m.id === assistantMessageId)?.agentSteps ?? []),
  ];
  const nativeToolCallRecords: ToolCall[] = [
    ...(get().messages.find((m) => m.id === assistantMessageId)?.toolCalls ?? []),
  ];

  // 持久化首批 tool_calls
  const persistedToolCalls: ToolCall[] = nativeToolCalls.map((tc) => {
    let queryStr = '';
    try {
      const args = JSON.parse(tc.function.arguments || '{}');
      queryStr = args.query ?? '';
    } catch { queryStr = tc.function.arguments; }
    return {
      id: tc.id,
      toolName: tc.function.name,
      callLabel: tc.function.name,
      query: queryStr,
      reason: 'native tool_calls',
      status: 'receiving' as const,
    };
  });
  get().updateMessage(assistantMessageId, {
    toolCalls: [...nativeToolCallRecords, ...persistedToolCalls],
  });

  // === 循环检测: 记录已执行的 (tool, query) 对 ===
  const executedCalls = new Set<string>();

  let currentToolCalls = nativeToolCalls;
  let stepCount = 0;

  while (stepCount < maxAgentSteps) {
    if (abortController?.signal.aborted) break;

    // 执行本轮所有工具调用
    for (const tc of currentToolCalls) {
      if (abortController?.signal.aborted) break;

      let queryStr = '';
      try {
        const args = JSON.parse(tc.function.arguments || '{}');
        queryStr = args.query ?? '';
      } catch { queryStr = tc.function.arguments; }

      // === 循环检测: 同一 (tool, query) 已执行过 → 终止 ===
      const callKey = `${tc.function.name}|${queryStr.trim().toLowerCase()}`;
      if (executedCalls.has(callKey)) {
        logger.warn("api", `检测到重复工具调用（${tc.function.name}: ${queryStr}），终止 Agentic 循环`);
        stepCount = maxAgentSteps;  // 强制退出外层 while
        break;
      }
      executedCalls.add(callKey);

      try {
        logger.info("api", `[Agentic 步骤 ${stepCount + 1}/${maxAgentSteps}] 执行: ${tc.function.name}（query=${queryStr.slice(0, 50)}）`);

        // 添加 tool_call 步骤（运行中）
        const nativeCallStepId = uuidv4();
        const nativeCallStep: AgentStep = {
          id: nativeCallStepId,
          type: "tool_call",
          title: tc.function.name,
          content: queryStr,
          status: "running",
          startedAt: Date.now(),
        };
        nativeAgentSteps.push(nativeCallStep);
        get().updateMessage(assistantMessageId, { agentSteps: [...nativeAgentSteps] });

        // 执行工具
        const rawResult = await executeToolByName(tc.function.name, queryStr);
        const truncatedResult = rawResult.length > 2000
          ? rawResult.slice(0, 2000) + '\n...[结果已截断]'
          : rawResult;

        // 标记完成
        nativeCallStep.status = "completed";
        nativeCallStep.endedAt = Date.now();
        const nativeResultStep: AgentStep = {
          id: uuidv4(),
          type: "tool_result",
          title: tc.function.name,
          content: truncatedResult,
          status: "completed",
          startedAt: nativeCallStep.startedAt,
          endedAt: Date.now(),
        };
        nativeAgentSteps.push(nativeResultStep);

        const matchedTool = filteredTools.find(
          (t) => t.callName === tc.function.name || t.name === tc.function.name,
        );
        nativeToolCallRecords.push({
          id: uuidv4(),
          toolName: matchedTool?.name ?? tc.function.name,
          callLabel: tc.function.name,
          query: queryStr,
          reason: "native tool_calls",
          status: "completed" as const,
          result: truncatedResult,
        });

        // 持久化工具结果
        const toolResultMessage: ChatMessage = {
          id: uuidv4(),
          role: "user",
          content: `<tool_call_result tool="${tc.function.name}">\n${truncatedResult}\n</tool_call_result>`,
          createdAt: Date.now(),
          metadata: {
            toolCallId: tc.id,
            toolName: tc.function.name,
            isToolResult: true,
          },
        };
        get().addMessage(toolResultMessage);

        get().updateMessage(assistantMessageId, {
          toolCalls: [...nativeToolCallRecords],
          agentSteps: [...nativeAgentSteps],
        });
      } catch (e) {
        console.warn('[Tool Calls] 工具执行失败:', tc.function.name, e);
        const errorMsg = e instanceof Error ? e.message : String(e);

        const errorStep: AgentStep = {
          id: uuidv4(),
          type: "tool_call",
          title: tc.function.name,
          content: errorMsg,
          status: "error",
          startedAt: Date.now(),
          endedAt: Date.now(),
        };
        nativeAgentSteps.push(errorStep);

        const matchedTool = filteredTools.find(
          (t) => t.callName === tc.function.name || t.name === tc.function.name,
        );
        nativeToolCallRecords.push({
          id: uuidv4(),
          toolName: matchedTool?.name ?? tc.function.name,
          callLabel: tc.function.name,
          query: '',
          reason: "native tool calls",
          status: "error" as const,
          error: errorMsg,
        });

        const toolErrorMessage: ChatMessage = {
          id: uuidv4(),
          role: "user",
          content: `<tool_call_result tool="${tc.function.name}">\n工具执行失败: ${errorMsg}\n</tool_call_result>`,
          createdAt: Date.now(),
          metadata: { toolCallId: tc.id, toolName: tc.function.name, isToolResult: true },
        };
        get().addMessage(toolErrorMessage);

        get().updateMessage(assistantMessageId, {
          toolCalls: [...nativeToolCallRecords],
          agentSteps: [...nativeAgentSteps],
        });
      }
    }

    if (stepCount >= maxAgentSteps) break;

    stepCount++;
    if (stepCount >= maxAgentSteps) {
      logger.warn("api", `达到最大 Agentic 步数: ${maxAgentSteps}`);
      break;
    }

    // === 续写请求: tool_choice: 'auto'，仍注入 tools ===
    const continuationMessage: ChatMessage = {
      id: uuidv4(),
      role: "assistant",
      content: "",
      createdAt: Date.now(),
      loading: true,
    };
    get().addMessage(continuationMessage);
    currentAssistantId = continuationMessage.id;

    const newContextMessages = get().messages.filter(
      (m) => m.id !== continuationMessage.id,
    );

    const { toolCalls: nextToolCalls } = await callApiWithRetry(
      continuationMessage.id,
      newContextMessages,
      {
        skipToolsInjection: false,   // 关键修复: 续写仍注入 tools
        forceToolCall: false,        // 续写用 auto，模型可选择继续调用或输出正文
      },
    );

    if (!nextToolCalls || nextToolCalls.length === 0) {
      logger.info("api", `Agentic 循环完成（${stepCount} 步），模型输出最终正文`);
      break;
    }

    currentToolCalls = nextToolCalls;
  }

  get().updateMessage(assistantMessageId, { agentSteps: [...nativeAgentSteps] });
}
```

#### 5h. 文本标签路径：`MAX_CONTINUATIONS` → `maxAgentSteps`

```typescript
// 替换 line 1593-1604 区域:
} else if (activeTools.length > 0 || builtinToolConfigs.some((c) => c.enabled)) {
  // force 模式: 文本标签续写循环
  const characterUuid = currentCharacter?.uuid ?? null;
  const filteredTools = filterToolsForCharacter(activeTools, characterUuid);

  for (let iteration = 0; iteration < maxAgentSteps; iteration++) {  // 改: MAX_CONTINUATIONS → maxAgentSteps
    if (iteration === maxAgentSteps - 1) {
      logger.warn("api", `达到最大续写次数: ${maxAgentSteps}`);
    }
    // ... 后续逻辑不变，但续写调用改为 options 对象:
    await callApiWithRetry(
      continuationMessage.id,
      newContextMessages,
      { skipToolsInjection: false },  // 改: true → false (force 模式下 no-op，但语义正确)
    );
```

#### 5i. 所有 `callApiWithRetry` / `callApiAndUpdate` 调用点更新

全文件搜索 `callApiWithRetry(` 和 `callApiAndUpdate(`，将所有调用点的第三个参数从 `boolean` 改为 `options` 对象：

| 调用位置 | 旧代码 | 新代码 |
|---|---|---|
| line 325 | `callApiAndUpdate(msgId, contextMsgs, skipToolsInjection)` | `callApiAndUpdate(msgId, contextMsgs, { skipToolsInjection: options.skipToolsInjection, forceToolCall: options.forceToolCall })` |
| line 1217 | `callApiWithRetry(assistantMessageId, contextMessages)` | `callApiWithRetry(assistantMessageId, contextMessages, { forceToolCall: true })` |
| line 1587-1591 | `callApiWithRetry(..., true)` | `callApiWithRetry(..., { skipToolsInjection: false, forceToolCall: false })` |
| line 1701-1705 | `callApiWithRetry(..., true)` | `callApiWithRetry(..., { skipToolsInjection: false })` |
| line 1798 | `callApiWithRetry(...)` | `callApiWithRetry(..., { skipToolsInjection: false })` |

---

### 修改 6：`frontend/app/services/presetContent.ts` — 强化 Step 8

```typescript
// 替换 line 372-399 (Step 8 内容):
### Step 8：工具调用协议

当系统提供了可用工具时，你必须优先调用工具收集信息，而非直接回复。

【强制工具调用 — 无例外】
- 每次回复前必须至少调用一个最相关的工具（被动触发工具除外）。
- 不调用任何工具就直接回复是严重错误。
- 即使是简单问候，也必须调用工具检索相关记忆或设定。
- 宁可多调用工具也不要遗漏关键信息——工具调用的成本远低于信息缺失导致的回复质量下降。
- 在角色扮演场景中，主动检索记忆和世界书设定是保持角色一致性和世界观连贯性的关键。

【工具使用优先级】
注意：部分工具已改为系统被动预执行（如记忆召回和世界书召回），你无需主动调用它们。具体哪些工具为被动触发，请参考 <available_tools> 中的工具描述。
在以下情况中，你必须主动调用工具：
1. 当需要回忆之前对话的细节时 → 调用记忆搜索类工具
2. 当用户消息涉及特定地名、人名、物品、事件，且需要关键词精确匹配时 → 调用世界书搜索类工具
3. 当需要联网查询实时信息时 → 调用联网搜索类工具
4. 当存在任何信息缺口或不确定的设定时 → 优先调用工具填补

只有在完全确信无需任何外部信息、且对话上下文已完全充分时，才不调用工具。

【查询关键词拆分规则 — 极其重要】
query 参数必须为空格分隔的多个关键词，从上下文中提取具体实体名称，禁止泛化描述词。

禁止词（无效泛化）："当前" "现在" "地点" "设定" "信息" "情况" "背景" "上下文"
正确做法：从开场白/世界书/历史对话中提取地名、人名、物品、事件、概念等具体名词。

错误示例：world-recall:周围环境场景设定
正确示例：world-recall:清龙城 第2区 市集 摊位 人类

【可用工具说明】
具体的工具列表、调用名称、功能介绍和返回条数请参考下方 <available_tools> 部分。
该列表由系统根据当前启用的内置工具、SKILL 工具和 MCP 工具动态生成。
当没有可用工具时，直接进行回复即可。
```

**改动量**：强化语言 + 新增 RP 场景说明。**风险**：低。

---

### 修改 7：设置 UI — 暴露 `maxAgentSteps` 配置

**文件**: 需要找到工具设置页面文件。根据之前的探索，应该是 `frontend/app/components/settings/tools.tsx` 或类似路径。

```tsx
// 新增: Agentic 最大步数滑块
<Slider
  label="最大工具调用步数"
  description="模型在单次回复中最多可进行的工具调用轮次。步数越多推理越深，但 token 消耗也越大。"
  min={1}
  max={20}
  step={1}
  value={toolGlobalSettings.maxAgentSteps}
  onChange={(v) => setToolGlobalSettings({
    ...toolGlobalSettings,
    maxAgentSteps: v,
  })}
/>
```

---

## 安全机制汇总

| 机制 | 实现 | 位置 |
|---|---|---|
| **步数上限** | `maxAgentSteps`（默认 10，可配 1-20） | `while` 条件 |
| **循环检测** | `Set<string>` 记录 `(tool\|query)` 对，重复即 break | `executedCalls` |
| **Abort 支持** | 每轮检查 `abortController.signal.aborted` | `while` + `for` 内部 |
| **`tool_choice` 回退** | API 400 → `forceToolCall: false` 立即重试 | `callApiWithRetry` |
| **无主动工具时跳过** | 过滤被动工具后为空 → 不设 `tool_choice`，不强制 | `callApiAndUpdate` |
| **Token 预算** | 工具结果限 2000 字符 + buildContext 压缩 | 现有逻辑 |
| **KV Cache 友好** | system prompt 在循环中不变，续写仅追加消息 | 架构设计 |

---

## 循环检测策略说明

**选择方案**：`Set<string>` 记录 `toolName|queryNormalized`，重复即终止。

**为什么这是最优**：

| 策略 | 优点 | 缺点 | 适用场景 |
|---|---|---|---|
| 连续两次相同才 break | 宽松，允许间隔重复 | A→B→A→B 检测不到 | 通用 chatbot |
| **任意重复即 break（选定）** | 简单有效，阻止所有重复 | 不允许跨步骤重新查询 | **RP 场景** |
| 滑动窗口 N 步内重复 | 介于两者之间 | 实现复杂，N 难定 | 需要精细控制 |

**RP 场景下"任意重复即 break"最优的原因**：
1. 工具结果已持久化在消息历史中，模型可在续写时看到——重新查同一关键词是浪费
2. 如果模型需要更多信息，应该用**不同关键词**或**不同工具**查询
3. 同一工具+同一查询重复，说明模型陷入"查了→忘了→又查"的循环，必须终止

---

## 向后兼容

| 场景 | 行为 |
|---|---|
| 旧用户 `mode: 'force'` | 加载时强制迁移为 `active`，但用户可手动切回 `force` |
| `force` 模式运行 | 文本标签协议路径保留，`MAX_CONTINUATIONS` 改为 `maxAgentSteps` |
| 无 `maxAgentSteps` 字段的旧数据 | 加载时补默认值 10 |
| API 不支持 `tool_choice: 'required'` | 自动回退 `'auto'`，日志记录 |
| 无主动工具可用（仅被动工具） | 不设 `tool_choice`，不强制，直接输出正文 |

---

## 实施顺序

```
修改 1 (luzzy.ts)          → 类型定义
  ↓
修改 2 (settings-slice.ts) → 默认值 + 迁移
  ↓
修改 3 (apiClient.ts)      → API 层支持
  ↓
修改 4 (chatService.ts)    → 协议注入
  ↓
修改 5 (chat-slice.ts)      → 核心改造（依赖 1-4）
  ↓
修改 6 (presetContent.ts)  → 提示词强化
  ↓
修改 7 (设置 UI)           → 用户配置
  ↓
TypeScript 检查 + Build
```

每步完成后运行 `npx tsc --noEmit` 确认无类型错误。全部完成后运行 `pnpm run build` 确认构建通过。

---

## 预期效果

| 指标 | 修改前 | 修改后 |
|---|---|---|
| 最大工具调用次数 | 1 | 10（可配） |
| 工具描述质量 | `"world-recall"` | 完整中文描述 |
| 强制工具调用 | 无 | `tool_choice: 'required'` |
| 续写时可见工具 | 否（`skipToolsInjection`） | 是 |
| 被动工具干扰 | 存在 | 过滤 |
| 双协议冲突 | 存在 | 按模式分离 |
| RP 场景记忆检索 | 依赖模型自觉 | 强制检索 |

---
