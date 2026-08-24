package com.newolf.jumpad.service

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import com.newolf.jumpad.data.ImageSkipRecord
import com.newolf.jumpad.data.RuleConfig
import com.newolf.jumpad.data.RuleRepository
import com.newolf.jumpad.data.LauncherActivityResolver
import com.newolf.jumpad.data.SkipRecord
import com.newolf.jumpad.data.UnmatchedRecord
import com.newolf.jumpad.data.SkipRule
import java.io.File
import java.io.FileOutputStream

/**
 * 跳过开屏广告的无障碍服务(核心)。
 *
 * 原理(借鉴李跳跳):
 * 1. 监听窗口内容变化事件;
 * 2. 遍历当前窗口的节点树,查找文案与"跳过"类关键词匹配、且可点击的控件;
 * 3. 对命中的控件执行点击(控件不可点击时向上查找可点击父节点),从而跳过广告。
 *
 * 特性:
 * - 完全离线,不访问网络;
 * - 规则来自 [RuleRepository](本地 MMKV 持久化);
 * - 通过节流避免对同一窗口的重复点击。
 */
class SkipAdAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "JumpAd"

        /** 匹配后的节流时间:同一次广告窗口只点击一次,避免误触后续界面。 */
        private const val CLICK_THROTTLE_MS = 1500L

        /** 遍历节点树的最大深度,防止极端布局导致的性能问题。 */
        private const val MAX_DEPTH = 40

        /** 关键词最大长度限制,避免过长文案误匹配(例如正文含"跳过"字样)。 */
        private const val MAX_TEXT_LEN = 12

        /**
         * 启动页扫描时间窗口(毫秒)。
         * 应用冷启动/切入前台后,在该窗口内持续视为"启动页阶段"并扫描广告,
         * 不再严格要求当前 Activity 等于 launcher。窗口结束或成功跳过后停止扫描。
         * 覆盖跳板式启动(launcher→隐私弹窗→SplashActivity→主界面)等多样场景。
         */
        private const val SPLASH_WINDOW_MS = 5000L

        /**
         * 图片兜底:从应用启动入口(launcher Activity)起允许的最大 Activity 跳转层数。
         * 0 表示仅入口页本身;跳板式启动(launcher→隐私弹窗/SplashActivity)通常在 1~2 层内。
         * 超过该层数说明已进入主界面,图片兜底不再触发,避免误触功能按钮。
         */
        private const val MAX_SPLASH_HOP = 2

        /** 图片兜底:候选控件最大面积占屏比(超过视为广告图/背景,排除)。 */
        private const val IMG_MAX_AREA_RATIO = 0.15

        /** 图片兜底:候选控件最小边长(像素粗略过滤,过小可能是装饰点)。 */
        private const val IMG_MIN_SIDE_PX = 24

        /** 图片兜底:候选控件单边最大占屏比(单边过大也排除)。 */
        private const val IMG_MAX_SIDE_RATIO = 0.5

        @Volatile
        var isRunning: Boolean = false
            private set
    }

    /** 李跳跳规则匹配引擎。 */
    private var engine: LiTiaoTiaoEngine? = null

    /** 主线程 Handler,用于弹出 Toast 提示。 */
    private val mainHandler = Handler(Looper.getMainLooper())

    /** 上次成功点击的时间戳,用于节流。 */
    private var lastClickTime = 0L

    /** 上次处理过的包名,用于配合节流按窗口区分。 */
    private var lastPackage: String? = null

    /**
     * 系统应用判定缓存(包名 -> 是否系统应用)。
     * 系统预装应用(车机自带 App、桌面、设置等)不应被扫描/干扰,只处理用户安装的第三方应用。
     */
    private val systemAppCache = HashMap<String, Boolean>()

    /**
     * 启动页会话状态:当前正处于启动页扫描窗口内的应用包名。
     * 当窗口切换到某应用(冷启动/切入前台)时置为该包名并开启扫描时间窗口;
     * 窗口超时、成功跳过、或切换到别的应用时结束。
     */
    private var splashPackage: String? = null

    /** 当前启动页扫描窗口的开始时间戳(毫秒)。用于判断是否仍在扫描窗口内。 */
    private var splashWindowStart = 0L

    /**
     * 本次前台会话是否已结束扫描(成功跳过或已记录未匹配)。
     * 用于避免同一会话内重复记录/重复扫描;应用切走后重置。
     */
    private val finishedPackages = HashSet<String>()

    /**
     * 当前启动页会话是否已成功跳过。
     * 进入某应用启动页阶段时重置为 false;命中并成功跳过后置 true。
     * 离开启动页时若仍为 false,则记录一条「未匹配」以便后续适配。
     */
    private var matchedInSession = false

    /** 当前启动页会话的 Activity 类名,记录未匹配时一并保存,便于适配定位。 */
    private var splashActivity: String? = null

    /**
     * 当前应用最新处于前台的 Activity 类名(仅记录真正的 Activity 事件)。
     * 用于在图片兜底触发时校验页面是否仍停留在启动页:
     * 若 5s 窗口结束时当前 Activity 已不等于 splashActivity,说明用户/应用已跳转到主界面,
     * 此时不应再执行启发式图片点击,避免误触主界面功能按钮。
     */
    private var currentActivity: String? = null

    /**
     * 本次会话应用的启动入口 Activity(即点击桌面图标进入的第一个界面,来自 PackageManager)。
     * 图片兜底以此为"启动页"基准:仅当当前页面仍是入口页、或入口页后紧邻的少量跳转
     * (跳板→隐私弹窗→SplashActivity)内才触发,超出则视为已进入主界面。
     */
    private var sessionLauncherActivity: String? = null

    /**
     * 从启动入口起经历的 Activity 跳转层数(入口页记为 0,每切换一个新 Activity +1)。
     * 用于配合 MAX_SPLASH_HOP 判断是否仍处于启动页阶段。
     */
    private var activityHopCount = 0

    override fun onServiceConnected() {
        super.onServiceConnected()
        RuleRepository.init(this)
        engine = LiTiaoTiaoEngine(this)
        isRunning = true
        val liCount = RuleRepository.liTiaoTiaoRules().size
        Log.i(TAG, "无障碍服务已连接,已加载李跳跳规则 $liCount 条")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val config = RuleRepository.currentConfig()
        if (!config.globalEnabled) return

        val packageName = event.packageName?.toString() ?: return
        // 忽略自身,避免在应用内界面误点击。
        if (packageName == this.packageName) return

        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                // 窗口(Activity)切换:更新启动页判定状态。
                updateSplashPhase(packageName, event.className?.toString())
                handleWindow(packageName, config)
            }
              AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                // 内容变化:仅在启动页扫描窗口内才继续扫描。
                if (packageName == splashPackage) {
                    if (System.currentTimeMillis() - splashWindowStart < SPLASH_WINDOW_MS) {
                        handleWindow(packageName, config)
                    } else {
                        // 窗口已超时:结束扫描并记录未匹配(若从未成功跳过)。
                        finishSession(packageName, splashActivity)
                    }
                }
            }
        }
    }

    /**
     * 根据窗口切换事件更新启动页判定状态(时间窗口策略)。
     *
     * 背景:实际应用启动方式差异极大——
     * - 有的启动页 Activity 恰好是 launcher(如 B 站 MainActivityV2);
     * - 有的 launcher 只是跳板,先弹隐私弹窗/SplashActivity 再进主界面(知乎、大众点评、智联、安居客等);
     * - 有的广告在非 launcher 的 Activity 上。
     * 因此不能简单用 `className == launcher` 判定启动页,否则大量应用永远进不了扫描阶段。
     *
     * 策略:以"应用切入前台"为起点开启一个扫描时间窗口(SPLASH_WINDOW_MS),
     * 窗口内该应用的所有窗口/内容变化都视为启动页阶段并持续扫描广告;
     * 命中跳过、或窗口超时、或切换到其它应用时结束扫描。
     */
    private fun updateSplashPhase(packageName: String, className: String?) {
        // 每应用开关:系统应用默认关闭、用户应用默认开启,用户可在应用详情页手动覆盖。
        // 关闭的应用不参与开屏广告扫描,避免误触系统界面或用户不希望处理的应用。
        if (!RuleRepository.isPackageEnabled(packageName, isSystemApp(packageName))) {
            return
        }

        // 窗口切换事件的 className 有时是控件类名(android.widget.FrameLayout)或 Fragment,
        // 并非真正的 Activity。这类事件不参与会话切换判定,避免打断正在进行的扫描窗口。
        if (className != null && !LauncherActivityResolver.isActivity(this, packageName, className)) {
            Log.d(TAG, "忽略非 Activity 事件: pkg=$packageName, class=$className")
            return
        }

        // 记录当前真正的 Activity(已过滤掉非 Activity 事件),供图片兜底校验页面是否仍在启动页。
        val currentActivityBefore = currentActivity
        currentActivity = className

        val now = System.currentTimeMillis()

        if (packageName != lastPackage) {
            // 发生应用切换:上一个应用会话结束;为新应用开启一次扫描窗口。
            //清除新应用的"已结束"标记,使其重新开始一轮扫描。
            finishedPackages.remove(packageName)
            splashPackage = packageName
            splashWindowStart = now
            splashActivity = className
            matchedInSession = false
            // 记录该应用的启动入口 Activity(桌面图标进入的第一个界面),作为图片兜底"启动页"基准。
            sessionLauncherActivity = LauncherActivityResolver.launcherActivityOf(this, packageName)
            activityHopCount = 0
            Log.d(TAG, "开启启动页扫描窗口: pkg=$packageName, activity=$className, launcher=$sessionLauncherActivity")
        } else {
            // 同一应用内的 Activity 切换:累加跳转层数(仅当 Activity 名确实变化时)。
            if (className != null && className != currentActivityBefore) {
                activityHopCount++
            }
            // 同一应用内的 Activity 切换:只要仍在扫描窗口内且未结束,则维持扫描。
            val withinWindow = now - splashWindowStart < SPLASH_WINDOW_MS
            if (!finishedPackages.contains(packageName) && withinWindow) {
                splashPackage = packageName
                if (splashActivity == null) splashActivity = className
                Log.d(TAG, "扫描窗口内 Activity 切换: pkg=$packageName, activity=$className")
            } else if (splashPackage == packageName) {
                // 窗口已超时且从未成功跳过:记录一条未匹配,结束扫描。
                finishSession(packageName, className)
            }
        }

        lastPackage = packageName
    }

    /**
     * 结束当前应用的扫描会话:若从未成功跳过则记录未匹配,并置空启动页状态。
     */
    private fun finishSession(packageName: String, className: String?) {
   if (!finishedPackages.contains(packageName) && !matchedInSession) {
            // 图片兜底:文字/规则均未命中且已到 5s 窗口末尾,尝试点击疑似"图片跳过按钮"。
            val config = RuleRepository.currentConfig()
            // 触发时机校验:图片兜底为启发式点击,只能在"仍停留于启动页"时执行。
            // 以应用的启动入口 Activity(桌面图标进入的第一个界面)为基准:
            //  1) 当前页面仍是入口页本身;或
            //  2) 从入口起的跳转层数未超过 MAX_SPLASH_HOP(覆盖 launcher→隐私弹窗→SplashActivity 跳板式启动)。
            // 若已超出,说明已进入主界面,继续启发式点击会误触功能按钮,故放弃图片兜底。
            // 无法解析入口(sessionLauncherActivity 为 null)时退化为原有的 splashActivity 一致性判断。
            val stillOnSplash = if (sessionLauncherActivity != null) {
                currentActivity == sessionLauncherActivity || activityHopCount <= MAX_SPLASH_HOP
            } else {
                splashActivity == null || currentActivity == null || currentActivity == splashActivity
            }
            val imageClicked = if (config.imageSkipEnabled && stillOnSplash) {
                runCatching { tryImageSkip(packageName, splashActivity ?: className) }.getOrDefault(false)
            } else {
                if (config.imageSkipEnabled && !stillOnSplash) {
                    Log.i(TAG, "页面已离开启动页,跳过图片兜底: pkg=$packageName, launcher=$sessionLauncherActivity, current=$currentActivity, hop=$activityHopCount")
                }
                false
            }

            if (imageClicked) {
                RuleRepository.incrementSkipCount()
                recordSkip(packageName, "图片兜底")
                matchedInSession = true
                showSkipToast("已跳过广告 · 图片兜底", config)
                Log.i(TAG, "图片兜底成功跳过: pkg=$packageName")
            } else {
                recordUnmatched(packageName, splashActivity ?: className)
                Log.i(TAG, "扫描窗口超时未匹配到跳过按钮,记录待适配: pkg=$packageName, activity=${splashActivity ?: className}")
            }
        }
        finishedPackages.add(packageName)
        splashPackage = null
        splashActivity = null
        currentActivity = null
        sessionLauncherActivity = null
        activityHopCount = 0
        Log.d(TAG, "结束扫描窗口: pkg=$packageName, activity=$className")
    }

    /**
     * 判断包名是否为系统预装应用(带缓存)。
     * 系统应用(FLAG_SYSTEM)及系统应用的更新版(FLAG_UPDATED_SYSTEM_APP)均视为系统应用,不做广告扫描。
     * 查询失败时保守返回 false(按第三方应用处理),避免漏扫真正的第三方应用。
     */
    private fun isSystemApp(packageName: String): Boolean {
        systemAppCache[packageName]?.let { return it }
        val result = runCatching {
            val info = packageManager.getApplicationInfo(packageName, 0)
            val mask = android.content.pm.ApplicationInfo.FLAG_SYSTEM or
                android.content.pm.ApplicationInfo.FLAG_UPDATED_SYSTEM_APP
            (info.flags and mask) != 0
        }.getOrDefault(false)
        systemAppCache[packageName] = result
        return result
    }

    private fun handleWindow(packageName: String, config: RuleConfig) {
        // 启动页守卫:仅在该应用处于启动页阶段时才扫描节点与对比。
        if (packageName != splashPackage) return

        val now = System.currentTimeMillis()
        if (packageName == lastPackage && now - lastClickTime < CLICK_THROTTLE_MS) {
            return
        }

        val root = rootInActiveWindow ?: return
        try {
            // 1) 优先应用李跳跳规则(id 触发 + action 执行)。
            val liRules = RuleRepository.liTiaoTiaoRules()
            if (liRules.isNotEmpty()) {
                val hit = engine?.apply(root, liRules)
                if (hit != null) {
                    lastClickTime = now
                    lastPackage = packageName
                    RuleRepository.incrementSkipCount()
                    recordSkip(packageName, "李跳跳规则")
                    matchedInSession = true
                    finishedPackages.add(packageName)
                    splashPackage = null
                    Log.i(TAG, "已按李跳跳规则跳过广告: pkg=$packageName, id=${hit.id}, action=${hit.action}")
                    showSkipToast("已跳过广告 · ${hit.action}", config)
                    return
                }
            }

            // 2) 回退:用户自定义/通用关键词匹配。
            val keywords = collectKeywords(packageName, config)
            if (keywords.isEmpty()) return
            Log.d(TAG, "李跳跳规则未命中,回退关键词匹配: pkg=$packageName, keywords=$keywords")
            val target = findSkipNode(root, keywords, 0)
            if (target != null) {
                val clicked = performClickOnNode(target)
                if (clicked) {
                    lastClickTime = now
                    lastPackage = packageName
                    RuleRepository.incrementSkipCount()
                    recordSkip(packageName, "关键词匹配")
                    matchedInSession = true
                    finishedPackages.add(packageName)
                    splashPackage = null
                    Log.i(TAG, "已跳过广告(关键词): pkg=$packageName")
                    showSkipToast("已跳过广告", config)
                }
                target.recycle()
            }
        } finally {
            root.recycle()
        }
    }

    /** 解析应用名称,失败时回退为包名。 */
    private fun resolveAppName(packageName: String): String = runCatching {
        val pm = packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
    }.getOrDefault(packageName)

    /** 记录一条跳过明细。 */
    private fun recordSkip(packageName: String, method: String) {
        RuleRepository.addSkipRecord(
            SkipRecord(
                packageName = packageName,
                appName = resolveAppName(packageName),
                timestamp = System.currentTimeMillis(),
                method = method
            )
        )
    }

    /** 记录一条未匹配明细(按包名去重,便于后续适配)。 */
    private fun recordUnmatched(packageName: String, activity: String?) {
        RuleRepository.addUnmatchedRecord(
            UnmatchedRecord(
                packageName = packageName,
                appName = resolveAppName(packageName),
                timestamp = System.currentTimeMillis(),
                activity = activity
            )
        )
    }

    /** 在主线程弹出跳过提示(短 Toast);受配置开关控制。 */
    private fun showSkipToast(message: String, config: RuleConfig) {
        if (!config.showSkipToast) return
        mainHandler.post {
            Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show()
        }
    }

    /** 收集适用于目标包名的启用关键词。 */
    private fun collectKeywords(packageName: String, config: RuleConfig): List<String> {
        val result = LinkedHashSet<String>()
        config.rules.forEach { rule: SkipRule ->
            if (!rule.enabled) return@forEach
            val applicable = rule.packageName.isBlank() || rule.packageName == packageName
            if (applicable) {
                rule.keywords.forEach { kw ->
                    if (kw.isNotBlank()) result.add(kw.trim())
                }
            }
        }
        return result.toList()
    }

    /**
     * 深度优先遍历节点树,查找文案命中关键词的节点。
     * 优先返回可点击的节点。
     */
    private fun findSkipNode(
        node: AccessibilityNodeInfo?,
        keywords: List<String>,
        depth: Int
    ): AccessibilityNodeInfo? {
        if (node == null || depth > MAX_DEPTH) return null

        // 先尝试通过文本直接检索(部分系统支持,效率更高)。
        if (depth == 0) {
            keywords.forEach { kw ->
                val cleaned = kw.replace("%d", "").replace("%ds", "").replace("%s", "").trim()
                if (cleaned.isNotBlank()) {
                    val found = node.findAccessibilityNodeInfosByText(cleaned)
                    found?.forEach { candidate ->
                        if (candidate != null && matchNode(candidate, keywords)) {
                            val clickable = findClickableSelf(candidate)
                            if (clickable != null) return clickable
                        }
                        candidate?.recycle()
                    }
                }
            }
        }

        // 回退:手动 DFS 遍历。
        if (matchNode(node, keywords)) {
            val clickable = findClickableSelf(node)
            if (clickable != null) return clickable
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val res = findSkipNode(child, keywords, depth + 1)
            if (res != null) {
                if (res !== child) child.recycle()
                return res
            }
            child.recycle()
        }
        return null
    }

    /** 判断节点文案是否命中任意关键词。 */
    private fun matchNode(node: AccessibilityNodeInfo, keywords: List<String>): Boolean {
        val text = (node.text?.toString() ?: node.contentDescription?.toString())?.trim()
            ?: return false
        if (text.isBlank() || text.length > MAX_TEXT_LEN) return false

        return keywords.any { kw ->
            val cleaned = kw.replace("%d", "").replace("%ds", "").replace("%s", "").trim()
            if (cleaned.isBlank()) false
            else text.contains(cleaned, ignoreCase = true)
        }
    }

    /** 从当前节点向上寻找可点击节点(自身或最近的可点击父节点)。 */
    private fun findClickableSelf(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var current: AccessibilityNodeInfo? = node
        var level = 0
        while (current != null && level < 8) {
            if (current.isClickable) return current
            current = current.parent
            level++
        }
        // 都不可点击时,返回原节点尝试用手势点击。
        return node
    }

    /** 对节点执行点击;不可点击时回退为坐标手势点击。 */
    private fun performClickOnNode(node: AccessibilityNodeInfo): Boolean {
        if (node.isClickable) {
            return node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        }
        // 回退:直接触发默认点击动作。
        return node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
    }

    // ---------------- 图片跳过兜底(启发式) ----------------

    /** 图片兜底候选:一个可点击的图片类控件及其启发式得分。 */
    private data class ImageCandidate(
        val node: AccessibilityNodeInfo,
        val bounds: Rect,
        val className: String?,
        val desc: String?,
        val score: Int
    )

    /**
     * 图片兜底扫描:文字/规则均未命中时调用。
     *
     * 思路:百度网盘、一刻相册等把"跳过"做成图片,无障碍拿不到图片内容,只能靠启发式猜:
     * 找可点击的图片类控件,按"位于屏幕角落 + 尺寸较小 + 非全屏背景"打分,点击得分最高者。
     * 点击后截图并记录控件特征,供人工核对;若点错了,后续可据记录修正启发式或关掉该应用开关。
     *
     * @return 是否成功点击了某个疑似跳过按钮
     */
    private fun tryImageSkip(packageName: String, activity: String?): Boolean {
        val root = rootInActiveWindow ?: return false
        val screen = Rect().also { root.getBoundsInScreen(it) }
        val screenW = if (screen.width() > 0) screen.width() else resources.displayMetrics.widthPixels
        val screenH = if (screen.height() > 0) screen.height() else resources.displayMetrics.heightPixels
        val candidates = ArrayList<ImageCandidate>()
        try {
            collectImageCandidates(root, screenW, screenH, 0, candidates)
            if (candidates.isEmpty()) {
                Log.d(TAG, "图片兜底:未找到可点击图片候选 pkg=$packageName")
                return false
            }
            // 结合历史人工标记做修正:点错过的位置直接排除,点对过的位置强力加分优先。
            val history = RuleRepository.imageSkipRecords.value.items.filter { it.packageName == packageName }
            val blacklist = history.filter { it.corrected == false }.mapNotNull { it.bounds }.toSet()
            val whitelist = history.filter { it.corrected == true }.mapNotNull { it.bounds }.toSet()
            val usable = candidates.filter { boundsKey(it.bounds) !in blacklist }
            if (usable.isEmpty()) {
                Log.d(TAG, "图片兜底:所有候选均在历史黑名单中,放弃点击 pkg=$packageName")
                return false
            }
            // 修正后得分 = 原始得分 + (命中历史点对位置 +100)。
            val best = usable.maxByOrNull {
                it.score + if (boundsKey(it.bounds) in whitelist) 100 else 0
            } ?: return false
            // 得分过低(既不在角落也无有效特征)则不冒险点击;但命中历史点对位置时放行。
            if (best.score <= 0 && boundsKey(best.bounds) !in whitelist) {
                Log.d(TAG, "图片兜底:候选得分过低,放弃点击 pkg=$packageName, score=${best.score}")
                return false
            }
            val clickTarget = findClickableSelf(best.node)
            val clicked = clickTarget?.performAction(AccessibilityNodeInfo.ACTION_CLICK) ?: false
            Log.i(
                TAG,
                "图片兜底点击 pkg=$packageName, class=${best.className}, bounds=${best.bounds}, score=${best.score}, clicked=$clicked"
            )
            if (clicked) {
                // 截图 + 记录(截图为异步,记录先落库,截图成功后不回填也可,人工按时间核对)。
                val shotPath = takeSkipScreenshot(packageName)
                recordImageSkip(packageName, activity, best, shotPath)
            }
            return clicked
        } finally {
            candidates.forEach { if (it.node !== root) it.node.recycle() }
            root.recycle()
        }
    }

    /**
     * DFS 收集可点击的图片类候选并打分。
     *
     * 打分维度(越高越可能是跳过按钮):
     * - 位于屏幕四角区域(尤其右上/右下):+3;仅在上半/右侧边缘:+1
     * - 尺寸较小(面积占屏比 < 阈值):+2
     * - contentDescription/text 含"跳过/skip/关闭"等:+4(几乎确定)
     * - className 含 Image:+1
     * 排除:面积过大(接近全屏,是广告图本身)、单边过大、过小的装饰点。
     */
    private fun collectImageCandidates(
        node: AccessibilityNodeInfo?,
        screenW: Int,
        screenH: Int,
        depth: Int,
        out: ArrayList<ImageCandidate>
    ) {
        if (node == null || depth > MAX_DEPTH) return
        val cls = node.className?.toString()
        val desc = node.contentDescription?.toString()
        val isImageLike = cls != null &&
            (cls.contains("Image", true) || cls.contains("Button", true) || cls.contains("View", true))
        val clickable = node.isClickable || run {
            // 自身不可点击但有可点击父,也算候选(点击时向上找)。
            var p = node.parent; var lv = 0; var ok = false
            while (p != null && lv < 4) { if (p.isClickable) { ok = true; break }; p = p.parent; lv++ }
            ok
        }
        if (isImageLike && clickable) {
            val r = Rect().also { node.getBoundsInScreen(it) }
            val w = r.width(); val h = r.height()
            val areaRatio = if (screenW > 0 && screenH > 0)
                (w.toDouble() * h) / (screenW.toDouble() * screenH) else 1.0
            val sideOk = w >= IMG_MIN_SIDE_PX && h >= IMG_MIN_SIDE_PX &&
                w <= screenW * IMG_MAX_SIDE_RATIO && h <= screenH * IMG_MAX_SIDE_RATIO
            if (sideOk && areaRatio in 0.0..IMG_MAX_AREA_RATIO && w > 0 && h > 0) {
                out.add(
                    ImageCandidate(
                        node = AccessibilityNodeInfo.obtain(node),
                        bounds = Rect(r),
                        className = cls,
                        desc = desc,
                        score = scoreCandidate(r, desc, cls, screenW, screenH)
                    )
                )
            }
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            collectImageCandidates(child, screenW, screenH, depth + 1, out)
            child.recycle()
        }
    }

    /** 对候选控件启发式打分。 */
    private fun scoreCandidate(r: Rect, desc: String?, cls: String?, screenW: Int, screenH: Int): Int {
        var score = 0
        val cx = r.centerX(); val cy = r.centerY()
        val rightZone = cx > screenW * 0.6
        val topZone = cy < screenH * 0.25
        val bottomZone = cy > screenH * 0.8
        // 角落权重:右上/右下最常见。
        when {
            rightZone && (topZone || bottomZone) -> score += 3
            topZone && cx > screenW * 0.45 -> score += 2 // 上方偏右
           bottomZone -> score += 1
            rightZone -> score += 1
        }
        // 尺寸小加分。
        val areaRatio = (r.width().toDouble() * r.height()) / (screenW.toDouble() * screenH)
        if (areaRatio < 0.05) score += 2 else if (areaRatio < IMG_MAX_AREA_RATIO) score += 1
        // 描述/文案强特征。
        val hint = desc?.lowercase().orEmpty()
        if (listOf("跳过", "skip", "关闭", "close", "广告").any { hint.contains(it) }) score += 4
        if (cls != null && cls.contains("Image", true)) score += 1
        return score
    }

    /** 将 Rect 转为与记录一致的位置字符串键(left,top,right,bottom),用于历史黑/白名单比对。 */
    private fun boundsKey(r: Rect): String = "${r.left},${r.top},${r.right},${r.bottom}"

    /** 记录一条图片兜底点击明细。 */
    private fun recordImageSkip(
        packageName: String,
        activity: String?,
        candidate: ImageCandidate,
        screenshot: String?
    ) {
        val b = candidate.bounds
        RuleRepository.addImageSkipRecord(
            ImageSkipRecord(
                packageName = packageName,
                appName = resolveAppName(packageName),
                timestamp = System.currentTimeMillis(),
                activity = activity,
                className = candidate.className,
                bounds = boundsKey(b),
                score = candidate.score,
                screenshot = screenshot,
                corrected = null
            )
        )
    }

    /**
     * 截取当前屏幕并保存到应用私有目录,供人工核对图片兜底是否点对。
     * 仅 API 30+ 支持无障碍截图;失败时返回 null。
     */
    private fun takeSkipScreenshot(packageName: String): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        return runCatching {
            val dir = File(filesDir, "image_skip_shots").apply { if (!exists()) mkdirs() }
            val file = File(dir, "${packageName}_${System.currentTimeMillis()}.png")
            takeScreenshot(
                Display.DEFAULT_DISPLAY,
                applicationContext.mainExecutor,
                object : TakeScreenshotCallback {
                    override fun onSuccess(screenshot: ScreenshotResult) {
                        runCatching {
                            val bmp = Bitmap.wrapHardwareBuffer(
                                screenshot.hardwareBuffer, screenshot.colorSpace
                            )
                            if (bmp != null) {
                                FileOutputStream(file).use { out ->
                                    bmp.compress(Bitmap.CompressFormat.PNG, 90, out)
                                }
                                bmp.recycle()
                            }
                            screenshot.hardwareBuffer.close()
                        }
                    }

                    override fun onFailure(errorCode: Int) {
                        Log.d(TAG, "图片兜底截图失败: code=$errorCode")
                    }
                }
            )
            file.absolutePath
        }.getOrNull()
    }

    override fun onInterrupt() {
        Log.d(TAG, "无障碍服务被中断")
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        mainHandler.removeCallbacksAndMessages(null)
        Log.d(TAG, "无障碍服务已销毁")
    }
}