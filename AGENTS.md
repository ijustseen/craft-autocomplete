# How this repository is laid out

Craft Autocomplete, a client-side mod for several Minecraft versions and three loaders. Shared code is written once; builds stay isolated. The rules below are mandatory: almost every breakage looks like "just fix one file".

## Where to write code

- Shared code and resources go only in `core/`. Class and method names are the Yarn names of the version in `active-version`. Until the first `./add-version fabric <mc>`, that file does not exist.
- `fabric/src`, `forge/src` and `neoforge/src` are symlinks to `core/`. Leave them as links; a real `src` directory next to them breaks the layout.
- A file at `{loader}/versions/{mc}/src/...` with the same path replaces the file from `core/` for that version only. That is how `ForgeEntrypoint` and the empty `FabricEntrypoint` on Forge and NeoForge are done: the Fabric API class does not compile there.
- Leave `build/`, `.gradle/` and `stonecutter-cache` alone. They are generated copies.

## Branching code

- The same action under a different method name is a string replacement in `stonecutter.gradle.kts`, in the `replacements` block. Keep replacements short and unambiguous: a long replacement matches unrelated code.
- Different logic is a conditional comment. The constants `fabric`, `forge` and `neoforge` are set in `parameters`. The active version compiles `core/` as stored, so inactive branches stay commented out in git, and Stonecutter removes the comment when it builds another branch. The sample is `CraftAutocomplete.initClient`.
- `active-version` is one line, created by the first `./add-version fabric <mc>`: the Fabric version whose Yarn names `core/` is written in. It must match a directory `fabric/versions/<mc>` that contains `gradle.properties`. Stonecutter reads it with `stonecutter active file("active-version")` and rewrites that file when the active version is switched. While the file is absent, Stonecutter has no active version. When Gradle runs for Forge or NeoForge only, that same version is registered with the stub buildscript, so the Forge or NeoForge project does not become the tree that compiles raw `core/`.

## Building

Fabric Loom and Architectury Loom cannot run in the same Gradle process. Use separate invocations:

```bash
./gradlew buildAndCollectAll
./gradlew -Pmod.loaders=<loader> :<loader>:<mc>:build
./gradlew -Pmod.loaders=<loader> :<loader>:<mc>:runClient
```

`<loader>` is `fabric`, `forge` or `neoforge`. `<mc>` is a directory under `{loader}/versions/`.

`buildAndCollectAll` starts three separate runs itself. Pass Fabric tasks and Forge or NeoForge tasks to different `./gradlew` invocations.

## Adding a Minecraft version

Gradle discovers versions by scanning `{loader}/versions/*/gradle.properties`. There is no version list to edit.

```bash
./add-version fabric 1.21.4
./add-version fabric 1.21.4 --active
./add-version forge 1.21.1
./add-version neoforge 1.21.1
```

The repository starts with no version directories. The command writes `gradle.properties` from Fabric Meta, Mojang, Maven and the Forge promotion feed. The first Fabric version writes `active-version`. `--active` points that file at a later Fabric directory. Forge and NeoForge require a Fabric version already and copy their entrypoint stubs from `templates/`. If Architectury has not published a Yarn patch for that Minecraft version, pass `--yarn-patch`.

Minecraft 26.1 and newer are unobfuscated and need Java 25. `./add-version fabric 26.3` writes the version and Gradle selects `build-fabric-unobfuscated.gradle.kts`. Shared code is written in the active version's names. Builds older than 26.1 translate `Minecraft` and `client.level` back to Yarn in `stonecutter.gradle.kts`. Forge and NeoForge still use Yarn, so `./add-version` refuses 26.x for those loaders.

## Names and metadata

- A Fabric id may contain a hyphen (`craft-autocomplete`). Forge and NeoForge reject a hyphen, so they use `craft_autocomplete` (`mod.fml_id`). The name passed to `loom.mods.create` on Forge and NeoForge matches that mod id, so classes and resources stay in the same jar.
- `mods.toml` and `neoforge.mods.toml` live in shared `core/main/resources`. The jar receives only the file expanded by that loader's `processResources`.
- The mod is client-side. Add a server entrypoint only after switching `side` in the metadata.

## What stays out of git

`run/`, `run-forge/` and `run-neoforge/` hold worlds, logs and the game cache. They are listed in `.gitignore`.
