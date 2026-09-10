package com.luzzymeow.luzzyrp.assistant.domain.tool

import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [ApprovalGate] 单测（硬性要求 11：写类工具逐调用审批，T2/T3 默认关闭）。
 */
class ApprovalGateTest {

    private class FakeTool(
        override val name: String,
        override val tier: ToolTier,
    ) : Tool {
        override val description: String = "fake"
        override val parameters: JsonObject = Schema.empty()
        override suspend fun execute(args: JsonObject, ctx: ToolContext): ToolResult = ToolResult.Ok("ok")
    }

    private val readTool = FakeTool("get_time", ToolTier.T0_READ)
    private val writeAppTool = FakeTool("memory_write", ToolTier.T1_WRITE_APP)
    private val deviceTool = FakeTool("terminal_run", ToolTier.T2_WRITE_DEVICE)

    @Test
    fun `T0 只读默认开启且不需审批`() {
        val gate = ApprovalGate()
        assertTrue(gate.isEnabled(readTool))
        assertFalse(gate.needsApproval(readTool))
    }

    @Test
    fun `T1 默认开启但需审批`() {
        val gate = ApprovalGate()
        assertTrue(gate.isEnabled(writeAppTool))
        assertTrue(gate.needsApproval(writeAppTool))
    }

    @Test
    fun `T2 默认关闭`() {
        val gate = ApprovalGate()
        assertFalse(gate.isEnabled(deviceTool))
    }

    @Test
    fun `本会话始终允许后不再弹审批`() {
        val gate = ApprovalGate()
        assertTrue(gate.needsApproval(writeAppTool))
        gate.allowForSession(writeAppTool.name)
        assertFalse(gate.needsApproval(writeAppTool))
    }

    @Test
    fun `允许一次只消费一次`() {
        val gate = ApprovalGate()
        gate.allowOnce(writeAppTool.name)
        assertTrue(gate.consumeOnce(writeAppTool.name))
        assertFalse(gate.consumeOnce(writeAppTool.name))
    }

    @Test
    fun `会话重置清空放行`() {
        val gate = ApprovalGate()
        gate.allowForSession(writeAppTool.name)
        gate.allowOnce(writeAppTool.name)
        gate.resetSession()
        assertTrue(gate.needsApproval(writeAppTool))
        assertFalse(gate.consumeOnce(writeAppTool.name))
    }

    @Test
    fun `白名单策略下显式开启的工具免审批`() {
        val gate = ApprovalGate(
            globalSwitch = { name -> if (name == deviceTool.name) true else null },
            policy = ApprovalGate.POLICY_WHITELIST,
        )
        assertTrue(gate.isEnabled(deviceTool))
        assertFalse(gate.needsApproval(deviceTool))
    }

    @Test
    fun `逐调用策略下显式开启仍要审批`() {
        val gate = ApprovalGate(globalSwitch = { true }, policy = ApprovalGate.POLICY_PER_CALL)
        assertTrue(gate.needsApproval(deviceTool))
    }

    @Test
    fun `显式开关覆盖 tier 默认值`() {
        // 2026-09-11 静态审查：设置页「工具开关」把用户显式开关写进 DataStore，运行时以 @Volatile
        // 快照喂给 globalSwitch。这条测试锁定该契约——显式值优先于 tier 默认，**两个方向都要生效**。
        val t2On = ApprovalGate(globalSwitch = { name -> if (name == deviceTool.name) true else null })
        assertTrue("显式开启要能覆盖 T2 默认关闭", t2On.isEnabled(deviceTool))

        val t0Off = ApprovalGate(globalSwitch = { name -> if (name == readTool.name) false else null })
        assertFalse("显式关闭要能覆盖 T0 默认开启", t0Off.isEnabled(readTool))

        val unset = ApprovalGate(globalSwitch = { null })
        assertTrue(unset.isEnabled(readTool))
        assertFalse(unset.isEnabled(deviceTool))
    }

    @Test
    fun `HARDLINE 不受本会话放行影响`() {
        // 审批门只管「是否弹卡」；HARDLINE 在工具实现内无条件拦截（见 HardlineGuardTest）
        val gate = ApprovalGate()
        gate.allowForSession("terminal_run")
        assertFalse(gate.needsApproval(deviceTool))
        assertTrue(HardlineGuard.isBlocked("rm -rf /"))
    }
}
