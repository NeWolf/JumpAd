package com.newolf.jumpad.service

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.newolf.jumpad.data.PopupRule

/**
 * 李跳跳规则匹配引擎。
 *
 * 负责将 [PopupRule] 的 id / action 语法作用到当前窗口的节点树上:
 * 1. 先判断 id 锚点条件是否成立(界面是否出现该规则针对的弹窗/广告);
 * 2. 若成立,查找 action 目标节点并执行点击(或特殊动作)。
 *
 * 语法约定(与李跳跳一致):
 * - 普通串:匹配控件的可见文本 或 viewIdResourceName 的短名(包含匹配)
 * - "=xxx" :精确匹配(文本或 viewId 短名完全相等)
 * - "a&b"  :与关系,拆分为多个子条件,均需命中(id 锚点判断用)
 * - "GLOBAL_ACTION_BACK":执行系统返回键(action 专用)
 */
class LiTiaoTiaoEngine(private val service: AccessibilityService) {

    companion object {
        private const val MAX_DEPTH = 45
        private const val GLOBAL_BACK = "GLOBAL_ACTION_BACK"
        private const val TAG = "JumpAd"
    }

    /**
     * 在给定根节点上尝试应用规则列表。命中并执行成功返回命中的规则,否则返回 null。
     * 一次仅执行一条(第一条命中的)规则,避免连环误点。
     */
    fun apply(root: AccessibilityNodeInfo, rules: List<PopupRule>): PopupRule? {
        // 采集当前窗口所有节点的文本 / viewId,用于快速判断 id 锚点是否存在。
        val index = buildIndex(root)

        for (rule in rules) {
            if (!isAnchorPresent(rule.id, index)) continue

            Log.d(TAG, "命中李跳跳规则锚点: id=${rule.id} -> action=${rule.action}")

            // 特殊动作:返回键。
            if (rule.action == GLOBAL_BACK) {
                if (service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)) {
                    Log.d(TAG, "执行返回键成功: id=${rule.id}")
                    return rule
                }
                Log.d(TAG, "执行返回键失败: id=${rule.id}")
                continue
            }

            // 查找 action 目标并点击。
            val target = findTarget(root, rule.action, 0)
            if (target != null) {
                val clicked = clickNode(target)
                if (target !== root) target.recycle()
                if (clicked) {
                    Log.d(TAG, "点击目标成功: action=${rule.action}")
                    return rule
                }
                Log.d(TAG, "点击目标失败: action=${rule.action}")
            } else {
                Log.d(TAG, "未找到 action 目标节点: action=${rule.action}")
            }
        }
        return null
    }

    /** 界面索引:收集全部可见文本与 viewId 短名。 */
    private data class ScreenIndex(val texts: Set<String>, val viewIds: Set<String>)

    private fun buildIndex(root: AccessibilityNodeInfo): ScreenIndex {
        val texts = HashSet<String>()
        val viewIds = HashSet<String>()
        collect(root, 0, texts, viewIds)
        return ScreenIndex(texts, viewIds)
    }

    private fun collect(
        node: AccessibilityNodeInfo?,
        depth: Int,
        texts: MutableSet<String>,
        viewIds: MutableSet<String>
    ) {
        if (node == null || depth > MAX_DEPTH) return
        node.text?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let { texts.add(it) }
        node.contentDescription?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let { texts.add(it) }
        shortId(node.viewIdResourceName)?.let { viewIds.add(it) }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            collect(child, depth + 1, texts, viewIds)
            child.recycle()
        }
    }

    /** 判断某个 id 锚点(支持 & 与、= 精确)是否在界面上存在。 */
    private fun isAnchorPresent(rawId: String, index: ScreenIndex): Boolean {
        val conditions = rawId.split("&").map { it.trim() }.filter { it.isNotEmpty() }
        if (conditions.isEmpty()) return false
        return conditions.all { cond -> matchIndex(cond, index) }
    }

    private fun matchIndex(cond: String, index: ScreenIndex): Boolean {
        val exact = cond.startsWith("=")
        val value = if (exact) cond.substring(1).trim() else cond
        if (value.isEmpty()) return false
        return if (exact) {
            index.texts.any { it == value } || index.viewIds.any { it == value }
        } else {
            index.texts.any { it.contains(value, ignoreCase = true) } ||
                index.viewIds.any { it.contains(value, ignoreCase = true) }
        }
    }

    /**
     * 在节点树中查找 action 目标节点(优先返回可点击节点)。
     * action 支持普通串 / "=精确"。若含 &,取第一个子条件作为目标。
   */
    private fun findTarget(
        node: AccessibilityNodeInfo?,
        rawAction: String,
        depth: Int
    ): AccessibilityNodeInfo? {
        if (node == null || depth > MAX_DEPTH) return null
        val target = rawAction.split("&").first().trim()
        val exact = target.startsWith("=")
        val value = if (exact) target.substring(1).trim() else target
        if (value.isEmpty()) return null

        if (nodeMatches(node, value, exact)) {
            return findClickable(node)
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val res = findTarget(child, rawAction, depth + 1)
            if (res != null) {
                if (res !== child) child.recycle()
                return res
            }
            child.recycle()
        }
        return null
    }

    private fun nodeMatches(node: AccessibilityNodeInfo, value: String, exact: Boolean): Boolean {
        val text = node.text?.toString()?.trim()
        val desc = node.contentDescription?.toString()?.trim()
        val vid = shortId(node.viewIdResourceName)
        return if (exact) {
            text == value || desc == value || vid == value
        } else {
            (text?.contains(value, ignoreCase = true) == true) ||
                (desc?.contains(value, ignoreCase = true) == true) ||
                (vid?.contains(value, ignoreCase = true) == true)
        }
    }

    /** 向上查找最近的可点击节点;找不到则返回自身。 */
    private fun findClickable(node: AccessibilityNodeInfo): AccessibilityNodeInfo {
        var current: AccessibilityNodeInfo? = node
        var level = 0
        while (current != null && level < 8) {
            if (current.isClickable) return current
            val parent = current.parent
            if (current !== node) current.recycle()
            current = parent
            level++
        }
        return node
    }

    private fun clickNode(node: AccessibilityNodeInfo): Boolean {
        return node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
    }

    /** 取 viewIdResourceName 的短名(去掉包名前缀)。 */
    private fun shortId(viewId: String?): String? {
        if (viewId.isNullOrBlank()) return null
        val idx = viewId.indexOf('/')
        val short = if (idx >= 0) viewId.substring(idx + 1) else viewId
        return short.trim().takeIf { it.isNotEmpty() }
    }
}