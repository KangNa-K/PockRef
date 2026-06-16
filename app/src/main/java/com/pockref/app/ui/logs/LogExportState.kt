package com.pockref.app.ui.logs

/**
 * V_NOW (2026-06-16, "启动页导出运行日志" feature):
 * Local UI state for the [LogExportFab]. Held inside the
 * composable's `remember { mutableStateOf(...) }` — deliberately
 * **not** in [com.pockref.app.model.PockRefUiState] because only
 * the FAB itself cares about this state, and adding a field to
 * that 100+ field data class would trigger needless recomposition
 * everywhere else whenever the export lifecycle ticks.
 *
 * Lifecycle:
 *   Idle ──(FAB clicked)──▶ Loading ──(VM text built + SAF launched)──▶ Idle
 *                             │
 *                             └──(VM throws / build fails)──▶ Error(msg)
 *                                                                    │
 *                                                  (1.5s later) ─────┴──▶ Idle
 */
sealed interface LogExportState {
    data object Idle : LogExportState
    data object Loading : LogExportState
    data class Error(val message: String) : LogExportState
}
