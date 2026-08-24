package com.newolf.jumpad.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.newolf.jumpad.data.RuleConfig
import com.newolf.jumpad.data.SkipRule

/**
 * 应用主界面:顶部展示无障碍服务状态与总开关,下方为规则列表管理。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    serviceEnabled: Boolean,
    config: RuleConfig,
    liTiaoTiaoCount: Int,
    foregroundEnabled: Boolean,
    onOpenSettings: () -> Unit,
    onGlobalToggle: (Boolean) -> Unit,
    onToastToggle: (Boolean) -> Unit,
    onForegroundToggle: (Boolean) -> Unit,
    onImageSkipToggle: (Boolean) -> Unit,
    onOpenAppList: () -> Unit,
   onOpenSkipRecord: () -> Unit,
    onOpenUnmatched: () -> Unit,
    onOpenImageSkip: () -> Unit,
    onOpenKeepAlive: () -> Unit,
    onRuleToggle: (String, Boolean) -> Unit,
    onRuleUpsert: (SkipRule) -> Unit,
    onRuleDelete: (String) -> Unit,
    onReset: () -> Unit
) {
    // 对话框状态:null 表示不显示;非 null 时用 editing 区分新增/编辑。
    var showDialog by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<SkipRule?>(null) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = { TopAppBar(title = { Text("JumpAd 跳过开屏广告") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                editing = null
                showDialog = true
            }) { Text("+", style = MaterialTheme.typography.headlineMedium) }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            item {
            StatusCard(
                serviceEnabled = serviceEnabled,
                liTiaoTiaoCount = liTiaoTiaoCount,
                skipCount = config.skipCount,
                onOpenSettings = onOpenSettings
            )

            Spacer(Modifier.height(12.dp))

            GlobalSwitchCard(
                globalEnabled = config.globalEnabled,
                showSkipToast = config.showSkipToast,
                foregroundEnabled = foregroundEnabled,
                imageSkipEnabled = config.imageSkipEnabled,
                onGlobalToggle = onGlobalToggle,
                onToastToggle = onToastToggle,
                onForegroundToggle = onForegroundToggle,
                onImageSkipToggle = onImageSkipToggle
            )

            Spacer(Modifier.height(12.dp))

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpenAppList)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("按应用配置规则", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = "为指定应用单独设置专属跳过规则",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text("›", style = MaterialTheme.typography.headlineMedium)
                }
            }

            Spacer(Modifier.height(12.dp))

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpenSkipRecord)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("跳过记录", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = "查看每次跳过广告的明细记录",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text("›", style = MaterialTheme.typography.headlineMedium)
                }
            }

            Spacer(Modifier.height(12.dp))

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpenUnmatched)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("待适配应用", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = "启动页未匹配到跳过按钮的应用清单",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text("›", style = MaterialTheme.typography.headlineMedium)
                }
            }

            Spacer(Modifier.height(12.dp))

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpenImageSkip)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("图片兜底记录", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = "启发式点击疑似图片跳过按钮的记录,可标记点对/点错",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text("›", style = MaterialTheme.typography.headlineMedium)
                }
            }

            Spacer(Modifier.height(12.dp))

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpenKeepAlive)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("保活设置", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = "开机自启/省电/悬浮窗等提升存活率(效果因设备而异)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text("›", style = MaterialTheme.typography.headlineMedium)
                }
            }

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("跳过规则", style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = onReset) { Text("恢复默认") }
            }
            }

            items(config.rules, key = { it.id }) { rule ->
                RuleCard(
                    rule = rule,
                    onToggle = { onRuleToggle(rule.id, it) },
                    onEdit = {
                        editing = rule
                        showDialog = true
                    },
                    onDelete = { onRuleDelete(rule.id) }
                )
            }
            item {
                Spacer(Modifier.height(80.dp))
            }
        }
    }

    if (showDialog) {
        RuleEditDialog(
            initial = editing,
            onDismiss = { showDialog = false },
            onConfirm = { rule ->
                onRuleUpsert(rule)
                showDialog = false
            }
        )
    }
}

/**
 * 无障碍服务状态卡片:提示用户是否已开启,并提供跳转设置入口。
 */
@Composable
private fun StatusCard(
    serviceEnabled: Boolean,
    liTiaoTiaoCount: Int,
    skipCount: Long,
    onOpenSettings: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (serviceEnabled) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.errorContainer
            }
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = if (serviceEnabled) "无障碍服务已开启" else "无障碍服务未开启",
                style = MaterialTheme.typography.titleMedium
           )
            Text(
                text = if (serviceEnabled) {
                    "跳广告功能已就绪,应用完全离线运行。"
                } else {
                    "需要开启无障碍服务后才能自动跳过开屏广告。"
                },
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "已内置李跳跳规则 $liTiaoTiaoCount 条 · 累计跳过 $skipCount 次",
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(8.dp))
           Button(onClick = onOpenSettings) {
                Text(if (serviceEnabled) "去无障碍设置" else "立即开启")
            }
        }
    }
}

/**
 * 总开关卡片:总开关 + 跳过提示开关 + 前台常驻服务开关。
 */
@Composable
private fun GlobalSwitchCard(
    globalEnabled: Boolean,
    showSkipToast: Boolean,
    foregroundEnabled: Boolean,
    imageSkipEnabled: Boolean,
    onGlobalToggle: (Boolean) -> Unit,
    onToastToggle: (Boolean) -> Unit,
    onForegroundToggle: (Boolean) -> Unit,
    onImageSkipToggle: (Boolean) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            SwitchRow(
                title = "跳广告总开关",
                subtitle = "关闭后所有规则暂停生效",
                checked = globalEnabled,
                onCheckedChange = onGlobalToggle
            )
            Spacer(Modifier.height(8.dp))
            SwitchRow(
                title = "跳过提示",
                subtitle = "每次跳过广告时弹出 Toast 提示",
                checked = showSkipToast,
                onCheckedChange = onToastToggle
            )
            Spacer(Modifier.height(8.dp))
            SwitchRow(
                title = "前台常驻服务",
                subtitle = "通知栏常驻,提升服务存活率并显示跳过次数",
                checked = foregroundEnabled,
                onCheckedChange = onForegroundToggle
            )
            Spacer(Modifier.height(8.dp))
            SwitchRow(
                title = "图片跳过兜底",
                subtitle = "文字规则未命中时,启发式点击疑似图片跳过按钮(如百度网盘/一刻相册)",
                checked = imageSkipEnabled,
                onCheckedChange = onImageSkipToggle
            )
        }
    }
}

/** 通用的标题 + 副标题 + 开关行。 */
@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}