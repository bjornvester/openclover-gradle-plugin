plugins {
    id("java-gradle-plugin")
    id("com.gradle.plugin-publish") version "0.12.0"
    `kotlin-dsl`
}

group = "com.github.bjornvester"
version = "0.5.2-SNAPSHOT"

repositories {
    jcenter()
}

dependencies {
    implementation("org.openclover:clover:4.4.1")
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.7.0")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.7.0")
}

tasks.test {
    useJUnitPlatform()
}

tasks.withType<Wrapper> {
    distributionType = Wrapper.DistributionType.ALL
    gradleVersion = "6.8.1"
}

kotlinDslPluginOptions {
    experimentalWarning.set(false)
}

gradlePlugin {
    plugins {
        create("openCloverPlugin") {
            id = "com.github.bjornvester.openclover"
            implementationClass = "com.github.bjornvester.OpenCloverPlugin"
        }
    }
}

pluginBundle {
    website = "https://github.com/bjornvester/openclover-gradle-plugin"
    vcsUrl = "https://github.com/bjornvester/openclover-gradle-plugin"
    description = "Adds OpenClover test coverage reporting to your project. Works with the Gradle build cache. Please see the Github project page for details."
    tags = listOf("openclover", "clover", "coverage")
    (plugins) {
        "openCloverPlugin" {
            displayName = "Gradle OpenClover plugin"
            description = "Changes: Support all report types in OpenClover (though not yet documented)"
        }
    }
}
