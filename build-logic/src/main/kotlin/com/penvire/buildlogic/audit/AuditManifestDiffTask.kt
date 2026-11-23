package com.penvire.buildlogic.audit

import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

/**
 * Compares the service's committed audit manifest with the base branch's, for the pull request
 * comment (ADR 0006). Writes an empty file if nothing changed. A missing manifest counts as one
 * without actions, so a new service lists all its actions.
 */
@DisableCachingByDefault(because = "Cheap; runs once per pull request")
abstract class AuditManifestDiffTask : DefaultTask() {

    @get:Input
    abstract val service: Property<String>

    /** The base branch's manifest; may not exist. */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val base: ConfigurableFileCollection

    /** The pull request's committed manifest; may not exist. */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val head: ConfigurableFileCollection

    @get:OutputFile
    abstract val markdown: RegularFileProperty

    @TaskAction
    fun compare() {
        val diff = AuditManifestDiff(read(base), read(head))
        markdown.get().asFile.writeText(diff.markdown(service.get()))
    }

    private fun read(files: ConfigurableFileCollection): List<ManifestAction> =
        files.files.singleOrNull()?.takeIf { it.isFile }?.let { ManifestAction.parse(it.readText()) }.orEmpty()
}
