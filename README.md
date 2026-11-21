# Craft Autocomplete

A client-side mod for Fabric, Forge and NeoForge, across several Minecraft versions. Shared code lives in `core/` and is written once, in Yarn names. Each version is a [Stonecutter](https://stonecutter.kikugie.dev/) `0.9.8` branch: a difference is either a string replacement or a file under `versions/{mc}/src` that overrides the shared one.

The mod starts with no Minecraft version. A version is a directory `{loader}/versions/<mc>` with a `gradle.properties` file, and Gradle registers every such directory. `active-version` appears when the first Fabric version is added and names the Yarn names `core/` is written in.

Finished jars land in `build/libs/<mod.version>/{fabric,forge,neoforge}/`. `<mod.version>` comes from `gradle.properties` and is `0.1.0` right now.

Fabric Loom and Architectury Loom break each other's remap when they share a Gradle process, so each loader is built in its own run. `buildAndCollectAll` starts those three runs itself. Pass tasks for different loaders to different `./gradlew` invocations, and set `-Pmod.loaders` to the loader you are building or launching. The rules that keep the layout intact are in `AGENTS.md`.

Licensed under the [MIT License](LICENSE). The project layout comes from the multi-loader mod template.

## Add a version

```bash
./add-version fabric 1.21.4
./add-version fabric 1.21.4 --active
./add-version forge 1.21.1
./add-version neoforge 1.21.1
```

`<loader>` is `fabric`, `forge` or `neoforge`. The command looks up Yarn, Fabric Loader, Fabric API, Forge or NeoForge, and the Java level. The first Fabric version becomes `active-version`. Later Fabric versions stay side by side until you pass `--active`. Forge and NeoForge need that Fabric version already, and their entrypoints are copied from `templates/`. If Architectury has no Yarn patch for that Minecraft version, pass `--yarn-patch <version>`. Minecraft 26.1 and newer, including 26.3, are added on Fabric. They use Java 25 and official names. Forge and NeoForge stay on Yarn versions.

## Commands

### Build every loader

```bash
./gradlew buildAndCollectAll
```

### Build one loader

```bash
./gradlew buildAndCollectFabric
./gradlew buildAndCollectForge
./gradlew buildAndCollectNeoForge
```

### Build or run one version

`<mc>` is the directory name under `{loader}/versions/`.

```bash
./gradlew -Pmod.loaders=<loader> :<loader>:<mc>:build
./gradlew -Pmod.loaders=<loader> :<loader>:<mc>:runClient
```

After `./add-version fabric 1.21.4`:

```bash
./gradlew -Pmod.loaders=fabric :fabric:1.21.4:build
./gradlew -Pmod.loaders=fabric :fabric:1.21.4:runClient
```

`build` writes the jar into that version's own `build/libs/`. The `buildAndCollect*` tasks also copy jars into `build/libs/<mod.version>/{fabric,forge,neoforge}/`.

Game directories are `run/` for Fabric, `run-forge/` for Forge and `run-neoforge/` for NeoForge. They are gitignored.
