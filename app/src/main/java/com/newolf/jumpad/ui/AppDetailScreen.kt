package com.newolf.jumpad.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
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
 * 应用详情页面:管理某个应用(包名)专属的跳过规则。
 *
 * @param packageName 目标应用包名
 * @param appName     目标应用名称
 * @param config      全局规则配置(从中筛选该应用专属规则)
 * @param skipEnabled 该应用当前是否开启跳过扫描
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppDetailScreen(
    packageName: String,
    appName: String,
    config: RuleConfig,
    skipEnabled: Boolean,
    onSkipEnabledChange: (Boolean) -> Unit,
    onBack: () -> Unit,
    onRuleToggle: (String, Boolean) -> Unit,
    onRuleUpsert: (SkipRule) -> Unit,
    onRuleDelete: (String) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<SkipRule?>(null) }

    // 该应用专属规则(packageName 精确匹配)。
    val appRules = remember(config, packageName) {
        config.rules.filter { it.packageName == packageName }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(appName, maxLines = 1) },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("返回") }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                editing = null
                showDialog = true
            }) { Text("+", style = MaterialTheme.typography.headlineMedium) }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            Card(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("应用信息", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "名称:$appName",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "包名:$packageName",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "为该应用配置专属跳过规则,只对该应用生效;全局规则依旧同时生效。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // 该应用的跳过总开关:关闭后无障碍服务不再扫描该应用的开屏广告。
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("跳过此应用广告", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = if (skipEnabled) "已开启:进入该应用时自动扫描并跳过开屏广告"
                            else "已关闭:不扫描该应用,不会自动跳过其广告",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Switch(checked = skipEnabled, onCheckedChange = onSkipEnabledChange)
                }
            }

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "专属规则(${appRules.size})",
                    style = MaterialTheme.typography.titleMedium
                )
            }

            if (appRules.isEmpty()) {
                Spacer(Modifier.height(24.dp))
                Text(
                    text = "暂无专属规则,点击右下角 + 添加。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(appRules, key = { it.id }) { rule ->
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
                    item { Spacer(Modifier.height(80.dp)) }
                }
            }
        }
    }

    if (showDialog) {
        AppRuleEditDialog(
            initial = editing,
            lockedPackageName = packageName,
            onDismiss = { showDialog = false },
            onConfirm = { rule ->
                onRuleUpsert(rule)
                showDialog = false
            }
        )
    }
}