package com.luzzymeow.luzzyrp.ui.markdown

/**
 * 解析结果记忆化（LRU，仅 UI 层用）。
 *
 * **为什么需要**：LazyColumn 滚动时会把划出屏幕的气泡销毁、滚回来重建。若重建那一帧拿不到
 * 解析结果，气泡会先以「只有名牌」的矮形态出现、下一帧才撑开——用户看到的是「气泡突然弹出」
 * 的视觉割裂（2026-09-12 用户实测反馈）。同步解析能消除空帧，而记忆化让**同步解析不必重复付费**
 * （同一内容只在首次解析，之后滚动来回都是缓存命中）。
 *
 * 容量取 24：够覆盖「一屏 + 预取」的静态消息；流式期间内容每个增量都变（必然 miss），
 * LRU 会把旧前缀自然淘汰，不会无限增长。
 *
 * 线程：同步方法；`remember` 在组合线程调用，加锁只是为将来可能的跨线程调用兜底。
 */
internal object MarkdownMemo {
    private const val MaxEntries = 24

    private val cache = object : LinkedHashMap<String, List<MdBlock>>(MaxEntries, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<MdBlock>>): Boolean =
            size > MaxEntries
    }

    /** 命中即返回；未命中则解析并写入缓存。 */
    @Synchronized
    fun blocksOf(text: String): List<MdBlock> = cache[text] ?: MarkdownParser.parse(text).also {
        cache[text] = it
    }

    /** 仅供单测：当前缓存条目数。 */
    @Synchronized
    fun size(): Int = cache.size

    /** 仅供单测：清空。 */
    @Synchronized
    fun clear() = cache.clear()
}
