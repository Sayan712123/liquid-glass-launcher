package com.kyant.backdrop.catalog.launcher

import android.app.ActivityManager
import android.content.Context
import android.os.Build

/**
 * Runtime performance profile.
 *
 * Every glass surface in this launcher records a graphics layer, runs a
 * RenderEffect blur and (on API 33+) a runtime-shader lens. On a low-RAM device
 * such as the POCO M4 5G that is enough to drop frames while scrolling the app
 * grid, so instead of one fixed look we pick a profile once at startup and feed
 * it to every glass surface: cheaper effects, no shader highlight, shorter
 * animations. The visual language stays, the GPU cost does not.
 */
data class DeviceProfile(
    val isLowRam: Boolean,
    val totalRamMb: Int,
    val blurRadiusDp: Float,
    val useLens: Boolean,
    val useShaderHighlight: Boolean,
    val animationScale: Float,
    val gridColumns: Int,
    val iconSizeDp: Int
) {
    companion object {
        fun detect(context: Context): DeviceProfile {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            val lowRamFlag = am?.isLowRamDevice ?: false
            val totalMb = if (am != null) {
                val mi = ActivityManager.MemoryInfo()
                am.getMemoryInfo(mi)
                (mi.totalMem / (1024L * 1024L)).toInt()
            } else {
                0
            }

            // POCO M4 5G reports ~3.6 GB usable; treat <= 4 GB as constrained.
            val constrained = lowRamFlag || (totalMb in 1..4096)

            return if (constrained) {
                DeviceProfile(
                    isLowRam = true,
                    totalRamMb = totalMb,
                    blurRadiusDp = 6f,
                    useLens = false,
                    useShaderHighlight = false,
                    animationScale = 0.5f,
                    gridColumns = 4,
                    iconSizeDp = 52
                )
            } else {
                DeviceProfile(
                    isLowRam = false,
                    totalRamMb = totalMb,
                    blurRadiusDp = 14f,
                    useLens = true,
                    useShaderHighlight = true,
                    animationScale = 1f,
                    gridColumns = 4,
                    iconSizeDp = 56
                )
            }
        }

        fun supportsRenderEffect(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

        fun supportsRuntimeShader(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    }
}
