package com.luzzymeow.luzzyrp.assistant.domain.tool

import java.net.InetAddress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [SsrfGuard] 单测（PLAN §13.2 / 硬性要求 11：web_fetch 必须拒绝私网/回环）。
 *
 * 覆盖：各类内网/保留地址、IPv4 映射 IPv6、域名解析到内网（DNS rebinding 第一跳）、
 * 协议白名单、正常公网地址放行。
 */
class SsrfGuardTest {

    private fun ip(s: String) = InetAddress.getByName(s)
    private fun resolverOf(vararg addrs: String) = SsrfGuard.Resolver { _ -> addrs.map { ip(it) } }

    @Test
    fun `回环与私网地址被拦截`() {
        for (a in listOf("127.0.0.1", "127.1.2.3", "10.0.0.5", "172.16.3.4", "172.31.255.254", "192.168.1.1")) {
            assertTrue("应拦截 $a", SsrfGuard.isBlockedAddress(ip(a)))
        }
    }

    @Test
    fun `链路本地与组播与保留被拦截`() {
        for (a in listOf("169.254.1.1", "224.0.0.1", "239.1.1.1", "0.0.0.0", "100.64.0.1", "198.18.0.1", "240.0.0.1")) {
            assertTrue("应拦截 $a", SsrfGuard.isBlockedAddress(ip(a)))
        }
    }

    @Test
    fun `IPv6 回环与唯一本地被拦截`() {
        assertTrue(SsrfGuard.isBlockedAddress(ip("::1")))
        assertTrue(SsrfGuard.isBlockedAddress(ip("fc00::1")))
        assertTrue(SsrfGuard.isBlockedAddress(ip("fd12:3456::1")))
        assertTrue(SsrfGuard.isBlockedAddress(ip("fe80::1")))
    }

    @Test
    fun `IPv4 映射 IPv6 不绕过`() {
        // ::ffff:127.0.0.1 必须按 127.0.0.1 处理
        assertTrue(SsrfGuard.isBlockedAddress(ip("::ffff:127.0.0.1")))
        assertTrue(SsrfGuard.isBlockedAddress(ip("::ffff:192.168.1.1")))
    }

    @Test
    fun `公网地址放行`() {
        for (a in listOf("8.8.8.8", "1.1.1.1", "93.184.216.34", "2606:4700:4700::1111")) {
            assertFalse("不应拦截 $a", SsrfGuard.isBlockedAddress(ip(a)))
        }
    }

    @Test
    fun `域名解析到内网被拦截（DNS rebinding 第一跳）`() {
        val reason = SsrfGuard.reasonOf("https://evil.example.com/steal", resolverOf("127.0.0.1"))
        assertTrue(reason != null && reason.contains("内网"))
    }

    @Test
    fun `多地址中任一为内网即拒绝`() {
        assertTrue(SsrfGuard.isBlocked("https://mixed.example.com", resolverOf("8.8.8.8", "10.0.0.1")))
    }

    @Test
    fun `解析失败被拒绝`() {
        val failing = SsrfGuard.Resolver { _ -> throw java.net.UnknownHostException("nope") }
        assertTrue(SsrfGuard.isBlocked("https://nx.example.com", failing))
    }

    @Test
    fun `非 http 协议被拒绝`() {
        assertFalse(SsrfGuard.schemeAllowed("file:///etc/passwd"))
        assertFalse(SsrfGuard.schemeAllowed("ftp://example.com"))
        assertFalse(SsrfGuard.schemeAllowed("data:text/html,hi"))
        assertTrue(SsrfGuard.schemeAllowed("https://example.com"))
        assertTrue(SsrfGuard.schemeAllowed("http://example.com"))
    }

    @Test
    fun `主机名提取正确`() {
        assertEquals("example.com", SsrfGuard.hostOf("https://example.com/a/b?c=1"))
        assertEquals("127.0.0.1", SsrfGuard.hostOf("http://127.0.0.1:8080/x"))
        assertNull(SsrfGuard.hostOf("not a url"))
        assertNull(SsrfGuard.hostOf("file:///etc/passwd"))
    }

    @Test
    fun `正常公网 URL 放行`() {
        assertNull(SsrfGuard.reasonOf("https://api.openai.com/v1/models", resolverOf("104.18.6.7")))
    }
}
