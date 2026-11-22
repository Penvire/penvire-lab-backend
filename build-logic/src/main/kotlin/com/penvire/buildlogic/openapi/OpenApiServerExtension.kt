package com.penvire.buildlogic.openapi

import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property

abstract class OpenApiServerExtension {
    abstract val sourceSpec: RegularFileProperty
    abstract val serverPackage: Property<String>
}
