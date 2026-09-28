---
name: 跳过记录对错反馈与黑名单机制
description: JumpAd 普通跳过记录(关键词/李跳跳)的跳对跳错人工标记及"下次不这样跳"黑名单闭环
type: project
---

普通跳过(非图片兜底)也支持"跳对了/跳错了"人工反馈,标记跳错则下次不再用该方式跳该应用。

**数据:** SkipRecord 增 matchKey(方式唯一标识)+corrected(null/true/false);RuleConfig 增 skipBlacklist(元素"包名|matchKey")。matchKey:李跳跳="li:规则id",关键词="kw:命中控件文案"。

**闭环:** RuleRepository.setSkipCorrected(ts,corrected) 联动黑名单——跳错入、跳对/清除出;isSkipBlacklisted(pkg,matchKey) 供服务查询。Service.handleWindow:李跳跳先 filter 掉黑名单规则再 apply;关键词命中后读控件文案判黑名单,命中则本次不点。图片兜底沿用自身 corrected+bounds 黑白名单(独立)。

**UI:** SkipRecordScreen 每条加"跳对了/跳错了/清除"按钮+核对状态,复用 ImageSkipRecordScreen 模式。