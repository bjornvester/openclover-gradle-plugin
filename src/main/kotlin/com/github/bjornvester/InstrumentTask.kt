package com.github.bjornvester

import com.atlassian.clover.cfg.instr.java.JavaInstrumentationConfig
import com.atlassian.clover.instr.java.FileInstrumentationSource
import com.atlassian.clover.instr.java.Instrumenter
import com.github.bjornvester.OpenCloverPlugin.Companion.logDbDirs
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.plugins.BasePlugin
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*
import java.io.StringWriter

@CacheableTask
open class InstrumentTask : DefaultTask() {
    companion object {
        val SUPPORTED_EXTENSIONS = arrayOf("java", "groovy")
    }

    @get:Optional
    @get:InputDirectory
    var inputDir: DirectoryProperty = project.objects.directoryProperty()

    @get:Input
    var dbTmpDirPath: Property<String> = project.objects.property(String::class.java)

    @get:OutputDirectory
    var sourcesOutputDir: DirectoryProperty = project.objects.directoryProperty()

    @get:Optional
    @get:InputDirectory
    var dbDirInput: DirectoryProperty = project.objects.directoryProperty()

    @get:OutputDirectory
    var dbDirOutput: DirectoryProperty = project.objects.directoryProperty()

    init {
        group = BasePlugin.BUILD_GROUP
        description = "Generates OpenClover instrumented source code."
    }

    @TaskAction
    fun doInstrumentation() {
        var config = JavaInstrumentationConfig()
        config.setInitstring("${dbTmpDirPath.get()}/clover.db")
        project.delete(dbTmpDirPath)
        project.mkdir(dbTmpDirPath)

        if (dbDirInput.isPresent) {
            project.copy {
                from(dbDirInput.get())
                into(dbTmpDirPath)
            }
        }

        var isJavaCodePresent = false
        var javaInstrumenter = Instrumenter(config)
        if (inputDir.isPresent) {
            inputDir.asFileTree.forEach { inputFile ->
                val relativeTarget = inputFile.relativeTo(inputDir.get().asFile).path
                val targetFile = sourcesOutputDir.file(relativeTarget).get().asFile
                if (targetFile.extension !in SUPPORTED_EXTENSIONS) return@forEach

                targetFile.parentFile.mkdirs()

                when (targetFile.extension) {
                    "java" -> {
                        if (!isJavaCodePresent) {
                            javaInstrumenter.startInstrumentation()
                            isJavaCodePresent = true
                        }
                        val stringWriter = StringWriter()
                        javaInstrumenter.instrument(FileInstrumentationSource(inputFile, "UTF-8"), stringWriter, "UTF-8")
                        targetFile.writeText(stringWriter.toString())
                    }
                    "groovy" -> {
                        inputFile.copyTo(targetFile, true)
                    }
                    else -> {
                        project.logger.debug("Skipping file ${inputFile.path} as it is not a Java or Groovy file")
                    }
                }
            }
        }

        if (isJavaCodePresent) {
            javaInstrumenter.endInstrumentation()
        }

        project.sync {
            from(dbTmpDirPath)
            into(dbDirOutput)
        }
        project.delete(dbTmpDirPath)

        logDbDirs(this, dbDirInput, dbDirOutput)
    }
}
