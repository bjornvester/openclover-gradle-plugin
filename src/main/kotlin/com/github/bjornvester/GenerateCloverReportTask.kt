package com.github.bjornvester

import com.atlassian.clover.reporters.CloverReporter
import com.atlassian.clover.reporters.Current
import com.atlassian.clover.reporters.Format
import com.github.bjornvester.OpenCloverPlugin.Companion.logDbDirs
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*
import org.gradle.language.base.plugins.LifecycleBasePlugin
import java.io.File

//@CacheableTask // TODO: Figure out if it can be cached
open class GenerateCloverReportTask : DefaultTask() {
    @get:Input
    var reportTitle: Property<String> = project.objects.property(String::class.java)

    @get:InputDirectory
    @get:SkipWhenEmpty
    var dbDir: DirectoryProperty = project.objects.directoryProperty()

    @get:InputFiles
    @get:SkipWhenEmpty
    var testResults: ListProperty<File> = project.objects.listProperty(File::class.java)

    @get:InputFiles
    var testSourcesFiles: ListProperty<File> = project.objects.listProperty(File::class.java)

    @get:OutputDirectory
    var reportDir: DirectoryProperty = project.objects.directoryProperty()

    init {
        group = LifecycleBasePlugin.VERIFICATION_GROUP
        description = "Generates an OpenClover code coverage report."
    }

    @TaskAction
    fun generateReport() {
        val config = Current()
        config.initString = dbDir.file("clover.db").get().toString()
        config.outFile = reportDir.get().asFile
        config.mainFileName = "index.html"
        config.format = Format.DEFAULT_HTML
        config.title = reportTitle.get()

        testSourcesFiles.get().forEach {
            project.logger.info("Adding test source to configuration: $it")
            config.addTestSourceFile(it)
        }

        testResults.get().forEach {
            config.addTestResultFile(it)
        }

        CloverReporter.buildReporter(config).execute()
        logDbDirs(this, dbDir, project.objects.directoryProperty())
        project.logger.info("Generated OpenClover report in ${reportDir.get()}")
    }

}
