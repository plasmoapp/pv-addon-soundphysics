import gg.essential.gradle.multiversion.StripReferencesTransform.Companion.registerStripReferencesAttribute

val fabricLoaderVersion: String by rootProject

plugins {
    id("gg.essential.defaults")
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
