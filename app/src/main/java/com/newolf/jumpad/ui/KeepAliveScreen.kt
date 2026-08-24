package com.newolf.jumpad.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * 保活设置页面:集中管理开机自启、省电引导、悬浮窗权限、1像素保活等手段。
 *
 * 页面本身只负责展示与触发回调,权限检测/跳转/开关持久化由上层(MainActivity)提供,
 * 便于在页面回到前台时刷新最新的权限状态。完全离线。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KeepAliveScreen(
    foregroundEnabled: Boolean,
    pixelKeepAliveEnabled: Boolean,
    ignoringBatteryOpt: Boolean,
    canDrawOverlays: Boolean,
    onBack: () -> Unit,
    onForegroundToggle: (Boolean) -> Unit,
    onPixelToggle: (Boolean) -> Unit,
    onRequestBatteryOpt: () -> Unit,
    onRequestOverlay: () -> Unit,
    onOpenAppDetail: () -> Unit
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("保活设置") },
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
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Text(
                text = "以下手段用于提升跳广告服务的存活率,受系统与厂商限制,效果因设备而异。完全离线运行。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(16.dp))

            SwitchCard(
                title = "前台常驻服务(开机自启)",
                desc = "以常驻通知保持服务存活;开启后设备重启会自动拉起该服务。",
                checked = foregroundEnabled,
                onCheckedChange = onForegroundToggle
            )
            Spacer(Modifier.height(12.dp))

            ActionCard(
                title = "忽略电池优化",
                desc = if (ignoringBatteryOpt) "已加入电池优化白名单" else "尚未加入白名单,建议开启以减少被系统限制",
                done = ignoringBatteryOpt,
                buttonText = if (ignoringBatteryOpt) "重新设置" else "去设置",
                onClick = onRequestBatteryOpt
            )
            Spacer(Modifier.height(12.dp))

            ActionCard(
                title = "显示在其他应用上层(悬浮窗)",
                desc = if (canDrawOverlays) "已授予悬浮窗权限" else "1像素保活依赖此权限,建议授予",
                done = canDrawOverlays,
                buttonText = if (canDrawOverlays) "重新设置" else "去授权",
                onClick = onRequestOverlay
            )
            Spacer(Modifier.height(12.dp))

            SwitchCard(
                title = "1像素保活",
                desc = "锁屏时显示一个 1像素透明窗口以提升存活率,需先授予悬浮窗权限。",
                checked = pixelKeepAliveEnabled,
                onCheckedChange = onPixelToggle
            )
            Spacer(Modifier.height(12.dp))

            ActionCard(
                title = "厂商自启动 / 后台管理",
                desc = "部分厂商需在系统设置中额外允许自启动与后台运行,点击前往应用详情。",
                done = false,
                buttonText = "打开应用详情",
                onClick = onOpenAppDetail
            )
        }
    }
}

/** 带开关的卡片:标题 + 说明 + Switch。 */
@Composable
private fun SwitchCard(
    title: String,
    desc: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Text(
                    text = desc,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(0.dp))
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}

/** 带跳转按钮的卡片:标题 + 状态说明 + 操作按钮。 */
@Composable
private fun ActionCard(
    title: String,
    desc: String,
    done: Boolean,
    buttonText: String,
    onClick: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = if (done) "已完成" else "待处理",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (done) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.error
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = desc,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onClick) { Text(buttonText) }
        }
    }
}