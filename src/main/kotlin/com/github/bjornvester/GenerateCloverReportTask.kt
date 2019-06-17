package com.github.bjornvester

import com.atlassian.clover.reporters.CloverReporter
import com.atlassian.clover.reporters.Current
import com.atlassian.clover.reporters.Format
import com.github.bjornvester.OpenCloverPlugin.Companion.logDbDirs
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*
import org.gradle.language.base.plugins.LifecycleBasePlugin
import java.io.File

//@CacheableTask // TODO: Figure out if it can (or should) be cached
open class GenerateCloverReportTask : DefaultTask() {
    companion object {
        val REPORT_TYPE_MAP = mapOf(
                "HTML" to Format.DEFAULT_HTML,
                "XML" to Format.DEFAULT_XML,
                "JSON" to Format.DEFAULT_JSON,
                "PDF" to Format.DEFAULT_PDF,
                "TEXT" to Format.DEFAULT_TEXT
        )
    }

    @get:Input
    var reportTitle: Property<String> = getCloverExtension().reportTitle

    @get:InputDirectory
    @get:SkipWhenEmpty
    var dbDir: DirectoryProperty = project.objects.directoryProperty()

    @get:InputFiles
    @get:SkipWhenEmpty
    var testResults: ListProperty<File> = project.objects.listProperty(File::class.java)

    @get:InputFiles
    var testSourcesFiles: ListProperty<File> = project.objects.listProperty(File::class.java)

    @get:Input
    var reportTypes: ListProperty<String> = getCloverExtension().reportTypes

    @get:OutputDirectory
    var reportDir: DirectoryProperty = getCloverExtension().reportDir

    init {
        group = LifecycleBasePlugin.VERIFICATION_GROUP
        description = "Generates an OpenClover code coverage report."
    }

    @TaskAction
    fun generateReport() {
        if (reportTypes.get().isEmpty()) {
            throw GradleException("The reportTypes property must not be empty")
        }

        reportTypes.get().forEach { reportType ->
            val config = Current()
            config.initString = dbDir.file("clover.db").get().toString()
            config.outFile = when (reportType) {
                "HTML" -> reportDir.get().asFile
                "TEXT" -> reportDir.get().file("openclover.txt").asFile
                else -> reportDir.get().file("openclover.${reportType.toLowerCase()}").asFile
            }
            config.mainFileName = "index.html"
            config.title = reportTitle.get()
            config.format = REPORT_TYPE_MAP[reportType]
                    ?: throw GradleException("Unknown report type '$reportType'. Supported types are ${REPORT_TYPE_MAP.keys}")

            testSourcesFiles.get().forEach {
                project.logger.info("Adding test source to configuration: $it")
                config.addTestSourceFile(it)
            }

            testResults.get().forEach {
                config.addTestResultFile(it)
            }

            CloverReporter.buildReporter(config).execute()
            logDbDirs(this, dbDir, project.objects.directoryProperty())
            project.logger.info("Generated OpenClover report of type $reportType in ${reportDir.get()}")
        }
    }

    private fun getCloverExtension() = project.extensions.getByName("openclover") as OpenCloverPluginExtension
}
