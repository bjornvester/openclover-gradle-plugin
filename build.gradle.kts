plugins {
    id("java-gradle-plugin")
    kotlin("jvm") version "1.3.31"
    id("com.gradle.plugin-publish") version "0.10.1"
}

group = "com.github.bjornvester"
version = "0.5.2-SNAPSHOT"

repositories {
    jcenter()
}

dependencies {
    implementation(kotlin("stdlib-jdk8"))
    implementation("org.openclover:clover:4.3.1")
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.4.2")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.4.2")
}

tasks.test {
    useJUnitPlatform()
}

tasks.withType<Wrapper> {
    distributionType = Wrapper.DistributionType.ALL
    gradleVersion = "5.6.2"
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
