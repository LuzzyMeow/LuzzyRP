package com.luzzymeow.luzzyrp.assistant.data.workspace

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.nio.file.Files

private val ASSISTANT_ID_PATTERN = Regex("[A-Za-z0-9][A-Za-z0-9._-]*")
private val CREATED_AT_PATTERN = Regex("\"createdAt\"\\s*:\\s*(\\d+)")

/**
 * 每助手独立工作区（PLAN v1.5.0 §4.2 文件系统布局 + §10.1 工作区）。
 *
 * 布局：filesDir/assistant/workspaces/&lt;assistantId&gt;/{files,attachments,exports,.meta/workspace.json}
 *
 * **路径安全（硬性要求）**：
 * 1. assistantId 必须匹配 [A-Za-z0-9][A-Za-z0-9._-]*（拒绝 ..、含分隔符的 id）；
 * 2. 相对路径先做词法归一（. 与 .. 就地解析，越出根即抛 WorkspaceSecurityException）；
 *    **拒绝**绝对路径（/ 开头）、Windows 盘符（C:）、NUL 字符；
 * 3. 归一后再 canonicalPath 解析，必须等于工作区根或 startsWith(根 + File.separator)，
 *    否则抛 WorkspaceSecurityException（防符号链接 / 重解析点把路径指到根外）；
 * 4. 路径各段若为符号链接（Files.isSymbolicLink）一律拒绝——即便指向根内，避免 TOCTOU 歧义；
 * 5. **不静默返回 null**：越界即抛异常（见 WorkspaceException 家族）。
 *
 * **配额**：单工作区默认 2GB、单文件默认 64MB（构造参数可配）。写入前统计用量
 * （O(文件数) 遍历，符号链接跳过），超限抛 WorkspaceQuotaExceededException / WorkspaceFileTooLargeException。
 *
 * **IO 与日志**：全部操作 suspend，内部走 Dispatchers.IO；本层**不打印任何日志**，
 * 异常消息只含路径与字节数，不含文件内容、不含密钥。
 *
 * **审计**：路径越界属安全事件，审计落 tool_audit 由工具层完成（本层不依赖 Room）。
 */
class WorkspaceManager(
    private val workspacesRoot: File,
    private val quotaBytesPerWorkspace: Long = DEFAULT_QUOTA_BYTES,
    private val maxFileBytes: Long = DEFAULT_MAX_FILE_BYTES,
) {

    /** App 侧构造：filesDir/assistant/workspaces。 */
    constructor(context: Context) : this(File(context.applicationContext.filesDir, WORKSPACES_DIR_RELATIVE))

    init {
        require(quotaBytesPerWorkspace > 0L) { "quotaBytesPerWorkspace 必须为正数" }
        require(maxFileBytes > 0L) { "maxFileBytes 必须为正数" }
    }

    // ---------------- 目录 ----------------

    /** 创建（或确保）工作区目录树；返回工作区根（canonical）。 */
    suspend fun ensureWorkspace(assistantId: String): File = io {
        val root = canonicalWorkspaceRoot(assistantId)
        mkdirsOrThrow(root, "工作区根目录")
        for (dir in SUB_DIRS) mkdirsOrThrow(File(root, dir), dir)
        val metaFile = File(File(root, DIR_META), META_FILE_NAME)
        if (!metaFile.isFile) writeMeta(root, assistantId, usageBytes(root))
        root
    }

    /** 工作区根（canonical，不创建）。 */
    suspend fun workspaceRoot(assistantId: String): File = io { canonicalWorkspaceRoot(assistantId) }

    /** Agent 可读写区 files/（沙盒模式映射为 /workspace）。 */
    suspend fun filesDir(assistantId: String): File = io {
        ensureWorkspace(assistantId)
        resolveInside(canonicalWorkspaceRoot(assistantId), DIR_FILES)
    }

    /** 导入附件区 attachments/（SAF 拷入）。 */
    suspend fun attachmentsDir(assistantId: String): File = io {
        ensureWorkspace(assistantId)
        resolveInside(canonicalWorkspaceRoot(assistantId), DIR_ATTACHMENTS)
    }

    /** 导出中转区 exports/。 */
    suspend fun exportsDir(assistantId: String): File = io {
        ensureWorkspace(assistantId)
        resolveInside(canonicalWorkspaceRoot(assistantId), DIR_EXPORTS)
    }

    /** 工作区是否存在（目录已建）。 */
    suspend fun existsWorkspace(assistantId: String): Boolean = io {
        canonicalWorkspaceRoot(assistantId).isDirectory
    }

    /**
     * 解析工作区内的相对路径为 canonical File（**不要求存在**）。
     * 越界 / 非法路径抛 WorkspaceSecurityException。
     */
    suspend fun resolve(assistantId: String, relativePath: String): File = io {
        resolveInside(canonicalWorkspaceRoot(assistantId), relativePath)
    }

    // ---------------- 读 ----------------

    /** 列目录（默认列 files/；relativePath 传 "" 列工作区根）。 */
    suspend fun list(assistantId: String, relativePath: String = DIR_FILES): List<WorkspaceEntry> = io {
        val root = canonicalWorkspaceRoot(assistantId)
        val target = resolveInside(root, relativePath)
        if (!target.exists()) throw WorkspaceNotFoundException("路径不存在: " + display(relativePath))
        if (!target.isDirectory) throw WorkspaceOperationException("不是目录: " + display(relativePath))
        (target.listFiles() ?: emptyArray())
            .filterNot { child -> Files.isSymbolicLink(child.toPath()) }
            .sortedWith(compareBy({ child -> !child.isDirectory }, { child -> child.name.lowercase() }))
            .map { child -> toEntry(root, child) }
    }

    /** 读字节；超过 maxBytes（默认 maxFileBytes）拒绝，避免大文件灌爆上下文。 */
    suspend fun readBytes(
        assistantId: String,
        relativePath: String,
        maxBytes: Long = maxFileBytes,
    ): ByteArray = io {
        val target = requireExistingFile(canonicalWorkspaceRoot(assistantId), relativePath)
        val length = target.length()
        if (maxBytes > 0L && length > maxBytes) {
            throw WorkspaceFileTooLargeException(length, maxBytes, "读取超过上限: " + display(relativePath))
        }
        target.readBytes()
    }

    suspend fun readText(
        assistantId: String,
        relativePath: String,
        maxBytes: Long = maxFileBytes,
    ): String = String(readBytes(assistantId, relativePath, maxBytes), Charsets.UTF_8)

    suspend fun exists(assistantId: String, relativePath: String): Boolean = io {
        resolveInside(canonicalWorkspaceRoot(assistantId), relativePath).exists()
    }

    suspend fun size(assistantId: String, relativePath: String): Long = io {
        requireExistingFile(canonicalWorkspaceRoot(assistantId), relativePath).length()
    }

    suspend fun stat(assistantId: String, relativePath: String): WorkspaceEntry = io {
        val root = canonicalWorkspaceRoot(assistantId)
        val target = resolveInside(root, relativePath)
        if (!target.exists()) throw WorkspaceNotFoundException("路径不存在: " + display(relativePath))
        toEntry(root, target)
    }

    // ---------------- 写 ----------------

    /**
     * 写文件（自动建父目录）。
     * @param append true = 追加（配额与单文件上限按追加后大小校验）
     */
    suspend fun writeBytes(
        assistantId: String,
        relativePath: String,
        bytes: ByteArray,
        append: Boolean = false,
    ): File = io {
        val root = canonicalWorkspaceRoot(assistantId)
        val normalized = normalizeRelative(relativePath)
        if (normalized.isEmpty()) throw WorkspaceSecurityException("拒绝写入工作区根目录")
        val target = resolveInside(root, relativePath)
        val existing = if (target.isFile) target.length() else 0L
        val newSize = if (append) existing + bytes.size.toLong() else bytes.size.toLong()
        val projected = enforceWriteLimits(root, newSize, existing)
        val parent = target.parentFile
            ?: throw WorkspaceOperationException("无法定位父目录: " + display(relativePath))
        mkdirsOrThrow(parent, parent.name)
        // 建目录后二次校验（防 mkdirs 期间被替换为符号链接）
        assertNoSymlinkComponents(root, normalized)
        if (append) target.appendBytes(bytes) else target.writeBytes(bytes)
        writeMeta(root, assistantId, projected)
        target
    }

    suspend fun writeText(
        assistantId: String,
        relativePath: String,
        text: String,
        append: Boolean = false,
    ): File = writeBytes(assistantId, relativePath, text.toByteArray(Charsets.UTF_8), append)

    /** 建目录（含中间目录）。 */
    suspend fun mkdir(assistantId: String, relativePath: String): File = io {
        val root = canonicalWorkspaceRoot(assistantId)
        val normalized = normalizeRelative(relativePath)
        if (normalized.isEmpty()) throw WorkspaceSecurityException("拒绝创建工作区根目录")
        val target = resolveInside(root, relativePath)
        mkdirsOrThrow(target, display(relativePath))
        target
    }

    /**
     * 删除文件 / 目录。目录非空时必须 recursive = true。
     * 拒绝删除工作区根（用 deleteWorkspace 显式表达「删整个工作区」的语义）。
     */
    suspend fun delete(
        assistantId: String,
        relativePath: String,
        recursive: Boolean = false,
    ): Boolean = io {
        val root = canonicalWorkspaceRoot(assistantId)
        val normalized = normalizeRelative(relativePath)
        if (normalized.isEmpty()) throw WorkspaceSecurityException("拒绝删除工作区根目录")
        val target = resolveInside(root, relativePath)
        if (!target.exists()) return@io false
        if (target.isDirectory) {
            val children = target.list()?.size ?: 0
            if (children > 0 && !recursive) {
                throw WorkspaceOperationException("目录非空（需 recursive = true）: " + display(relativePath))
            }
            if (!target.deleteRecursively()) {
                throw WorkspaceOperationException("删除失败: " + display(relativePath))
            }
        } else if (!target.delete()) {
            throw WorkspaceOperationException("删除失败: " + display(relativePath))
        }
        writeMeta(root, assistantId, usageBytes(root))
        true
    }

    /** 移动 / 重命名（同一工作区内）；overwrite = false 时目标已存在则拒绝。 */
    suspend fun move(
        assistantId: String,
        fromRelativePath: String,
        toRelativePath: String,
        overwrite: Boolean = false,
    ): File = io {
        val root = canonicalWorkspaceRoot(assistantId)
        val from = resolveInside(root, fromRelativePath)
        val to = resolveInside(root, toRelativePath)
        if (from == root || to == root) throw WorkspaceSecurityException("拒绝移动工作区根目录")
        if (normalizeRelative(fromRelativePath).isEmpty() || normalizeRelative(toRelativePath).isEmpty()) {
            throw WorkspaceSecurityException("拒绝移动工作区根目录")
        }
        if (!from.exists()) throw WorkspaceNotFoundException("源路径不存在: " + display(fromRelativePath))
        if (to.path == from.path) throw WorkspaceOperationException("源与目标相同: " + display(toRelativePath))
        if (to.path.startsWith(from.path + File.separator)) {
            throw WorkspaceOperationException("目标不能位于源目录内部: " + display(toRelativePath))
        }
        if (to.exists()) {
            if (!overwrite) throw WorkspaceOperationException("目标已存在: " + display(toRelativePath))
            val usage = usageBytes(root)
            val replaced = sizeOf(to)
            if (usage - replaced > quotaBytesPerWorkspace) {
                throw WorkspaceQuotaExceededException(usage, quotaBytesPerWorkspace, usage - replaced)
            }
            if (!to.deleteRecursively()) throw WorkspaceOperationException("覆盖删除失败: " + display(toRelativePath))
        }
        val parent = to.parentFile
            ?: throw WorkspaceOperationException("无法定位父目录: " + display(toRelativePath))
        mkdirsOrThrow(parent, parent.name)
        val moved = from.renameTo(to)
        if (!moved) {
            val copied = try {
                from.copyRecursively(to, overwrite = true)
            } catch (e: IOException) {
                throw WorkspaceOperationException("移动失败: " + display(fromRelativePath), e)
            }
            if (!copied || !from.deleteRecursively()) {
                throw WorkspaceOperationException("移动失败: " + display(fromRelativePath))
            }
        }
        writeMeta(root, assistantId, usageBytes(root))
        to
    }

    /** 工作区总用量（字节；符号链接不跟随）。 */
    suspend fun quotaUsageBytes(assistantId: String): Long = io { usageBytes(canonicalWorkspaceRoot(assistantId)) }

    /** 配额上限（字节）。 */
    fun quotaBytes(): Long = quotaBytesPerWorkspace

    /** 单文件上限（字节）。 */
    fun maxFileSizeBytes(): Long = maxFileBytes

    /**
     * 删除整个工作区（PLAN §6.1：删除助手时默认保留工作区，用户勾选「一并删除」才调用）。
     * @return true = 确实删除了；false = 本来就不存在
     */
    suspend fun deleteWorkspace(assistantId: String): Boolean = io {
        val root = canonicalWorkspaceRoot(assistantId)
        if (!root.exists()) return@io false
        if (!root.deleteRecursively()) throw WorkspaceOperationException("删除工作区失败: " + assistantId)
        true
    }

    // ---------------- 内部 ----------------

    private suspend fun <T> io(block: suspend () -> T): T = withContext(Dispatchers.IO) { block() }

    private fun requireAssistantId(assistantId: String) {
        if (assistantId.length > MAX_ASSISTANT_ID_LENGTH || !ASSISTANT_ID_PATTERN.matches(assistantId)) {
            throw WorkspaceSecurityException("非法助手 id（只允许 [A-Za-z0-9._-] 且不以点开头）: " + assistantId)
        }
    }

    private fun canonicalWorkspaceRoot(assistantId: String): File {
        requireAssistantId(assistantId)
        val base = try {
            workspacesRoot.canonicalFile
        } catch (e: IOException) {
            throw WorkspaceOperationException("无法解析工作区根目录", e)
        }
        val root = try {
            File(base, assistantId).canonicalFile
        } catch (e: IOException) {
            throw WorkspaceSecurityException("无法解析助手工作区路径: " + assistantId, e)
        }
        if (root != base && !root.path.startsWith(base.path + File.separator)) {
            throw WorkspaceSecurityException("助手工作区越出根目录: " + assistantId)
        }
        if (Files.isSymbolicLink(root.toPath())) {
            throw WorkspaceSecurityException("助手工作区根不得是符号链接: " + assistantId)
        }
        return root
    }

    private fun resolveInside(root: File, relativePath: String): File {
        val normalized = normalizeRelative(relativePath)
        assertNoSymlinkComponents(root, normalized)
        val candidate = if (normalized.isEmpty()) root else File(root, normalized)
        val canonical = try {
            candidate.canonicalFile
        } catch (e: IOException) {
            throw WorkspaceSecurityException("无法解析路径: " + display(relativePath), e)
        }
        if (canonical != root && !canonical.path.startsWith(root.path + File.separator)) {
            throw WorkspaceSecurityException("路径越出工作区: " + display(relativePath))
        }
        return canonical
    }

    /** 词法归一：统一分隔符、解析 . 与 ..，越出根即抛异常。返回 / 分隔、无首尾斜杠的相对路径。 */
    private fun normalizeRelative(relativePath: String): String {
        if (relativePath.indexOf('\u0000') >= 0) {
            throw WorkspaceSecurityException("路径含非法字符")
        }
        val unified = relativePath.replace('\\', '/')
        if (unified.isEmpty()) return ""
        if (unified.startsWith("/")) throw WorkspaceSecurityException("拒绝绝对路径: " + relativePath)
        if (unified.length >= 2 && unified[1] == ':') {
            throw WorkspaceSecurityException("拒绝盘符路径: " + relativePath)
        }
        if (unified.startsWith("~")) throw WorkspaceSecurityException("拒绝 ~ 路径: " + relativePath)
        val stack = ArrayList<String>(8)
        for (part in unified.split('/')) {
            when (part) {
                "", "." -> Unit
                ".." -> {
                    if (stack.isEmpty()) throw WorkspaceSecurityException("路径越界（../ 逃逸）: " + relativePath)
                    stack.removeAt(stack.lastIndex)
                }
                else -> stack.add(part)
            }
        }
        return stack.joinToString("/")
    }

    /** 逐段检查符号链接（含最终段）。 */
    private fun assertNoSymlinkComponents(root: File, normalized: String) {
        if (normalized.isEmpty()) return
        var current = root
        for (part in normalized.split('/')) {
            current = File(current, part)
            if (Files.isSymbolicLink(current.toPath())) {
                throw WorkspaceSecurityException("拒绝符号链接穿越: " + normalized)
            }
        }
    }

    private fun requireExistingFile(root: File, relativePath: String): File {
        val target = resolveInside(root, relativePath)
        if (!target.isFile) throw WorkspaceNotFoundException("文件不存在: " + display(relativePath))
        return target
    }

    private fun mkdirsOrThrow(dir: File, label: String) {
        if (dir.isDirectory) return
        if (!dir.mkdirs() && !dir.isDirectory) {
            throw WorkspaceOperationException("无法创建目录: " + label)
        }
    }

    private fun enforceWriteLimits(root: File, newSize: Long, existingSize: Long): Long {
        if (newSize > maxFileBytes) {
            throw WorkspaceFileTooLargeException(newSize, maxFileBytes, "单文件超过上限（maxFileBytes）")
        }
        val usage = usageBytes(root)
        val projected = usage - existingSize + newSize
        if (projected > quotaBytesPerWorkspace) {
            throw WorkspaceQuotaExceededException(usage, quotaBytesPerWorkspace, projected)
        }
        return projected
    }

    private fun usageBytes(dir: File): Long {
        if (dir.isFile) return dir.length()
        if (!dir.isDirectory) return 0L
        var total = 0L
        val stack = ArrayDeque<File>()
        stack.addLast(dir)
        while (stack.isNotEmpty()) {
            val current = stack.removeLast()
            val children = current.listFiles() ?: continue
            for (child in children) {
                if (Files.isSymbolicLink(child.toPath())) continue
                if (child.isDirectory) stack.addLast(child) else total += child.length()
            }
        }
        return total
    }

    private fun sizeOf(file: File): Long = if (file.isFile) file.length() else usageBytes(file)

    private fun toEntry(root: File, file: File): WorkspaceEntry = WorkspaceEntry(
        name = file.name,
        relativePath = file.relativeTo(root).path.replace(File.separatorChar, '/'),
        isDirectory = file.isDirectory,
        sizeBytes = if (file.isFile) file.length() else 0L,
        lastModified = file.lastModified(),
    )

    private fun display(relativePath: String): String = if (relativePath.isEmpty()) "<root>" else relativePath

    /** 写 .meta/workspace.json；元数据非关键路径，写失败不影响数据操作。 */
    private fun writeMeta(root: File, assistantId: String, usageBytes: Long) {
        val metaDir = File(root, DIR_META)
        if (!metaDir.isDirectory && !metaDir.mkdirs()) return
        val metaFile = File(metaDir, META_FILE_NAME)
        val createdAt = readCreatedAt(metaFile) ?: System.currentTimeMillis()
        val meta = WorkspaceMeta(
            assistantId = assistantId,
            schemaVersion = META_SCHEMA_VERSION,
            createdAt = createdAt,
            updatedAt = System.currentTimeMillis(),
            quotaBytes = quotaBytesPerWorkspace,
            maxFileBytes = maxFileBytes,
            usageBytes = usageBytes,
        )
        try {
            metaFile.writeText(meta.toJson(), Charsets.UTF_8)
        } catch (e: IOException) {
            // 元数据写失败不阻断数据操作；不打印日志（本层禁止输出）
        }
    }

    private fun readCreatedAt(metaFile: File): Long? {
        if (!metaFile.isFile) return null
        return try {
            CREATED_AT_PATTERN.find(metaFile.readText(Charsets.UTF_8))
                ?.groupValues?.getOrNull(1)?.toLongOrNull()
        } catch (e: IOException) {
            null
        }
    }

    companion object {
        /** PLAN §4.2：filesDir/assistant/workspaces */
        const val WORKSPACES_DIR_RELATIVE: String = "assistant/workspaces"

        const val DIR_FILES: String = "files"
        const val DIR_ATTACHMENTS: String = "attachments"
        const val DIR_EXPORTS: String = "exports"
        const val DIR_META: String = ".meta"
        const val META_FILE_NAME: String = "workspace.json"
        const val META_SCHEMA_VERSION: Int = 1

        /** 单工作区默认配额 2GB（PLAN §4.2 / §10.1）。 */
        const val DEFAULT_QUOTA_BYTES: Long = 2L * 1024L * 1024L * 1024L

        /** 单文件默认上限 64MB（PLAN §10.1）。 */
        const val DEFAULT_MAX_FILE_BYTES: Long = 64L * 1024L * 1024L

        private const val MAX_ASSISTANT_ID_LENGTH: Int = 128

        private val SUB_DIRS: List<String> = listOf(DIR_FILES, DIR_ATTACHMENTS, DIR_EXPORTS, DIR_META)
    }
}
