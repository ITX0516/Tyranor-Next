package com.tyranor.next.core.engine.external

import android.content.Context
import android.os.Build
import com.tyranor.next.core.settings.EngineSettingsStore

/**
 * Winlator 外置启动参数自动调优：仅对用户未显式设置（空串）的图形驱动，
 * 按设备 GPU 自动选择最优驱动，减少 GalGame 用户的手动配置负担。
 *
 * 设计原则：
 * - 仅填充空值；用户已设置的值一律尊重，不覆盖。
 * - 图形驱动：骁龙 GPU → turnip（Adreno 原生 Vulkan）；其余 → vortek（兼容面更广）。
 *   OpenGL 侧统一 zink（对 GalGame 的 2D 绘制足够且稳定性好）。
 * - 分辨率：不自动检测，留空跟随 Winlator 容器配置（避免与容器自身分辨率策略冲突）。
 */
object WinlatorAutoTuning {

    /** 为 [settings] 中的空值字段填充自动检测结果，返回新的 [WinlatorContract.LaunchOptions]。 */
    fun tune(
        context: Context,
        settings: EngineSettingsStore.Winlator,
    ): WinlatorContract.LaunchOptions {
        val graphicsDriver = settings.graphicsDriver.ifBlank { detectGraphicsDriver(context) }
        return WinlatorContract.LaunchOptions(
            containerId = settings.containerId,
            containerName = settings.containerName,
            graphicsDriver = graphicsDriver,
            dxwrapper = settings.dxwrapper,
            screenSize = settings.screenSize,
            lcAll = settings.lcAll,
            tz = settings.tz,
            box64Preset = settings.box64Preset,
            save = settings.save,
        )
    }

    /**
     * 检测 GPU 厂商并返回 Winlator 图形驱动组合（格式 `vulkan,opengl`）。
     * 骁龙（Adreno）→ `turnip,zink`；其余（Mali/PowerVR 等）→ `vortek,zink`。
     */
    fun detectGraphicsDriver(context: Context): String {
        val gl = runCatching {
            // 取 GPU 渲染器名称判断厂商；无需创建真实 GL 上下文，用反射/系统属性兜底。
            // Build.HARDWARE 对部分机型不可靠，优先读 ro.hardware.egl / ro.graphics.driver。
            getProperty("ro.hardware.egl")
                ?: getProperty("ro.graphics.driver")
                ?: Build.HARDWARE
                ?: ""
        }.getOrDefault("")
        val lower = gl.lowercase()
        val isAdreno = lower.contains("adreno") ||
            lower.contains("qcom") ||
            lower.contains("qualcomm") ||
            Build.HARDWARE?.lowercase()?.contains("qcom") == true
        return if (isAdreno) "turnip,zink" else "vortek,zink"
    }

    private fun getProperty(key: String): String? = runCatching {
        val c = Class.forName("android.os.SystemProperties")
        val m = c.getMethod("get", String::class.java)
        (m.invoke(null, key) as? String)?.takeIf { it.isNotBlank() }
    }.getOrNull()
}
