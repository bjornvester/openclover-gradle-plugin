package com.github.bjornvester

import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.lang.management.ManagementFactory

// TODO: These tests do not yet work
class OpenCloverPluginIntegrationTest {
    @Test
    fun `Applying the plugin is sweet`() {
        val integrationTestDir = File("integration-test")

        val buildResult = GradleRunner
                .create()
                .withProjectDir(integrationTestDir)
                //.withPluginClasspath()
                .forwardOutput()
                .withArguments("clean", "hello") // --rerun-tasks
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
