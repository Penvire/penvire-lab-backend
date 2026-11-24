package com.penvire.buildlogic.audit

import java.io.Closeable
import java.io.File
import java.io.InputStream
import java.util.zip.ZipFile
import org.jboss.jandex.ClassInfo
import org.jboss.jandex.DotName
import org.jboss.jandex.Indexer

/**
 * Finds classes by name in class directories, jars and the JDK, and reads them with Jandex on
 * demand. Nothing is loaded, so the service's classes need no JVM of their own.
 */
internal class ClassFinder(roots: Iterable<File>) : Closeable {

    private val directories = roots.filter { it.isDirectory }
    private val jars = roots.filter { it.isFile && it.name.endsWith(".jar") }.map(::ZipFile)
    private val found = HashMap<DotName, ClassInfo?>()

    /** The class, or null if it is on neither the given roots nor the JDK. */
    fun find(name: DotName): ClassInfo? {
        if (name !in found) {
            found[name] = read(name)
        }
        return found[name]
    }

    private fun read(name: DotName): ClassInfo? {
        val stream = open(name.toString().replace('.', '/') + ".class") ?: return null
        val indexer = Indexer()
        stream.use { indexer.index(it) }
        return indexer.complete().getClassByName(name)
    }

    private fun open(path: String): InputStream? {
        for (directory in directories) {
            val file = File(directory, path)
            if (file.isFile) {
                return file.inputStream()
            }
        }
        for (jar in jars) {
            val entry = jar.getEntry(path)
            if (entry != null) {
                return jar.getInputStream(entry)
            }
        }
        // Class files are never encapsulated in JDK modules.
        return ClassLoader.getPlatformClassLoader().getResourceAsStream(path)
    }

    override fun close() {
        jars.forEach(ZipFile::close)
    }
}
