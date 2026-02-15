plugins {
    java
    application
}

dependencies {
    // JSON parsing for mod metadata
    implementation("com.google.code.gson:gson:2.11.0")

    // Testing
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

application {
    mainClass.set("net.alloymc.loader.AlloyLoader")
}
