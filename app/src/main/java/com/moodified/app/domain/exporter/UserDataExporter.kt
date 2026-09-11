package com.moodified.app.domain.exporter

/**
 * Writes a snapshot of the user's on-device data to a location the user can share.
 * All Android I/O and MediaStore concerns live in the data-layer implementation.
 */
interface UserDataExporter {
    suspend fun export(): ExportResult

    data class ExportResult(val location: String)
}
