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

    // Mechanical renames between versions. Shared code is written with Yarn names.
    // Add a replacement here when the new name is the same call. Different logic
    // goes in an override file at {loader}/versions/{mc}/src.
    //
    // Minecraft 26.x (unobfuscated names, Java 25) is left out of this template.
    // To add that branch, create a separate buildscript and a block like:
    //   string(eval(current.version, ">= 26.1")) {
    //       replace("net.minecraft.client.MinecraftClient", "net.minecraft.client.Minecraft")
    //       replace("MinecraftClient", "Minecraft")
    //   }
    replacements {
    }
}
