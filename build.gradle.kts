import io.github.pacifistmc.forgix.plugin.ForgixMergeExtension.FabricContainer
import io.github.pacifistmc.forgix.plugin.ForgixMergeExtension.ForgeContainer
import net.fabricmc.loom.api.LoomGradleExtensionAPI

val mavenGroup: String by rootProject
val buildVersion: String by rootProject
val pvVersion: String by rootProject

plugins {
    java
    idea
    id("io.github.pacifistmc.forgix") version "1.2.6"
    id("com.github.johnrengelman.shadow") version "7.1.0" apply false
    id("architectury-plugin") version "3.4-SNAPSHOT" apply false
    id("dev.architectury.loom") version "1.0-SNAPSHOT" apply false
}

group = mavenGroup
version = buildVersion

subprojects {
    apply(plugin = "architectury-plugin")
    apply(plugin = "dev.architectury.loom")
    apply(plugin = "com.github.johnrengelman.shadow")

    var mappingsDependency: Dependency? = null

    configure<LoomGradleExtensionAPI> {
        mappingsDependency = layered {
            officialMojangMappings()
        }
    }

    dependencies {
        "minecraft"("com.mojang:minecraft:1.16.5")

        mappingsDependency?.let { "mappings"(it) }

        compileOnly("com.google.guava:guava:31.1-jre")
        compileOnly("org.jetbrains:annotations:23.0.0")
        compileOnly("org.projectlombok:lombok:1.18.24")

        compileOnly("su.plo.voice.api:client:${pvVersion}")
        compileOnly("su.plo.config:config:1.0.0")

        annotationProcessor("su.plo.voice.api:client:${pvVersion}")
        annotationProcessor("org.projectlombok:lombok:1.18.24")
        annotationProcessor("com.google.guava:guava:31.1-jre")
        annotationProcessor("com.google.code.gson:gson:2.9.0")
    }

    repositories {
        mavenCentral()
        mavenLocal()

        maven {
            url = uri("https://repo.plo.su")
        }
    }

    configurations {
        create("shadowCommon")
    }
}

tasks {
    java {
        toolchain.languageVersion.set(JavaLanguageVersion.of(8))
    }
}

forgix {
    group = mavenGroup
    mergedJarName = "${project.name}-${project.version}.jar"
    outputDir = "build/merged"

    closureOf<ForgeContainer> {
        projectName = "forge"
        jarLocation = "build/libs/forge.jar"
    }

    closureOf<FabricContainer> {
        projectName = "fabric"
        jarLocation = "build/libs/fabric.jar"
    }

    removeDuplicate("su.plo.voice.soundphysics")
}
