package com.cycling.workitout.data.export

sealed class ExportState {
    object Idle : ExportState()
    object InProgress : ExportState()
    data class Ready(val file: java.io.File) : ExportState()
    data class Failed(val message: String) : ExportState()
}