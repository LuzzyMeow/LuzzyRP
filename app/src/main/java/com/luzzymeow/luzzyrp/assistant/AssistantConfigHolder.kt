package com.luzzymeow.luzzyrp.assistant

/**
 * Web 端供应商配置的**只读**内存缓存（PLAN §1.2 / §14）。
 *
 * 设计缘由：Web 端配置（供应商 / API Key / 模型列表）存在 WebView 的 IndexedDB 里，
 * 原生侧读不到；因此由扩展层 `assets/ext/luzzy-assistant.js` 在页面就绪与设置变更时
 * 主动推送 JSON 到 `LuzzyBridge.setAssistantConfig`，此处缓存供助手模块读取。
 *
 * **安全（硬性要求）**：
 * - 仅驻留内存，不落盘、不进日志、不参与 Android 备份；
 * - 本类所有方法**不得**打印内容；
 * - 助手侧只读复用，不回写 Web 端配置（避免密钥二次落盘，PLAN §1.1）。
 */
object AssistantConfigHolder {

    @Volatile
    private var json: String = ""

    /** 最近一次推送时间（诊断用，不含配置内容）。 */
    @Volatile
    var lastUpdatedAtMillis: Long = 0L
        private set

    /** 覆盖式更新（空串表示清空）。 */
    fun update(payload: String) {
        json = payload
        lastUpdatedAtMillis = System.currentTimeMillis()
    }

    /** 返回当前缓存（未推送时为空串，调用方需按「未配置」处理）。 */
    fun get(): String = json

    /** 是否有可用配置。 */
    fun hasConfig(): Boolean = json.isNotEmpty()
}
