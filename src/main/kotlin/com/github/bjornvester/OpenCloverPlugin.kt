package com.github.bjornvester

import com.atlassian.clover.Logger
import com_atlassian_clover.CloverVersionInfo
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.Configuration
import org.gradle.api.file.Directory
import org.gradle.api.file.FileCollection
import org.gradle.api.file.RegularFile
import org.gradle.api.internal.tasks.testing.junit.JUnitTestFramework
import org.gradle.api.internal.tasks.testing.junitplatform.JUnitPlatformTestFramework
import org.gradle.api.internal.tasks.testing.testng.TestNGTestFramework
import org.gradle.api.plugins.BasePlugin
import org.gradle.api.plugins.GroovyPlugin
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.compile.GroovyCompile
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.testing.Test
import org.gradle.util.GradleVersion

@Suppress("unused")
class OpenCloverPlugin : Plugin<Project> {
    companion object {
        const val MINIMUM_GRADLE_VERSION = "5.4"
        const val PLUGIN_ID = "com.github.bjornvester.openclover"
        private const val CLOVER_DB_TMP_DIR = "clover-db-tmp" // TODO: Make this configurable

        const val CLOVER_TASK_INST_JAVA = "cloverInstrumentJava"
        const val CLOVER_TASK_INST_JAVA_TEST = "cloverInstrumentJavaTest"
        const val CLOVER_TASK_INST_GROOVY_JOINT_JAVA = "cloverInstrumentGroovyJointJava"
        const val CLOVER_TASK_INST_GROOVY_JOINT_JAVA_TEST = "cloverInstrumentGroovyJointJavaTest"
        const val CLOVER_TASK_PREPARE_GROVER = "cloverPrepareGrover"
        const val CLOVER_TASK_TEST = "cloverTest"
        const val CLOVER_TASK_REPORT = "cloverReport"
        const val CLOVER_TASK_MERGED_REPORT = "cloverMergedReport"
        const val CLOVER_TASK_COMPILE_INST_JAVA = "cloverCompileInstrumentJava"
        const val CLOVER_TASK_COMPILE_INST_JAVA_TEST = "cloverCompileInstrumentJavaTest"
        const val CLOVER_TASK_COMPILE_INST_GROOVY_JOINT_JAVA = "cloverCompileInstrumentGroovyJointJava"
        const val CLOVER_TASK_COMPILE_INST_GROOVY_JOINT_JAVA_TEST = "cloverCompileInstrumentGroovyJointJavaTest"

        fun <T : Task?> getCloverDbOutputDirFromProvider(taskProvider: TaskProvider<T>): Provider<Directory> =
            taskProvider.map { getCloverDbOutputDir(it!!).get() }

        fun getCloverDbOutputDir(task: Task): Provider<Directory> = task.project.layout.buildDirectory.dir("clover-db-${task.name}")

        private fun getCloverDbTmpDir(project: Project): String = project.layout.buildDirectory.dir(CLOVER_DB_TMP_DIR).get().asFile.absolutePath

        fun logDbDirs(task: Task, dbDirInput: Provider<Directory>, dbDirOutput: Provider<Directory>) {
            val dbDirInputString = when {
                dbDirInput.isPresent -> dbDirInput.get().toString()
                else -> "-"
            }
            val dbDirOutputString = when {
                dbDirOutput.isPresent -> dbDirOutput.get().toString()
                else -> "-"
            }

            task.project.logger.info("Task: [${task.project.name}:${task.name}], Input DB: [$dbDirInputString], Output DB: [$dbDirOutputString]")
        }

        fun logDbDirs(task: Task, dbDirsInput: ListProperty<RegularFile>, dbDirOutput: Provider<Directory>) {
            task.project.logger.info(
                "Task: [${task.project.name}:${task.name}], Input DB: [${
                    dbDirsInput.get().map { it.asFile }
                }], Output DB: [${dbDirOutput.get()}]"
            )
        }
    }

    override fun apply(project: Project) {
        if (project.logger.isDebugEnabled) {
            Logger.setDebug(true)
        } else {
            Logger.setVerbose(true)
        }
        project.logger.debug("Applying $PLUGIN_ID to project ${project.name}")
        project.plugins.apply(BasePlugin::class.java)
        verifyGradleVersion()
        project.extensions.create("openclover", OpenCloverPluginExtension::class.java, project)
        val cloverConfig = createCloverConfig(project)

        addTaskCloverMergedReport(project)

        project.plugins.withType(JavaPlugin::class.java) {
            addTaskInstJava(project)
            addTaskInstJavaTest(project)
            addTaskCompileInstJava(project)
            addTaskCompileInstJavaTest(project)
            addTaskCloverTest(project/*, cloverConfig*/) // TODO: Support custom test tasks as well
            addTaskCloverReport(project)
        }

        project.plugins.withType(GroovyPlugin::class.java) {
            addTaskInstGroovyJointJava(project)
            addTaskInstGroovyJointJavaTest(project)
            addTaskPrepareGrover(project, cloverConfig)
            addTaskCompileInstGroovyJointJava(project)
            addTaskCompileInstGroovyJointJavaTest(project)
        }
    }

    private fun addTaskInstJava(project: Project) {
        project.tasks.register(CLOVER_TASK_INST_JAVA, InstrumentTask::class.java) {
            var inputDir = project.layout.projectDirectory.dir("src/main/java")
            if (inputDir.asFile.exists()) {
                this.inputDir.set(inputDir)
            }
            sourcesOutputDir.set(project.layout.buildDirectory.dir("/generated/sources/openclover/main/java"))
            dbTmpDirPath.set(getCloverDbTmpDir(project))
            dbDirOutput.set(getCloverDbOutputDir(this))
        }
    }

    private fun addTaskInstJavaTest(project: Project) {
        project.tasks.register(CLOVER_TASK_INST_JAVA_TEST, InstrumentTask::class.java) {
            val compileJavaTestTask = project.tasks.named("compileTestJava", JavaCompile::class.java).get()
            val instJavaTask = project.tasks.named(CLOVER_TASK_INST_JAVA, InstrumentTask::class.java)
            val inputDir = project.layout.projectDirectory.dir("src/test/java")
            if (inputDir.asFile.exists()) {
                this.inputDir.set(inputDir)
            }
            sourcesOutputDir.set(project.layout.buildDirectory.dir("/generated/sources/openclover/test/java"))
            dbDirInput.set(instJavaTask.map { it.dbDirOutput.get() })
            dbTmpDirPath.set(getCloverDbTmpDir(project))
            dbDirOutput.set(getCloverDbOutputDir(this))
            dependsOn(instJavaTask)
            project.rootProject.tasks.withType(GenerateCloverReportTask::class.java) {
                testSourcesFiles.addAll(compileJavaTestTask.source)
            }
        }
    }

    private fun addTaskCompileInstJava(project: Project) {
        project.tasks.register(CLOVER_TASK_COMPILE_INST_JAVA, JavaCompile::class.java) {
            val instJavaTask = project.tasks.named(CLOVER_TASK_INST_JAVA, InstrumentTask::class.java).get()
            val compileJavaTask = project.tasks.named("compileJava", JavaCompile::class.java).get()
            val classesDir = project.layout.buildDirectory.dir("classes-openclover/java/${compileJavaTask.destinationDir.name}")
            group = BasePlugin.BUILD_GROUP
            description = "Compiles the OpenClover instrumented source code."
            source = instJavaTask.sourcesOutputDir.asFileTree
            destinationDir = classesDir.get().asFile
            classpath = compileJavaTask.classpath + project.configurations.findByName("openclover") as FileCollection
            sourceCompatibility = compileJavaTask.sourceCompatibility
            targetCompatibility = compileJavaTask.targetCompatibility
            dependsOn(instJavaTask)
            dependsOn(compileJavaTask.dependsOn)
            // TODO: Copy compiler options as well
        }
    }

    private fun addTaskCompileInstJavaTest(project: Project) {
        project.tasks.register(CLOVER_TASK_COMPILE_INST_JAVA_TEST, JavaCompile::class.java) {
            val instJavaTestTask = project.tasks.named(CLOVER_TASK_INST_JAVA_TEST, InstrumentTask::class.java).get()
            val compileJavaTestTask = project.tasks.named("compileTestJava", JavaCompile::class.java).get()
            val classesDir = project.layout.buildDirectory.dir("classes-openclover/java/${compileJavaTestTask.destinationDir.name}")
            group = BasePlugin.BUILD_GROUP
            description = "Compiles the OpenClover instrumented source code."
            source = instJavaTestTask.sourcesOutputDir.asFileTree
            destinationDir = classesDir.get().asFile
            classpath = compileJavaTestTask.classpath + project.configurations.findByName("openclover") as FileCollection
            sourceCompatibility = compileJavaTestTask.sourceCompatibility
            targetCompatibility = compileJavaTestTask.targetCompatibility
            dependsOn(instJavaTestTask)
            dependsOn(compileJavaTestTask.dependsOn)
            // TODO: Copy compiler options as well
        }
    }

    private fun addTaskCloverTest(project: Project) {
        val testTask = project.tasks.register(CLOVER_TASK_TEST, CloverTestTask::class.java) {
            val compileInstJavaTask = project.tasks.named(CLOVER_TASK_COMPILE_INST_JAVA, JavaCompile::class.java).get()
            val compileInstJavaTestTask = project.tasks.named(CLOVER_TASK_COMPILE_INST_JAVA_TEST, JavaCompile::class.java).get()
            val cloverInstJavaTestTask = project.tasks.named(CLOVER_TASK_INST_JAVA_TEST, InstrumentTask::class.java)
            val testTask = project.tasks.named("test", Test::class.java).get()
            val cloverTestTask = this

            dependsOn(compileInstJavaTask)
            dependsOn(compileInstJavaTestTask)
            dependsOn(CLOVER_TASK_COMPILE_INST_JAVA_TEST)

            testClassesDirs = project.files(compileInstJavaTestTask.destinationDir)
            dbDirInput.set(cloverInstJavaTestTask.get().dbDirOutput)
            dbTmpDirPath.set(getCloverDbTmpDir(project))
            dbDirOutput.set(getCloverDbOutputDir(cloverTestTask))

            project.plugins.withType(GroovyPlugin::class.java) {
                val compileInstGroovyTask = project.tasks.named(CLOVER_TASK_COMPILE_INST_GROOVY_JOINT_JAVA, GroovyCompile::class.java)
                val compileInstGroovyTestTask = project.tasks.named(CLOVER_TASK_COMPILE_INST_GROOVY_JOINT_JAVA_TEST, GroovyCompile::class.java)
                cloverTestTask.dependsOn(compileInstGroovyTestTask)
                cloverTestTask.dependsOn(compileInstGroovyTask)
                cloverTestTask.testClassesDirs =
                    project.files(compileInstJavaTestTask.destinationDir) + project.files(compileInstGroovyTestTask.get().destinationDir) // TODO
                cloverTestTask.dbDirInput.set(getCloverDbOutputDir(compileInstGroovyTestTask.get()))
            }

            cloverTestTask.environment = testTask.environment
            // TODO: Copy the rest of the configurations, or document what is missing (like "testLogging")

            when (testTask.testFramework) {
                is JUnitPlatformTestFramework -> cloverTestTask.useJUnitPlatform()
                is JUnitTestFramework -> cloverTestTask.useJUnit()
                is TestNGTestFramework -> cloverTestTask.useTestNG()
            }
            project.logger.debug("Registered test task: ${cloverTestTask.name}")
        }

        // Add this test task to the mergedb task, if present
        project.rootProject.tasks.withType(MergeDbTask::class.java) {
            inputFiles.add(getCloverDbOutputDirFromProvider(testTask).map { it.file("clover.db") }) // TODO: Not lazy
            dependsOn(testTask)
        }

        project.rootProject.tasks.withType(GenerateCloverReportTask::class.java) {
            testResults.addAll(testTask.map { testTask ->
                testTask.project.fileTree(testTask.reports.junitXml.destination).matching {
                    include("*.xml")
                }
            })

            dependsOn(testTask)
        }
    }

    private fun addTaskCloverReport(project: Project) {
        project.tasks.register(CLOVER_TASK_REPORT, GenerateCloverReportTask::class.java) {
            val cloverTestTask = project.tasks.named(CLOVER_TASK_TEST, Test::class.java)
            val compileJavaTestTask = project.tasks.named("compileTestJava", JavaCompile::class.java)
            val instGroovyTask = project.tasks.findByName(CLOVER_TASK_INST_GROOVY_JOINT_JAVA_TEST) as? InstrumentTask
            dependsOn(cloverTestTask)
            dbDir.set(getCloverDbOutputDir(cloverTestTask.get()))

            project.fileTree(cloverTestTask.get().reports.junitXml.destination).matching {
                include("*.xml")
            }.forEach {
                testResults.add(it)
            }

            testSourcesFiles.addAll(compileJavaTestTask.get().source)
            if (instGroovyTask != null) {
                testSourcesFiles.addAll(instGroovyTask.sourcesOutputDir.map { it.asFileTree.files })
            }
        }
    }

    private fun addTaskCloverMergedReport(project: Project) {
        if (project == project.rootProject && project.subprojects.isNotEmpty()) {
            val cloverMergeDbTask = project.tasks.register("cloverMergeDb", MergeDbTask::class.java) {
                dbDir.set(getCloverDbOutputDir(this))
            }
            project.tasks.register(CLOVER_TASK_MERGED_REPORT, GenerateCloverReportTask::class.java) {
                dependsOn(cloverMergeDbTask)
                dbDir.set(cloverMergeDbTask.get().dbDir)
            }
        }
    }

    private fun addTaskInstGroovyJointJava(project: Project) {
        project.tasks.register(CLOVER_TASK_INST_GROOVY_JOINT_JAVA, InstrumentTask::class.java) {
            val instJavaTestTask = project.tasks.named(CLOVER_TASK_INST_JAVA_TEST, InstrumentTask::class.java).get()
            val inputDir = project.layout.projectDirectory.dir("src/main/groovy")
            if (inputDir.asFile.exists()) {
                this.inputDir.set(inputDir)
            }
            sourcesOutputDir.set(project.layout.buildDirectory.dir("generated/sources/openclover/main/groovy"))
            dbDirInput.set(instJavaTestTask.dbDirOutput)
            dbTmpDirPath.set(getCloverDbTmpDir(project))
            dbDirOutput.set(getCloverDbOutputDir(this))
            dependsOn(instJavaTestTask)
        }
    }

    private fun addTaskInstGroovyJointJavaTest(project: Project) {
        project.tasks.register(CLOVER_TASK_INST_GROOVY_JOINT_JAVA_TEST, InstrumentTask::class.java) {
            val instGroovyTask = project.tasks.named(CLOVER_TASK_INST_GROOVY_JOINT_JAVA, InstrumentTask::class.java).get()
            val inputDir = project.layout.projectDirectory.dir("src/test/groovy")
            if (inputDir.asFile.exists()) {
                this.inputDir.set(inputDir)
            }
            sourcesOutputDir.set(project.layout.buildDirectory.dir("generated/sources/openclover/test/groovy"))
            dbDirInput.set(instGroovyTask.dbDirOutput)
            dbTmpDirPath.set(getCloverDbTmpDir(project))
            dbDirOutput.set(getCloverDbOutputDir(this))
            dependsOn(instGroovyTask)
        }
    }

    private fun addTaskPrepareGrover(project: Project, cloverConfig: Configuration?) {
        project.tasks.register(CLOVER_TASK_PREPARE_GROVER, PrepareGroverTask::class.java) {
            val instGroovyTask = project.tasks.named(CLOVER_TASK_INST_GROOVY_JOINT_JAVA, InstrumentTask::class.java).get()
            val instGroovyTestTask = project.tasks.named(CLOVER_TASK_INST_GROOVY_JOINT_JAVA_TEST, InstrumentTask::class.java).get()
            dependsOn(instGroovyTask)
            dependsOn(instGroovyTestTask)
            cloverJars.set(cloverConfig)
            inputSourceDirectory.set(instGroovyTask.sourcesOutputDir)
            inputSourceTestDirectory.set(instGroovyTestTask.sourcesOutputDir)
            dbTmpDirPath.set(getCloverDbTmpDir(project))
            outputDirectory.set(project.layout.buildDirectory.dir("clover-grover"))
        }
    }

    private fun addTaskCompileInstGroovyJointJava(project: Project) {
        project.tasks.register(CLOVER_TASK_COMPILE_INST_GROOVY_JOINT_JAVA, GroovyCompile::class.java) {
            val instGroovyTask = project.tasks.named(CLOVER_TASK_INST_GROOVY_JOINT_JAVA, InstrumentTask::class.java).get()
            val instGroovyTestTask = project.tasks.named(CLOVER_TASK_INST_GROOVY_JOINT_JAVA_TEST, InstrumentTask::class.java).get()
            val compileGroovyTask = project.tasks.named("compileGroovy", GroovyCompile::class.java).get()
            val cloverPrepareGroverTask = project.tasks.named(CLOVER_TASK_PREPARE_GROVER, PrepareGroverTask::class.java).get()
            val classesDir = project.layout.buildDirectory.dir("classes-openclover/groovy/${compileGroovyTask.destinationDir.name}")
            group = BasePlugin.BUILD_GROUP
            description = "Compiles the OpenClover instrumented source code."
            source = instGroovyTask.sourcesOutputDir.asFileTree
            destinationDir = classesDir.get().asFile
            classpath = compileGroovyTask.classpath + project.configurations.findByName("openclover") as FileCollection
            sourceCompatibility = compileGroovyTask.sourceCompatibility
            targetCompatibility = compileGroovyTask.targetCompatibility
            dependsOn(instGroovyTask)
            dependsOn(compileGroovyTask.dependsOn)
            // TODO: Copy compiler options as well (both java and groovy options)

            // Grover stuff
            dependsOn(cloverPrepareGroverTask)
            inputs.dir(cloverPrepareGroverTask.outputDirectory)
            classpath += project.files(cloverPrepareGroverTask.outputDirectory.file("grover.jar"))
            classpath += project.files(cloverPrepareGroverTask.outputDirectory.get())
            groovyOptions.configurationScript = when {
                project.logger.isDebugEnabled -> project.file(cloverPrepareGroverTask.outputDirectory.file("CompilerConfigDebug.groovy"))
                else -> project.file(cloverPrepareGroverTask.outputDirectory.file("CompilerConfigVerbose.groovy"))
            }

            val dbDirInput = instGroovyTestTask.dbDirOutput
            val dbDirOutput = getCloverDbOutputDir(this)
            val dbDirTmp = getCloverDbTmpDir(project)

            dependsOn(instGroovyTestTask)
            inputs.dir(dbDirInput)
            inputs.property("dbTmpDir", dbDirTmp)
            outputs.dir(dbDirOutput)

            doFirst {
                project.delete(dbDirTmp)
                project.mkdir(dbDirTmp)
                project.copy {
                    from(dbDirInput)
                    into(dbDirTmp)
                }
            }

            doLast {
                project.sync {
                    from(dbDirTmp)
                    into(dbDirOutput)
                }
                project.delete(dbDirTmp)
                logDbDirs(this, dbDirInput, dbDirOutput)
            }
        }
    }

    private fun addTaskCompileInstGroovyJointJavaTest(project: Project) {
        project.tasks.register(CLOVER_TASK_COMPILE_INST_GROOVY_JOINT_JAVA_TEST, GroovyCompile::class.java) {
            // TODO: Much of the following is the same as in addTaskCompileInstGroovyJointJava(). Try to generalize it.
            val compileInstGroovyTask = project.tasks.named(CLOVER_TASK_COMPILE_INST_GROOVY_JOINT_JAVA, GroovyCompile::class.java).get()
            val instGroovyTestTask = project.tasks.named(CLOVER_TASK_INST_GROOVY_JOINT_JAVA_TEST, InstrumentTask::class.java).get()
            val compileGroovyTestTask = project.tasks.named("compileTestGroovy", GroovyCompile::class.java).get()
            val cloverPrepareGroverTask = project.tasks.named(CLOVER_TASK_PREPARE_GROVER, PrepareGroverTask::class.java).get()
            val classesDir = project.layout.buildDirectory.dir("classes-openclover/groovy/${compileGroovyTestTask.destinationDir.name}")
            group = BasePlugin.BUILD_GROUP
            description = "Compiles the OpenClover instrumented source code."
            source = instGroovyTestTask.sourcesOutputDir.asFileTree
            destinationDir = classesDir.get().asFile
            classpath = compileGroovyTestTask.classpath + project.configurations.findByName("openclover") as FileCollection
            sourceCompatibility = compileGroovyTestTask.sourceCompatibility
            targetCompatibility = compileGroovyTestTask.targetCompatibility
            dependsOn(instGroovyTestTask)
            dependsOn(compileGroovyTestTask.dependsOn)
            // TODO: Copy compiler options as well (both java and groovy options)

            // Grover stuff
            dependsOn(cloverPrepareGroverTask)
            dependsOn(CLOVER_TASK_COMPILE_INST_GROOVY_JOINT_JAVA)
            inputs.dir(cloverPrepareGroverTask.outputDirectory)
            classpath += project.files(cloverPrepareGroverTask.outputDirectory.file("grover.jar"))
            classpath += project.files(cloverPrepareGroverTask.outputDirectory.get())
            groovyOptions.configurationScript = when {
                project.logger.isDebugEnabled -> project.file(cloverPrepareGroverTask.outputDirectory.file("CompilerConfigDebug.groovy"))
                else -> project.file(cloverPrepareGroverTask.outputDirectory.file("CompilerConfigVerbose.groovy"))
            }

            val dbDirInput = getCloverDbOutputDir(compileInstGroovyTask)
            val dbDirOutput = getCloverDbOutputDir(this)
            val dbDirTmp = getCloverDbTmpDir(project)

            dependsOn(compileInstGroovyTask)
            inputs.dir(dbDirInput)
            inputs.property("dbTmpDir", dbDirTmp)
            outputs.dir(dbDirOutput)

            doFirst {
                project.delete(dbDirTmp)
                project.mkdir(dbDirTmp)
                project.copy {
                    from(dbDirInput)
                    into(dbDirTmp)
                }
            }

            doLast {
                project.sync {
                    from(dbDirTmp)
                    into(dbDirOutput)
                }
                project.delete(dbDirTmp)
                logDbDirs(this, dbDirInput, dbDirOutput)
            }

            project.rootProject.tasks.withType(GenerateCloverReportTask::class.java) {
                testSourcesFiles.addAll(instGroovyTestTask.sourcesOutputDir.asFileTree)
            }
        }
    }

    private fun createCloverConfig(project: Project): Configuration? {
        val cloverConfig = project.configurations.maybeCreate("openclover")
        cloverConfig.isVisible = false

        cloverConfig.defaultDependencies {
            add(project.dependencies.create("org.openclover:clover:${CloverVersionInfo.getReleaseNum()}"))
        }

        return cloverConfig
    }


    private fun verifyGradleVersion() {
        if (GradleVersion.current() < GradleVersion.version(MINIMUM_GRADLE_VERSION)) {
            throw UnsupportedOperationException(
                "Plugin $PLUGIN_ID requires at least Gradle $MINIMUM_GRADLE_VERSION, " +
                        "but you are using ${GradleVersion.current().version}"
            )
        }
    }
}

/*
private fun getOpenCloverVersion(): String {
return CloverReporter::class.java.`package`.specificationVersion
}*/
