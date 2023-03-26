import gg.essential.gradle.multiversion.StripReferencesTransform.Companion.registerStripReferencesAttribute

val mavenGroup: String by rootProject
val buildVersion: String by rootProject
val pvVersion: String by rootProject

plugins {
    java
    kotlin("jvm") version("1.6.0")
    idea
    id("gg.essential.defaults") version("0.1.18")
    id("su.plo.voice.plugin") version("1.0.0")
}

group = mavenGroup
version = buildVersion

val common = registerStripReferencesAttribute("common") {
    excludes.add("net.minecraft")
}

dependencies {
    compileOnly("com.google.guava:guava:31.1-jre")
    compileOnly("org.jetbrains:annotations:23.0.0")
    compileOnly("org.projectlombok:lombok:1.18.24")

    compileOnly("su.plo.voice.api:client:${pvVersion}")
    compileOnly("su.plo.config:config:1.0.0")

    compileOnly("gg.essential:universalcraft-1.8.9-forge:254") {
        attributes { attribute(common, true) }
    }

    annotationProcessor("org.projectlombok:lombok:1.18.24")
    annotationProcessor("com.google.guava:guava:31.1-jre")
    annotationProcessor("com.google.code.gson:gson:2.9.0")
}

repositories {
    mavenCentral()
    mavenLocal()

    maven("https://repo.plo.su")
    maven("https://repo.essential.gg/repository/maven-public")
}

tasks {
    java {
        toolchain.languageVersion.set(JavaLanguageVersion.of(8))
    }
}
