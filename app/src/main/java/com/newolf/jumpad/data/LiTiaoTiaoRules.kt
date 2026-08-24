package com.newolf.jumpad.data

import android.content.Context
import android.util.Log
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * 李跳跳(LiTiaotiao)规则的运行时表示。
 *
 * 规则来源:参考 https://github.com/rongzhiy/LiTiaotiao 的 assets/AllRules.json。
 * 每条规则的语义:当界面上出现命中 [id] 的锚点(文本或控件 id)时,
 * 查找并对 [action] 指向的目标(文本 / 控件 id / 特殊动作)执行点击。
 *
 * id / action 语法(与李跳跳保持一致):
 * - 普通字符串:控件的 viewIdResourceName 或可见文本(包含匹配)
 * - "=xxx"     :精确匹配(文本或 viewId 完全相等)
 * - "a&b"      :与关系,多个条件需同时满足(仅用于 id 锚点判断)
 * - "GLOBAL_ACTION_BACK":执行返回键(action 专用)
 *
 * @param id     触发锚点(界面需满足)
 * @param action 命中后要点击的目标
 * @param times  可选:限制点击次数(为空表示不限制)
 */
data class PopupRule(
    val id: String,
    val action: String,
    val times: Int? = null
)

/**
 * 李跳跳规则加载器:从 assets/AllRules.json 读取并解析为扁平的 [PopupRule] 列表。
 *
 * AllRules.json 结构为:
 * ```
 * [ { "<hash>": "<被转义的 JSON 字符串>" }, ... ]
 * ```
 * 内层 JSON 含 popup_rules / unite_popup_rules 数组。由于原始 hash 是李跳跳
 * 私有的界面指纹(无法在运行时复现),这里采用实用策略:合并全部规则并去重,
 * 运行时对每条规则检查"锚点是否存在"来决定是否执行。
 *
 * 完全离线:仅读取本地 assets,不访问网络。
 */
object LiTiaoTiaoRules {

    private const val TAG = "JumpAd"
    private const val ASSET_NAME = "AllRules.json"

    private val json = Json { ignoreUnknownKeys = true }

    @Volatile
    private var cache: List<PopupRule>? = null

    /** 加载并缓存全部李跳跳规则(幂等)。 */
    fun load(context: Context): List<PopupRule> {
        cache?.let { return it }
        synchronized(this) {
            cache?.let { return it }
            val parsed = runCatching { parse(context) }
                .getOrElse {
                    Log.e(TAG, "加载 AllRules.json 失败: ${it.message}")
                    emptyList()
                }
            cache = parsed
            Log.d(TAG, "已加载李跳跳规则 ${parsed.size} 条")
            return parsed
        }
    }

    /** 已加载的规则数量(未加载时为 0)。 */
    fun count(): Int = cache?.size ?: 0

    private fun parse(context: Context): List<PopupRule> {
        val raw = context.assets.open(ASSET_NAME).bufferedReader().use { it.readText() }
        val topArray = json.parseToJsonElement(raw).jsonArray

        val result = LinkedHashMap<String, PopupRule>()
        for (entry in topArray) {
            val obj = entry.jsonObject
            for ((_, payloadElement) in obj) {
                val payloadStr = payloadElement.jsonPrimitive.content
                val inner = runCatching { json.parseToJsonElement(payloadStr).jsonObject }
                    .getOrNull() ?: continue

                collectRules(inner["popup_rules"], result)
                collectRules(inner["unite_popup_rules"], result)
            }
        }
        return result.values.toList()
    }

    private fun collectRules(
        element: kotlinx.serialization.json.JsonElement?,
        out: LinkedHashMap<String, PopupRule>
    ) {
        val arr = element?.let { runCatching { it.jsonArray }.getOrNull() } ?: return
        for (item in arr) {
            val ruleObj = runCatching { item.jsonObject }.getOrNull() ?: continue
            val id = ruleObj["id"]?.jsonPrimitive?.content?.trim().orEmpty()
            val action = ruleObj["action"]?.jsonPrimitive?.content?.trim().orEmpty()
            if (id.isBlank() || action.isBlank()) continue
            val times = ruleObj["times"]?.jsonPrimitive?.content?.toIntOrNull()
            val key = "$id=>$action"
            if (!out.containsKey(key)) {
                out[key] = PopupRule(id = id, action = action, times = times)
            }
        }
    }
}