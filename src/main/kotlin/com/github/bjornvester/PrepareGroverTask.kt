package com.github.bjornvester

import com.atlassian.clover.CloverNames
import com.atlassian.clover.cfg.instr.InstrumentationConfig
import com_atlassian_clover.CloverVersionInfo
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.artifacts.Configuration
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import java.util.jar.JarFile

open class PrepareGroverTask : DefaultTask() {
    @get:Input
    val cloverJars: Property<Configuration> = project.objects.property(Configuration::class.java)

    @get:InputDirectory
    val inputSourceDirectory: DirectoryProperty = project.objects.directoryProperty()

    @get:InputDirectory
    val inputSourceTestDirectory: DirectoryProperty = project.objects.directoryProperty()

    @get:Input
    var dbTmpDirPath: Property<String> = project.objects.property(String::class.java)

    @get:OutputDirectory
    val outputDirectory: DirectoryProperty = project.objects.directoryProperty()

    @TaskAction
    fun prepareGrover() {
        project.delete(outputDirectory)
        project.mkdir(outputDirectory)

        unpackGroverJar()
        createInstrumentationConfiguration()
        createGroovyCompilerConfiguration()
    }

    private fun unpackGroverJar() {
        if (cloverJars.get().files.size != 1) {
            throw GradleException("Expected a single file in the clover configuration, but found the following: " + cloverJars.get().files)
        }

        val embeddedGroverJarName = "embeddedjars/clover${CloverVersionInfo.RELEASE_NUM}/grover.jar"
        val cloverJarFile = cloverJars.get().files.first()
        val cloverJar = JarFile(cloverJarFile)
        val groverEntry = cloverJar.getJarEntry(embeddedGroverJarName)
                ?: throw GradleException("Could not find the grover jar file in $cloverJarFile with path $embeddedGroverJarName")
        cloverJar.getInputStream(groverEntry).use { groverInputStream ->
            outputDirectory.file("grover.jar").get().asFile.outputStream().use { groverOutputStream ->
                groverInputStream.copyTo(groverOutputStream)
            }
        }
    }

    private fun createInstrumentationConfiguration() {
        val config = InstrumentationConfig()
        config.setInitstring("${dbTmpDirPath.get()}/clover.db")
        config.includedFiles = inputSourceDirectory.asFileTree.files + inputSourceTestDirectory.asFileTree.files
        config.saveToFile(outputDirectory.file(CloverNames.getGroverConfigFileName()).get().asFile)
    }

    private fun createGroovyCompilerConfiguration() {
        // Switching logging level breaks up-to-date checking, and forces a re-run of the compilation.
        // Not sure if there is a way around this.
        val compilerConfig = this.javaClass.getResource("/CompilerConfig.groovy").readText()

        val configFileDebug = outputDirectory.file("CompilerConfigDebug.groovy").get().asFile
        configFileDebug.createNewFile()
        configFileDebug.writeText(compilerConfig.replaceFirst("REPLACE_ME_LOGGING_LEVEL", "debug"))

        val configFileVerbose = outputDirectory.file("CompilerConfigVerbose.groovy").get().asFile
        configFileVerbose.createNewFile()
        configFileVerbose.writeText(compilerConfig.replaceFirst("REPLACE_ME_LOGGING_LEVEL", "verbose"))
    }

}
