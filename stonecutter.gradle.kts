plugins {
    id("dev.kikugie.stonecutter")
}

// Absent until ./add-version fabric <mc>. Forge and NeoForge runs register that same
// node with the stub buildscript, so they do not compile raw core/.
val activeVersionFile = file("active-version")
val activeVersionText = if (activeVersionFile.isFile) activeVersionFile.readText().trim() else ""
if (activeVersionText.isEmpty()) {
    stonecutter.active(null)
} else {
    stonecutter active activeVersionFile
}

stonecutter parameters {
    val loader = node.branch.id.ifBlank { "fabric" }
    constants {
        match(loader, "fabric", "neoforge", "forge")
    }

    // Mechanical renames between versions. Shared code is written in the names of the
    // active version. 26.1 and newer use Mojang's official names. Older versions are Yarn,
    // so those builds translate the official names back.
    replacements {
        string(eval(current.version, "< 26.1")) {
            replace("net.minecraft.client.Minecraft", "net.minecraft.client.MinecraftClient")
            replace("Minecraft.getInstance()", "MinecraftClient.getInstance()")
            replace("client.level", "client.world")
        }
    }
}
