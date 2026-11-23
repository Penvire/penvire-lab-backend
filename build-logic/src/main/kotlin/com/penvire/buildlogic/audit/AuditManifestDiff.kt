package com.penvire.buildlogic.audit

/** One action of a committed audit manifest. */
internal data class ManifestAction(
    val method: String,
    val action: String,
    val deniedOn: List<String>,
    val failedOn: List<String>,
    val state: String?,
    val fields: Map<String, String>,
) {
    companion object {
        /** Reads the YAML that [AuditManifest.render] writes; it reads no other YAML. */
        fun parse(yaml: String): List<ManifestAction> {
            val actions = mutableListOf<ManifestAction>()
            var current: Builder? = null
            var section: String? = null
            for (line in yaml.lines()) {
                when {
                    line.isBlank() || line.startsWith("#") || line.startsWith("actions:") -> Unit
                    line.startsWith("  - method: ") -> {
                        current?.let { actions += it.build() }
                        current = Builder(line.removePrefix("  - method: "))
                        section = null
                    }
                    line.startsWith("      - ") -> current!!.list(section!!) += line.removePrefix("      - ")
                    line.startsWith("      ") -> {
                        val (path, recording) = line.trim().split(": ", limit = 2)
                        current!!.fields[path] = recording
                    }
                    line.startsWith("    ") -> {
                        val (key, value) = line.trim().split(":", limit = 2).map(String::trim)
                        section = key
                        when (key) {
                            "action" -> current!!.action = value
                            "state" -> current!!.state = value
                        }
                    }
                    else -> throw IllegalArgumentException("Unexpected line in audit manifest: $line")
                }
            }
            current?.let { actions += it.build() }
            return actions
        }
    }

    private class Builder(val method: String) {
        var action = ""
        var state: String? = null
        val deniedOn = mutableListOf<String>()
        val failedOn = mutableListOf<String>()
        val fields = linkedMapOf<String, String>()

        fun list(section: String) = when (section) {
            "deniedOn" -> deniedOn
            "failedOn" -> failedOn
            else -> throw IllegalArgumentException("Unexpected list $section in audit manifest")
        }

        fun build() = ManifestAction(method, action, deniedOn, failedOn, state, fields)
    }
}

/**
 * What changed between the audit manifest of the base branch and of a pull request, as Markdown for
 * the review comment (ADR 0006). Actions are matched by method. Fields that now record their values
 * come first: they are what a reviewer must check before a value enters the immutable log.
 */
internal class AuditManifestDiff(base: List<ManifestAction>, head: List<ManifestAction>) {

    private val base = base.associateBy { it.method }
    private val head = head.associateBy { it.method }

    /** The service's section of the comment, or an empty string if nothing changed. */
    fun markdown(service: String): String {
        val values = newValues()
        val added = head.values.filter { it.method !in base }
        val removed = base.values.filter { it.method !in head }
        val changed = head.values.mapNotNull { action -> base[action.method]?.let { changes(it, action) } }
        if (values.isEmpty() && added.isEmpty() && removed.isEmpty() && changed.isEmpty()) {
            return ""
        }

        return buildString {
            append("### $service\n\n")
            if (values.isNotEmpty()) {
                append("**Now recorded with their values.** Check that none of them is personal data or a secret: ")
                append("a recorded value cannot be removed from the audit log.\n\n")
                append("| Action | Field |\n| --- | --- |\n")
                values.forEach { (action, path) -> append("| `$action` | `$path` |\n") }
                append("\n")
            }
            if (added.isNotEmpty()) {
                append("**Added actions**\n\n")
                added.forEach { append("- `${it.action}`: `${it.method}`\n") }
                append("\n")
            }
            if (removed.isNotEmpty()) {
                append("**Removed actions**\n\n")
                removed.forEach { append("- `${it.action}`: `${it.method}`\n") }
                append("\n")
            }
            if (changed.isNotEmpty()) {
                append("**Changed actions**\n\n")
                changed.forEach { append(it) }
                append("\n")
            }
        }
    }

    /** Fields recorded with values in the pull request but not on the base branch, new actions included. */
    private fun newValues(): List<Pair<String, String>> =
        head.values.flatMap { action ->
            val before = base[action.method]?.fields.orEmpty()
            action.fields
                .filter { (path, recording) -> recording == "value" && before[path] != "value" }
                .map { (path, _) -> action.action to path }
        }

    private fun changes(before: ManifestAction, after: ManifestAction): String? {
        val lines = mutableListOf<String>()
        if (before.action != after.action) {
            lines += "action: `${before.action}` → `${after.action}`"
        }
        lists("deniedOn", before.deniedOn, after.deniedOn)?.let { lines += it }
        lists("failedOn", before.failedOn, after.failedOn)?.let { lines += it }
        if (before.state != after.state) {
            lines += "state: ${code(before.state)} → ${code(after.state)}"
        }
        for (path in (before.fields.keys + after.fields.keys).toSortedSet()) {
            val old = before.fields[path]
            val new = after.fields[path]
            when {
                old == new -> Unit
                old == null -> lines += "field `$path` added: `$new`"
                new == null -> lines += "field `$path` removed"
                else -> lines += "field `$path`: `$old` → `$new`"
            }
        }
        if (lines.isEmpty()) {
            return null
        }
        return "- `${after.action}` (`${after.method}`)\n" + lines.joinToString("") { "  - $it\n" }
    }

    private fun lists(name: String, before: List<String>, after: List<String>): String? {
        val added = after - before.toSet()
        val removed = before - after.toSet()
        if (added.isEmpty() && removed.isEmpty()) {
            return null
        }
        val parts = added.map { "added `$it`" } + removed.map { "removed `$it`" }
        return "$name: ${parts.joinToString(", ")}"
    }

    private fun code(value: String?) = if (value == null) "none" else "`$value`"
}
