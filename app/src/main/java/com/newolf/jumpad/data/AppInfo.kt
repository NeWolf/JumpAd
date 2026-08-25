package com.newolf.jumpad.data

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable

/**
 * 已安装应用的展示信息。
 *
 * @param packageName 应用包名
 * @param appName     应用名称(用户可读)
 * @param icon        应用图标
 * @param isSystem    是否为系统应用
 */
data class AppInfo(
    val packageName: String,
    val appName: String,
    val icon: Drawable?,
    val isSystem: Boolean
)

/**
 * 已安装应用加载器:通过 [PackageManager] 查询设备上的应用列表。
 *
 * 完全离线,仅读取本机安装信息。查询过程较耗时,应在后台线程调用。
 */
object InstalledAppLoader {

    /**
     * 加载已安装应用列表。
     *
     * @param includeSystem 是否包含系统应用(默认包含,即列出手机里的所有应用)
     * @return 按应用名称排序的应用列表
     */
    fun loadInstalledApps(
        context: Context,
        includeSystem: Boolean = true
    ): List<AppInfo> {
        val pm = context.packageManager

        // 列出设备上"全部已安装应用",而非仅"桌面可启动"的应用。
        // 输入法(IME)、无障碍工具等没有 CATEGORY_LAUNCHER 启动图标的应用,
        // 用 CATEGORY_LAUNCHER 查询会被漏掉;这里改用 getInstalledApplications 覆盖全部包,
        // 以便用户能对输入法等无图标应用单独设置开屏广告扫描开关。
        val installed = pm.getInstalledApplications(0)

        return installed.asSequence()
            // 按包名去重(保险起见)。
            .distinctBy { it.packageName }
            .filter { info ->
                if (includeSystem) true
                else !info.isSystemApp() || info.isUpdatedSystemApp()
            }
            // 过滤掉自身
            .filter { it.packageName != context.packageName }
            .map { info ->
                AppInfo(
                    packageName = info.packageName,
                    appName = runCatching { pm.getApplicationLabel(info).toString() }
                        .getOrElse { info.packageName },
                    icon = runCatching { pm.getApplicationIcon(info) }.getOrNull(),
                    isSystem = info.isSystemApp()
                )
            }
            // 先按类型(用户应用在前、系统应用在后),再按应用名排序,方便 UI 分组展示。
            .sortedWith(compareBy({ it.isSystem }, { it.appName.lowercase() }))
            .toList()
    }

    private fun ApplicationInfo.isSystemApp(): Boolean =
        (flags and ApplicationInfo.FLAG_SYSTEM) != 0

    private fun ApplicationInfo.isUpdatedSystemApp(): Boolean =
        (flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
}

/**
 * 启动页(Launcher/Splash Activity)解析器。
 *
 * 用途:无障碍服务据此判断当前窗口是否处于应用的启动页阶段。
 * 只有在启动页阶段才扫描节点查找"跳过"按钮;一旦离开启动页则停止扫描,提升性能与准确性。
 *
 * 完全离线,仅读取本机包管理信息。
 */
object LauncherActivityResolver {

    // 包名 -> 启动 Activity 完整类名(带简单缓存,避免频繁查询 PackageManager)。
    private val cache = HashMap<String, String?>()

    /**
     * 获取某应用的启动 Activity 类名(即点击桌面图标进入的第一个界面)。
     *
     * @return 启动 Activity 的完整类名;无法解析时返回 null
     */
    fun launcherActivityOf(context: Context, packageName: String): String? {
        cache[packageName]?.let { return it }
        if (cache.containsKey(packageName)) return null

        val pm = context.packageManager
        val className = runCatching {
            pm.getLaunchIntentForPackage(packageName)?.component?.className
        }.getOrNull()
        cache[packageName] = className
        return className
    }

    /** 清空缓存(应用安装/卸载后可调用刷新)。 */
    fun clear() {
        cache.clear()
        activityCache.clear()
    }

    // "包名/类名" -> 该类名是否为本包已注册的 Activity(带缓存)。
    private val activityCache = HashMap<String, Boolean>()

    /**
     * 判断 [className] 是否为 [packageName] 下已在 Manifest 注册的 Activity。
     *
     * 无障碍事件的 className 有时是控件类名(如 android.widget.FrameLayout)或 Fragment,
     * 并非真正的 Activity。据此过滤,避免把控件/Fragment 事件误判为"离开启动页"。
     *
     * @return true 表示是本包的 Activity;无法解析或不是 Activity 时返回 false
     */
    fun isActivity(context: Context, packageName: String, className: String): Boolean {
        val key = "$packageName/$className"
        activityCache[key]?.let { return it }

        val pm = context.packageManager
        val result = runCatching {
            val cn = android.content.ComponentName(packageName, className)
            pm.getActivityInfo(cn, 0)
            true
        }.getOrDefault(false)
        activityCache[key] = result
        return result
    }
}