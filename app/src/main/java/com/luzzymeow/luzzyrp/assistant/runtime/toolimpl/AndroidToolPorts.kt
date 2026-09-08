package com.luzzymeow.luzzyrp.assistant.runtime.toolimpl

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.StatFs
import com.luzzymeow.luzzyrp.assistant.domain.tool.ClipboardPort
import com.luzzymeow.luzzyrp.assistant.domain.tool.ClockProvider
import com.luzzymeow.luzzyrp.assistant.domain.tool.DeviceInfoProvider
import java.io.File
import java.time.ZoneId

/**
 * Android 侧端口实现（PLAN §2.2 `assistant/runtime/toolimpl/`）。
 *
 * domain 层只依赖端口接口，Android 细节集中在此——便于单测与后续沙盒/宿主切换。
 */

/** 设备信息：机型 / 系统 / API / 可用存储。 */
class AndroidDeviceInfoProvider(private val context: Context) : DeviceInfoProvider {

    override fun summary(): String =
        "${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"

    override fun freeStorageBytes(): Long = runCatching {
        val target: File = context.filesDir ?: Environment.getDataDirectory()
        StatFs(target.absolutePath).availableBytes
    }.getOrDefault(-1L)
}

/** 剪贴板。 */
class AndroidClipboardPort(private val context: Context) : ClipboardPort {

    override fun read(): String? = runCatching {
        val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        manager.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString()
    }.getOrNull()

    override fun write(text: String): Boolean = runCatching {
        val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        manager.setPrimaryClip(ClipData.newPlainText("LuzzyRP", text))
        true
    }.getOrDefault(false)
}

/** 系统时钟 + 设备时区。 */
class SystemClockProvider : ClockProvider {
    override fun nowMillis(): Long = System.currentTimeMillis()
    override fun zoneId(): String = runCatching { ZoneId.systemDefault().id }.getOrDefault("UTC")
}
