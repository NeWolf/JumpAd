package com.newolf.jumpad.data

import kotlinx.serialization.Serializable

/**
 * 跳过开屏广告的规则。
 *
 * 借鉴李跳跳的规则思路:通过匹配屏幕上"跳过"类文案的可点击控件来自动点击跳过广告。
 * 应用完全离线,规则通过 MMKV 本地保存。
 *
 * @param id           规则唯一标识
 * @param name         规则名称(便于用户识别)
 * @param packageName  作用的应用包名;为空("")表示对所有应用生效(全局规则)
 * @param keywords     需要匹配的文案关键词列表(命中任意一个即尝试点击)
 * @param enabled      是否启用该规则
 * @param builtin      是否为内置默认规则(内置规则可禁用但不建议删除)
 */
@Serializable
data class SkipRule(
    val id: String,
    val name: String,
    val packageName: String = "",
    val keywords: List<String> = emptyList(),
    val enabled: Boolean = true,
    val builtin: Boolean = false
)

/**
 * 规则集合的持久化容器。
 *
 * @param globalEnabled  跳广告总开关
 * @param rules          用户/内置规则列表
 * @param showSkipToast  每次跳过广告时是否弹出提示
 * @param skipCount      累计跳过广告次数(统计用)
 * @param foregroundEnabled     前台常驻服务开关(开机自启依据此项)
 * @param pixelKeepAliveEnabled 1像素透明悬浮窗保活开关(随锁屏显隐)
 * @param enabledPackages       用户显式"开启"跳过的包名集合(覆盖默认关闭,如手动开启的系统应用)
 * @param disabledPackages      用户显式"关闭"跳过的包名集合(覆盖默认开启,如手动关闭的用户应用)
 * @param imageSkipEnabled      图片跳过兜底开关:文字/规则均未命中时,尝试点击疑似"图片跳过按钮"(小图+角落)
 */
@Serializable
data class RuleConfig(
    val globalEnabled: Boolean = true,
    val rules: List<SkipRule> = emptyList(),
    val showSkipToast: Boolean = true,
    val skipCount: Long = 0L,
    val foregroundEnabled: Boolean = false,
    val pixelKeepAliveEnabled: Boolean = false,
    val enabledPackages: Set<String> = emptySet(),
    val disabledPackages: Set<String> = emptySet(),
    val imageSkipEnabled: Boolean = true
)

/**
 * 单条跳过广告记录(明细)。
 *
 * @param packageName 被跳过广告的应用包名
 * @param appName     应用名称(用户可读,解析失败时回退为包名)
 * @param timestamp   跳过发生的时间戳(毫秒)
 * @param method      跳过方式描述(如"李跳跳规则"、"关键词匹配")
 */
@Serializable
data class SkipRecord(
    val packageName: String,
    val appName: String,
    val timestamp: Long,
    val method: String
)

/** 跳过记录集合的持久化容器。 */
@Serializable
data class SkipRecords(
    val items: List<SkipRecord> = emptyList()
)

/**
 * 未匹配记录:某应用在启动页阶段结束时仍未成功跳过,单独记录以便后续适配规则。
 *
 * @param packageName 应用包名
 * @param appName     应用名称
 * @param timestamp   记录时间(毫秒)
 * @param activity    启动页 Activity 类名(可能为空,便于适配时定位)
 */
@Serializable
data class UnmatchedRecord(
    val packageName: String,
    val appName: String,
    val timestamp: Long,
    val activity: String? = null
)

/** 未匹配记录集合的持久化容器。 */
@Serializable
data class UnmatchedRecords(
    val items: List<UnmatchedRecord> = emptyList()
)

/**
 * 图片跳过兜底记录:文字/规则均未命中时,通过启发式(小图+角落+可点击)点击了某个疑似跳过按钮。
 *
 * 记录点击控件的特征,便于人工核对是否点对了(配合截图),以及后续修正启发式或沉淀为专属规则。
 *
 * @param packageName 应用包名
 * @param appName     应用名称
 * @param timestamp   点击时间(毫秒)
 * @param activity    启动页 Activity 类名(可能为空)
 * @param className   被点击控件的类名(如 android.widget.ImageView)
 * @param bounds      被点击控件在屏幕上的位置矩形(格式:left,top,right,bottom)
 * @param score       启发式得分(越高越可能是跳过按钮),便于调参核对
 * @param screenshot  截图文件路径(私有目录,可能为空:设备不支持或截图失败)
 * @param corrected   人工核对结论:true=点对了,false=点错了,null=尚未核对(供后续修正)
 */
@Serializable
data class ImageSkipRecord(
    val packageName: String,
    val appName: String,
    val timestamp: Long,
    val activity: String? = null,
    val className: String? = null,
    val bounds: String? = null,
    val score: Int = 0,
    val screenshot: String? = null,
    val corrected: Boolean? = null
)

/** 图片跳过兜底记录集合的持久化容器。 */
@Serializable
data class ImageSkipRecords(
    val items: List<ImageSkipRecord> = emptyList()
)

/**
 * 内置的默认关键词与规则,复用李跳跳常见的通用跳过文案。
 */
object DefaultRules {

    /** 通用跳过文案关键词(适用于绝大多数开屏广告)。 */
    val COMMON_KEYWORDS: List<String> = listOf(
        "跳过",
        "跳过广告",
        "跳过 广告",
        "点击跳过",
        "跳过 >",
        "关闭广告",
        "Skip",
        "skip",
        "跳过 %d",
        "跳过 %ds",
        "秒后跳过"
    )

    /** 默认的全局规则:对所有应用生效,匹配通用跳过文案。 */
    fun defaultConfig(): RuleConfig = RuleConfig(
        globalEnabled = true,
        rules = listOf(
            SkipRule(
                id = "builtin_global",
                name = "通用跳过(全局)",
                packageName = "",
                keywords = COMMON_KEYWORDS,
                enabled = true,
                builtin = true
            )
        )
    )
}