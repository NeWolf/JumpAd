package com.newolf.jumpad.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.newolf.jumpad.data.RuleRepository
import com.newolf.jumpad.data.SkipRecord
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 跳过记录页面:展示每次跳过广告的明细(应用名 / 包名 / 时间 / 方式),
 * 并提供"跳对了/跳错了"人工标记;标记"跳错了"后,下次不再用这种方式跳该应用的广告。
 * 支持一键清空。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SkipRecordScreen(onBack: () -> Unit) {
    val records by RuleRepository.skipRecords.collectAsState()
    val items = records.items

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("跳过记录") },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("返回") }
                },
                actions = {
                    if (items.isNotEmpty()) {
                        TextButton(onClick = { RuleRepository.clearSkipRecords() }) {
                            Text("清空")
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (items.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "暂无跳过记录",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                items(items, key = { it.timestamp }) { record ->
                    RecordRow(
                        record = record,
                        onMark = { corrected ->
                            RuleRepository.setSkipCorrected(record.timestamp, corrected)
                        }
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

/** 单条跳过记录行:应用名 / 包名 / 时间·方式 / 核对状态 + 标记按钮。 */
@Composable
private fun RecordRow(
    record: SkipRecord,
    onMark: (Boolean?) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(
            text = record.appName,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = record.packageName,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = "${dateFormat.format(Date(record.timestamp))} · ${record.method}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(Modifier.height(8.dp))
        val statusText = when (record.corrected) {
            true -> "已标记: 跳对了"
            false -> "已标记: 跳错了(下次不再这样跳)"
            null -> "未核对"
        }
        Text(
            text = statusText,
            style = MaterialTheme.typography.labelMedium,
            color = when (record.corrected) {
                true -> MaterialTheme.colorScheme.primary
                false -> MaterialTheme.colorScheme.error
                null -> MaterialTheme.colorScheme.onSurfaceVariant
            }
        )

        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = { onMark(true) }) { Text("跳对了") }
            FilledTonalButton(onClick = { onMark(false) }) { Text("跳错了") }
            if (record.corrected != null) {
                OutlinedButton(onClick = { onMark(null) }) { Text("清除") }
            }
        }
    }
}