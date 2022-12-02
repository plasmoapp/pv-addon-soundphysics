import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

val fabricLoaderVersion: String by rootProject
val forgeVersion: String by rootProject

architectury {
    platformSetupLoomIde()
    forge()
}

dependencies {
    "forge"("net.minecraftforge:forge:${forgeVersion}")

    "shadowCommon"(project(":common", "transformProductionFabric")) {
        isTransitive = false
    }
}

tasks {
    processResources {
        filesMatching("META-INF/mods.toml") {
            expand(mutableMapOf(
                "version" to rootProject.version,
            ))
        }
    }

    shadowJar {
        configurations = listOf(project.configurations.getByName("shadowCommon"))
        archiveClassifier.set("dev-shadow")
    }

    remapJar {
        dependsOn(getByName<ShadowJar>("shadowJar"))
        inputFile.set(shadowJar.get().archiveFile)
    }
}
