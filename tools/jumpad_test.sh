#!/bin/bash
# JumpAd 开屏广告跳过测试脚本
# 逐个冷启动应用,采集无障碍服务日志,判定"跳过成功/未匹配/无广告"
set -u

D="7XVKZ5MJWCJFPRYL"
ADB="adb -s $D"
WAIT=8          # 每个应用启动后等待秒数(覆盖开屏广告展示+5s扫描窗口)
OUT_DIR="tools/jumpad_logs"
SUMMARY="$OUT_DIR/summary.txt"
mkdir -p "$OUT_DIR"
: > "$SUMMARY"

# 待测应用列表(典型带开屏广告)
APPS=(
  "com.greenpoint.android.mc10086.activity 中国移动10086"
  "com.jingdong.app.mall 京东"
  "com.xunmeng.pinduoduo 拼多多"
  "com.taobao.taobao 淘宝"
  "com.taobao.idlefish 闲鱼"
  "com.sankuai.meituan 美团"
  "com.dianping.v1 大众点评"
  "com.xingin.xhs 小红书"
  "com.zhihu.android 知乎"
  "tv.danmaku.bili 哔哩哔哩"
  "com.smile.gifmaker 快手"
  "com.qiyi.video 爱奇艺"
  "com.tencent.qqlive 腾讯视频"
  "com.baidu.searchbox 百度"
  "com.quark.browser 夸克"
  "com.kmxs.reader 七猫小说"
  "com.duokan.reader 多看阅读"
  "com.hpbr.bosszhipin BOSS直聘"
  "com.zhaopin.social 智联招聘"
  "com.job.android 前程无忧"
  "com.lietou.mishu 猎聘"
  "com.anjuke.android.app 安居客"
  "com.autonavi.minimap 高德地图"
  "com.sdu.didi.psnger 滴滴出行"
  "com.xunlei.downloadprovider 迅雷"
  "com.baidu.netdisk 百度网盘"
  "com.xiaomi.shop 小米商城"
  "com.tencent.qqmusic QQ音乐"
)

echo "===== JumpAd 开屏广告跳过测试 $(date '+%F %T') =====" | tee -a "$SUMMARY"

for entry in "${APPS[@]}"; do
  pkg="${entry%% *}"
  name="${entry#* }"
  # 跳过未安装的
  if ! $ADB shell pm path "$pkg" >/dev/null 2>&1; then
    echo "[跳过] $name ($pkg) 未安装" | tee -a "$SUMMARY"
    continue
  fi

  # 强停+清日志缓冲,确保冷启动
  $ADB shell am force-stop "$pkg" >/dev/null 2>&1
  sleep 1
  $ADB logcat -c >/dev/null 2>&1

  # 冷启动
  $ADB shell monkey -p "$pkg" -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1
  sleep "$WAIT"

  # 抓取本次 JumpAd 日志
  raw="$OUT_DIR/${pkg}.log"
  $ADB logcat -d -s JumpAd:* > "$raw" 2>/dev/null

  # 仅保留与该包相关的行
  applog=$(grep "$pkg" "$raw")

  # 判定结果
  if echo "$applog" | grep -q "已按李跳跳规则跳过广告\|已跳过广告"; then
    result="✅ 跳过成功"
  elif echo "$applog" | grep -q "记录待适配\|未匹配到跳过按钮"; then
    result="❌ 未适配(扫描窗口内未命中)"
  elif echo "$applog" | grep -q "开启启动页扫描窗口\|扫描窗口内 Activity"; then
    result="⚠️ 进入扫描窗口但未跳过(超时/无广告)"
  else
    result="➖ 无窗口事件(可能无开屏广告/启动失败)"
  fi

  echo "----- $name ($pkg): $result" | tee -a "$SUMMARY"
  # 附关键日志行
  echo "$applog" | grep "开启启动页扫描窗口\|已按李跳跳规则跳过广告\|已跳过广告\|记录待适配\|结束扫描窗口" | sed 's/^/    /' | tee -a "$SUMMARY"

  # 回桌面
  $ADB shell am start -a android.intent.action.MAIN -c android.intent.category.HOME >/dev/null 2>&1
  $ADB shell am force-stop "$pkg" >/dev/null 2>&1
  sleep 1
done

echo "" | tee -a "$SUMMARY"
echo "===== 测试完成,详见 $OUT_DIR/ =====" | tee -a "$SUMMARY"