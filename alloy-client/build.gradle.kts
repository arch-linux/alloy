plugins {
    java
}

val mcVersion: String = project.findProperty("mcVersion")?.toString()
    ?: rootProject.layout.projectDirectory.dir("cache").asFile
        .listFiles()?.filter { it.isDirectory }
        ?.maxByOrNull { it.lastModified() }?.name
    ?: "unknown"

val modVersion = "0.1.0"

version = "$modVersion-mc$mcVersion"

tasks.jar {
    archiveBaseName.set("AlloyClient")
    archiveVersion.set("$modVersion-mc$mcVersion")

    manifest {
        attributes(
            "Mod-Id" to "alloy-client",
            "Mod-Name" to "Alloy Client",
            "Mod-Version" to "$modVersion-mc$mcVersion",
            "Mod-Entrypoint" to "net.alloymc.client.AlloyClient"
        )
    }
}

tasks.register<Copy>("release") {
    group = "alloy"
    description = "Build the Alloy Client JAR and copy it to ~/Desktop"
    dependsOn(tasks.jar)

    from(tasks.jar.get().archiveFile)
    into(System.getProperty("user.home") + "/Desktop")

    doLast {
        val jarName = tasks.jar.get().archiveFile.get().asFile.name
        println("==============================================")
        println("  Alloy Client $modVersion for MC $mcVersion")
        println("  JAR: ~/Desktop/$jarName")
        println("==============================================")
    }
}

dependencies {
    implementation(project(":alloy-api"))
    implementation(project(":alloy-loader"))

    testImplementation("org.junit.jupiter:junit-jupiter:5.11.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
