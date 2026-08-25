package com.newolf.jumpad.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.newolf.jumpad.data.AppInfo

/**
 * 应用列表页面:展示设备上已安装的应用,点击进入应用详情配置专属规则。
 *
 * @param ruleCountOf 返回某包名当前已配置的专属规则数量(用于展示徽标)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppListScreen(
    ruleCountOf: (String) -> Int,
    onBack: () -> Unit,
    onAppClick: (AppInfo) -> Unit,
    apps: List<AppInfo>?,
    query: String,
    onQueryChange: (String) -> Unit,
    listState: LazyListState
) {

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("选择应用") },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("返回") }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                label = { Text("搜索应用名称 / 包名") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
          )

            when {
                apps == null -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(Modifier.height(12.dp))
                            Text("正在加载应用列表…", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }

                else -> {
                    val filtered = remember(apps, query) {
                        if (query.isBlank()) apps
                        else apps.filter {
                            it.appName.contains(query, ignoreCase = true) ||
                                it.packageName.contains(query, ignoreCase = true)
                        }
                    }
                    if (filtered.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (query.isBlank()) "未找到可显示的应用"
                                else "没有匹配\"$query\"的应用",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        // 按"用户应用 / 系统应用"分组展示,组内已在数据层按应用名排序。
                        val userApps = remember(filtered) { filtered.filter { !it.isSystem } }
                        val systemApps = remember(filtered) { filtered.filter { it.isSystem } }
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            state = listState
                        ) {
                            if (userApps.isNotEmpty()) {
                                item(key = "header_user") {
                                    AppGroupHeader("用户应用 (${userApps.size})")
                                }
                                items(userApps, key = { it.packageName }) { app ->
                                    AppRow(
                                        app = app,
                                        ruleCount = ruleCountOf(app.packageName),
                                        onClick = { onAppClick(app) }
                                    )
                                    HorizontalDivider()
                                }
                            }
                            if (systemApps.isNotEmpty()) {
                                item(key = "header_system") {
                                    AppGroupHeader("系统应用 (${systemApps.size})")
                                }
                                items(systemApps, key = { it.packageName }) { app ->
                                    AppRow(
                                        app = app,
                                        ruleCount = ruleCountOf(app.packageName),
                                        onClick = { onAppClick(app) }
                                    )
                                    HorizontalDivider()
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 分组标题:用户应用 / 系统应用,吸顶展示。 */
@Composable
private fun AppGroupHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

/** 单个应用行:图标 + 名称 + 包名 + 已配置规则数徽标。 */
@Composable
private fun AppRow(
    app: AppInfo,
    ruleCount: Int,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AppIcon(app)
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = app.appName,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Spacer(Modifier.width(8.dp))
                AppTypeBadge(isSystem = app.isSystem)
            }
            Text(
                text = app.packageName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (ruleCount > 0) {
            Spacer(Modifier.width(8.dp))
            Text(
                text = "$ruleCount 条规则",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

/** 应用类型徽标:区分系统应用与用户应用。 */
@Composable
private fun AppTypeBadge(isSystem: Boolean) {
    val (label, container, content) = if (isSystem) {
        Triple(
            "系统",
            MaterialTheme.colorScheme.tertiaryContainer,
            MaterialTheme.colorScheme.onTertiaryContainer
        )
    } else {
        Triple(
            "用户",
            MaterialTheme.colorScheme.secondaryContainer,
            MaterialTheme.colorScheme.onSecondaryContainer
        )
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(container)
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = content
        )
    }
}

/** 将应用图标 Drawable 渲染为 Compose 图像。 */
@Composable
private fun AppIcon(app: AppInfo) {
    val bitmap = remember(app.packageName) {
        runCatching { app.icon?.toBitmap(96, 96)?.asImageBitmap() }.getOrNull()
    }
    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = app.appName,
            modifier = Modifier.size(40.dp)
        )
    } else {
        Box(
            modifier = Modifier.size(40.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = app.appName.take(1),
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
}