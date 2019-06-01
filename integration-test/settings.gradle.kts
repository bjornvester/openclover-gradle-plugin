pluginManagement {
    resolutionStrategy {
        eachPlugin {
            if (requested.id.id == "io.ebean") {
                useModule("io.ebean:ebean-gradle-plugin:11.27.1-bjornvester-1.0.3")
            }
        }
    }
    repositories {
        maven { url = uri("https://dl.bintray.com/bjornvester/maven") }
        gradlePluginPortal()
    }
}

include("java-junit4", "groovy-spock")
includeBuild("..")
