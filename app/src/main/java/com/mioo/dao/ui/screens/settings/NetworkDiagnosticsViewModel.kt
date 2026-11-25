package com.mioo.dao.ui.screens.settings

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mioo.dao.data.network.DiagnosticProbeResult
import com.mioo.dao.data.network.DiagnosticSummary
import com.mioo.dao.data.network.NetworkEnvironmentSnapshot
import com.mioo.dao.data.network.ProbeId
import com.mioo.dao.data.network.ProbeStatus
import com.mioo.dao.data.network.XIslandNetworkDiagnosticsRunner
import com.mioo.dao.data.network.formatDiagnosticReport
import com.mioo.dao.data.network.summarizeDiagnostics
import com.mioo.dao.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@Immutable
data class NetworkDiagnosticsUiState(
    val environment: NetworkEnvironmentSnapshot = NetworkEnvironmentSnapshot(),
    val probes: List<DiagnosticProbeResult> = ProbeId.entries.map {
        DiagnosticProbeResult(id = it, status = ProbeStatus.IDLE)
    },
    val summary: DiagnosticSummary? = null,
    val isRunning: Boolean = false,
    val runningProbeIds: Set<ProbeId> = emptySet(),
    val lastCompletedAt: String? = null
)

@HiltViewModel
class NetworkDiagnosticsViewModel @Inject constructor(
    private val runner: XIslandNetworkDiagnosticsRunner,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        NetworkDiagnosticsUiState(environment = runner.inspectEnvironment())
    )
    val uiState: StateFlow<NetworkDiagnosticsUiState> = _uiState.asStateFlow()

    private var activeJob: Job? = null
    private var hasStartedInitialRun = false

    fun startInitialRunIfNeeded() {
        if (hasStartedInitialRun) return
        hasStartedInitialRun = true
        runAll()
    }

    fun runAll() {
        activeJob?.cancel()
        val env = runner.inspectEnvironment()
        val runningProbes = ProbeId.entries.map { id ->
            DiagnosticProbeResult(
                id = id,
                status = ProbeStatus.RUNNING,
                headline = "正在检测…"
            )
        }
        _uiState.value = NetworkDiagnosticsUiState(
            environment = env,
            probes = runningProbes,
            summary = summarizeDiagnostics(env, runningProbes),
            isRunning = true,
            runningProbeIds = ProbeId.entries.toSet(),
            lastCompletedAt = _uiState.value.lastCompletedAt
        )

        activeJob = viewModelScope.launch {
            try {
                val (finalEnv, finalResults) = runner.runAll { result ->
                    _uiState.update { state ->
                        val updated = state.probes.map { if (it.id == result.id) result else it }
                        val remaining = state.runningProbeIds - result.id
                        state.copy(
                            probes = updated,
                            runningProbeIds = remaining,
                            summary = summarizeDiagnostics(state.environment, updated)
                        )
                    }
                }
                _uiState.update { state ->
                    state.copy(
                        environment = finalEnv,
                        probes = finalResults,
                        summary = summarizeDiagnostics(finalEnv, finalResults),
                        isRunning = false,
                        runningProbeIds = emptySet(),
                        lastCompletedAt = nowTimestamp()
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                _uiState.update { state ->
                    state.copy(
                        isRunning = false,
                        runningProbeIds = emptySet(),
                        summary = summarizeDiagnostics(state.environment, state.probes)
                    )
                }
            }
        }
    }

    fun retestProbe(probeId: ProbeId) {
        if (_uiState.value.isRunning) return
        activeJob?.cancel()
        val env = runner.inspectEnvironment()
        _uiState.update { state ->
            val updated = state.probes.map {
                if (it.id == probeId) {
                    DiagnosticProbeResult(id = probeId, status = ProbeStatus.RUNNING, headline = "正在重测…")
                } else {
                    it
                }
            }
            state.copy(
                environment = env,
                probes = updated,
                summary = summarizeDiagnostics(env, updated),
                isRunning = true,
                runningProbeIds = setOf(probeId)
            )
        }

        activeJob = viewModelScope.launch {
            try {
                val (finalEnv, result) = runner.runSingle(probeId)
                _uiState.update { state ->
                    val updated = state.probes.map { if (it.id == probeId) result else it }
                    state.copy(
                        environment = finalEnv,
                        probes = updated,
                        summary = summarizeDiagnostics(finalEnv, updated),
                        isRunning = false,
                        runningProbeIds = emptySet(),
                        lastCompletedAt = nowTimestamp()
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                _uiState.update { state ->
                    state.copy(
                        isRunning = false,
                        runningProbeIds = emptySet(),
                        summary = summarizeDiagnostics(state.environment, state.probes)
                    )
                }
            }
        }
    }

    fun cancelRun() {
        activeJob?.cancel()
        activeJob = null
        _uiState.update { state ->
            val updated = state.probes.map {
                if (it.status == ProbeStatus.RUNNING) {
                    it.copy(status = ProbeStatus.IDLE, headline = "已取消")
                } else {
                    it
                }
            }
            state.copy(
                probes = updated,
                summary = summarizeDiagnostics(state.environment, updated),
                isRunning = false,
                runningProbeIds = emptySet()
            )
        }
    }

    fun enableCompatibilityModeAndRetest() {
        val allowThirdParty = settingsRepository.thirdPartyConsent.value?.allowThirdPartyServices == true
        if (!allowThirdParty) return
        activeJob?.cancel()
        activeJob = viewModelScope.launch {
            settingsRepository.updateNetworkCompatibility(true)
            withTimeoutOrNull(1_500L) {
                settingsRepository.settings.first { it.networkCompatibilityEnabled }
            }
            runAll()
        }
    }

    fun buildReport(appVersion: String, androidRelease: String): String {
        val state = _uiState.value
        return formatDiagnosticReport(
            env = state.environment,
            results = state.probes,
            summary = state.summary,
            appVersion = appVersion,
            androidRelease = androidRelease,
            timestampText = state.lastCompletedAt
        )
    }

    private fun nowTimestamp(): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
}
