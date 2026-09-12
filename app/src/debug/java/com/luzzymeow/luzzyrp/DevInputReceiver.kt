package com.luzzymeow.luzzyrp

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.luzzymeow.luzzyrp.ui.DevHooks

/**
 * 仅 debug 源集的开发文本注入接收器（见 [DevHooks] 的说明）。
 *
 * 用法：
 * ```
 * adb shell am broadcast -a com.luzzymeow.luzzyrp.DEV_INPUT \
 *   -n com.luzzymeow.luzzyrp.debug/com.luzzymeow.luzzyrp.DevInputReceiver \
 *   --es text '那棵苹果树……' --ez send true
 * ```
 * 该组件**不存在于 release 包**（源集隔离，不参与 release 合并）。
 */
class DevInputReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION) return
        intent.getStringExtra(EXTRA_TEXT)?.let { DevHooks.inputInjector?.invoke(it) }
        if (intent.getBooleanExtra(EXTRA_SEND, false)) DevHooks.sendTrigger?.invoke()
    }

    companion object {
        const val ACTION = "com.luzzymeow.luzzyrp.DEV_INPUT"
        const val EXTRA_TEXT = "text"
        const val EXTRA_SEND = "send"
    }
}
