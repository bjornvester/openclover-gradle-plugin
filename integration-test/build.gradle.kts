plugins {
    id("com.gradle.build-scan") version "2.3"
    id("com.github.bjornvester.openclover")
}

openclover {
    reportTypes.set(listOf("HTML", "XML"))
}