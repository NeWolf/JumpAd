package com.newolf.jumpad.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.newolf.jumpad.data.SkipRule
import java.util.UUID

/**
 * 单条规则卡片:展示名称、作用范围、关键词,并提供启用开关与删除按钮。
 */
@Composable
fun RuleCard(
    rule: SkipRule,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = rule.name + if (rule.builtin) "(内置)" else "",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "范围:" + if (rule.packageName.isBlank()) "全局" else rule.packageName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "关键词:" + rule.keywords.joinToString("、"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Row {
                    TextButton(onClick = onEdit) { Text("编辑") }
                    if (!rule.builtin) {
                        TextButton(onClick = onDelete) { Text("删除") }
                    }
                }
            }
            Switch(checked = rule.enabled, onCheckedChange = onToggle)
        }
    }
}

/**
 * 规则编辑/新增对话框。
 *
 * @param initial 传入已有规则表示编辑;传入 null 表示新增。
 */
@Composable
fun RuleEditDialog(
    initial: SkipRule?,
    onDismiss: () -> Unit,
    onConfirm: (SkipRule) -> Unit
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var packageName by remember { mutableStateOf(initial?.packageName ?: "") }
    var keywordsText by remember {
        mutableStateOf(initial?.keywords?.joinToString(",") ?: "")
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "新增规则" else "编辑规则") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("规则名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = packageName,
                    onValueChange = { packageName = it },
                    label = { Text("应用包名(留空表示全局)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = keywordsText,
                    onValueChange = { keywordsText = it },
                    label = { Text("跳过关键词(用英文逗号分隔)") },
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = "示例关键词:跳过,跳过广告,Skip,关闭",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val keywords = keywordsText
                        .split(",", ",", "、")
                        .map { it.trim() }
                        .filter { it.isNotBlank() }
                    if (name.isBlank() || keywords.isEmpty()) return@TextButton
                    val rule = SkipRule(
                        id = initial?.id ?: "custom_${UUID.randomUUID()}",
                        name = name.trim(),
                        packageName = packageName.trim(),
                        keywords = keywords,
                        enabled = initial?.enabled ?: true,
                        builtin = initial?.builtin ?: false
                    )
                    onConfirm(rule)
                }
            ) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

/**
 * 应用专属规则编辑/新增对话框(包名锁定为指定应用)。
 *
 * 与 [RuleEditDialog] 的区别:包名固定为传入的 [lockedPackageName],用户不可修改,
 * 从而保证新增/编辑的规则始终归属于当前应用。
 *
 * @param initial            传入已有规则表示编辑;传入 null 表示新增。
 * @param lockedPackageName  固定的目标应用包名。
 */
@Composable
fun AppRuleEditDialog(
    initial: SkipRule?,
    lockedPackageName: String,
    onDismiss: () -> Unit,
    onConfirm: (SkipRule) -> Unit
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var keywordsText by remember {
        mutableStateOf(initial?.keywords?.joinToString(",") ?: "")
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "新增专属规则" else "编辑专属规则") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "作用应用:$lockedPackageName",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("规则名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = keywordsText,
                    onValueChange = { keywordsText = it },
                    label = { Text("跳过关键词(用英文逗号分隔)") },
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = "示例关键词:跳过,跳过广告,Skip,关闭",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val keywords = keywordsText
                        .split(",", ",", "、")
                        .map { it.trim() }
                        .filter { it.isNotBlank() }
                    if (name.isBlank() || keywords.isEmpty()) return@TextButton
                    val rule = SkipRule(
                        id = initial?.id ?: "custom_${UUID.randomUUID()}",
                        name = name.trim(),
                        packageName = lockedPackageName,
                        keywords = keywords,
                        enabled = initial?.enabled ?: true,
                        builtin = false
                    )
                    onConfirm(rule)
                }
            ) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}