package com.newolf.jumpad

import android.Manifest
import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.newolf.jumpad.data.AppInfo
import com.newolf.jumpad.data.InstalledAppLoader
import com.newolf.jumpad.data.RuleRepository
import com.newolf.jumpad.service.AccessibilityUtil
import com.newolf.jumpad.service.KeepAliveUtil
import com.newolf.jumpad.service.SkipForegroundService
import com.newolf.jumpad.ui.AppDetailScreen
import com.newolf.jumpad.ui.AppListScreen
import com.newolf.jumpad.ui.ImageSkipRecordScreen
import com.newolf.jumpad.ui.KeepAliveScreen
import com.newolf.jumpad.ui.MainScreen
import com.newolf.jumpad.ui.SkipRecordScreen
import com.newolf.jumpad.ui.UnmatchedRecordScreen
import com.newolf.jumpad.ui.theme.JumpAdTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    // POST_NOTIFICATIONS 运行时权限请求器(Android 13+)。授予后再启动前台服务。
    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            // 无论是否授予都尝试启动:未授予时前台通知可能不显示,但服务仍可运行。
            SkipForegroundService.start(applicationContext)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 初始化本地规则存储(MMKV)。
        RuleRepository.init(applicationContext)
        enableEdgeToEdge()
        setContent {
            JumpAdTheme {
                AppRoot(
                    onRequestForeground = { enable -> toggleForeground(enable) }
                )
            }
        }
    }

    /** 启动/停止前台服务;启动前在 Android 13+ 上申请通知权限。 */
    private fun toggleForeground(enable: Boolean) {
        if (enable) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
                android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                SkipForegroundService.start(applicationContext)
            }
        } else {
            SkipForegroundService.stop(applicationContext)
        }
    }
}

/** 应用内导航目的地。 */
private sealed interface Screen {
    data object Main : Screen
    data object AppList : Screen
    data class AppDetail(val packageName: String, val appName: String) : Screen
    data object SkipRecord : Screen
    data object Unmatched : Screen
    data object ImageSkip : Screen
    data object KeepAlive : Screen
}

/**
 * 应用根组件:负责监听生命周期以刷新无障碍服务开启状态,并连接规则数据与页面导航。
 */
@Composable
private fun AppRoot(onRequestForeground: (Boolean) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val config by RuleRepository.config.collectAsStateWithLifecycle()
    val liTiaoTiaoCount by RuleRepository.liTiaoTiaoCount.collectAsStateWithLifecycle()

    var serviceEnabled by remember { mutableStateOf(AccessibilityUtil.isServiceEnabled(context)) }
    var foregroundEnabled by remember { mutableStateOf(SkipForegroundService.isRunning) }
    var ignoringBatteryOpt by remember { mutableStateOf(KeepAliveUtil.isIgnoringBatteryOptimizations(context)) }
    var canDrawOverlays by remember { mutableStateOf(KeepAliveUtil.canDrawOverlays(context)) }
    var screen by remember { mutableStateOf<Screen>(Screen.Main) }

    // 应用列表状态提升到此处,避免进入应用详情后返回时被销毁重建导致重新扫描加载与丢失滚动位置。
    var installedApps by remember { mutableStateOf<List<AppInfo>?>(null) }
    var appListQuery by remember { mutableStateOf("") }
    val appListState = rememberLazyListState()
    // 应用列表只在首次需要时加载一次,后续返回列表复用缓存。
    LaunchedEffect(Unit) {
        if (installedApps == null) {
            installedApps = withContext(Dispatchers.IO) {
                InstalledAppLoader.loadInstalledApps(context, includeSystem = true)
            }
        }
    }

    // 每次界面回到前台时刷新无障碍服务与前台服务状态(用户可能刚从系统设置返回)。
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                serviceEnabled = AccessibilityUtil.isServiceEnabled(context)
                foregroundEnabled = SkipForegroundService.isRunning
                ignoringBatteryOpt = KeepAliveUtil.isIgnoringBatteryOptimizations(context)
                canDrawOverlays = KeepAliveUtil.canDrawOverlays(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // 拦截系统返回手势/返回键:非主页面时先在应用内回退到上一级,而不是直接退出应用。
    // 仅在非 Main 页面启用;Main 页面不拦截,交由系统默认行为(退出应用)。
    BackHandler(enabled = screen !is Screen.Main) {
        screen = when (screen) {
            is Screen.AppDetail -> Screen.AppList
            is Screen.AppList -> Screen.Main
            is Screen.SkipRecord -> Screen.Main
            is Screen.Unmatched -> Screen.Main
            is Screen.ImageSkip -> Screen.Main
            is Screen.KeepAlive -> Screen.Main
            is Screen.Main -> Screen.Main
        }
    }

    when (val current = screen) {
        is Screen.Main -> MainScreen(
            serviceEnabled = serviceEnabled,
            config = config,
            liTiaoTiaoCount = liTiaoTiaoCount,
            foregroundEnabled = foregroundEnabled,
            onOpenSettings = { AccessibilityUtil.openAccessibilitySettings(context) },
            onGlobalToggle = { RuleRepository.setGlobalEnabled(it) },
            onToastToggle = { RuleRepository.setShowSkipToast(it) },
            onForegroundToggle = {
                foregroundEnabled = it
                onRequestForeground(it)
            },
            onImageSkipToggle = { RuleRepository.setImageSkipEnabled(it) },
            onOpenAppList = { screen = Screen.AppList },
            onOpenSkipRecord = { screen = Screen.SkipRecord },
            onOpenUnmatched = { screen = Screen.Unmatched },
            onOpenImageSkip = { screen = Screen.ImageSkip },
            onOpenKeepAlive = { screen = Screen.KeepAlive },
            onRuleToggle = { id, enabled -> RuleRepository.toggleRule(id, enabled) },
            onRuleUpsert = { RuleRepository.upsertRule(it) },
            onRuleDelete = { RuleRepository.deleteRule(it) },
            onReset = { RuleRepository.resetToDefault() }
        )

        is Screen.AppList -> AppListScreen(
            ruleCountOf = { pkg -> RuleRepository.rulesForPackage(pkg).size },
            onBack = { screen = Screen.Main },
            onAppClick = { app: AppInfo ->
                screen = Screen.AppDetail(app.packageName, app.appName)
            },
            apps = installedApps,
            query = appListQuery,
            onQueryChange = { appListQuery = it },
            listState = appListState
        )

        is Screen.AppDetail -> AppDetailScreen(
            packageName = current.packageName,
            appName = current.appName,
            config = config,
            skipEnabled = run {
                val isSys = isSystemPackage(context, current.packageName)
                RuleRepository.isPackageEnabled(current.packageName, isSys)
            },
            onSkipEnabledChange = { enabled ->
                val isSys = isSystemPackage(context, current.packageName)
                RuleRepository.setPackageEnabled(current.packageName, enabled, isSys)
            },
            onBack = { screen = Screen.AppList },
            onRuleToggle = { id, enabled -> RuleRepository.toggleRule(id, enabled) },
            onRuleUpsert = { RuleRepository.upsertRule(it) },
            onRuleDelete = { RuleRepository.deleteRule(it) }
        )

        is Screen.SkipRecord -> SkipRecordScreen(
            onBack = { screen = Screen.Main }
        )

        is Screen.Unmatched -> UnmatchedRecordScreen(
            onBack = { screen = Screen.Main }
        )

        is Screen.ImageSkip -> ImageSkipRecordScreen(
            onBack = { screen = Screen.Main }
        )

        is Screen.KeepAlive -> KeepAliveScreen(
            foregroundEnabled = foregroundEnabled,
            pixelKeepAliveEnabled = config.pixelKeepAliveEnabled,
            ignoringBatteryOpt = ignoringBatteryOpt,
            canDrawOverlays = canDrawOverlays,
            onBack = { screen = Screen.Main },
            onForegroundToggle = {
                foregroundEnabled = it
                RuleRepository.setForegroundEnabled(it)
                onRequestForeground(it)
            },
            onPixelToggle = { RuleRepository.setPixelKeepAliveEnabled(it) },
            onRequestBatteryOpt = { KeepAliveUtil.requestIgnoreBatteryOptimizations(context) },
            onRequestOverlay = { KeepAliveUtil.openOverlayPermissionSettings(context) },
            onOpenAppDetail = { KeepAliveUtil.openAppDetailSettings(context) }
        )
    }
}

/**
 * 判断某个包是否为系统预装应用(含系统应用更新版)。
 * 用于决定每应用跳过开关的默认值:系统应用默认关闭,用户应用默认开启。
 * 查询失败时保守返回 false(按用户应用处理)。
 */
private fun isSystemPackage(context: Context, packageName: String): Boolean =
    runCatching {
        val info = context.packageManager.getApplicationInfo(packageName, 0)
        val mask = ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP
        (info.flags and mask) != 0
    }.getOrDefault(false)