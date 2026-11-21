plugins {
    id("fabric-loom") version "1.13-SNAPSHOT"
}

fun modProp(name: String): String =
    (findProperty(name) ?: rootProject.findProperty(name)) as String

val minecraftVersion = stonecutter.current.version
val requiredJava = JavaVersion.toVersion(modProp("deps.java_min"))

version = "${modProp("mod.version")}+$minecraftVersion"
group = modProp("mod.group")
base.archivesName = modProp("mod.archives_name")

dependencies {
    minecraft("com.mojang:minecraft:$minecraftVersion")
    mappings("net.fabricmc:yarn:${modProp("deps.yarn_mappings")}:v2")
    modImplementation("net.fabricmc:fabric-loader:${modProp("deps.fabric_loader")}")
    modImplementation("net.fabricmc.fabric-api:fabric-api:${modProp("deps.fabric_api")}")
}

loom {
    enableModProvidedJavadoc = false
    splitEnvironmentSourceSets()

    mods {
        create(modProp("mod.id")) {
            sourceSet(sourceSets.main.get())
            sourceSet(sourceSets.getByName("client"))
        }
    }

    runConfigs.all {
        runDir = "../../../run"
    }
}

java {
    withSourcesJar()
    sourceCompatibility = requiredJava
    targetCompatibility = requiredJava
}

tasks {
    processResources {
        val props = mapOf(
            "version" to modProp("mod.version"),
            "minecraft" to modProp("mod.mc_compat"),
            "fabric_loader_min" to modProp("deps.fabric_loader_min"),
            "java_min" to modProp("deps.java_min"),
        )
        inputs.properties(props)
        filesMatching("fabric.mod.json") { expand(props) }
    }

    withType<JavaCompile>().configureEach {
        options.release.set(requiredJava.majorVersion.toInt())
        // Otherwise javac compiles raw core/ and misses the //? result and the replacements.
        dependsOn(if (name.contains("Client")) "stonecutterGenerateClient" else "stonecutterGenerate")
    }

    register<Copy>("collectJars") {
        group = "build"
        from(remapJar.get().archiveFile, remapSourcesJar.get().archiveFile)
        into(rootProject.layout.buildDirectory.dir("libs/${modProp("mod.version")}/fabric"))
        dependsOn("build")
    }
}

if (stonecutter.current.isActive) {
    rootProject.tasks.register("buildActive") {
        group = "project"
        dependsOn("collectJars")
    }

    rootProject.tasks.register("runActive") {
        group = "project"
        dependsOn(tasks.named("runClient"))
    }
}
