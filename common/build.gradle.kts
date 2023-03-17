import gg.essential.gradle.multiversion.StripReferencesTransform.Companion.registerStripReferencesAttribute

val fabricLoaderVersion: String by rootProject

plugins {
    id("gg.essential.defaults")
    id("su.plo.crowdin.plugin") version("1.0.0")
}

val common = registerStripReferencesAttribute("common") {
    excludes.add("net.minecraft")
}

dependencies {
    compileOnly("gg.essential:universalcraft-1.8.9-forge:254") {
        attributes { attribute(common, true) }
    }

    modImplementation("net.fabricmc:fabric-loader:${fabricLoaderVersion}")
}

architectury {
    common("fabric", "forge")
    injectInjectables = false
}

plasmoCrowdin {
    projectId = "plasmo-voice-addons"
    sourceFileName = "client/soundphysics.json"
    resourceDir = "assets/pvaddonsoundphysics/lang"
}

tasks {
    processResources {
        dependsOn(plasmoCrowdinDownload)
    }
}
