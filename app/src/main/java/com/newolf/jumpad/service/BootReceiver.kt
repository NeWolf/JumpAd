package com.newolf.jumpad.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.newolf.jumpad.data.RuleRepository

/**
 * 开机自启广播接收器。
 *
 * 监听系统开机完成(及部分厂商快速开机)广播,在用户已开启"前台常驻服务"的前提下,
 * 开机后自动拉起 [SkipForegroundService],提升无障碍服务的存活率。
 *
 * 说明:
 * - 无障碍服务本身由系统在开机后自动恢复(用户已在系统设置授权),此处主要负责拉起前台服务;
 * - 完全离线,不涉及任何网络访问;
 * - 仅当用户在应用内开启前台服务开关时才拉起,尊重用户选择。
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != "android.intent.action.QUICKBOOT_POWERON" &&
            action != "com.htc.intent.action.QUICKBOOT_POWERON"
        ) {
            return
        }

        // 读取本地配置,判断用户是否希望常驻。
        RuleRepository.init(context.applicationContext)
        if (RuleRepository.currentConfig().foregroundEnabled) {
            SkipForegroundService.start(context.applicationContext)
        }
    }
}