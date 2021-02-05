plugins {
    id("com.github.bjornvester.openclover")
}

openclover {
    reportTypes.set(listOf("HTML", "XML"))
}