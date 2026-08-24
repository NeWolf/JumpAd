package com.newolf.jumpad.service

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.WindowManager

/**
 * 1像素透明悬浮窗保活管理器。
 *
 * 原理:在屏幕熄灭(锁屏)时,通过 [WindowManager] 添加一个 1x1 像素、透明、位于屏幕角落的窗口,
 * 使进程在锁屏后仍保有一个"可见窗口",从而降低被系统回收的概率;亮屏时移除该窗口以避免打扰。
 *
 * 注意:
 * - 需要"显示在其他应用上层"(SYSTEM_ALERT_WINDOW)权限;未授权时静默跳过;
 * - 该手段在高版本 Android(尤其 8.0+ 后台限制加强后)保活效果有限,仅作为辅助手段;
 * - 完全离线,不涉及任何网络访问;
 * - 所有操作在主线程调用(由前台服务的屏幕广播回调触发)。
 */
object PixelKeepAliveManager {

    private var pixelView: View? = null

    /**
     * 显示 1像素保活窗口(锁屏时调用)。
     * 若未授予悬浮窗权限或已显示,则不重复添加。
     */
    fun show(context: Context) {
        if (pixelView != null) return
        if (!KeepAliveUtil.canDrawOverlays(context)) return

        val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            1,
            1,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSPARENT
        ).apply {
            gravity = Gravity.START or Gravity.TOP
            x = 0
            y = 0
            width = 1
            height = 1
        }

        val view = View(context)
        runCatching {
            wm.addView(view, params)
            pixelView = view
        }
    }

    /**
     * 移除 1像素保活窗口(亮屏时调用)。
     */
    fun hide(context: Context) {
        val view = pixelView ?: return
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
        runCatching { wm?.removeView(view) }
        pixelView = null
    }

    /** 是否正在显示 1像素窗口。 */
    fun isShowing(): Boolean = pixelView != null
}