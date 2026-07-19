package com.kalazacare.app.util

import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Minimal OOXML (.xlsx) writer with no third-party dependency — an xlsx is
 * just a zip of small XML parts, and a mock report only needs plain text/
 * number cells (via inline strings), not styling or formulas.
 */
class XlsxWriter {
    private val sheets = mutableListOf<Pair<String, List<List<String>>>>()

    fun addSheet(name: String, rows: List<List<String>>) {
        sheets.add(uniqueSheetName(sanitizeSheetName(name)) to rows)
    }

    private fun uniqueSheetName(name: String): String {
        if (sheets.none { it.first.equals(name, ignoreCase = true) }) return name
        var suffix = 2
        while (true) {
            val candidate = "${name.take(31 - " ($suffix)".length)} ($suffix)"
            if (sheets.none { it.first.equals(candidate, ignoreCase = true) }) return candidate
            suffix++
        }
    }

    fun build(): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            fun entry(path: String, content: String) {
                zip.putNextEntry(ZipEntry(path))
                zip.write(content.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
            entry("[Content_Types].xml", contentTypesXml())
            entry("_rels/.rels", relsXml())
            entry("xl/workbook.xml", workbookXml())
            entry("xl/_rels/workbook.xml.rels", workbookRelsXml())
            sheets.forEachIndexed { index, (_, rows) ->
                entry("xl/worksheets/sheet${index + 1}.xml", sheetXml(rows))
            }
        }
        return out.toByteArray()
    }

    private fun sanitizeSheetName(name: String): String =
        name.replace(Regex("[\\\\/*\\[\\]:?]"), " ").take(31).ifBlank { "Sheet" }

    private fun contentTypesXml(): String {
        val overrides = sheets.indices.joinToString("") { i ->
            "<Override PartName=\"/xl/worksheets/sheet${i + 1}.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>"
        }
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
<Default Extension="xml" ContentType="application/xml"/>
<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
$overrides
</Types>"""
    }

    private fun relsXml() = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
</Relationships>"""

    private fun workbookXml(): String {
        val sheetTags = sheets.mapIndexed { i, (name, _) ->
            "<sheet name=\"${escape(name)}\" sheetId=\"${i + 1}\" r:id=\"rId${i + 1}\"/>"
        }.joinToString("")
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
<sheets>$sheetTags</sheets>
</workbook>"""
    }

    private fun workbookRelsXml(): String {
        val rels = sheets.indices.joinToString("") { i ->
            "<Relationship Id=\"rId${i + 1}\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet${i + 1}.xml\"/>"
        }
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">$rels</Relationships>"""
    }

    private fun sheetXml(rows: List<List<String>>): String {
        val rowTags = rows.mapIndexed { r, row ->
            val cells = row.mapIndexed { c, value ->
                val ref = "${columnLetter(c)}${r + 1}"
                if (isPlainNumber(value)) {
                    "<c r=\"$ref\"><v>$value</v></c>"
                } else {
                    "<c r=\"$ref\" t=\"inlineStr\"><is><t xml:space=\"preserve\">${escape(value)}</t></is></c>"
                }
            }.joinToString("")
            "<row r=\"${r + 1}\">$cells</row>"
        }.joinToString("")
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>$rowTags</sheetData></worksheet>"""
    }

    private val plainNumberRegex = Regex("^-?(0|[1-9]\\d*)(\\.\\d+)?$")

    private fun isPlainNumber(value: String): Boolean =
        plainNumberRegex.matches(value)

    private fun columnLetter(index: Int): String {
        var i = index
        val sb = StringBuilder()
        do {
            sb.insert(0, 'A' + (i % 26))
            i = i / 26 - 1
        } while (i >= 0)
        return sb.toString()
    }

    private fun escape(s: String) = s
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
}
