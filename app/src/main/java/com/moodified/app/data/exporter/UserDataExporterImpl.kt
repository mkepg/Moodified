package com.moodified.app.data.exporter

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.moodified.app.data.local.database.MoodifiedDatabase
import com.moodified.app.domain.exporter.UserDataExporter
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserDataExporterImpl
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val database: MoodifiedDatabase,
    ) : UserDataExporter {
        private val tablesToExport =
            listOf(
                "mood_entries",
                "sleep_segments",
                "activity_daily_summaries",
                "interaction_daily_summaries",
                "intervention_history",
            )

        override suspend fun export(): UserDataExporter.ExportResult =
            withContext(Dispatchers.IO) {
                val json =
                    buildString {
                        append("{\n")
                        append("  \"exportedAt\": \"").append(timestamp()).append("\",\n")
                        append("  \"schemaVersion\": 1,\n")
                        tablesToExport.forEachIndexed { index, table ->
                            append("  \"").append(table).append("\": ")
                            append(dumpTable(table))
                            if (index < tablesToExport.lastIndex) append(",")
                            append("\n")
                        }
                        append("}\n")
                    }

                val filename = "moodified-export-${timestamp()}.json"
                val location =
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        writeViaMediaStore(filename, json)
                    } else {
                        writeToLegacyDownloads(filename, json)
                    }
                UserDataExporter.ExportResult(location = location)
            }

        private fun dumpTable(table: String): String {
            val cursor: Cursor =
                database.openHelper.readableDatabase
                    .query("SELECT * FROM $table")
            return cursor.use {
                val cols = it.columnNames
                val rows = mutableListOf<String>()
                while (it.moveToNext()) {
                    val row =
                        cols.indices.joinToString(separator = ",", prefix = "{", postfix = "}") { i ->
                            "\"${cols[i]}\":${valueToJson(it, i)}"
                        }
                    rows += row
                }
                rows.joinToString(separator = ",", prefix = "[", postfix = "]")
            }
        }

        private fun valueToJson(
            cursor: Cursor,
            index: Int,
        ): String =
            when {
                cursor.isNull(index) -> "null"
                else ->
                    when (cursor.getType(index)) {
                        Cursor.FIELD_TYPE_INTEGER -> cursor.getLong(index).toString()
                        Cursor.FIELD_TYPE_FLOAT -> cursor.getDouble(index).toString()
                        Cursor.FIELD_TYPE_STRING ->
                            "\"" +
                                cursor.getString(index)
                                    .replace("\\", "\\\\")
                                    .replace("\"", "\\\"")
                                    .replace("\n", "\\n") + "\""
                        else -> "null"
                    }
            }

        private fun timestamp(): String = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))

        private fun writeViaMediaStore(
            filename: String,
            content: String,
        ): String {
            val values =
                ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                    put(MediaStore.MediaColumns.MIME_TYPE, "application/json")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
            val resolver = context.contentResolver
            val uri: Uri =
                resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: error("Could not create export file in Downloads")
            resolver.openOutputStream(uri)?.use { it.write(content.toByteArray()) }
                ?: error("Could not open export file for writing")
            return "Downloads/$filename"
        }

        private fun writeToLegacyDownloads(
            filename: String,
            content: String,
        ): String {
            @Suppress("DEPRECATION")
            val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (!dir.exists()) dir.mkdirs()
            val file = File(dir, filename)
            FileOutputStream(file).use { it.write(content.toByteArray()) }
            return file.absolutePath
        }
    }
