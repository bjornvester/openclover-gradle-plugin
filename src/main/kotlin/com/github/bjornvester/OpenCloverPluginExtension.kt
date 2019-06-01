package com.github.bjornvester

import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import javax.inject.Inject

open class OpenCloverPluginExtension @Inject constructor(project: Project) {
    var reportDir: DirectoryProperty = project.objects.directoryProperty().convention(project.layout.buildDirectory.dir("reports/clover-coverage"))
    var reportTitle: Property<String> = project.objects.property(String::class.java).convention(project.displayName)
}
