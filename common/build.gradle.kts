val fabricLoaderVersion: String by rootProject

dependencies {
    modImplementation("net.fabricmc:fabric-loader:${fabricLoaderVersion}")
}

architectury {
    common("fabric", "forge")
    injectInjectables = false
}
