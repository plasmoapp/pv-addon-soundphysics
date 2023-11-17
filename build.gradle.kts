import gg.essential.gradle.multiversion.StripReferencesTransform.Companion.registerStripReferencesAttribute


plugins {
    java
    kotlin("jvm") version(libs.versions.kotlin.get())
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

    compileOnly("gg.essential:universalcraft-1.8.9-forge:254") {
        attributes { attribute(common, true) }
    }
}

repositories {
    mavenCentral()

    maven("https://repo.plasmoverse.com/releases")
    maven("https://repo.plasmoverse.com/snapshots")
    maven("https://repo.essential.gg/repository/maven-public")
}

tasks {
    java {
        toolchain.languageVersion.set(JavaLanguageVersion.of(8))
    }
}
