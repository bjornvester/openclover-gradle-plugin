plugins {
    id("java-gradle-plugin")
    kotlin("jvm") version "1.3.31"
    id("com.gradle.plugin-publish") version "0.10.1"
}

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

group = "com.github.bjornvester"
version = "0.3"

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
    description = "Adds OpenClover test coverage reporting to your project. Please see the Github project page for details."
    (plugins) {
        "openCloverPlugin" {
            displayName = "Gradle OpenClover plugin"
            tags = listOf("openclover", "coverage")
        }
    }
}
