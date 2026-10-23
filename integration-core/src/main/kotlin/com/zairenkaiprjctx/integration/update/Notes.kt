// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 ZairenkaiPrjctX contributors
package com.zairenkaiprjctx.integration.update

/** A block of release notes: the small Markdown subset changelogs use. */
sealed interface NoteBlock {
    data class Heading(val level: Int, val text: String) : NoteBlock
    data class Bullet(val text: String, val depth: Int) : NoteBlock
    data class Paragraph(val text: String) : NoteBlock
}

/** One version's entry in CHANGELOG.md: `## [0.2.0] - 2026-10-23` and its blocks. */
data class ChangelogEntry(val version: String, val date: String?, val blocks: List<NoteBlock>)

object Notes {
    private val HEADING = Regex("^(#{1,6})\\s+(.*)$")
    private val BULLET = Regex("^(\\s*)[-*+]\\s+(.*)$")
    private val LINK = Regex("\\[([^\\]]+)]\\([^)]*\\)")
    private val ENTRY = Regex("^\\[?v?([0-9][^\\]\\s]*)]?(?:\\s*[-–]\\s*(.+))?$")

    /**
     * Markdown to blocks: headings, bullets (nesting by indentation) and paragraphs; links keep
     * their text, emphasis and code marks are dropped, HTML comments and tags are removed. Notes
     * are shown as plain text, never rendered as HTML.
     */
    fun parse(markdown: String): List<NoteBlock> {
        val out = mutableListOf<NoteBlock>()
        val para = StringBuilder()
        fun flush() {
            if (para.isNotBlank()) out += NoteBlock.Paragraph(clean(para.toString()))
            para.clear()
        }
        val text = markdown.replace("\r\n", "\n").replace(Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL), "")
        for (raw in text.lines()) {
            val line = raw.trimEnd()
            val h = HEADING.matchEntire(line.trimStart())
            val b = BULLET.matchEntire(line)
            when {
                line.isBlank() -> flush()
                h != null -> { flush(); out += NoteBlock.Heading(h.groupValues[1].length, clean(h.groupValues[2])) }
                b != null -> { flush(); out += NoteBlock.Bullet(clean(b.groupValues[2]), b.groupValues[1].length / 2) }
                out.lastOrNull() is NoteBlock.Bullet && para.isEmpty() && raw.startsWith("  ") -> {
                    // A bullet's continuation line.
                    val last = out.removeAt(out.lastIndex) as NoteBlock.Bullet
                    out += last.copy(text = last.text + " " + clean(line.trim()))
                }
                else -> { if (para.isNotEmpty()) para.append(' '); para.append(line.trim()) }
            }
        }
        flush()
        return out
    }

    private fun clean(s: String): String = s
        .replace(LINK, "$1")
        .replace(Regex("<[^>]+>"), "")
        .replace(Regex("[`*_]{1,3}(?=\\S)|(?<=\\S)[`*_]{1,3}"), "")
        .trim()

    /** The entries of a Keep-a-Changelog file, newest first as written. */
    fun changelog(markdown: String): List<ChangelogEntry> {
        val entries = mutableListOf<ChangelogEntry>()
        var version: String? = null
        var date: String? = null
        val body = StringBuilder()
        fun close() {
            version?.let { entries += ChangelogEntry(it, date, parse(body.toString())) }
            body.clear()
        }
        for (line in markdown.replace("\r\n", "\n").lines()) {
            if (line.startsWith("## ")) {
                close()
                val m = ENTRY.matchEntire(line.removePrefix("## ").trim())
                version = m?.groupValues?.get(1) ?: line.removePrefix("## ").trim().removePrefix("[").substringBefore("]")
                date = m?.groupValues?.get(2)?.trim()?.ifEmpty { null }
            } else if (version != null) {
                body.appendLine(line)
            }
        }
        close()
        return entries
    }

    /** The entry for [version] (exact text, without a leading v), or null. */
    fun entryFor(markdown: String, version: String): ChangelogEntry? =
        changelog(markdown).firstOrNull { it.version == version.removePrefix("v") }
}
