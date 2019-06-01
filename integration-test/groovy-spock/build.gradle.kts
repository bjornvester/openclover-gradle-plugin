plugins {
    id("groovy")
    id("com.github.bjornvester.openclover")
    id("io.ebean") version "11.27.1-bjornvester-1.0.3"
}

repositories {
    jcenter()
}

dependencies {
    implementation(localGroovy())
    implementation("io.ebean:ebean:11.38.1")
    testImplementation("org.spockframework:spock-core:1.3-groovy-2.5")
}
