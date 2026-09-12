package com.luzzymeow.luzzyrp.ui

/**
 * 开发调试挂点（**release 下无注册者，恒为 null，不产生任何行为**）。
 *
 * 为什么需要：模拟器只有英文 IME，`adb shell input text` 对 CJK 直接抛 NPE，
 * 而本应用的全部内容都是中文——没有注入通道就无法对 UI 做端到端验证。
 * 注册方在 `src/debug/`（[DevInputReceiver] 收到 adb 广播后调用），release 包不含该源集。
 *
 * 纪律：本对象只承载「把文本送进输入框」「触发发送」两个动作，不读写业务数据、
 * 不影响任何非 debug 构建的行为。
 */
object DevHooks {
    /** 把文本写入聊天输入框（debug 专用注册方设置）。 */
    var inputInjector: ((String) -> Unit)? = null

    /** 触发一次发送（等价于点发送按钮）。 */
    var sendTrigger: (() -> Unit)? = null
}
