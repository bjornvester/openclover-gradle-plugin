package com.github.bjornvester

import com.github.bjornvester.OpenCloverPlugin.Companion.logDbDirs
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*
import org.gradle.api.tasks.compile.GroovyCompile
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.testing.Test

@CacheableTask
open class CloverTestTask : Test() {
    @get:InputDirectory
    var dbDirInput: DirectoryProperty = project.objects.directoryProperty()

    @get:OutputDirectory
    var dbDirOutput: DirectoryProperty = project.objects.directoryProperty()

    @get:Input
    var dbTmpDirPath: Property<String> = project.objects.property(String::class.java)

    init {
        group = JavaBasePlugin.VERIFICATION_GROUP
        description = "Runs the unit test using OpenClover instrumented classes."
    }

    @TaskAction
    override fun executeTests() {
        val compileJavaTask = project.tasks.named("compileJava", JavaCompile::class.java).get()
        val compileJavaTestTask = project.tasks.named("compileTestJava", JavaCompile::class.java).get()
        val compileInstJavaTask = project.tasks.named(OpenCloverPlugin.CLOVER_TASK_COMPILE_INST_JAVA, JavaCompile::class.java).get()
        val compileInstJavaTestTask = project.tasks.named(OpenCloverPlugin.CLOVER_TASK_COMPILE_INST_JAVA_TEST, JavaCompile::class.java).get()
        val compileGroovyTask = project.tasks.findByName("compileGroovy") as? GroovyCompile
        val compileGroovyTestTask = project.tasks.findByName("compileTestGroovy") as? GroovyCompile

        val newClasspath = classpath.map {
            val compileInstGroovyTask = project.tasks.findByName(OpenCloverPlugin.CLOVER_TASK_COMPILE_INST_GROOVY_JOINT_JAVA) as? GroovyCompile
            val compileInstGroovyTestTask = project.tasks.findByName(OpenCloverPlugin.CLOVER_TASK_COMPILE_INST_GROOVY_JOINT_JAVA_TEST) as? GroovyCompile
            when (it) {
                compileJavaTask.destinationDir -> {
                    compileInstJavaTask.destinationDir
                }
                compileJavaTestTask.destinationDir -> {
                    compileInstJavaTestTask.destinationDir
                }
                compileGroovyTask?.destinationDir -> {
                    compileInstGroovyTask?.destinationDir
                }
                compileGroovyTestTask?.destinationDir -> {
                    compileInstGroovyTestTask?.destinationDir
                }
                else -> it
            }
        }
        project.logger.debug("New classpath for task ${name}: $newClasspath")
        val cloverConfig = project.configurations.getByName("openclover")
        classpath = project.files(newClasspath) + cloverConfig

        val dbDirTmp = project.file(dbTmpDirPath)

        project.delete(dbDirTmp)
        project.mkdir(dbDirTmp)
        project.copy {
            from(dbDirInput)
            into(dbDirTmp)
        }

        super.executeTests()

        project.sync {
            from(dbDirTmp)
            into(dbDirOutput)
        }
        project.delete(dbDirTmp)
        logDbDirs(this, dbDirInput, dbDirOutput)
    }
}