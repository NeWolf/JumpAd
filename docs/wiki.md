# JumpAd 项目 Wiki

> 一款**完全离线**的 Android 开屏广告自动跳过应用,借鉴「李跳跳」思路,基于无障碍服务(AccessibilityService)实现。所有规则与数据均本地持久化,不访问任何网络。

---

## 1. 项目概述

JumpAd 通过系统无障碍服务监听窗口变化,在应用**冷启动的开屏页阶段**自动识别并点击「跳过」类控件,从而跳过开屏广告。核心设计目标:

- **精准**:仅在启动页时间窗口内扫描,区分冷启动与进程间切换(温启动),避免在普通界面误扫误点。
- **多级兜底**:李跳跳规则引擎 → 关键词匹配 → 图片启发式兜底,层层递进。
- **离线**:规则来自本地 assets 与 MMKV,无网络请求。
- **可反馈**:用户可标记「跳对/跳错」,跳错进入黑名单,形成闭环。
- **保活**:前台服务 + 1像素悬浮窗 + 开机自启 + 电池优化白名单引导。

---

## 2. 技术栈与构建

| 项目 | 说明 |
| --- | --- |
| 语言 | Kotlin |
| UI | Jetpack Compose + Material3 |
| 状态管理 | StateFlow(响应式) |
| 持久化 | MMKV |
| 序列化 | Kotlinx Serialization (JSON) |
| compileSdk / targetSdk / minSdk | 37 / 36 / 24 |
| applicationId | `com.newolf.jumpad` |
| Java 兼容 | 11 |

构建命令:

```bash
./gradlew :app:compileDebugKotlin -q   # 快速编译校验
./gradlew :app:assembleDebug           # 打包 debug APK
```

---

## 3. 权限清单(AndroidManifest.xml)

| 权限 | 用途 |
| --- | --- |
| `FOREGROUND_SERVICE` / `FOREGROUND_SERVICE_SPECIAL_USE` | 前台常驻服务提升存活率 |
| `POST_NOTIFICATIONS` | Android 13+ 通知运行时权限 |
| `QUERY_ALL_PACKAGES` | 列出全部已安装应用以按应用配置规则 |
| `RECEIVE_BOOT_COMPLETED` | 开机自启拉起前台服务 |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | 申请电池优化白名单 |
| `SYSTEM_ALERT_WINDOW` | 1像素透明悬浮窗保活 |

无障碍服务配置 `res/xml/accessibility_service_config.xml`:监听 `typeWindowStateChanged | typeWindowContentChanged`,启用 `flagRetrieveInteractiveWindows | flagReportViewIds`,`canPerformGestures=true`,`notificationTimeout=100`。

---

## 4. 架构分层

```
com.newolf.jumpad
├── MainActivity.kt            # Compose 入口、导航与状态提升
├── data/                      # 数据层
│   ├── AppInfo.kt             # 已安装应用加载 + 启动 Activity 解析
│   ├── SkipRule.kt            # 数据模型 + 内置默认规则
│   ├── RuleRepository.kt      # 单例仓库(MMKV + StateFlow)
│   └── LiTiaoTiaoRules.kt     # 从 assets 加载李跳跳规则
├── service/                   # 服务层
│   ├── SkipAdAccessibilityService.kt  # 核心:事件监听 + 三级跳过
│   ├── LiTiaoTiaoEngine.kt            # 李跳跳规则匹配引擎
│   ├── SkipForegroundService.kt       # 前台常驻服务 + 通知
│   ├── KeepAliveUtil.kt               # 保活工具(电池/悬浮窗/自启引导)
│   ├── PixelKeepAliveManager.kt       # 1像素悬浮窗保活
│   ├── BootReceiver.kt                # 开机自启广播接收
│   └── AccessibilityUtil.kt           # 无障碍服务检测/跳转
└── ui/                        # 界面层(Compose)
    ├── MainScreen.kt          # 主界面(总开关/入口/状态)
    ├── AppListScreen.kt       # 应用列表(逐应用开关)
    ├── AppDetailScreen.kt     # 单应用规则详情
    ├── SkipRecordScreen.kt    # 跳过记录(可标记对错)
    ├── UnmatchedRecordScreen.kt  # 未匹配记录
    ├── ImageSkipRecordScreen.kt  # 图片兜底记录
    ├── KeepAliveScreen.kt     # 保活设置
    ├── RuleComponents.kt      # 规则相关复用组件
    └── theme/                 # 主题
```

---

## 5. 核心工作流

### 5.1 事件监听
`SkipAdAccessibilityService` 监听 `TYPE_WINDOW_STATE_CHANGED` 与 `TYPE_WINDOW_CONTENT_CHANGED`。每次事件:
1. 校验总开关与逐应用开关(系统预装应用默认关闭)。
2. 通过 `updateSplashPhase` 维护「启动页会话」状态。
3. 若处于扫描窗口内,`handleWindow` 执行三级跳过策略。

### 5.2 启动页会话(Splash Session)
关键常量:
- `SPLASH_WINDOW_MS = 5000`:冷启动后 5 秒内视为启动页阶段,持续扫描。
- `MAX_SPLASH_HOP = 2`:图片兜底允许的从入口页起最大 Activity 跳转层数(覆盖 launcher→隐私弹窗→Splash)。
- `CLICK_THROTTLE_MS = 1500`:同一窗口只点一次,防连环误点。

会话状态字段:`splashPackage` / `splashWindowStart` / `matchedInSession` / `sessionLauncherActivity` / `activityHopCount`。

### 5.3 三级跳过策略(handleWindow)
1. **李跳跳规则引擎**(`LiTiaoTiaoEngine`):按 `PopupRule` 的 `id` 锚点判断弹窗是否出现,命中后点击 `action` 目标(或执行 `GLOBAL_ACTION_BACK`)。语法支持 `=精确`、`a&b` 与关系、包含匹配。
2. **关键词匹配**:遍历节点树(`MAX_DEPTH=40`),查找文案匹配「跳过」类关键词(`MAX_TEXT_LEN=12` 防误匹配)且可点击的控件,不可点击则向上找可点击父节点。
3. **图片兜底**:窗口结束前若仍未跳过,对「广告页特征」进行启发式点击(见第 7 节)。

---

## 6. 冷启动 vs 温启动判定

**问题背景**:Android 无法可靠查询其他应用进程是否存活(`getRunningAppProcesses` 仅返回自身)。JumpAd 需要只在「冷启动」时扫描广告,进程间切换(温启动)恢复的是上次停留界面,不应扫描。

**解决方案**:以「启动入口 Activity」(`getLaunchIntentForPackage` 目标)作为冷启动的**可观测代理信号**——真正冷启动总是从入口 Activity 进入,而进程间切换恢复的是上次停留的 Activity。

在 `updateSplashPhase` 的应用切换分支中:

```kotlin
val launcher = LauncherActivityResolver.launcherActivityOf(this, packageName)
val isColdStart = launcher == null || className == null || className == launcher
if (isColdStart) {
    // 开启启动页扫描会话
} else {
    // 温启动:清空 splashPackage,不扫描
}
```

- `launcher == null`(如输入法无启动图标)或 `className == null` 时**退化为原行为**,避免漏扫。
- `LauncherActivityResolver`(`AppInfo.kt`)带缓存解析启动 Activity 并判断类名是否为已注册 Activity。
- **非 Activity 覆盖窗口分支**(如百度网盘 FrameLayout 广告)不做冷启动门控,因其有独立特征校验守卫。

---

## 7. 图片兜底机制

当规则与关键词都未命中,在启动页会话末尾(`finishSession`)触发图片启发式点击。**广告页特征判据**:

- `AD_FULLSCREEN_MIN_RATIO = 0.6`:存在近全屏广告图/视频(主判据)。
- `AD_MAX_CLICKABLE = 25`:可见可点击控件数量上限(兜底防护,排除按钮众多的普通页)。

**「跳过按钮」候选控件过滤**:

- `IMG_MAX_AREA_RATIO = 0.15`:面积占屏比上限(排除广告大图/背景)。
- `IMG_MIN_SIDE_PX = 24`:最小边长(排除装饰点)。
- `IMG_MAX_SIDE_RATIO = 0.5`:单边最大占屏比。

且受 `MAX_SPLASH_HOP = 2` 约束:超过入口页 2 层跳转视为已进入主界面,不再兜底。

---

## 8. 数据持久化(RuleRepository)

单例仓库,MMKV 持久化 + StateFlow 响应式暴露。管理:

| 数据 | 说明 |
| --- | --- |
| `RuleConfig` | 全局配置:总开关 `globalEnabled`、前台服务 `foregroundEnabled`、1像素保活 `pixelKeepAliveEnabled`、跳过计数 `skipCount` |
| `SkipRecords` | 跳过成功记录(关键词/李跳跳),可标记对错 |
| `UnmatchedRecords` | 未匹配记录 |
| `ImageSkipRecords` | 图片兜底记录 |

**逐应用开关**:`isPackageEnabled` / `setPackageEnabled`,默认**系统应用关、用户应用开**。

数据模型(`SkipRule.kt`):`SkipRule`、`RuleConfig`、`SkipRecord`、`UnmatchedRecord`、`ImageSkipRecord`,以及内置 `DefaultRules`(通用跳过关键词 + 全局规则 `builtin_global`)。

---

## 9. 反馈闭环与黑名单

用户在「跳过记录」界面可对每条记录标记**跳对 / 跳错**:

- `setSkipCorrected(...)`:记录人工标记。
- 标记为「跳错」→ 写入 `skipBlacklist`(格式 `"包名|matchKey"`)。
- `isSkipBlacklisted(...)`:后续扫描命中黑名单则「下次不这样跳」,形成闭环,减少误点。

---

## 10. 保活方案

| 手段 | 实现 | 说明 |
| --- | --- | --- |
| 前台常驻服务 | `SkipForegroundService` | 常驻通知展示状态与累计跳过次数,`START_STICKY` 被杀后重建 |
| 1像素悬浮窗 | `PixelKeepAliveManager` | 锁屏(熄屏)时添加 1×1 透明窗口,亮屏移除;需悬浮窗权限,高版本效果有限 |
| 屏幕广播 | `SkipForegroundService.screenReceiver` | 监听 `SCREEN_ON/OFF` 控制 1像素窗口显隐 |
| 开机自启 | `BootReceiver` | 监听 `BOOT_COMPLETED`,若用户开启前台服务则开机拉起 |
| 电池优化白名单 | `KeepAliveUtil` | 检测 + 引导申请忽略电池优化 |
| 权限引导 | `KeepAliveUtil` / `AccessibilityUtil` | 悬浮窗、无障碍、厂商自启统一回退到应用详情页 |

所有保活手段仅「检测 + 引导授权」,不强制修改系统设置。

---

## 11. UI 界面(Jetpack Compose)

- **MainScreen**:总开关、无障碍/前台服务状态、各功能入口。
- **AppListScreen**:全部已安装应用列表 + 逐应用开关。列表在 `MainActivity` 的 `ON_RESUME` 生命周期回调中协程后台刷新(`InstalledAppLoader.loadInstalledApps`),新装/卸载即时反映,旧列表展示至刷新完成避免白屏。
- **AppDetailScreen**:单应用规则详情。
- **SkipRecordScreen / UnmatchedRecordScreen / ImageSkipRecordScreen**:三类记录浏览与反馈标记。
- **KeepAliveScreen**:保活相关权限设置与引导。

---

## 12. 应用列表加载(AppInfo.kt)

- `InstalledAppLoader.loadInstalledApps`:用 `getInstalledApplications(0)` 列出全部包(含无启动图标的输入法等)。
- `LauncherActivityResolver`:带缓存解析启动 Activity,供冷启动判定使用。

---

## 13. 调试工具

`tools/` 目录提供辅助脚本:
- `jumpad_test.sh`:测试脚本。
- `ad_dump.xml`:广告页节点树 dump 样本。
- `jumpad_logs/`:日志目录。

日志统一使用 TAG `"JumpAd"`,可通过 `adb logcat -s JumpAd` 过滤观察规则命中、冷启动判定与兜底触发。