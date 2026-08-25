---
name: 图片型跳过按钮兜底功能
description: JumpAd 图片跳过兜底、多窗口根节点与 FrameLayout 覆盖窗口会话根因修复及排查线索
type: project
---

针对"跳过按钮为图片、无法用文字判断"的场景,纯启发式兜底点击+截图+记录(完全离线)。

**图片兜底:** finishSession(5s 超时且文字/李跳跳未命中)触发 tryImageSkip;打分角落+3/小+2/desc含跳过skip关闭+4/含Image+1,score<=0 放弃,排除面积>0.15、单边>屏0.5、边长<24px;detectAdPageFeature 需近全屏控件(>=0.6)且可点击<=25 防误点;corrected 点错入黑名单、点对入白名单。

**根因一 多窗口根节点(已修复):** 广告独立覆盖窗口弹出,rootInActiveWindow 只返回活动窗口→漏点。修复:collectWindowRoots(pkg) 用 getWindows() 遍历该包所有窗口根;handleWindow/tryImageSkip 逐窗口尝试。

**根因二 FrameLayout 覆盖窗口会话(已修复):** 百度网盘广告以控件级 FrameLayout 覆盖窗口弹出、无 Activity 事件→旧 updateSplashPhase 非 Activity 事件直接 return,会话开不起来,handleWindow 被 packageName!=splashPackage 守卫拦下(日志仅"忽略非 Activity 事件");广告常 5s 后弹、初始会话已进 finishedPackages。修复(非 Activity 分支):会话内维持;否则不受 finishedPackages 限制、remove 后重开,靠 SPLASH_WINDOW_MS 节流,点击仍由特征校验把关。

**验证:** 必须 `am start -n com.baidu.netdisk/.ui.DefaultMainActivity`(monkey 无效);见"非 Activity 覆盖窗口开启扫描会话"后多次"已跳过广告",普通界面未误触。广告概率下发需多次冷启动。