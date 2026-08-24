package com.newolf.jumpad.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.newolf.jumpad.MainActivity
import com.newolf.jumpad.R
import com.newolf.jumpad.data.RuleRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * 前台常驻服务。
 *
 * 作用:
 * - 以前台通知常驻,提高无障碍服务被系统回收后重启的存活率;
 * - 在通知栏实时展示跳广告状态与累计跳过次数;
 * - 完全离线,不涉及任何网络访问。
 *
 * 生命周期由用户在应用内通过开关控制(启动/停止)。
 */
class SkipForegroundService : Service() {

    companion object {
        const val ACTION_START = "com.newolf.jumpad.action.START_FOREGROUND"
        const val ACTION_STOP = "com.newolf.jumpad.action.STOP_FOREGROUND"

        private const val CHANNEL_ID = "jumpad_foreground"
        private const val CHANNEL_NAME = "JumpAd 常驻服务"
        private const val NOTIFICATION_ID = 1001

        @Volatile
        var isRunning: Boolean = false
            private set

        /** 启动前台服务。 */
        fun start(context: Context) {
            val intent = Intent(context, SkipForegroundService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        /** 停止前台服务。 */
        fun stop(context: Context) {
            val intent = Intent(context, SkipForegroundService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var collectJob: Job? = null

    /**
     * 屏幕开关广播接收器:锁屏(熄屏)时显示 1像素保活窗口,亮屏时移除。
     * 仅当用户开启 1像素保活开关且已授予悬浮窗权限时才生效。
     */
    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val ctx = context ?: return
            if (!RuleRepository.currentConfig().pixelKeepAliveEnabled) {
                PixelKeepAliveManager.hide(ctx)
                return
            }
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> PixelKeepAliveManager.show(ctx)
                Intent.ACTION_SCREEN_ON -> PixelKeepAliveManager.hide(ctx)
            }
        }
    }

    private var screenReceiverRegistered = false

    override fun onCreate() {
        super.onCreate()
        RuleRepository.init(applicationContext)
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopForegroundCompat()
                stopSelf()
                return START_NOT_STICKY
            }
            else -> {
                startForeground(NOTIFICATION_ID, buildNotification())
                isRunning = true
                observeConfig()
                registerScreenReceiver()
            }
        }
        // 被系统杀死后尝试重建,以维持常驻。
        return START_STICKY
    }

    /** 监听配置变化,实时刷新通知内容(计数/开关)。 */
    private fun observeConfig() {
        if (collectJob != null) return
        collectJob = scope.launch {
            RuleRepository.config.collectLatest {
                val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                manager.notify(NOTIFICATION_ID, buildNotification())
            }
        }
    }

    /** 注册屏幕开关广播,用于 1像素保活窗口的显隐控制。 */
    private fun registerScreenReceiver() {
        if (screenReceiverRegistered) return
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        registerReceiver(screenReceiver, filter)
        screenReceiverRegistered = true
    }

    /** 注销屏幕开关广播并移除 1像素窗口。 */
    private fun unregisterScreenReceiver() {
        if (!screenReceiverRegistered) return
        runCatching { unregisterReceiver(screenReceiver) }
        screenReceiverRegistered = false
        PixelKeepAliveManager.hide(this)
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "保持跳广告服务常驻并展示运行状态"
                setShowBadge(false)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val config = RuleRepository.currentConfig()
        val enabled = config.globalEnabled
        val count = config.skipCount

        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = if (enabled) "JumpAd 正在守护" else "JumpAd 已暂停"
        val text = if (enabled) {
            "已累计跳过广告 $count 次 · 完全离线"
        } else {
            "跳广告总开关已关闭"
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOnlyAlertOnce(true)
            .build()
    }

    @Suppress("DEPRECATION")
    private fun stopForegroundCompat() {
        isRunning = false
        unregisterScreenReceiver()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            stopForeground(true)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        unregisterScreenReceiver()
        collectJob?.cancel()
        collectJob = null
        scope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}