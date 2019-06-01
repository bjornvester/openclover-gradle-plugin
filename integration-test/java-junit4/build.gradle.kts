plugins {
    id("java")
    id("com.github.bjornvester.openclover")
}

repositories {
    jcenter()
}

dependencies {
    testImplementation("junit:junit:4.12")
}

tasks.test {
    useJUnit()
}
