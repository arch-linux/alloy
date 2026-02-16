plugins {
    java
}

allprojects {
    group = "net.alloymc"
    version = "0.1.0-SNAPSHOT"

    repositories {
        mavenCentral()
    }
}

subprojects {
    apply(plugin = "java")

    java {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    tasks.withType<JavaCompile> {
        options.release.set(21)
        options.encoding = "UTF-8"
    }

    tasks.withType<Test> {
        useJUnitPlatform()
    }
}

// ---- Launch Tasks ----

tasks.register("launchClient") {
    group = "alloy"
    description = "Launch Minecraft with the Alloy mod loader"
    dependsOn(":alloy-mappings:setupWorkspace")

    doLast {
        // Find the version directory (most recent in cache/)
        val cacheDir = file("cache")
        val versionDir = cacheDir.listFiles()
            ?.filter { it.isDirectory }
            ?.maxByOrNull { it.lastModified() }
            ?: error("No cached version found. Run ./gradlew :alloy-mappings:setupWorkspace first.")

        val launchScript = file("${versionDir}/launch.sh")
        if (!launchScript.exists()) {
            error("launch.sh not found in ${versionDir}. Run ./gradlew :alloy-mappings:setupWorkspace first.")
        }

        println("[Alloy] Launching from: ${launchScript.absolutePath}")

        val process = ProcessBuilder("bash", launchScript.absolutePath)
            .directory(file("run"))
            .inheritIO()
            .start()

        val exitCode = process.waitFor()
        if (exitCode != 0) {
            println("[Alloy] Minecraft exited with code $exitCode")
        }
    }
}

tasks.register("launchServer") {
    group = "alloy"
    description = "Launch the Minecraft dedicated server with the Alloy mod loader"
    dependsOn(":alloy-mappings:setupWorkspace")

    doLast {
        val cacheDir = file("cache")
        val versionDir = cacheDir.listFiles()
            ?.filter { it.isDirectory }
            ?.maxByOrNull { it.lastModified() }
            ?: error("No cached version found. Run ./gradlew :alloy-mappings:setupWorkspace first.")

        val launchScript = file("${versionDir}/launch-server.sh")
        if (!launchScript.exists()) {
            error("launch-server.sh not found in ${versionDir}. Run ./gradlew :alloy-mappings:setupWorkspace first.")
        }

        val runServerDir = file("run-server")
        runServerDir.mkdirs()

        println("[Alloy] Launching server from: ${launchScript.absolutePath}")

        val process = ProcessBuilder("bash", launchScript.absolutePath)
            .directory(runServerDir)
            .inheritIO()
            .start()

        val exitCode = process.waitFor()
        if (exitCode != 0) {
            println("[Alloy] Server exited with code $exitCode")
        }
    }
}
