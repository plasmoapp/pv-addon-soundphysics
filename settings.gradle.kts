pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        mavenLocal()

        maven {
            name = "Architectury"
            url = uri("https://maven.architectury.dev/")
        }
        maven {
            name = "Fabric"
            url = uri("https://maven.fabricmc.net/")
        }
        maven {
            name = "Forge"
            url = uri("https://files.minecraftforge.net/maven/")
        }
    }
}

rootProject.name = "pv-addon-soundphysics"

include(
    "common",
    "fabric",
    "forge"
)
