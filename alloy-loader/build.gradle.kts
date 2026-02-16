plugins {
    java
    application
}

dependencies {
    // Alloy API (needed for bootstrap initialization)
    implementation(project(":alloy-api"))

    // JSON parsing for mod metadata
    implementation("com.google.code.gson:gson:2.11.0")

    // Bytecode transformation
    implementation("org.ow2.asm:asm:9.7.1")

    // LWJGL (on MC classpath at runtime — needed for icon loading)
    compileOnly("org.lwjgl:lwjgl:3.3.3")
    compileOnly("org.lwjgl:lwjgl-stb:3.3.3")
    compileOnly("org.lwjgl:lwjgl-glfw:3.3.3")

    // macOS dock icon (on MC classpath at runtime)
    compileOnly("ca.weblite:java-objc-bridge:1.1")

    // Brigadier (Mojang's command library — on MC classpath at runtime)
    compileOnly(files("../cache/1.21.11/libraries/brigadier-1.3.10.jar"))

    // Testing
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

application {
    mainClass.set("net.alloymc.loader.AlloyLoader")
}

tasks.jar {
    manifest {
        attributes(
            "Premain-Class" to "net.alloymc.loader.agent.AlloyAgent",
            "Can-Retransform-Classes" to "true",
            "Can-Redefine-Classes" to "true"
        )
    }

    // Bundle ALL runtime deps into the loader JAR so the agent's classes
    // (and their dependencies like alloy-api) are available when injected
    // into classloaders that don't delegate to the system classloader
    // (e.g. MC's bundler classloader).
    from(configurations.runtimeClasspath.get()
        .map { if (it.isDirectory) it else zipTree(it) }
    )
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}
