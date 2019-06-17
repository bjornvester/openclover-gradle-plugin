# OpenClover Gradle Plugin
This Gradle plugin adds support for [OpenClover](https://openclover.org/) in your Gradle builds.

Please note that at the time of this writing (June, 2019), the plugin is not yet ready. But it's getting there.

# Requirements and limitations
This plugin requires Gradle 5.4 or later.

At this time, there are a few important limitations to be aware of:
* It will only register coverage on the Java test task called "test", so any custom test tasks will not be included. This limitation will hopefully be addressed later.
* It is not yet possible to configure OpenClover.
* The plugin creates a new Test task based on the existing "test" task. But it will not copy all configurations over. So if you have configured the test task, you may need to do the same for the "cloverTest" task. 
* As for the test task, the plugin also creates new compile tasks based on the existing ones. Similarly, if you have customized the existing ones, you may need to do the same for these as well.
* It does not support incremental compilation. This is due to limitations in OpenClover itself.
* It uses absolute paths for the OpenClover database. This is needed due to limitations in OpenClover, and means that the build cache will only be used if you use the same project directory path.

# Status
The main priority has been to get OpenClover working with up-to-date checking and the build cache. This is working now for the basic usage og Java and Groovy.

Next up is automatic tests and more comprehensive integration tests. This is important to mitigate the risk of stuff getting broken down the line.

After that, the plan is to make it possible to configure OpenClover. 

## Configuration
See the Gradle Plugins page for how to apply the plugin here: https://plugins.gradle.org/plugin/com.github.bjornvester.openclover

It uses OpenClover 4.3.1 by default. If you like to use a different version, it has to be configured on the buildscript classpath like this:

```groovy
buildscript {
    repositories {
        jcenter()
    }
    dependencies {
        classpath("org.openclover:clover:4.3.1")
    }
}
```

At this time, there is not much else to configure. This will hopefully be addressed in a future version.

To actually run clover, use the task "cloverReport" for a single project, or "cloverMergedReport" in the root project for multi-projects.  

If you run into a problem with OpenClover itself, you can find the list of open issues over on the official [OpenClover repository](https://bitbucket.org/openclover/clover/issues) on Bitbucket. 

## Alternatives
If you can't get this plugin to work, you may want to try https://github.com/bmuschko/gradle-clover-plugin.
I started out using that plugin but eventually decided create a new one as there were too many limitations in it.
Most noticeably, at least at the time of this writing:
* No up-to-date checking, meaning that both the generated instrumented source code, the compiled instrumented code, the OpenClover DB files and the final report will all be run again even if there are no changes.
* No support for the Gradle build cache. Not only is it not used, but you actively have to disable it to avoid caching uninstrumented code which will then be used by the running tests, leading to empty reports. 
* No smart way to enable and disable the instrumentation phase. This phase takes quite a lot of time (especially due to the lack of optimizations as noted above), so you basically have to leave it disabled by default. Then when you like to generate a coverage report, you have to enable it again.
