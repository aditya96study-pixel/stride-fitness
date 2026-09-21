package com.aditya.stride.export

/**
 * RFC 4180 parsing and quoting.
 *
 * `split(",")` is not an option: the exporter quotes any field holding a comma, a quote
 * or a newline, and a note with a line break in it would otherwise be torn into pieces
 * that shift every later column. Excel also adds a UTF-8 byte-order mark when it re-saves
 * a file, which would otherwise end up inside the first header cell.
 */
object Csv {

    const val VERSION = 2

    fun quote(value: String): String =
        if (value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
            "\"" + value.replace("\"", "\"\"") + "\""
        } else {
            value
        }

    fun row(fields: List<String>): String = fields.joinToString(",") { quote(it) }

    /**
     * Splits a whole file into rows of fields. A state machine rather than a line loop,
     * because a quoted field may itself contain the line separator.
     */
    fun parse(text: String): List<List<String>> {
        val body = text.removePrefix("﻿")
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val field = StringBuilder()
        var inQuotes = false
        var i = 0

        fun endField() {
            row.add(field.toString())
            field.setLength(0)
        }

        fun endRow() {
            endField()
            // A trailing newline produces one empty field, which is not a row.
            if (row.size > 1 || row.firstOrNull()?.isNotEmpty() == true) rows.add(row)
            row = mutableListOf()
        }

        while (i < body.length) {
            val c = body[i]
            when {
                inQuotes && c == '"' ->
                    if (i + 1 < body.length && body[i + 1] == '"') {
                        field.append('"')
                        i++
                    } else {
                        inQuotes = false
                    }

                inQuotes -> field.append(c)
                c == '"' -> inQuotes = true
                c == ',' -> endField()
                c == '\r' -> {
                    // Accept CRLF and a lone CR alike.
                    endRow()
                    if (i + 1 < body.length && body[i + 1] == '\n') i++
                }
                c == '\n' -> endRow()
                else -> field.append(c)
            }
            i++
        }
        if (field.isNotEmpty() || row.isNotEmpty()) endRow()
        return rows
    }
}
