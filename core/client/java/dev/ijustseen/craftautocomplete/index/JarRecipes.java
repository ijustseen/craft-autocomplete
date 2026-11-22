package dev.ijustseen.craftautocomplete.index;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import dev.ijustseen.craftautocomplete.CraftAutocomplete;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Recipes shipped inside the mods installed on this client. A remote server no longer sends them;
 * the jars are already on disk because the player needed them to join.
 */
final class JarRecipes {
    private JarRecipes() {}

    static List<RecipeHolder<?>> read(RegistryAccess registries) {
        RegistryOps<com.google.gson.JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, registries);
        List<RecipeHolder<?>> recipes = new ArrayList<>();
        int failed = 0;
        for (ModContainer mod : FabricLoader.getInstance().getAllMods()) {
            for (Path root : mod.getRootPaths()) {
                failed += readRoot(root, ops, recipes);
            }
        }
        CraftAutocomplete.LOGGER.info("read {} recipes from local mod jars", recipes.size());
        if (failed > 0) {
            CraftAutocomplete.LOGGER.info("skipped {} recipe files that did not parse", failed);
        }
        return List.copyOf(recipes);
    }

    private static int readRoot(Path root, RegistryOps<com.google.gson.JsonElement> ops, List<RecipeHolder<?>> recipes) {
        if (Files.isRegularFile(root)) {
            try (FileSystem jar = FileSystems.newFileSystem(root)) {
                return readData(jar.getPath("/"), ops, recipes);
            } catch (IOException ignored) {
                return 0;
            }
        }
        return readData(root, ops, recipes);
    }

    private static int readData(Path root, RegistryOps<com.google.gson.JsonElement> ops, List<RecipeHolder<?>> recipes) {
        Path data = root.resolve("data");
        if (!Files.isDirectory(data)) {
            return 0;
        }
        int failed = 0;
        try (Stream<Path> namespaces = Files.list(data)) {
            for (Path namespaceDir : namespaces.toList()) {
                if (!Files.isDirectory(namespaceDir)) {
                    continue;
                }
                Path recipeDir = namespaceDir.resolve("recipe");
                if (!Files.isDirectory(recipeDir)) {
                    continue;
                }
                String namespace = namespaceDir.getFileName().toString();
                failed += readNamespace(namespace, recipeDir, ops, recipes);
            }
        } catch (IOException ignored) {
            return failed;
        }
        return failed;
    }

    private static int readNamespace(
            String namespace,
            Path recipeDir,
            RegistryOps<com.google.gson.JsonElement> ops,
            List<RecipeHolder<?>> recipes
    ) throws IOException {
        int failed = 0;
        try (Stream<Path> files = Files.walk(recipeDir)) {
            for (Path file : files.filter(path -> path.getFileName().toString().endsWith(".json")).toList()) {
                String relative = recipeDir.relativize(file).toString().replace('\\', '/');
                String path = relative.substring(0, relative.length() - ".json".length());
                Identifier id;
                try {
                    id = Identifier.fromNamespaceAndPath(namespace, path);
                } catch (RuntimeException ignored) {
                    failed++;
                    continue;
                }
                try {
                    com.google.gson.JsonElement json = JsonParser.parseString(Files.readString(file));
                    Recipe<?> recipe = Recipe.DIRECT_CODEC.parse(ops, json).getOrThrow();
                    ResourceKey<Recipe<?>> key = ResourceKey.create(Registries.RECIPE, id);
                    recipes.add(new RecipeHolder<>(key, recipe));
                } catch (RuntimeException | IOException ignored) {
                    failed++;
                }
            }
        }
        return failed;
    }
}
