fun minecraftVersionCount(loader: String): Int {
    val directory = rootDir.resolve("$loader/versions")
    if (!directory.isDirectory) return 0
    return directory.listFiles()?.count { it.isDirectory && it.resolve("gradle.properties").isFile } ?: 0
}

fun registerIsolatedLoaderBuild(
    taskName: String,
    loaders: String,
    description: String,
    delegateTasks: List<String>,
) {
    tasks.register<Exec>(taskName) {
        group = "build"
        this.description = description
        workingDir = rootDir
        onlyIf { minecraftVersionCount(loaders) > 0 }
        commandLine(
            buildList {
                add(rootDir.resolve("gradlew").absolutePath)
                add("-Pmod.loaders=$loaders")
                addAll(delegateTasks)
            }
        )
    }
}

registerIsolatedLoaderBuild(
    "buildAndCollectFabric",
    "fabric",
    "Build every Fabric jar into build/libs/<version>/fabric/",
    listOf("collectJars"),
)
registerIsolatedLoaderBuild(
    "buildAndCollectForge",
    "forge",
    "Build every Forge jar into build/libs/<version>/forge/",
    listOf("collectJars"),
)
registerIsolatedLoaderBuild(
    "buildAndCollectNeoForge",
    "neoforge",
    "Build every NeoForge jar into build/libs/<version>/neoforge/",
    listOf("collectJars"),
)

tasks.register("buildAndCollectAll") {
    group = "build"
    description = "Fabric, Forge and NeoForge, three isolated Gradle runs"
    dependsOn("buildAndCollectFabric", "buildAndCollectForge", "buildAndCollectNeoForge")
    doFirst {
        if (listOf("fabric", "forge", "neoforge").all { minecraftVersionCount(it) == 0 }) {
            throw GradleException("No Minecraft versions yet. Add one with ./add-version fabric <mc>.")
        }
    }
}

tasks.named("buildAndCollectForge") { mustRunAfter("buildAndCollectFabric") }
tasks.named("buildAndCollectNeoForge") { mustRunAfter("buildAndCollectForge") }
