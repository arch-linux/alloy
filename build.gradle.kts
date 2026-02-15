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

// ---- Launch Task ----

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
