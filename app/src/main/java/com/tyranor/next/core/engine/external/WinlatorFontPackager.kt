package com.tyranor.next.core.engine.external

import android.content.Context
import android.os.Environment
import java.io.File
import java.io.FileOutputStream

/**
 * Winlator 中文字体预置辅助。
 *
 * Winlator 外置启动协议不支持直接注入字体或注册表，因此采用「导出配置包到共享存储 +
 * 引导用户在 Winlator 内导入」的方式，把中文显示所需的两步（字体 + font substitution）
 * 压缩为一次操作。
 *
 * 输出内容（写入 `Download/TyranorNext/winlator_cn/`）：
 * 1. `fontsubstitute.reg` —— Wine 字体替换注册表，把 GalGame 常用的日文字体名
 *    （MS Gothic / MS Mincho / Meiryo 等）映射到中文字体，避免方块/乱码。
 * 2. `README.txt` —— 操作步骤说明。
 *
 * 字体本身：优先从 Android 系统字体目录复制 Noto Sans CJK；若系统无此字体，
 * 提示用户自行准备字体文件放入同一目录。
 */
object WinlatorFontPackager {

    private const val PACK_DIR = "TyranorNext/winlator_cn"

    /** GalGame 常见日文字体名 → 中文字体 的替换映射。 */
    private val FONT_SUBSTITUTES = mapOf(
        "MS Gothic" to "Noto Sans CJK SC",
        "MS Mincho" to "Noto Serif CJK SC",
        "MS PGothic" to "Noto Sans CJK SC",
        "MS PMincho" to "Noto Serif CJK SC",
        "Meiryo" to "Noto Sans CJK SC",
        "Meiryo UI" to "Noto Sans CJK SC",
        "Yu Gothic" to "Noto Sans CJK SC",
        "Yu Mincho" to "Noto Serif CJK SC",
        "SimSun" to "Noto Serif CJK SC",
        "NSimSun" to "Noto Serif CJK SC",
        "SimHei" to "Noto Sans CJK SC",
        "FangSong" to "Noto Serif CJK SC",
        "KaiTi" to "Noto Serif CJK SC",
        "Arial" to "Noto Sans CJK SC",
        "Tahoma" to "Noto Sans CJK SC",
    )

    /**
     * 导出中文字体配置包到公共下载目录。
     * @return 导出目录的绝对路径；失败返回 null。
     */
    fun exportToDownload(context: Context): String? {
        val dir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            PACK_DIR,
        )
        if (!dir.exists() && !dir.mkdirs()) return null
        return runCatching {
            writeRegFile(dir)
            copySystemFonts(dir)
            writeReadme(dir)
            dir.absolutePath
        }.getOrNull()
    }

    private fun writeRegFile(dir: File) {
        val sb = StringBuilder()
        sb.appendLine("REGEDIT4")
        sb.appendLine()
        sb.appendLine("[HKEY_LOCAL_MACHINE\\SOFTWARE\\Microsoft\\Windows NT\\CurrentVersion\\FontSubstitutes]")
        for ((from, to) in FONT_SUBSTITUTES) {
            sb.appendLine("\"$from\"=\"$to\"")
        }
        File(dir, "fontsubstitute.reg").writeText(sb.toString(), Charsets.UTF_8)
    }

    /**
     * 从 Android 系统字体目录复制 Noto Sans/Serif CJK 到导出目录。
     * 不同厂商的系统字体文件名可能不同，这里尝试常见命名。
     */
    private fun copySystemFonts(dir: File) {
        val systemFontDir = File("/system/fonts")
        val candidates = listOf(
            "NotoSansCJK-Regular.ttc",
            "NotoSansSC-Regular.otf",
            "NotoSansCJKsc-Regular.otf",
            "NotoSerifCJK-Regular.ttc",
            "NotoSerifSC-Regular.otf",
            "SourceHanSansSC-Regular.otf",
            "SourceHanSerifSC-Regular.otf",
        )
        for (name in candidates) {
            val src = File(systemFontDir, name)
            if (src.exists()) {
                src.copyTo(File(dir, name), overwrite = true)
            }
        }
    }

    private fun writeReadme(dir: File) {
        val text = """
            Tyranor Next — Winlator 中文显示优化包
            =====================================

            本目录包含让 Winlator 正确显示中文所需的文件。

            使用步骤：
            1. 打开 Winlator，进入目标容器。
            2. 把本目录下的所有 .ttc/.otf 字体文件复制到容器的
               C:\windows\fonts\ 目录。
               （若没有系统字体文件，请自行准备 SimSun.ttc 或
               Noto Sans CJK 字体放入此目录。）
            3. 在 Winlator 中运行 fontsubstitute.reg（双击或用
               wine regedit 导入），注册字体替换规则。
            4. 重启容器，启动游戏即可正常显示中文。

            原理：
            - GalGame 通常请求 MS Gothic / MS Mincho 等日文字体名；
            - fontsubstitute.reg 把这些名称映射到中文字体；
            - 配合 lcAll=zh_CN.utf8（Tyranor Next 已默认下发），
              即可正确渲染中文文本。
        """.trimIndent()
        File(dir, "README.txt").writeText(text, Charsets.UTF_8)
    }
}
