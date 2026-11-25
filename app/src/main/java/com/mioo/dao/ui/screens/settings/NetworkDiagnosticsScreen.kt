package com.mioo.dao.ui.screens.settings

import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mioo.dao.BuildConfig
import com.mioo.dao.data.network.BlockedStage
import com.mioo.dao.data.network.DiagnosticProbeResult
import com.mioo.dao.data.network.NetworkEnvironmentSnapshot
import com.mioo.dao.data.network.ProbeGroup
import com.mioo.dao.data.network.ProbeId
import com.mioo.dao.data.network.ProbeStatus
import com.mioo.dao.ui.theme.DaoTheme
import com.mioo.dao.ui.theme.readingMetaStyle
import com.mioo.dao.ui.theme.settingsDescriptionStyle
import com.mioo.dao.ui.theme.settingsItemStyle

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun NetworkDiagnosticsScreen(
    viewModel: NetworkDiagnosticsViewModel,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    LaunchedEffect(Unit) {
        viewModel.startInitialRunIfNeeded()
    }

    val hasAnyResult = uiState.probes.any {
        it.status != ProbeStatus.IDLE && it.status != ProbeStatus.RUNNING
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "X 岛通信诊断",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                },
                actions = {
                    IconButton(
                        enabled = hasAnyResult,
                        onClick = {
                            val report = viewModel.buildReport(
                                appVersion = BuildConfig.VERSION_NAME,
                                androidRelease = Build.VERSION.RELEASE ?: "unknown"
                            )
                            clipboard.setText(AnnotatedString(report))
                            Toast.makeText(
                                context,
                                "已复制诊断报告（不含饼干信息）",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.ContentCopy,
                            contentDescription = "复制诊断报告"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DaoTheme.colors.glassTopBar
                )
            )
        },
        modifier = modifier.fillMaxSize(),
        containerColor = secondaryPageBackground(),
        contentColor = MaterialTheme.colorScheme.onSurface,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OverviewCard(
                uiState = uiState,
                onRunAll = viewModel::runAll,
                onCancel = viewModel::cancelRun,
                onEnableCompatibilityAndRetest = viewModel::enableCompatibilityModeAndRetest
            )

            val coreProbes = uiState.probes.filter { it.id.group == ProbeGroup.CORE_API }
            if (coreProbes.isNotEmpty()) {
                Text(
                    text = "接口与解析",
                    style = settingsDescriptionStyle(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                SettingsGroupCard(modifier = Modifier.fillMaxWidth()) {
                    coreProbes.forEachIndexed { index, probe ->
                        if (index > 0) SettingsGroupDivider()
                        ProbeResultRow(
                            probe = probe,
                            enabled = !uiState.isRunning,
                            onRetest = { viewModel.retestProbe(probe.id) }
                        )
                    }
                }
            }

            val mediaProbes = uiState.probes.filter { it.id.group == ProbeGroup.MEDIA_AND_WEB }
            if (mediaProbes.isNotEmpty()) {
                Text(
                    text = "图床与网页",
                    style = settingsDescriptionStyle(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                SettingsGroupCard(modifier = Modifier.fillMaxWidth()) {
                    mediaProbes.forEachIndexed { index, probe ->
                        if (index > 0) SettingsGroupDivider()
                        ProbeResultRow(
                            probe = probe,
                            enabled = !uiState.isRunning,
                            onRetest = { viewModel.retestProbe(probe.id) }
                        )
                    }
                }
            }

            Text(
                text = "诊断均使用独立短连接实时探测，不复用旧连接或本地缓存；报告与界面均不会展示或复制任何饼干内容。点击任意单项可单独重测。",
                style = settingsDescriptionStyle(),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
            )
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OverviewCard(
    uiState: NetworkDiagnosticsUiState,
    onRunAll: () -> Unit,
    onCancel: () -> Unit,
    onEnableCompatibilityAndRetest: () -> Unit
) {
    val summary = uiState.summary
    val severity = summary?.severity ?: if (uiState.isRunning) ProbeStatus.RUNNING else ProbeStatus.IDLE
    val accentColor = statusAccentColor(severity)

    SettingsGroupCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(accentColor.copy(alpha = 0.12f), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    if (severity == ProbeStatus.RUNNING) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = accentColor
                        )
                    } else {
                        Icon(
                            imageVector = statusIcon(severity),
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = summary?.title ?: "准备检测 X 岛通信",
                        style = settingsItemStyle(),
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = summary?.detail ?: "将检测 DNS、核心 API、饼干鉴权、图床与网页域名。",
                        style = settingsDescriptionStyle(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (uiState.isRunning) {
                Spacer(modifier = Modifier.height(12.dp))
                val done = uiState.probes.count {
                    it.status != ProbeStatus.IDLE && it.status != ProbeStatus.RUNNING
                }
                val total = uiState.probes.size.coerceAtLeast(1)
                LinearProgressIndicator(
                    progress = { (done.toFloat() / total.toFloat()).coerceIn(0.08f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            EnvironmentTags(env = uiState.environment)

            uiState.lastCompletedAt?.takeIf { !uiState.isRunning }?.let { timestamp ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "最近检测：$timestamp",
                    style = readingMetaStyle(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (uiState.isRunning) {
                    OutlinedButton(
                        onClick = onCancel,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("停止检测")
                    }
                } else {
                    Button(
                        onClick = onRunAll,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (uiState.lastCompletedAt == null) "开始检测" else "重新测试")
                    }
                }
            }

            if (summary?.suggestEnableCompatibilityMode == true && !uiState.isRunning) {
                Spacer(modifier = Modifier.height(8.dp))
                FilledTonalButton(
                    onClick = onEnableCompatibilityAndRetest,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("开启网络兼容模式并重测")
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EnvironmentTags(env: NetworkEnvironmentSnapshot) {
    val networkText = buildString {
        append(env.transportLabel)
        if (env.vpnActive) append(" · VPN")
        if (env.proxyConfigured) append(" · 代理")
    }
    val dnsText = when {
        env.networkCompatibilityEffective -> "兼容加密 DNS"
        env.networkCompatibilityRequested && !env.allowThirdParty -> "系统 DNS（兼容模式未授权）"
        else -> "系统 DNS"
    }
    val cookieText = if (env.hasSpeakingCookie) "已配饼干" else "未配饼干"

    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        EnvTag(networkText)
        EnvTag(dnsText)
        EnvTag(cookieText)
    }
}

@Composable
private fun EnvTag(label: String) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
    ) {
        Text(
            text = label,
            style = readingMetaStyle(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ProbeResultRow(
    probe: DiagnosticProbeResult,
    enabled: Boolean,
    onRetest: () -> Unit
) {
    val accent = statusAccentColor(probe.status)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onRetest)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .padding(top = 2.dp)
                .size(32.dp)
                .background(accent.copy(alpha = 0.11f), RoundedCornerShape(9.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (probe.status == ProbeStatus.RUNNING) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = accent
                )
            } else {
                Icon(
                    imageVector = statusIcon(probe.status),
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = probe.id.title,
                    style = settingsItemStyle(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                LatencyBadge(probe = probe, accent = accent)
            }

            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "${probe.id.host} · ${probe.id.endpointHint}",
                style = readingMetaStyle(),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = probe.headline,
                style = settingsDescriptionStyle(),
                fontWeight = FontWeight.Medium,
                color = if (probe.status == ProbeStatus.BLOCKED || probe.status == ProbeStatus.WARN) {
                    accent
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
            )

            val stageLine = buildStageBreakdown(probe)
            if (stageLine != null) {
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = stageLine,
                    style = readingMetaStyle(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (probe.resolvedIps.isNotEmpty()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "IP: ${probe.resolvedIps.joinToString(", ")}",
                    style = readingMetaStyle(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (!probe.detail.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = probe.detail,
                    style = settingsDescriptionStyle(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f)
                )
            }
        }
    }
}

@Composable
private fun LatencyBadge(
    probe: DiagnosticProbeResult,
    accent: Color
) {
    val text = when {
        probe.status == ProbeStatus.RUNNING -> "检测中"
        probe.timings.totalMs != null -> "${probe.timings.totalMs} ms"
        probe.status == ProbeStatus.BLOCKED -> "受阻"
        else -> "--"
    }
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = accent.copy(alpha = 0.11f)
    ) {
        Text(
            text = text,
            style = readingMetaStyle().copy(fontWeight = FontWeight.SemiBold),
            color = accent,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            maxLines = 1
        )
    }
}

private fun buildStageBreakdown(probe: DiagnosticProbeResult): String? {
    val parts = buildList {
        probe.timings.dnsMs?.let { add("DNS ${it}ms") }
        probe.timings.tcpMs?.let { add("TCP ${it}ms") }
        probe.timings.tlsMs?.let { add("TLS ${it}ms") }
        probe.timings.ttfbMs?.let { add("首字节 ${it}ms") }
        probe.httpCode?.let { add("HTTP $it") }
        probe.protocol?.let { add(it) }
        probe.tlsVersion?.let { add(it) }
        if (probe.blockedStage != BlockedStage.NONE) add("阻塞: ${probe.blockedStage.label}")
    }
    return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
}

@Composable
private fun statusAccentColor(status: ProbeStatus): Color {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    return when (status) {
        ProbeStatus.OK -> if (dark) Color(0xFF66BB6A) else Color(0xFF2E7D32)
        ProbeStatus.SLOW, ProbeStatus.WARN -> if (dark) Color(0xFFFFB74D) else Color(0xFFEF6C00)
        ProbeStatus.BLOCKED -> MaterialTheme.colorScheme.error
        ProbeStatus.RUNNING -> MaterialTheme.colorScheme.primary
        ProbeStatus.IDLE -> MaterialTheme.colorScheme.onSurfaceVariant
    }
}

private fun statusIcon(status: ProbeStatus) = when (status) {
    ProbeStatus.OK -> Icons.Default.CheckCircle
    ProbeStatus.SLOW, ProbeStatus.WARN -> Icons.Default.Warning
    ProbeStatus.BLOCKED -> Icons.Default.Error
    ProbeStatus.RUNNING -> Icons.Default.Schedule
    ProbeStatus.IDLE -> Icons.Default.NetworkCheck
}
