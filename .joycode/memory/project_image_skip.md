---
name: 图片型跳过按钮兜底功能
description: JumpAd 针对百度网盘/一刻相册等图片跳过按钮的启发式兜底方案与后续待办
type: project
---

针对"跳过按钮做成图片、无法用文字判断"的场景，采用纯启发式兜底点击 + 截图 + 记录方案（完全离线）。

**Why:** 无障碍 API 只能拿 className/bounds/clickable/contentDescription，拿不到图片像素，无法做图像识别，只能启发式猜测。用户明确选择"小图+角落位置+可点击就点击，并记录+截图供人工核对"。

**How to apply:**
- 触发时机：finishSession（5s 窗口超时且文字/李跳跳规则均未命中）时先尝试 tryImageSkip，成功则计数+记录，否则 recordUnmatched。
- 打分维度：屏幕角落(右上/右下+3)、尺寸小(面积占屏<0.05+2)、描述含跳过/skip/关闭+4、className含Image+1；score<=0 放弃点击（保守，可能漏点，需真机调参）。
- 排除：面积占屏>0.15、单边>屏0.5、边长<24px。
- 截图：API30+ takeScreenshot→Bitmap.wrapHardwareBuffer→存 filesDir/image_skip_shots（takeScreenshot 有约每秒1次频率限制，已用 runCatching 兜底）。
- ImageSkipRecord.corrected 字段(点对/点错/未核对)已接入修正闭环：点错过的 bounds 加黑名单(排除)、点对过的 bounds 加白名单(得分+100 优先点击)，用 boundsKey(Rect→"l,t,r,b") 比对。

**已完成 UI：** ①ImageSkipRecordScreen 记录查看界面(截图预览+bounds+score+点对/点错/清除标记)；②MainScreen 加"图片兜底记录"入口卡片 + GlobalSwitchCard 加"图片跳过兜底"开关；③清空记录入口(TopAppBar)。RuleRepository.setImageSkipCorrected(timestamp, corrected) 以 timestamp 为唯一键写入。