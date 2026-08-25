package com.newolf.jumpad.data

import android.content.Context
import com.tencent.mmkv.MMKV
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.Json

/**
 * 基于 MMKV 的规则仓库,负责规则的本地持久化读写。
 *
 * 采用单例 + StateFlow,便于 UI 与无障碍服务共享同一份规则数据。
 * 完全离线,不涉及任何网络访问。
 */
object RuleRepository {

    private const val MMKV_ID = "jumpad_rules"
    private const val KEY_CONFIG = "rule_config"
    private const val KEY_RECORDS = "skip_records"
    private const val KEY_UNMATCHED = "unmatched_records"
    private const val KEY_IMAGE_SKIP = "image_skip_records"

    /** 跳过记录最大保留条数,超出后丢弃最旧记录,避免无限增长。 */
    private const val MAX_RECORDS = 500

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = false
        encodeDefaults = true
    }

    @Volatile
    private var mmkv: MMKV? = null

    private val _config = MutableStateFlow(DefaultRules.defaultConfig())

    /** 对外暴露的只读规则状态流。 */
    val config: StateFlow<RuleConfig> = _config

    private val _skipRecords = MutableStateFlow(SkipRecords())

    /** 对外暴露的只读跳过记录状态流(最新的在前)。 */
    val skipRecords: StateFlow<SkipRecords> = _skipRecords

    private val _unmatchedRecords = MutableStateFlow(UnmatchedRecords())

    /** 对外暴露的只读未匹配记录状态流(最新的在前,按包名去重)。 */
    val unmatchedRecords: StateFlow<UnmatchedRecords> = _unmatchedRecords

    private val _imageSkipRecords = MutableStateFlow(ImageSkipRecords())

    /** 对外暴露的只读图片兜底点击记录状态流(最新的在前)。 */
    val imageSkipRecords: StateFlow<ImageSkipRecords> = _imageSkipRecords

    /** 已加载的李跳跳规则列表(来自 assets/AllRules.json,只读)。 */
    @Volatile
    private var liTiaoTiaoRules: List<PopupRule> = emptyList()

    /** 李跳跳规则数量状态流,供 UI 展示。 */
    private val _liTiaoTiaoCount = MutableStateFlow(0)
    val liTiaoTiaoCount: StateFlow<Int> = _liTiaoTiaoCount

    /** 供无障碍服务读取的李跳跳规则列表。 */
    fun liTiaoTiaoRules(): List<PopupRule> = liTiaoTiaoRules

    /**
     * 初始化 MMKV 并加载本地规则。应在 Application / Service 启动时调用。
     * 允许重复调用(幂等)。
     */
    fun init(context: Context) {
        if (mmkv != null) return
        synchronized(this) {
            if (mmkv != null) return
            MMKV.initialize(context.applicationContext)
            mmkv = MMKV.mmkvWithID(MMKV_ID)
            _config.value = load()
            _skipRecords.value = loadRecords()
            _unmatchedRecords.value = loadUnmatched()
            _imageSkipRecords.value = loadImageSkip()
            // 加载李跳跳规则(assets 本地文件,离线)。
            liTiaoTiaoRules = LiTiaoTiaoRules.load(context.applicationContext)
            _liTiaoTiaoCount.value = liTiaoTiaoRules.size
        }
    }

    private fun kv(): MMKV = mmkv
        ?: throw IllegalStateException("RuleRepository 未初始化,请先调用 init(context)")

    /** 从 MMKV 读取规则,若不存在则返回默认规则并写回。 */
    private fun load(): RuleConfig {
        val raw = kv().decodeString(KEY_CONFIG)
        return if (raw.isNullOrBlank()) {
            val default = DefaultRules.defaultConfig()
            persist(default)
            default
        } else {
            runCatching { json.decodeFromString<RuleConfig>(raw) }
                .getOrElse {
                    val default = DefaultRules.defaultConfig()
                    persist(default)
                    default
                }
        }
    }

    private fun persist(config: RuleConfig) {
        kv().encode(KEY_CONFIG, json.encodeToString(RuleConfig.serializer(), config))
    }

    private fun update(newConfig: RuleConfig) {
        persist(newConfig)
        _config.value = newConfig
    }

    /** 供无障碍服务同步读取最新规则(避免依赖 Flow 收集时机)。 */
    fun currentConfig(): RuleConfig = _config.value

    /** 设置全局总开关。 */
    fun setGlobalEnabled(enabled: Boolean) {
        update(_config.value.copy(globalEnabled = enabled))
    }

    /** 新增或更新一条规则(按 id 判断)。 */
    fun upsertRule(rule: SkipRule) {
        val list = _config.value.rules.toMutableList()
        val index = list.indexOfFirst { it.id == rule.id }
        if (index >= 0) list[index] = rule else list.add(rule)
        update(_config.value.copy(rules = list))
    }

    /** 切换某条规则的启用状态。 */
    fun toggleRule(id: String, enabled: Boolean) {
        val list = _config.value.rules.map {
            if (it.id == id) it.copy(enabled = enabled) else it
        }
        update(_config.value.copy(rules = list))
    }

    /** 删除一条规则(内置规则不允许删除)。 */
    fun deleteRule(id: String) {
        val list = _config.value.rules.filterNot { it.id == id && !it.builtin }
        update(_config.value.copy(rules = list))
    }

    /** 设置"跳过时弹出提示"开关。 */
    fun setShowSkipToast(show: Boolean) {
        update(_config.value.copy(showSkipToast = show))
    }

    /** 设置前台常驻服务开关(开机自启依据此项)。 */
    fun setForegroundEnabled(enabled: Boolean) {
        update(_config.value.copy(foregroundEnabled = enabled))
    }

    /** 设置 1像素透明悬浮窗保活开关。 */
    fun setPixelKeepAliveEnabled(enabled: Boolean) {
        update(_config.value.copy(pixelKeepAliveEnabled = enabled))
    }

    /** 设置图片跳过兜底开关(文字/规则未命中时启发式点击疑似跳过图片)。 */
    fun setImageSkipEnabled(enabled: Boolean) {
        update(_config.value.copy(imageSkipEnabled = enabled))
    }

    // ---------------- 每应用跳过开关 ----------------

    /**
     * 判断某个应用当前是否开启跳过扫描。
     *
     * 判定优先级:用户显式设置 > 默认值。
     * - 命中 [RuleConfig.disabledPackages] → 关闭(用户主动关闭)
     * - 命中 [RuleConfig.enabledPackages] → 开启(用户主动开启)
     * - 否则按默认:系统应用默认关闭,用户应用默认开启
     *
     * @param isSystem 是否为系统应用(决定默认值)
     */
    fun isPackageEnabled(packageName: String, isSystem: Boolean): Boolean {
        val cfg = _config.value
        return when {
            cfg.disabledPackages.contains(packageName) -> false
            cfg.enabledPackages.contains(packageName) -> true
            else -> !isSystem // 默认:系统应用关,用户应用开
        }
    }

    /**
     * 设置某个应用的跳过开关(用户在详情页/列表页操作)。
     *
     * 若设置值恰好等于默认值,则从两个集合中移除该包名(保持配置精简,回归默认行为);
     * 否则写入对应集合并从另一集合移除。
     *
     * @param enabled  目标开关状态
     * @param isSystem 是否为系统应用(用于判断是否等于默认值)
     */
    fun setPackageEnabled(packageName: String, enabled: Boolean, isSystem: Boolean) {
        val cfg = _config.value
        val enabledSet = cfg.enabledPackages.toMutableSet()
        val disabledSet = cfg.disabledPackages.toMutableSet()
        enabledSet.remove(packageName)
        disabledSet.remove(packageName)
        val default = !isSystem
        if (enabled != default) {
            // 与默认不同,需显式记录。
            if (enabled) enabledSet.add(packageName) else disabledSet.add(packageName)
        }
        update(cfg.copy(enabledPackages = enabledSet, disabledPackages = disabledSet))
    }

    /** 跳过成功后累加计数(供无障碍服务调用)。 */
    fun incrementSkipCount() {
        update(_config.value.copy(skipCount = _config.value.skipCount + 1))
    }

    /** 清零跳过计数。 */
    fun resetSkipCount() {
        update(_config.value.copy(skipCount = 0L))
    }

    // ---------------- 跳过记录(明细) ----------------

    /** 从 MMKV 读取跳过记录。 */
    private fun loadRecords(): SkipRecords {
        val raw = kv().decodeString(KEY_RECORDS)
        return if (raw.isNullOrBlank()) {
            SkipRecords()
        } else {
            runCatching { json.decodeFromString<SkipRecords>(raw) }.getOrElse { SkipRecords() }
        }
    }

    private fun persistRecords(records: SkipRecords) {
        kv().encode(KEY_RECORDS, json.encodeToString(SkipRecords.serializer(), records))
    }

    /**
     * 新增一条跳过记录(供无障碍服务调用)。最新的记录排在最前面,超出上限时丢弃最旧的。
     */
    fun addSkipRecord(record: SkipRecord) {
        val current = _skipRecords.value.items
        val merged = (listOf(record) + current).take(MAX_RECORDS)
        val newRecords = SkipRecords(items = merged)
        persistRecords(newRecords)
        _skipRecords.value = newRecords
    }

    /** 清空全部跳过记录(不影响累计计数)。 */
    fun clearSkipRecords() {
        val empty = SkipRecords()
        persistRecords(empty)
        _skipRecords.value = empty
    }

    /**
     * 人工标记某条跳过记录的核对结论(跳对/跳错/清除),用 timestamp 作为唯一键。
     *
     * 标记"跳错(false)"时,将该记录的"包名|matchKey"加入黑名单,下次不再用这种方式跳;
     * 标记"跳对(true)"或"清除(null)"时,将其从黑名单移除,恢复该方式。
     */
    fun setSkipCorrected(timestamp: Long, corrected: Boolean?) {
        val list = _skipRecords.value.items.map {
            if (it.timestamp == timestamp) it.copy(corrected = corrected) else it
        }
        val newRecords = SkipRecords(items = list)
        persistRecords(newRecords)
        _skipRecords.value = newRecords

        // 联动黑名单:跳错入黑名单,跳对/清除出黑名单。
        val target = list.firstOrNull { it.timestamp == timestamp } ?: return
        val key = target.matchKey?.takeIf { it.isNotBlank() } ?: return
        val entry = "${target.packageName}|$key"
        val cfg = _config.value
        val newBlacklist = if (corrected == false) cfg.skipBlacklist + entry
        else cfg.skipBlacklist - entry
        if (newBlacklist != cfg.skipBlacklist) {
            update(cfg.copy(skipBlacklist = newBlacklist))
        }
    }

    /** 供无障碍服务查询:某个"包名|matchKey"跳过方式是否已被标记跳错(在黑名单中)。 */
    fun isSkipBlacklisted(packageName: String, matchKey: String): Boolean {
        if (matchKey.isBlank()) return false
        return _config.value.skipBlacklist.contains("$packageName|$matchKey")
    }

    // ---------------- 未匹配记录(待适配) ----------------

    /** 从 MMKV 读取未匹配记录。 */
    private fun loadUnmatched(): UnmatchedRecords {
        val raw = kv().decodeString(KEY_UNMATCHED)
        return if (raw.isNullOrBlank()) {
            UnmatchedRecords()
        } else {
            runCatching { json.decodeFromString<UnmatchedRecords>(raw) }.getOrElse { UnmatchedRecords() }
        }
    }

    private fun persistUnmatched(records: UnmatchedRecords) {
        kv().encode(KEY_UNMATCHED, json.encodeToString(UnmatchedRecords.serializer(), records))
    }

    /**
     * 新增一条未匹配记录(供无障碍服务调用)。按包名去重:同一应用只保留最新一条,
     * 最新的排在最前面,超出上限时丢弃最旧的。
     */
    fun addUnmatchedRecord(record: UnmatchedRecord) {
        val current = _unmatchedRecords.value.items.filterNot { it.packageName == record.packageName }
        val merged = (listOf(record) + current).take(MAX_RECORDS)
        val newRecords = UnmatchedRecords(items = merged)
        persistUnmatched(newRecords)
        _unmatchedRecords.value = newRecords
    }

    /** 清空全部未匹配记录。 */
    fun clearUnmatchedRecords() {
        val empty = UnmatchedRecords()
        persistUnmatched(empty)
        _unmatchedRecords.value = empty
    }

    // ---------------- 图片跳过兜底记录 ----------------

    /** 从 MMKV 读取图片兜底点击记录。 */
    private fun loadImageSkip(): ImageSkipRecords {
        val raw = kv().decodeString(KEY_IMAGE_SKIP)
        return if (raw.isNullOrBlank()) {
            ImageSkipRecords()
        } else {
            runCatching { json.decodeFromString<ImageSkipRecords>(raw) }.getOrElse { ImageSkipRecords() }
        }
    }

    private fun persistImageSkip(records: ImageSkipRecords) {
        kv().encode(KEY_IMAGE_SKIP, json.encodeToString(ImageSkipRecords.serializer(), records))
    }

    /**
     * 新增一条图片兜底点击记录(供无障碍服务调用)。最新的排在最前面,超出上限时丢弃最旧的。
     */
    fun addImageSkipRecord(record: ImageSkipRecord) {
        val current = _imageSkipRecords.value.items
        val merged = (listOf(record) + current).take(MAX_RECORDS)
        val newRecords = ImageSkipRecords(items = merged)
        persistImageSkip(newRecords)
        _imageSkipRecords.value = newRecords
    }

    /** 清空全部图片兜底点击记录。 */
    fun clearImageSkipRecords() {
        val empty = ImageSkipRecords()
        persistImageSkip(empty)
        _imageSkipRecords.value = empty
    }

    /**
     * 人工标记某条图片兜底记录的核对结论(点对/点错/清除标记),用 timestamp 作为唯一键。
     *
     * @param timestamp 记录的时间戳(唯一键)
     * @param corrected true=点对,false=点错,null=清除标记回到未核对
     */
    fun setImageSkipCorrected(timestamp: Long, corrected: Boolean?) {
        val list = _imageSkipRecords.value.items.map {
            if (it.timestamp == timestamp) it.copy(corrected = corrected) else it
        }
        val newRecords = ImageSkipRecords(items = list)
        persistImageSkip(newRecords)
        _imageSkipRecords.value = newRecords
    }

    /**
     * 返回某个应用(包名)专属的规则列表。
     * 不含全局规则(packageName 为空的规则)。
     */
    fun rulesForPackage(packageName: String): List<SkipRule> =
        _config.value.rules.filter { it.packageName == packageName }

    /** 恢复为默认规则(保留跳过计数,避免统计被清零)。 */
    fun resetToDefault() {
        val keepCount = _config.value.skipCount
        update(DefaultRules.defaultConfig().copy(skipCount = keepCount))
    }
}