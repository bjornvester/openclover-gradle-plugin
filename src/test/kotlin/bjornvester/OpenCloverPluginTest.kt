package com.github.bjornvester

import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.lang.management.ManagementFactory

// TODO: These tests do not yet work
class OpenCloverPluginTest {
    @TempDir
    lateinit var tempDir: File

    @Test
    fun `Applying the plugin is sweet`() {
        File(tempDir, "build.gradle.kts").writeText("""
plugins {
  id("com.github.bjornvester.openclover")
}
""".trim())

        File("$tempDir/src/main/java/MyClass.java").writeText("""
plugins {
  id("com.github.bjornvester.openclover")
}
""".trim())

        val buildResult = GradleRunner
                .create()
                .withProjectDir(tempDir)
                .withPluginClasspath()
                .forwardOutput()
                .withArguments("hello")
                .withDebug(isDebuggerAttached())
                .build()
        println(buildResult.output)
        /*
        GradleRunner.create()
      .withPluginClasspath()
      .withTestKitDir(testKitDir)
      .withProjectDir(workspaceDir)
      .withArguments(args)
      .forwardOutput()
      .withGradleVersion(gradleVersion.version)
      .withDebug(isDebuggerAttached())
         */
        //Assertions.assertTrue(buildResult.)
    }

    private fun isDebuggerAttached(): Boolean {
        return ManagementFactory.getRuntimeMXBean().inputArguments.toString().indexOf("-agentlib:jdwp") > 0
    }
}
