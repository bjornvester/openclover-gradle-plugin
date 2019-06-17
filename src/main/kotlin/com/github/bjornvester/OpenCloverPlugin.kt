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

        fun <T : Task?> getCloverDbOutputDirFromProvider(taskProvider: TaskProvider<T>): Provider<Directory> = taskProvider.map { getCloverDbOutputDir(it!!).get() }
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
            task.project.logger.info("Task: [${task.project.name}:${task.name}], Input DB: [${dbDirsInput.get().map { it.asFile }}], Output DB: [${dbDirOutput.get()}]")
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
        val extension = project.extensions.create("openclover", OpenCloverPluginExtension::class.java, project)
        val cloverConfig = createCloverConfig(project)

        addTaskCloverMergedReport(project, extension)

        project.plugins.withType(JavaPlugin::class.java) {
            addTaskInstJava(project)
            addTaskInstJavaTest(project)
            addTaskCompileInstJava(project)
            addTaskCompileInstJavaTest(project)
            addTaskCloverTest(project/*, cloverConfig*/) // TODO: Support custom test tasks as well
            addTaskCloverReport(project, extension)
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
        project.tasks.register(CLOVER_TASK_INST_JAVA, InstrumentTask::class.java) { instJavaTask ->
            var inputDir = project.layout.projectDirectory.dir("src/main/java")
            if (inputDir.asFile.exists()) {
                instJavaTask.inputDir.set(inputDir)
            }
            instJavaTask.sourcesOutputDir.set(project.layout.buildDirectory.dir("/generated/sources/openclover/main/java"))
            instJavaTask.dbTmpDirPath.set(getCloverDbTmpDir(project))
            instJavaTask.dbDirOutput.set(getCloverDbOutputDir(instJavaTask))
        }
    }

    private fun addTaskInstJavaTest(project: Project) {
        project.tasks.register(CLOVER_TASK_INST_JAVA_TEST, InstrumentTask::class.java) { instJavaTestTask ->
            val compileJavaTestTask = project.tasks.named("compileTestJava", JavaCompile::class.java).get()
            val instJavaTask = project.tasks.named(CLOVER_TASK_INST_JAVA, InstrumentTask::class.java)
            val inputDir = project.layout.projectDirectory.dir("src/test/java")
            if (inputDir.asFile.exists()) {
                instJavaTestTask.inputDir.set(inputDir)
            }
            instJavaTestTask.sourcesOutputDir.set(project.layout.buildDirectory.dir("/generated/sources/openclover/test/java"))
            instJavaTestTask.dbDirInput.set(instJavaTask.map { it.dbDirOutput.get() })
            instJavaTestTask.dbTmpDirPath.set(getCloverDbTmpDir(project))
            instJavaTestTask.dbDirOutput.set(getCloverDbOutputDir(instJavaTestTask))
            instJavaTestTask.dependsOn(instJavaTask)
            project.rootProject.tasks.withType(GenerateCloverReportTask::class.java) { mergeReportTask ->
                mergeReportTask.testSourcesFiles.addAll(compileJavaTestTask.source)
            }
        }
    }

    private fun addTaskCompileInstJava(project: Project) {
        project.tasks.register(CLOVER_TASK_COMPILE_INST_JAVA, JavaCompile::class.java) { compileInstJavaTask ->
            val instJavaTask = project.tasks.named(CLOVER_TASK_INST_JAVA, InstrumentTask::class.java).get()
            val compileJavaTask = project.tasks.named("compileJava", JavaCompile::class.java).get()
            val classesDir = project.layout.buildDirectory.dir("classes-openclover/java/${compileJavaTask.destinationDir.name}")
            compileInstJavaTask.group = BasePlugin.BUILD_GROUP
            compileInstJavaTask.description = "Compiles the OpenClover instrumented source code."
            compileInstJavaTask.source = instJavaTask.sourcesOutputDir.asFileTree
            compileInstJavaTask.destinationDir = classesDir.get().asFile
            compileInstJavaTask.classpath = compileJavaTask.classpath + project.configurations.findByName("openclover") as FileCollection
            compileInstJavaTask.sourceCompatibility = compileJavaTask.sourceCompatibility
            compileInstJavaTask.targetCompatibility = compileJavaTask.targetCompatibility
            compileInstJavaTask.dependsOn(instJavaTask)
            compileInstJavaTask.dependsOn(compileJavaTask.dependsOn)
            // TODO: Copy compiler options as well
        }
    }

    private fun addTaskCompileInstJavaTest(project: Project) {
        project.tasks.register(CLOVER_TASK_COMPILE_INST_JAVA_TEST, JavaCompile::class.java) { compileInstJavaTask ->
            val instJavaTestTask = project.tasks.named(CLOVER_TASK_INST_JAVA_TEST, InstrumentTask::class.java).get()
            val compileJavaTestTask = project.tasks.named("compileTestJava", JavaCompile::class.java).get()
            val classesDir = project.layout.buildDirectory.dir("classes-openclover/java/${compileJavaTestTask.destinationDir.name}")
            compileInstJavaTask.group = BasePlugin.BUILD_GROUP
            compileInstJavaTask.description = "Compiles the OpenClover instrumented source code."
            compileInstJavaTask.source = instJavaTestTask.sourcesOutputDir.asFileTree
            compileInstJavaTask.destinationDir = classesDir.get().asFile
            compileInstJavaTask.classpath = compileJavaTestTask.classpath + project.configurations.findByName("openclover") as FileCollection
            compileInstJavaTask.sourceCompatibility = compileJavaTestTask.sourceCompatibility
            compileInstJavaTask.targetCompatibility = compileJavaTestTask.targetCompatibility
            compileInstJavaTask.dependsOn(instJavaTestTask)
            compileInstJavaTask.dependsOn(compileJavaTestTask.dependsOn)
            // TODO: Copy compiler options as well
        }
    }

    private fun addTaskCloverTest(project: Project) {
        val testTask = project.tasks.register(CLOVER_TASK_TEST, CloverTestTask::class.java) { cloverTestTask ->
            val compileInstJavaTask = project.tasks.named(CLOVER_TASK_COMPILE_INST_JAVA, JavaCompile::class.java).get()
            val compileInstJavaTestTask = project.tasks.named(CLOVER_TASK_COMPILE_INST_JAVA_TEST, JavaCompile::class.java).get()
            val cloverInstJavaTestTask = project.tasks.named(CLOVER_TASK_INST_JAVA_TEST, InstrumentTask::class.java)
            val testTask = project.tasks.named("test", Test::class.java).get()

            cloverTestTask.dependsOn(compileInstJavaTask)
            cloverTestTask.dependsOn(compileInstJavaTestTask)
            cloverTestTask.dependsOn(CLOVER_TASK_COMPILE_INST_JAVA_TEST)

            cloverTestTask.testClassesDirs = project.files(compileInstJavaTestTask.destinationDir)
            cloverTestTask.dbDirInput.set(cloverInstJavaTestTask.get().dbDirOutput)
            cloverTestTask.dbTmpDirPath.set(getCloverDbTmpDir(project))
            cloverTestTask.dbDirOutput.set(getCloverDbOutputDir(cloverTestTask))

            project.plugins.withType(GroovyPlugin::class.java) {
                val compileInstGroovyTask = project.tasks.named(CLOVER_TASK_COMPILE_INST_GROOVY_JOINT_JAVA, GroovyCompile::class.java)
                val compileInstGroovyTestTask = project.tasks.named(CLOVER_TASK_COMPILE_INST_GROOVY_JOINT_JAVA_TEST, GroovyCompile::class.java)
                cloverTestTask.dependsOn(compileInstGroovyTestTask)
                cloverTestTask.dependsOn(compileInstGroovyTask)
                cloverTestTask.testClassesDirs = project.files(compileInstJavaTestTask.destinationDir) + project.files(compileInstGroovyTestTask.get().destinationDir) // TODO
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
        project.rootProject.tasks.withType(MergeDbTask::class.java) { cloverMergeDbTask ->
            cloverMergeDbTask.inputFiles.add(getCloverDbOutputDirFromProvider(testTask).map { it.file("clover.db") }) // TODO: Not lazy
            cloverMergeDbTask.dependsOn(testTask)
        }

        project.rootProject.tasks.withType(GenerateCloverReportTask::class.java) { mergeReportTask ->
            mergeReportTask.testResults.addAll(testTask.map { testTask ->
                testTask.project.fileTree(testTask.reports.junitXml.destination).matching { filter ->
                    filter.include("*.xml")
                }
            })

            mergeReportTask.dependsOn(testTask)
        }
    }

    private fun addTaskCloverReport(project: Project, extension: OpenCloverPluginExtension) {
        project.tasks.register(CLOVER_TASK_REPORT, GenerateCloverReportTask::class.java) { reportTask ->
            val cloverTestTask = project.tasks.named(CLOVER_TASK_TEST, Test::class.java)
            val compileJavaTestTask = project.tasks.named("compileTestJava", JavaCompile::class.java)
            val instGroovyTask = project.tasks.findByName(CLOVER_TASK_INST_GROOVY_JOINT_JAVA_TEST) as? InstrumentTask
            reportTask.dependsOn(cloverTestTask)
            reportTask.reportTitle = extension.reportTitle
            reportTask.dbDir.set(getCloverDbOutputDir(cloverTestTask.get()))

            project.fileTree(cloverTestTask.get().reports.junitXml.destination).matching {
                it.include("*.xml")
            }.forEach {
                reportTask.testResults.add(it)
            }

            reportTask.reportDir.set(extension.reportDir)
            reportTask.testSourcesFiles.addAll(compileJavaTestTask.get().source)
            if (instGroovyTask != null) {
                reportTask.testSourcesFiles.addAll(instGroovyTask.sourcesOutputDir.map { it.asFileTree.files })
            }
        }
    }

    private fun addTaskCloverMergedReport(project: Project, extension: OpenCloverPluginExtension) {
        if (project == project.rootProject && project.subprojects.isNotEmpty()) {
            val cloverMergeDbTask = project.tasks.register("cloverMergeDb", MergeDbTask::class.java) { mergeDbTask ->
                mergeDbTask.dbDir.set(getCloverDbOutputDir(mergeDbTask))
            }
            project.tasks.register(CLOVER_TASK_MERGED_REPORT, GenerateCloverReportTask::class.java) { reportTask ->
                reportTask.dependsOn(cloverMergeDbTask)
                reportTask.reportTitle = extension.reportTitle
                reportTask.dbDir.set(cloverMergeDbTask.get().dbDir)
                reportTask.reportDir.set(extension.reportDir)
            }
        }
    }

    private fun addTaskInstGroovyJointJava(project: Project) {
        project.tasks.register(CLOVER_TASK_INST_GROOVY_JOINT_JAVA, InstrumentTask::class.java) { instGroovyTask ->
            val instJavaTestTask = project.tasks.named(CLOVER_TASK_INST_JAVA_TEST, InstrumentTask::class.java).get()
            val inputDir = project.layout.projectDirectory.dir("src/main/groovy")
            if (inputDir.asFile.exists()) {
                instGroovyTask.inputDir.set(inputDir)
            }
            instGroovyTask.sourcesOutputDir.set(project.layout.buildDirectory.dir("/generated/sources/openclover/main/groovy"))
            instGroovyTask.dbDirInput.set(instJavaTestTask.dbDirOutput)
            instGroovyTask.dbTmpDirPath.set(getCloverDbTmpDir(project))
            instGroovyTask.dbDirOutput.set(getCloverDbOutputDir(instGroovyTask))
            instGroovyTask.dependsOn(instJavaTestTask)
        }
    }

    private fun addTaskInstGroovyJointJavaTest(project: Project) {
        project.tasks.register(CLOVER_TASK_INST_GROOVY_JOINT_JAVA_TEST, InstrumentTask::class.java) { instGroovyTestTask ->
            val instGroovyTask = project.tasks.named(CLOVER_TASK_INST_GROOVY_JOINT_JAVA, InstrumentTask::class.java).get()
            val inputDir = project.layout.projectDirectory.dir("src/test/groovy")
            if (inputDir.asFile.exists()) {
                instGroovyTestTask.inputDir.set(inputDir)
            }
            instGroovyTestTask.sourcesOutputDir.set(project.layout.buildDirectory.dir("/generated/sources/openclover/test/groovy"))
            instGroovyTestTask.dbDirInput.set(instGroovyTask.dbDirOutput)
            instGroovyTestTask.dbTmpDirPath.set(getCloverDbTmpDir(project))
            instGroovyTestTask.dbDirOutput.set(getCloverDbOutputDir(instGroovyTestTask))
            instGroovyTestTask.dependsOn(instGroovyTask)
        }
    }

    private fun addTaskPrepareGrover(project: Project, cloverConfig: Configuration?) {
        project.tasks.register(CLOVER_TASK_PREPARE_GROVER, PrepareGroverTask::class.java) { prepareGroverTask ->
            val instGroovyTask = project.tasks.named(CLOVER_TASK_INST_GROOVY_JOINT_JAVA, InstrumentTask::class.java).get()
            val instGroovyTestTask = project.tasks.named(CLOVER_TASK_INST_GROOVY_JOINT_JAVA_TEST, InstrumentTask::class.java).get()
            prepareGroverTask.dependsOn(instGroovyTask)
            prepareGroverTask.dependsOn(instGroovyTestTask)
            prepareGroverTask.cloverJars.set(cloverConfig)
            prepareGroverTask.inputSourceDirectory.set(instGroovyTask.sourcesOutputDir)
            prepareGroverTask.inputSourceTestDirectory.set(instGroovyTestTask.sourcesOutputDir)
            prepareGroverTask.dbTmpDirPath.set(getCloverDbTmpDir(project))
            prepareGroverTask.outputDirectory.set(project.layout.buildDirectory.dir("clover-grover"))
        }
    }

    private fun addTaskCompileInstGroovyJointJava(project: Project) {
        project.tasks.register(CLOVER_TASK_COMPILE_INST_GROOVY_JOINT_JAVA, GroovyCompile::class.java) { compileInstGroovyTask ->
            val instGroovyTask = project.tasks.named(CLOVER_TASK_INST_GROOVY_JOINT_JAVA, InstrumentTask::class.java).get()
            val instGroovyTestTask = project.tasks.named(CLOVER_TASK_INST_GROOVY_JOINT_JAVA_TEST, InstrumentTask::class.java).get()
            val compileGroovyTask = project.tasks.named("compileGroovy", GroovyCompile::class.java).get()
            val cloverPrepareGroverTask = project.tasks.named(CLOVER_TASK_PREPARE_GROVER, PrepareGroverTask::class.java).get()
            val classesDir = project.layout.buildDirectory.dir("classes-openclover/groovy/${compileGroovyTask.destinationDir.name}")
            compileInstGroovyTask.group = BasePlugin.BUILD_GROUP
            compileInstGroovyTask.description = "Compiles the OpenClover instrumented source code."
            compileInstGroovyTask.source = instGroovyTask.sourcesOutputDir.asFileTree
            compileInstGroovyTask.destinationDir = classesDir.get().asFile
            compileInstGroovyTask.classpath = compileGroovyTask.classpath + project.configurations.findByName("openclover") as FileCollection
            compileInstGroovyTask.sourceCompatibility = compileGroovyTask.sourceCompatibility
            compileInstGroovyTask.targetCompatibility = compileGroovyTask.targetCompatibility
            compileInstGroovyTask.dependsOn(instGroovyTask)
            compileInstGroovyTask.dependsOn(compileGroovyTask.dependsOn)
            // TODO: Copy compiler options as well (both java and groovy options)

            // Grover stuff
            compileInstGroovyTask.dependsOn(cloverPrepareGroverTask)
            compileInstGroovyTask.inputs.dir(cloverPrepareGroverTask.outputDirectory)
            compileInstGroovyTask.classpath += project.files(cloverPrepareGroverTask.outputDirectory.file("grover.jar"))
            compileInstGroovyTask.classpath += project.files(cloverPrepareGroverTask.outputDirectory.get())
            compileInstGroovyTask.groovyOptions.configurationScript = when {
                project.logger.isDebugEnabled -> project.file(cloverPrepareGroverTask.outputDirectory.file("CompilerConfigDebug.groovy"))
                else -> project.file(cloverPrepareGroverTask.outputDirectory.file("CompilerConfigVerbose.groovy"))
            }

            val dbDirInput = instGroovyTestTask.dbDirOutput
            val dbDirOutput = getCloverDbOutputDir(compileInstGroovyTask)
            val dbDirTmp = getCloverDbTmpDir(project)

            compileInstGroovyTask.dependsOn(instGroovyTestTask)
            compileInstGroovyTask.inputs.dir(dbDirInput)
            compileInstGroovyTask.inputs.property("dbTmpDir", dbDirTmp)
            compileInstGroovyTask.outputs.dir(dbDirOutput)

            compileInstGroovyTask.doFirst {
                project.delete(dbDirTmp)
                project.mkdir(dbDirTmp)
                project.copy {
                    it.from(dbDirInput)
                    it.into(dbDirTmp)
                }
            }

            compileInstGroovyTask.doLast {
                project.sync {
                    it.from(dbDirTmp)
                    it.into(dbDirOutput)
                }
                project.delete(dbDirTmp)
                logDbDirs(compileInstGroovyTask, dbDirInput, dbDirOutput)
            }
        }
    }

    private fun addTaskCompileInstGroovyJointJavaTest(project: Project) {
        project.tasks.register(CLOVER_TASK_COMPILE_INST_GROOVY_JOINT_JAVA_TEST, GroovyCompile::class.java) { compileInstGroovyTestTask ->
            // TODO: Much of the following is the same as in addTaskCompileInstGroovyJointJava(). Try to generalize it.
            val compileInstGroovyTask = project.tasks.named(CLOVER_TASK_COMPILE_INST_GROOVY_JOINT_JAVA, GroovyCompile::class.java).get()
            val instGroovyTestTask = project.tasks.named(CLOVER_TASK_INST_GROOVY_JOINT_JAVA_TEST, InstrumentTask::class.java).get()
            val compileGroovyTestTask = project.tasks.named("compileTestGroovy", GroovyCompile::class.java).get()
            val cloverPrepareGroverTask = project.tasks.named(CLOVER_TASK_PREPARE_GROVER, PrepareGroverTask::class.java).get()
            val classesDir = project.layout.buildDirectory.dir("classes-openclover/groovy/${compileGroovyTestTask.destinationDir.name}")
            compileInstGroovyTestTask.group = BasePlugin.BUILD_GROUP
            compileInstGroovyTestTask.description = "Compiles the OpenClover instrumented source code."
            compileInstGroovyTestTask.source = instGroovyTestTask.sourcesOutputDir.asFileTree
            compileInstGroovyTestTask.destinationDir = classesDir.get().asFile
            compileInstGroovyTestTask.classpath = compileGroovyTestTask.classpath + project.configurations.findByName("openclover") as FileCollection
            compileInstGroovyTestTask.sourceCompatibility = compileGroovyTestTask.sourceCompatibility
            compileInstGroovyTestTask.targetCompatibility = compileGroovyTestTask.targetCompatibility
            compileInstGroovyTestTask.dependsOn(instGroovyTestTask)
            compileInstGroovyTestTask.dependsOn(compileGroovyTestTask.dependsOn)
            // TODO: Copy compiler options as well (both java and groovy options)

            // Grover stuff
            compileInstGroovyTestTask.dependsOn(cloverPrepareGroverTask)
            compileInstGroovyTestTask.dependsOn(CLOVER_TASK_COMPILE_INST_GROOVY_JOINT_JAVA)
            compileInstGroovyTestTask.inputs.dir(cloverPrepareGroverTask.outputDirectory)
            compileInstGroovyTestTask.classpath += project.files(cloverPrepareGroverTask.outputDirectory.file("grover.jar"))
            compileInstGroovyTestTask.classpath += project.files(cloverPrepareGroverTask.outputDirectory.get())
            compileInstGroovyTestTask.groovyOptions.configurationScript = when {
                project.logger.isDebugEnabled -> project.file(cloverPrepareGroverTask.outputDirectory.file("CompilerConfigDebug.groovy"))
                else -> project.file(cloverPrepareGroverTask.outputDirectory.file("CompilerConfigVerbose.groovy"))
            }

            val dbDirInput = getCloverDbOutputDir(compileInstGroovyTask)
            val dbDirOutput = getCloverDbOutputDir(compileInstGroovyTestTask)
            val dbDirTmp = getCloverDbTmpDir(project)

            compileInstGroovyTestTask.dependsOn(compileInstGroovyTask)
            compileInstGroovyTestTask.inputs.dir(dbDirInput)
            compileInstGroovyTestTask.inputs.property("dbTmpDir", dbDirTmp)
            compileInstGroovyTestTask.outputs.dir(dbDirOutput)

            compileInstGroovyTestTask.doFirst {
                project.delete(dbDirTmp)
                project.mkdir(dbDirTmp)
                project.copy {
                    it.from(dbDirInput)
                    it.into(dbDirTmp)
                }
            }

            compileInstGroovyTestTask.doLast {
                project.sync {
                    it.from(dbDirTmp)
                    it.into(dbDirOutput)
                }
                project.delete(dbDirTmp)
                logDbDirs(compileInstGroovyTestTask, dbDirInput, dbDirOutput)
            }

            project.rootProject.tasks.withType(GenerateCloverReportTask::class.java) { mergeReportTask ->
                mergeReportTask.testSourcesFiles.addAll(instGroovyTestTask.sourcesOutputDir.asFileTree)
            }
        }
    }

    private fun createCloverConfig(project: Project): Configuration? {
        val cloverConfig = project.configurations.maybeCreate("openclover")
        cloverConfig.isVisible = false

        cloverConfig.defaultDependencies {
            it.add(project.dependencies.create("org.openclover:clover:${CloverVersionInfo.getReleaseNum()}"))
        }

        return cloverConfig
    }


    private fun verifyGradleVersion() {
        if (GradleVersion.current() < GradleVersion.version(MINIMUM_GRADLE_VERSION)) {
            throw UnsupportedOperationException("Plugin $PLUGIN_ID requires at least Gradle $MINIMUM_GRADLE_VERSION, " +
                    "but you are using ${GradleVersion.current().version}")
        }
    }
}

/*
private fun getOpenCloverVersion(): String {
return CloverReporter::class.java.`package`.specificationVersion
}*/
