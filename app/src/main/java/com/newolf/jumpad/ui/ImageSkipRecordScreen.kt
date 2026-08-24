package com.newolf.jumpad.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import android.graphics.BitmapFactory
import com.newolf.jumpad.data.ImageSkipRecord
import com.newolf.jumpad.data.RuleRepository
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 图片兜底记录页面:展示每次启发式点击"疑似图片跳过按钮"的记录(含截图、位置、得分),
 * 并提供"点对/点错"人工标记,便于后续修正启发式或沉淀专属规则。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageSkipRecordScreen(onBack: () -> Unit) {
    val records by RuleRepository.imageSkipRecords.collectAsState()
    val items = records.items

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("图片兜底记录") },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("返回") }
                },
                actions = {
                    if (items.isNotEmpty()) {
                        TextButton(onClick = { RuleRepository.clearImageSkipRecords() }) {
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
                    text = "暂无图片兜底记录",
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
                    ImageSkipRow(
                        record = record,
                        onMark = { corrected ->
                            RuleRepository.setImageSkipCorrected(record.timestamp, corrected)
                        }
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

private val imgDateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

/** 单条图片兜底记录行:应用名 / 截图 / 控件信息 / 得分 / 核对状态 + 标记按钮。 */
@Composable
private fun ImageSkipRow(
    record: ImageSkipRecord,
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

        // 截图预览(存在且可解码时展示)。
        val bitmap = remember(record.screenshot) {
            record.screenshot
                ?.takeIf { it.isNotBlank() && File(it).exists() }
                ?.let { runCatching { BitmapFactory.decodeFile(it) }.getOrNull() }
        }
        if (bitmap != null) {
            Spacer(Modifier.height(8.dp))
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "点击时截图",
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 240.dp)
            )
        }

        Spacer(Modifier.height(4.dp))
        if (!record.className.isNullOrBlank()) {
            Text(
                text = "控件: ${record.className}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
               overflow = TextOverflow.Ellipsis
            )
        }
        if (!record.bounds.isNullOrBlank()) {
            Text(
                text = "位置: ${record.bounds}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = "得分: ${record.score} · ${imgDateFormat.format(Date(record.timestamp))}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(Modifier.height(8.dp))
        val statusText = when (record.corrected) {
            true -> "已标记: 点对了"
            false -> "已标记: 点错了"
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
            FilledTonalButton(onClick = { onMark(true) }) { Text("点对了") }
            FilledTonalButton(onClick = { onMark(false) }) { Text("点错了") }
            if (record.corrected != null) {
                OutlinedButton(onClick = { onMark(null) }) { Text("清除") }
            }
        }
    }
}