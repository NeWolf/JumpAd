package com.newolf.jumpad.service

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings

/**
 * 保活相关的工具方法:电池优化白名单检测/申请、悬浮窗权限检测/跳转、厂商自启动设置跳转。
 *
 * 说明:
 * - 各手段均只做"检测 + 引导授权",不强制修改系统设置,尊重用户与系统限制;
 * - 完全离线,不涉及任何网络访问。
 */
object KeepAliveUtil {

    /** 判断本应用是否已加入电池优化白名单(被忽略电池优化)。 */
    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    /**
     * 申请忽略电池优化(弹出系统对话框)。部分系统会拦截此直接申请,
     * 若失败则回退到电池优化设置列表页由用户手动设置。
     */
    fun requestIgnoreBatteryOptimizations(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        val direct = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(direct) }.onFailure {
            openBatteryOptimizationSettings(context)
        }
    }

    /** 打开系统电池优化设置列表页,由用户手动将本应用设为"不优化"。 */
    fun openBatteryOptimizationSettings(context: Context) {
        val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }.onFailure {
            openAppDetailSettings(context)
        }
    }

    /** 判断是否已授予"显示在其他应用上层"(悬浮窗)权限。 */
    fun canDrawOverlays(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else {
            true
        }
    }

    /** 跳转到悬浮窗权限授权页面。 */
    fun openOverlayPermissionSettings(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}")
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }.onFailure {
            openAppDetailSettings(context)
        }
    }

    /**
     * 跳转到本应用的系统详情页,作为各类权限/自启动引导的通用回退入口。
     * 厂商自启动管理入口高度碎片化,统一回退到应用详情页由用户操作最稳妥。
     */
    fun openAppDetailSettings(context: Context) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
    }
}