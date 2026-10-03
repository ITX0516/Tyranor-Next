package com.tyranor.next.core.engine.external

import android.content.Context
import android.os.Build
import android.util.DisplayMetrics
import android.view.WindowManager
import com.tyranor.next.core.settings.EngineSettingsStore

/**
 * Winlator 外置启动参数自动调优：对用户未显式设置（空串）的图形驱动与分辨率，
 * 按设备 GPU 与屏幕尺寸自动选择最优档位，减少 GalGame 用户的手动配置负担。
 *
 * 设计原则：
 * - 仅填充空值；用户已设置的值一律尊重，不覆盖。
 * - 图形驱动：骁龙 GPU → turnip（Adreno 原生 Vulkan）；其余 → vortek（兼容面更广）。
 *   OpenGL 侧统一 zink（对 GalGame 的 2D 绘制足够且稳定性好）。
 * - 分辨率：从 [EngineSettingsStore.WINLATOR_SCREEN_SIZES] 中选宽高比最接近设备屏幕、
 *   且不超过设备物理分辨率的档位；找不到时回退 1280x720。
 */
object WinlatorAutoTuning {

    /** 为 [settings] 中的空值字段填充自动检测结果，返回新的 [WinlatorContract.LaunchOptions]。 */
    fun tune(
        context: Context,
        settings: EngineSettingsStore.Winlator,
    ): WinlatorContract.LaunchOptions {
        val graphicsDriver = settings.graphicsDriver.ifBlank { detectGraphicsDriver(context) }
        val screenSize = settings.screenSize.ifBlank { detectScreenSize(context) }
        return WinlatorContract.LaunchOptions(
            containerId = settings.containerId,
            containerName = settings.containerName,
            graphicsDriver = graphicsDriver,
            dxwrapper = settings.dxwrapper,
            screenSize = screenSize,
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

    /**
     * 根据设备屏幕物理分辨率，从固定档位中选择最合适的一项。
     * 策略：宽高比偏差最小，且宽×高不超过设备物理像素数的档位；都不满足时回退 1280x720。
     */
    fun detectScreenSize(context: Context): String {
        val metrics = realScreenMetrics(context)
        val physW = metrics.widthPixels
        val physH = metrics.heightPixels
        val deviceRatio = physW.toDouble() / physH.toDouble()
        var best = "1280x720"
        var bestScore = Double.MAX_VALUE
        for (size in EngineSettingsStore.WINLATOR_SCREEN_SIZES) {
            val (w, h) = parseSize(size) ?: continue
            if (w > physW || h > physH) continue
            val ratio = w.toDouble() / h.toDouble()
            // 评分 = 宽高比偏差的绝对值（越小越接近设备比例）
            val score = kotlin.math.abs(ratio - deviceRatio)
            if (score < bestScore) {
                bestScore = score
                best = size
            }
        }
        return best
    }

    private fun realScreenMetrics(context: Context): DisplayMetrics {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        wm.defaultDisplay.getRealMetrics(metrics)
        return metrics
    }

    private fun parseSize(size: String): Pair<Int, Int>? {
        val parts = size.split("x")
        if (parts.size != 2) return null
        val w = parts[0].toIntOrNull() ?: return null
        val h = parts[1].toIntOrNull() ?: return null
        return w to h
    }

    private fun getProperty(key: String): String? = runCatching {
        val c = Class.forName("android.os.SystemProperties")
        val m = c.getMethod("get", String::class.java)
        (m.invoke(null, key) as? String)?.takeIf { it.isNotBlank() }
    }.getOrNull()
}
