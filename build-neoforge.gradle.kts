plugins {
    id("dev.architectury.loom") version "1.9-SNAPSHOT"
}

fun modProp(name: String): String =
    (findProperty(name) ?: rootProject.findProperty(name)) as String

val selectedLoaders: Set<String> =
    ((findProperty("mod.loaders") as? String) ?: "fabric,forge,neoforge")
        .split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet()
val fmlBuildAllowed = "fabric" !in selectedLoaders

val minecraftVersion = stonecutter.current.version
val requiredJava = JavaVersion.toVersion(modProp("deps.java_min"))

version = "${modProp("mod.version")}+$minecraftVersion-neoforge"
group = modProp("mod.group")
base.archivesName = modProp("mod.archives_name")

repositories {
    maven("https://maven.neoforged.net/releases/")
    maven("https://maven.architectury.dev/")
    mavenCentral()
}

dependencies {
    minecraft("com.mojang:minecraft:$minecraftVersion")
    mappings(loom.layered {
        mappings("net.fabricmc:yarn:${modProp("deps.yarn_mappings")}:v2")
        mappings("dev.architectury:yarn-mappings-patch-neoforge:${modProp("deps.yarn_patch_neoforge")}")
    })
    "neoForge"("net.neoforged:neoforge:${modProp("deps.neoforge")}")
}

// Architectury Loom on NeoForge forbids splitEnvironmentSourceSets, while Stonecutter expects a "client" source set.
sourceSets {
    named("main") {
        resources.srcDir("src/client/resources")
    }
    create("client") {
        compileClasspath += named("main").get().compileClasspath + named("main").get().output
        runtimeClasspath += named("main").get().runtimeClasspath + named("main").get().output
    }
}

dependencies {
    "clientImplementation"(sourceSets.main.get().output)
}

loom {
    silentMojangMappingsLicense()

    mods {
        create(modProp("mod.fml_id")) {
            sourceSet(sourceSets.main.get())
            sourceSet(sourceSets.getByName("client"))
        }
    }

    runConfigs.all {
        runDir = "../../../run-neoforge"
    }
}

tasks.named<Jar>("jar") {
    from(sourceSets["client"].output.classesDirs)
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

tasks.register<Copy>("syncMainResourcesToClient") {
    dependsOn("processResources", "processClientResources")
    from(layout.buildDirectory.dir("resources/main")) {
        exclude("META-INF/**")
        exclude("fabric.mod.json")
    }
    into(layout.buildDirectory.dir("resources/client"))
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
    doLast {
        delete(layout.buildDirectory.dir("resources/client/META-INF"))
        delete(layout.buildDirectory.file("resources/client/fabric.mod.json"))
    }
}

tasks.named("clientClasses") {
    dependsOn("syncMainResourcesToClient")
}

tasks.matching { it.name.startsWith("run") || it.name == "configureClientLaunch" }.configureEach {
    dependsOn("clientClasses")
}

// Otherwise the client source set keeps raw neoforge/src (a symlink to core/) next to the override
// and compiles FabricEntrypoint twice. Generated already contains core with the overrides applied.
afterEvaluate {
    sourceSets.getByName("client").java.setSrcDirs(listOf(
        "src/client/java",
        layout.buildDirectory.dir("generated/stonecutter/client/java"),
    ))
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
            "java_min" to modProp("deps.java_min"),
            "neoforge" to modProp("deps.neoforge"),
        )
        inputs.properties(props)
        filesMatching("META-INF/neoforge.mods.toml") { expand(props) }
    }

    withType<JavaCompile>().configureEach {
        options.release.set(requiredJava.majorVersion.toInt())
        dependsOn(if (name.contains("Client")) "stonecutterGenerateClient" else "stonecutterGenerate")
    }

    register<Copy>("collectJars") {
        group = "build"
        onlyIf { fmlBuildAllowed }
        from(remapJar.get().archiveFile, remapSourcesJar.get().archiveFile)
        into(rootProject.layout.buildDirectory.dir("libs/${modProp("mod.version")}/neoforge"))
        dependsOn("build")
    }
}

tasks.named("remapJar") { onlyIf { fmlBuildAllowed } }
tasks.named("remapSourcesJar") { onlyIf { fmlBuildAllowed } }
