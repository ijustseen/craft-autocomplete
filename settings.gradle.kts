pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        maven("https://maven.fabricmc.net/")
        maven("https://maven.minecraftforge.net/")
        maven("https://maven.neoforged.net/releases/")
        maven("https://maven.architectury.dev/")
        maven("https://maven.kikugie.dev/releases") { name = "KikuGie Releases" }
        maven("https://maven.kikugie.dev/snapshots") { name = "KikuGie Snapshots" }
    }
}

plugins {
    id("dev.kikugie.stonecutter") version "0.9.8"
}

// Fabric Loom and Architectury Loom cannot remap in the same Gradle run.
// Build with: ./gradlew buildAndCollectFabric|Forge|NeoForge (isolated -Pmod.loaders).
val modLoaders = providers.gradleProperty("mod.loaders")
    .orElse("fabric,forge,neoforge")
    .get()
    .split(',')
    .map { it.trim() }
    .filter { it.isNotEmpty() }
    .toSet()

val wantFabric = "fabric" in modLoaders
val wantForge = "forge" in modLoaders
val wantNeoForge = "neoforge" in modLoaders

fun minecraftVersions(loader: String): List<String> {
    val directory = settingsDir.resolve("$loader/versions")
    if (!directory.isDirectory) return emptyList()
    return directory.listFiles()
        ?.filter { it.isDirectory && it.resolve("gradle.properties").isFile }
        ?.map { it.name }
        ?.sortedWith { left, right ->
            val leftParts = left.split('.', '-').map { it.toIntOrNull() ?: Int.MIN_VALUE }
            val rightParts = right.split('.', '-').map { it.toIntOrNull() ?: Int.MIN_VALUE }
            val width = maxOf(leftParts.size, rightParts.size)
            for (index in 0 until width) {
                val compared = leftParts.getOrElse(index) { 0 }.compareTo(rightParts.getOrElse(index) { 0 })
                if (compared != 0) return@sortedWith compared
            }
            left.compareTo(right)
        }
        ?: emptyList()
}

val fabricVersions = minecraftVersions("fabric")
val forgeVersions = minecraftVersions("forge")
val neoForgeVersions = minecraftVersions("neoforge")
val activeVersion = settingsDir.resolve("active-version").takeIf { it.isFile }?.readText()?.trim().orEmpty()
val needsFabricAnchor = (wantForge && forgeVersions.isNotEmpty()) || (wantNeoForge && neoForgeVersions.isNotEmpty())

if (fabricVersions.isNotEmpty() && activeVersion !in fabricVersions) {
    error(
        "active-version must name a directory under fabric/versions that contains gradle.properties. " +
            "Current value: '${activeVersion.ifEmpty { "<empty>" }}'. " +
            "The first ./add-version fabric <mc> writes this file."
    )
}
if (fabricVersions.isEmpty() && needsFabricAnchor) {
    error("Forge and NeoForge need a Fabric version as the Stonecutter anchor. Run ./add-version fabric <mc> first.")
}

val anyVersions = fabricVersions.isNotEmpty() || forgeVersions.isNotEmpty() || neoForgeVersions.isNotEmpty()

if (anyVersions) stonecutter {
    kotlinController = true
    create(rootProject) {
        if (wantFabric && fabricVersions.isNotEmpty()) {
            branch("fabric") {
                for (mc in fabricVersions) {
                    // 26.1 and newer ship unobfuscated and use a Loom build that does not remap.
                    val buildscript = if (sc.eval(mc, ">= 26.1")) {
                        "build-fabric-unobfuscated.gradle.kts"
                    } else {
                        "build-fabric.gradle.kts"
                    }
                    version(mc).buildscript(buildscript)
                }
            }
        } else if (needsFabricAnchor) {
            // vcs/active anchor without Fabric Loom. The node name matches active-version,
            // so Forge or NeoForge does not become the tree that compiles raw core/.
            branch("fabric") {
                version(activeVersion).buildscript("build-fabric-stub.gradle.kts")
            }
        }

        if (wantNeoForge && neoForgeVersions.isNotEmpty()) {
            branch("neoforge") {
                for (mc in neoForgeVersions) {
                    version(mc).buildscript("build-neoforge.gradle.kts")
                }
            }
        }
        if (wantForge && forgeVersions.isNotEmpty()) {
            branch("forge") {
                for (mc in forgeVersions) {
                    version(mc).buildscript("build-forge.gradle.kts")
                }
            }
        }

        if (activeVersion.isNotEmpty()) {
            vcsVersion = activeVersion
        }
    }
}

rootProject.name = "craft-autocomplete"
