import gg.essential.gradle.multiversion.StripReferencesTransform.Companion.registerStripReferencesAttribute
import java.net.URI


plugins {
    java
    kotlin("jvm") version(libs.versions.kotlin.get())
    alias(libs.plugins.crowdin)
    alias(libs.plugins.essential.defaults)
    alias(libs.plugins.pv.java.templates)
    alias(libs.plugins.pv.entrypoints)
}

val common = registerStripReferencesAttribute("common") {
    excludes.add("net.minecraft")
}

dependencies {
    compileOnly(libs.annotations)

    compileOnly(libs.pv)

    compileOnly("maven.modrinth:sound-physics-remastered:fabric-1.20.1-1.4.12")

    compileOnly("gg.essential:universalcraft-1.8.9-forge:254") {
        attributes { attribute(common, true) }
    }
}

repositories {
    mavenCentral()

    maven("https://repo.plasmoverse.com/releases")
    maven("https://repo.plasmoverse.com/snapshots")
    maven("https://repo.essential.gg/repository/maven-public")

    exclusiveContent {
        forRepository {
            maven("https://api.modrinth.com/maven")
        }
        filter {
            includeGroup("maven.modrinth")
        }
    }
}

crowdin {
    url = URI.create("https://github.com/plasmoapp/plasmo-voice-crowdin/archive/refs/heads/addons.zip").toURL()
    sourceFileName = "client/soundphysics.json"
    resourceDir = "assets/pvaddonsoundphysics/lang"
}

tasks {
    jar {
        enabled = false
    }

    shadowJar {
        configurations = listOf(project.configurations.shadow.get())
        archiveClassifier.set("")
    }

    java {
        toolchain.languageVersion.set(JavaLanguageVersion.of(17))
    }
}
