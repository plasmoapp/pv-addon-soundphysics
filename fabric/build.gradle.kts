import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

val fabricLoaderVersion: String by rootProject

architectury {
    platformSetupLoomIde()
    fabric()
}

dependencies {
    modApi("net.fabricmc:fabric-loader:$fabricLoaderVersion")

    "shadowCommon"(project(":common", "transformProductionFabric")) {
        isTransitive = false
    }
}

tasks {
    processResources {
        filesMatching("fabric.mod.json") {
            expand(
                mutableMapOf(
                    "version" to rootProject.version,
                )
            )
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
