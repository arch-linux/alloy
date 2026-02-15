plugins {
    java
    application
}

dependencies {
    // Bytecode manipulation — foundational library used across the entire Java ecosystem
    implementation("org.ow2.asm:asm:9.7")
    implementation("org.ow2.asm:asm-commons:9.7")

    // JSON parsing — minimal, single-purpose
    implementation("com.google.code.gson:gson:2.11.0")

    // Testing
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

application {
    mainClass.set("net.alloymc.mappings.AlloyMappings")
}

tasks.register<JavaExec>("setupWorkspace") {
    group = "alloy"
    description = "Fetch Minecraft, download libraries/assets, produce deobfuscated JAR, generate launch script"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("net.alloymc.mappings.AlloyMappings")

    val mcVersion = project.findProperty("mcVersion")?.toString() ?: "latest"
    val cacheDir = rootProject.layout.projectDirectory.dir("cache").asFile.absolutePath

    // Pass the alloy-loader JAR path so the launch script includes it
    val loaderJar = rootProject.project(":alloy-loader").tasks.named("jar").map {
        it.outputs.files.singleFile.absolutePath
    }

    dependsOn(":alloy-loader:jar")

    args = listOf(mcVersion, cacheDir)

    doFirst {
        args = listOf(mcVersion, cacheDir, loaderJar.get())
    }
}
