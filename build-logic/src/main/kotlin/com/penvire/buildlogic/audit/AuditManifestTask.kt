package com.penvire.buildlogic.audit

import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import org.jboss.jandex.Indexer

/**
 * Writes the service's audit manifest from its compiled `@Audited` methods (ADR 0006). The file is
 * committed; CI fails if it is out of date.
 */
@DisableCachingByDefault(because = "Writes a committed file in the project directory")
abstract class AuditManifestTask : DefaultTask() {

    /** The service's own classes, searched for `@Audited` methods. */
    @get:Classpath
    abstract val classesDirs: ConfigurableFileCollection

    /** Where state types and `Audited` itself are looked up. */
    @get:Classpath
    abstract val classpath: ConfigurableFileCollection

    @get:OutputFile
    abstract val manifest: RegularFileProperty

    @TaskAction
    fun generate() {
        val indexer = Indexer()
        classesDirs.asFileTree.matching { include("**/*.class") }.forEach { file ->
            file.inputStream().use { indexer.index(it) }
        }
        val yaml = ClassFinder(classesDirs + classpath).use { AuditManifest(it).render(indexer.complete()) }
        manifest.get().asFile.writeText(yaml)
    }
}
