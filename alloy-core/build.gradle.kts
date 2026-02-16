plugins {
    java
}

dependencies {
    implementation(project(":alloy-api"))
    implementation(project(":alloy-loader"))

    // JSON parsing for permissions config
    implementation("com.google.code.gson:gson:2.11.0")

    // Testing
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.jar {
    archiveBaseName.set("AlloyCore")

    manifest {
        attributes(
            "Mod-Id" to "alloy-core",
            "Mod-Name" to "Alloy Core",
            "Mod-Entrypoint" to "net.alloymc.core.AlloyCore"
        )
    }
}
