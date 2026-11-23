package dev.ijustseen.craftautocomplete.learn;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.ijustseen.craftautocomplete.CraftAutocomplete;
import dev.ijustseen.craftautocomplete.rank.SuggestionRanker;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Accept and reject counts for one player, stored next to the mod config. The ladder stays the
 * prior until the same situation has been answered a few times.
 */
public final class PlayerWeights {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Path file;
    private final Path exportFile;
    private final Map<String, Map<String, Counts>> weights = new HashMap<>();
    private final Set<String> announced = new HashSet<>();

    private PlayerWeights(Path directory) {
        this.file = directory.resolve("weights.json");
        this.exportFile = directory.resolve("craft-weights-export.json");
    }

    public static PlayerWeights load(Path configDir) {
        Path directory = configDir.resolve("craft-autocomplete");
        PlayerWeights weights = new PlayerWeights(directory);
        if (!Files.isRegularFile(weights.file)) {
            return weights;
        }
        try (Reader reader = Files.newBufferedReader(weights.file)) {
            Stored stored = GSON.fromJson(reader, Stored.class);
            if (stored == null) {
                return weights;
            }
            if (stored.weights != null) {
                weights.weights.putAll(stored.weights);
            }
            if (stored.announced != null) {
                weights.announced.addAll(stored.announced);
            }
        } catch (IOException | RuntimeException exception) {
            CraftAutocomplete.LOGGER.warn("Could not read learned weights, starting empty", exception);
        }
        return weights;
    }

    /** Positive when this family is chosen in the situation, negative when it is skipped. */
    public double evidence(String situation, String family) {
        Counts counts = countsOrNull(situation, family);
        if (counts == null) {
            return 0d;
        }
        return SuggestionRanker.evidence(counts.accept, counts.reject);
    }

    public void accept(String situation, String family) {
        add(situation, family, 1, 0);
    }

    public void reject(String situation, String family) {
        add(situation, family, 0, 1);
    }

    /** @return true the first time this world is joined */
    public boolean markAnnounced(String worldKey) {
        if (worldKey == null || worldKey.isEmpty() || !announced.add(worldKey)) {
            return false;
        }
        save();
        return true;
    }

    /** Writes a copy without the list of visited worlds, and returns that file. */
    public Path export() {
        save();
        Stored shareable = new Stored();
        shareable.weights = weights;
        write(exportFile, shareable);
        return exportFile.toAbsolutePath();
    }

    private void add(String situation, String family, int accept, int reject) {
        if (situation == null || situation.isEmpty() || family == null || family.isEmpty()) {
            return;
        }
        Counts counts = weights
                .computeIfAbsent(situation, ignored -> new HashMap<>())
                .computeIfAbsent(family, ignored -> new Counts());
        counts.accept += accept;
        counts.reject += reject;
        save();
    }

    private Counts countsOrNull(String situation, String family) {
        Map<String, Counts> row = weights.get(situation);
        if (row == null) {
            return null;
        }
        return row.get(family);
    }

    private void save() {
        Stored stored = new Stored();
        stored.weights = weights;
        stored.announced = new ArrayList<>(announced);
        write(file, stored);
    }

    private static void write(Path path, Stored stored) {
        try {
            Files.createDirectories(path.getParent());
            Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
            try (Writer writer = Files.newBufferedWriter(temporary)) {
                GSON.toJson(stored, writer);
            }
            Files.move(temporary, path, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException exception) {
            CraftAutocomplete.LOGGER.warn("Could not write learned weights", exception);
        }
    }

    private static final class Stored {
        int version = 1;
        List<String> announced = new ArrayList<>();
        Map<String, Map<String, Counts>> weights = new HashMap<>();
    }

    private static final class Counts {
        int accept;
        int reject;
    }
}
