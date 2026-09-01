package com.luzzymeow.luzzyrp.web

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView

/**
 * 文件选择桥（角色卡 PNG/JSON 导入）+ JS console 转发。
 *
 * RP-Hub 的 `input[type=file]` 在 WebView 中默认不可用，必须通过
 * `onShowFileChooser` 用系统文件选择器（SAF）接管。
 * 上游代码无需任何修改——这是 WebView 层自动回调（AGENTS.md §5.4）。
 *
 * 同时转发 JS console 到 Logcat（tag: JSConsole）——RP-Hub 是纯 JS 应用，
 * 调试与扩展层验证全靠 console 输出。
 */
class FileChooserHandler {

    private var filePathCallback: ValueCallback<Array<Uri>>? = null

    fun webChromeClient(): WebChromeClient = object : WebChromeClient() {
        override fun onShowFileChooser(
            webView: WebView,
            filePathCallback: ValueCallback<Array<Uri>>,
            fileChooserParams: FileChooserParams
        ): Boolean {
            this@FileChooserHandler.filePathCallback?.onReceiveValue(null)
            this@FileChooserHandler.filePathCallback = filePathCallback

            val intent = fileChooserParams.createIntent()
            intent.addCategory(Intent.CATEGORY_OPENABLE)

            // 需要 Activity 上下文；壳工程 MainActivity 是唯一宿主
            val activity = webView.context as? Activity ?: return false
            try {
                activity.startActivityForResult(
                    Intent.createChooser(intent, "选择文件"),
                    REQUEST_CODE_FILE_CHOOSER
                )
            } catch (e: Exception) {
                this@FileChooserHandler.filePathCallback = null
                return false
            }
            return true
        }

        override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
            val msg = consoleMessage.message()
            val line = consoleMessage.lineNumber()
            val source = consoleMessage.sourceId()
            when (consoleMessage.messageLevel()) {
                ConsoleMessage.MessageLevel.ERROR -> Log.e("JSConsole", "[$source:$line] $msg")
                ConsoleMessage.MessageLevel.WARNING -> Log.w("JSConsole", "[$source:$line] $msg")
                else -> Log.i("JSConsole", "[$source:$line] $msg")
            }
            return true
        }
    }

    /** 由 MainActivity 在 onActivityResult 转发（见 [handleActivityResult]）。 */
    fun handleActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode != REQUEST_CODE_FILE_CHOOSER) return
        val callback = filePathCallback ?: return
        filePathCallback = null

        if (resultCode == Activity.RESULT_OK && data != null) {
            val clipData = data.clipData
            val uris = if (clipData != null && clipData.itemCount > 0) {
                Array(clipData.itemCount) { i -> clipData.getItemAt(i).uri }
            } else {
                arrayOf(data.data).filterNotNull().toTypedArray()
            }
            callback.onReceiveValue(uris.ifEmpty { null })
        } else {
            callback.onReceiveValue(null)
        }
    }

    companion object {
        private const val REQUEST_CODE_FILE_CHOOSER = 1001
    }
}
