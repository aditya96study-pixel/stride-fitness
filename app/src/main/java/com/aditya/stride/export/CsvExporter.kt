package com.aditya.stride.export

import android.content.Context
import android.net.Uri
import com.aditya.stride.data.Repository
import com.aditya.stride.data.epochDayToLocalDate
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Everything the app holds, as one flat CSV. A single table with a `type` column
 * beats five files: it opens straight into any spreadsheet and filters easily.
 */
object CsvExporter {

    private val timeFmt = DateTimeFormatter.ofPattern("HH:mm")

    suspend fun build(context: Context): String {
        val repo = Repository.get(context)
        val rows = mutableListOf<List<String>>()

        rows += listOf(
            "type", "date", "time", "value", "unit", "detail", "duration_min", "notes",
        )

        repo.weight.allForExport().forEach {
            rows += listOf(
                "weight",
                it.epochDay.epochDayToLocalDate().toString(),
                it.timestamp.hhmm(),
                String.format("%.2f", it.weightKg),
                "kg", "", "", it.note.orEmpty(),
            )
        }

        repo.meals.allForExport().forEach {
            rows += listOf(
                "food",
                it.epochDay.epochDayToLocalDate().toString(),
                it.timestamp.hhmm(),
                it.kcal.toString(),
                "kcal", it.name, "", it.note.orEmpty(),
            )
        }

        repo.water.allForExport().forEach {
            rows += listOf(
                "water",
                it.epochDay.epochDayToLocalDate().toString(),
                it.timestamp.hhmm(),
                String.format("%.2f", it.liters),
                "L", "", "", "",
            )
        }

        repo.exercise.allForExport().forEach {
            rows += listOf(
                "exercise",
                it.epochDay.epochDayToLocalDate().toString(),
                it.timestamp.hhmm(),
                it.kcal.toString(),
                "kcal", it.activity, it.durationMin.toString(), it.note.orEmpty(),
            )
        }

        repo.runs.allForExport().forEach {
            val minutes = it.movingTimeMs / 60000.0
            rows += listOf(
                "run",
                it.epochDay.epochDayToLocalDate().toString(),
                it.startTime.hhmm(),
                String.format("%.3f", it.distanceM / 1000.0),
                "km",
                "avg ${String.format("%.2f", it.avgSpeedMps * 3.6)} km/h, " +
                    "${it.kcal} kcal, +${String.format("%.0f", it.elevationGainM)} m",
                String.format("%.1f", minutes),
                it.note.orEmpty(),
            )
        }

        return rows.joinToString("\n") { row -> row.joinToString(",") { escape(it) } }
    }

    suspend fun writeTo(context: Context, uri: Uri): Boolean = runCatching {
        val csv = build(context)
        context.contentResolver.openOutputStream(uri)?.use { stream ->
            stream.write(csv.toByteArray(Charsets.UTF_8))
            stream.flush()
        } ?: return false
        true
    }.getOrDefault(false)

    fun suggestedFileName(): String =
        "stride-export-${java.time.LocalDate.now()}.csv"

    private fun escape(value: String): String =
        if (value.contains(',') || value.contains('"') || value.contains('\n')) {
            "\"" + value.replace("\"", "\"\"") + "\""
        } else {
            value
        }

    private fun Long.hhmm(): String =
        Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).format(timeFmt)
}
