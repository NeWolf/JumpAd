package com.newolf.jumpad.service

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.text.TextUtils

/**
 * 无障碍服务相关的工具方法:检测服务是否开启、跳转到系统无障碍设置。
 */
object AccessibilityUtil {

    /** 判断本应用的无障碍服务是否已被用户开启。 */
    fun isServiceEnabled(context: Context): Boolean {
        val expected = "${context.packageName}/${SkipAdAccessibilityService::class.java.name}"
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        val accessibilityEnabled = Settings.Secure.getInt(
            context.contentResolver,
            Settings.Secure.ACCESSIBILITY_ENABLED,
            0
        )
        if (accessibilityEnabled != 1) return false

        val splitter = TextUtils.SimpleStringSplitter(':')
        splitter.setString(enabledServices)
        while (splitter.hasNext()) {
            if (splitter.next().equals(expected, ignoreCase = true)) return true
        }
        return false
    }

    /** 打开系统无障碍设置页面,引导用户开启服务。 */
    fun openAccessibilitySettings(context: Context) {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}