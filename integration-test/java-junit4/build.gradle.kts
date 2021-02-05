plugins {
    id("java")
    id("com.github.bjornvester.openclover")
}

repositories {
    jcenter()
}

dependencies {
    testImplementation("junit:junit:4.13.1")
}

tasks.test {
    useJUnit()
}
