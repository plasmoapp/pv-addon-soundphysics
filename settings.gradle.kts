pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        mavenLocal()

        maven("https://maven.fabricmc.net")
        maven("https://maven.architectury.dev/")
        maven("https://maven.minecraftforge.net")
        maven("https://repo.essential.gg/repository/maven-public")
    }

    plugins {
        val egtVersion = "0.1.18"
        id("gg.essential.defaults") version egtVersion
//        id("gg.essential.multi-version.root") version egtVersion
    }
}

rootProject.name = "pv-addon-soundphysics"

include(
    "common",
    "fabric",
    "forge"
)
