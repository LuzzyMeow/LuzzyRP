package com.luzzymeow.luzzyrp.assistant.domain.tool

import java.net.InetAddress

/**
 * `web_fetch` 的 SSRF 防护（PLAN §13.2，硬性要求 11）。
 *
 * **必须在 DNS 解析层拒绝**私网 / 回环 / 链路本地 / 组播 / 保留地址：
 * 仅校验 URL 字符串不够——域名可以解析到内网 IP（DNS rebinding 的第一跳），
 * 因此解析出的**每一个**地址都要过白名单判定，任一命中即拒绝整次请求。
 *
 * 纯 Kotlin（[InetAddress] 属 JDK，单测可注入假解析器）。
 */
object SsrfGuard {

    /** 解析端口：便于单测注入。 */
    fun interface Resolver {
        fun resolve(host: String): List<InetAddress>
    }

    val systemResolver = Resolver { host -> InetAddress.getAllByName(host).toList() }

    /** 拒绝原因（null = 放行）。 */
    fun reasonOf(url: String, resolver: Resolver = systemResolver): String? {
        val host = hostOf(url) ?: return "URL 缺少主机名"
        // 字面 IP 直接判定；域名先解析
        val addresses = try {
            resolver.resolve(host)
        } catch (e: Exception) {
            return "无法解析主机名: $host"
        }
        if (addresses.isEmpty()) return "无法解析主机名: $host"
        for (addr in addresses) {
            if (isBlockedAddress(addr)) {
                return "拒绝访问内网/保留地址（$host → ${addr.hostAddress}）"
            }
        }
        return null
    }

    fun isBlocked(url: String, resolver: Resolver = systemResolver): Boolean = reasonOf(url, resolver) != null

    /** 单个地址是否属于禁止网段。 */
    fun isBlockedAddress(addr: InetAddress): Boolean {
        if (addr.isAnyLocalAddress) return true      // 0.0.0.0 / ::
        if (addr.isLoopbackAddress) return true      // 127/8, ::1
        if (addr.isLinkLocalAddress) return true     // 169.254/16, fe80::/10
        if (addr.isSiteLocalAddress) return true     // 10/8, 172.16/12, 192.168/16
        if (addr.isMulticastAddress) return true     // 224/4, ff00::/8
        val b = addr.address
        if (b.size == 4) {
            val a = b[0].toInt() and 0xFF
            val c = b[1].toInt() and 0xFF
            val d = b[2].toInt() and 0xFF
            // 100.64.0.0/10 运营商级 NAT
            if (a == 100 && c in 64..127) return true
            // 198.18.0.0/15 基准测试
            if (a == 198 && (c == 18 || c == 19)) return true
            // 192.0.0.0/24 协议保留 + 192.0.2.0/24 TEST-NET-1
            if (a == 192 && c == 0 && (d == 0 || d == 2)) return true
            // 240.0.0.0/4 保留
            if (a >= 240) return true
        } else if (b.size == 16) {
            // IPv4-mapped ::ffff:a.b.c.d
            val mapped = addr.hostAddress?.substringAfterLast(':')?.takeIf { it.contains('.') }
            if (mapped != null) {
                val parts = mapped.split('.').mapNotNull { it.toIntOrNull() }
                if (parts.size == 4) {
                    return isBlockedAddress(InetAddress.getByAddress(parts.map { it.toByte() }.toByteArray()))
                }
            }
            // fc00::/7 唯一本地地址
            if (b[0].toInt() and 0xFE == 0xFC) return true
        }
        return false
    }

    /** 取 URL 主机名（不含端口；非法 URL 返回 null）。 */
    fun hostOf(url: String): String? = try {
        val u = java.net.URI(url.trim())
        val scheme = u.scheme?.lowercase()
        if (scheme != "http" && scheme != "https") null else u.host?.takeIf { it.isNotBlank() }
    } catch (e: Exception) {
        null
    }

    /** 协议白名单（只允许 http/https；file:// 等一律拒绝）。 */
    fun schemeAllowed(url: String): Boolean = try {
        val scheme = java.net.URI(url.trim()).scheme?.lowercase()
        scheme == "http" || scheme == "https"
    } catch (e: Exception) {
        false
    }
}
