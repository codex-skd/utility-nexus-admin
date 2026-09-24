package com.skd.utilitynexusadmin.datapack;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.skd.utilitynexusadmin.config.UNAConfig;
import com.skd.utilitynexusadmin.logging.UNALog;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.repository.RepositorySource;
import net.neoforged.neoforge.event.AddPackFindersEvent;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Stream;
import java.util.zip.ZipFile;

public class DatapackManager {
    private static final String DATAPACKS_DIR = "datapacks";
    private static final String METADATA_FILE = "pack.mcmeta";

    private final MinecraftServer server;
    private final Path datapacksPath;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "UtilityNexusAdmin-DatapackLoader");
        t.setDaemon(true);
        return t;
    });

    public DatapackManager(MinecraftServer server) {
        this.server = server;
        this.datapacksPath = FMLPaths.GAMEDIR.get().resolve(DATAPACKS_DIR);
        ensureDirectory();
    }

    private void ensureDirectory() {
        try {
            Files.createDirectories(datapacksPath);
        } catch (IOException e) {
            UNALog.error("Failed to create datapacks directory: {}", e.getMessage());
        }
    }

    /**
     * B1: Scan the global datapacks directory and return the set of discovered pack IDs.
     * Used by {@code UtilityNexusAdmin.onServerAboutToStart} to select packs before the
     * initial datapack load, avoiding a full reload.
     */
    public static Set<String> discoverPackIds() {
        Path datapacksDir = FMLPaths.GAMEDIR.get().resolve(DATAPACKS_DIR);
        Set<String> ids = new HashSet<>();
        if (!Files.isDirectory(datapacksDir)) {
            try { Files.createDirectories(datapacksDir); } catch (IOException ignored) {}
            return ids;
        }
        try (Stream<Path> stream = Files.list(datapacksDir)) {
            stream.filter(p -> Files.isDirectory(p) || p.toString().endsWith(".zip"))
                    .forEach(p -> {
                        String name = p.getFileName().toString();
                        String id = name.endsWith(".zip")
                                ? name.substring(0, name.length() - 4)
                                : name;
                        ids.add(id);
                    });
        } catch (IOException e) {
            UNALog.error("Failed to scan global datapacks directory: {}", e.getMessage());
        }
        return ids;
    }

    /**
     * Called from {@code UtilityNexusAdmin.onAddPackFinders} to register a
     * {@link RepositorySource} that injects all packs found in {@code <gameDir>/datapacks/}
     * into every world's server-data pack repository.
     */
    public static void registerPackSource(AddPackFindersEvent event) {
        Path datapacksDir = FMLPaths.GAMEDIR.get().resolve(DATAPACKS_DIR);
        if (!Files.isDirectory(datapacksDir)) {
            try { Files.createDirectories(datapacksDir); } catch (IOException ignored) {}
        }

        event.addRepositorySource(consumer -> {
            int found = 0, injected = 0, skipped = 0;
            List<String> injectedNames = new ArrayList<>();
            List<String> skippedNames = new ArrayList<>();

            try (Stream<Path> stream = Files.list(datapacksDir)) {
                List<Path> entries = stream
                        .filter(p -> Files.isDirectory(p) || p.toString().endsWith(".zip"))
                        .sorted()
                        .toList();

                for (Path entry : entries) {
                    String entryName = entry.getFileName().toString();
                    String packId = entryName.endsWith(".zip")
                            ? entryName.substring(0, entryName.length() - 4)
                            : entryName;
                    found++;

                    Pack.ResourcesSupplier supplier;
                    if (Files.isDirectory(entry)) {
                        supplier = new net.minecraft.server.packs.PathPackResources.PathResourcesSupplier(entry);
                    } else if (entryName.endsWith(".zip")) {
                        supplier = new net.minecraft.server.packs.FilePackResources.FileResourcesSupplier(entry);
                    } else {
                        skipped++;
                        skippedNames.add(entryName);
                        continue;
                    }

                    if (UNAConfig.validateOnLoad() && !validatePack(entry, entryName)) {
                        skipped++;
                        skippedNames.add(entryName);
                        continue;
                    }

                    if (UNAConfig.createMissingMetadata() && Files.isDirectory(entry)) {
                        createDefaultMetadataIfNeeded(entry, packId);
                    }

                    PackLocationInfo locationInfo = new PackLocationInfo(
                            packId,
                            Component.literal(packId),
                            PackSource.BUILT_IN,
                            java.util.Optional.empty());
                    PackSelectionConfig selectionConfig = new PackSelectionConfig(
                            false, Pack.Position.TOP, false);

                    Pack pack = Pack.readMetaAndCreate(locationInfo, supplier, PackType.SERVER_DATA, selectionConfig);
                    if (pack != null) {
                        consumer.accept(pack);
                        injected++;
                        injectedNames.add(packId);
                    } else {
                        skipped++;
                        skippedNames.add(packId);
                    }
                }
            } catch (IOException e) {
                UNALog.error("Failed to scan global datapacks directory: {}", e.getMessage());
            }

            UNALog.info("Global datapacks: {} found, {} injected, {} skipped", found, injected, skipped);
            if (!injectedNames.isEmpty()) {
                UNALog.info("Injected: {}", String.join(", ", injectedNames));
            }
            if (!skippedNames.isEmpty()) {
                UNALog.warn("Skipped: {}", String.join(", ", skippedNames));
            }
        });
    }

    private static boolean validatePack(Path packPath, String name) {
        if (Files.isDirectory(packPath)) {
            Path metaPath = packPath.resolve(METADATA_FILE);
            if (!Files.exists(metaPath)) {
                if (!UNAConfig.createMissingMetadata()) {
                    UNALog.warn("Folder datapack '{}' missing pack.mcmeta", name);
                    return false;
                }
            }
            return true;
        } else if (name.endsWith(".zip")) {
            try (ZipFile zipFile = new ZipFile(packPath.toFile())) {
                if (zipFile.getEntry(METADATA_FILE) == null) {
                    UNALog.warn("ZIP datapack '{}' missing pack.mcmeta, cannot auto-fix", name);
                    return false;
                }
                return true;
            } catch (Exception e) {
                UNALog.warn("Failed to validate ZIP datapack '{}': {}", name, e.getMessage());
                return false;
            }
        }
        return false;
    }

    private static void createDefaultMetadataIfNeeded(Path dirPath, String name) {
        Path metaPath = dirPath.resolve(METADATA_FILE);
        if (Files.exists(metaPath)) return;
        try {
            JsonObject meta = new JsonObject();
            JsonObject pack = new JsonObject();
            pack.addProperty("pack_format", 48);
            pack.addProperty("description", "Utility Nexus Admin datapack: " + name);
            meta.add("pack", pack);
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            Files.writeString(metaPath, gson.toJson(meta));
            UNALog.info("Created default pack.mcmeta for '{}'", name);
        } catch (IOException e) {
            UNALog.error("Failed to create default pack.mcmeta for '{}': {}", name, e.getMessage());
        }
    }

    public CompletableFuture<DatapackResult> loadDatapack(String name) {
        return CompletableFuture.supplyAsync(() -> {
            String resolvedId = resolvePackId(name);
            if (resolvedId == null) {
                return DatapackResult.failure("Datapack not found: " + name);
            }
            PackRepository repo = server.getPackRepository();
            Set<String> enabled = new HashSet<>(repo.getSelectedIds());
            if (enabled.contains(resolvedId)) {
                return DatapackResult.failure("Datapack already enabled: " + resolvedId);
            }
            enabled.add(resolvedId);
            repo.setSelected(enabled);
            server.reloadResources(enabled).join();
            UNALog.info("Enabled datapack: {}", resolvedId);
            return DatapackResult.success("Datapack enabled: " + resolvedId, resolvedId);
        }, executor);
    }

    public CompletableFuture<DatapackResult> unloadDatapack(String name) {
        return CompletableFuture.supplyAsync(() -> {
            String resolvedId = resolvePackId(name);
            if (resolvedId == null) {
                return DatapackResult.failure("Datapack not found: " + name);
            }
            PackRepository repo = server.getPackRepository();
            Set<String> enabled = new HashSet<>(repo.getSelectedIds());
            if (!enabled.contains(resolvedId)) {
                return DatapackResult.failure("Datapack is not enabled: " + resolvedId);
            }
            enabled.remove(resolvedId);
            repo.setSelected(enabled);
            server.reloadResources(enabled).join();
            UNALog.info("Disabled datapack: {}", resolvedId);
            return DatapackResult.success("Datapack disabled: " + resolvedId);
        }, executor);
    }

    public CompletableFuture<DatapackResult> reloadDatapack(String name) {
        return CompletableFuture.supplyAsync(() -> {
            String resolvedId = resolvePackId(name);
            if (resolvedId == null) {
                return DatapackResult.failure("Datapack not found: " + name);
            }
            PackRepository repo = server.getPackRepository();
            Set<String> enabled = new HashSet<>(repo.getSelectedIds());
            enabled.remove(resolvedId);
            repo.setSelected(enabled);
            server.reloadResources(enabled).join();
            enabled.add(resolvedId);
            repo.setSelected(enabled);
            server.reloadResources(enabled).join();
            UNALog.info("Reloaded datapack: {}", resolvedId);
            return DatapackResult.success("Datapack reloaded: " + resolvedId, resolvedId);
        }, executor);
    }

    public CompletableFuture<DatapackResult> validateDatapack(String name) {
        return CompletableFuture.supplyAsync(() -> {
            Path packPath = findPackPath(name);
            if (packPath == null) {
                return DatapackResult.failure("Datapack not found: " + name);
            }
            String entryName = packPath.getFileName().toString();
            boolean valid = validatePack(packPath, entryName);
            if (valid) {
                return DatapackResult.success("Datapack is valid: " + name, name);
            } else {
                return DatapackResult.failure("Datapack validation failed: " + name);
            }
        }, executor);
    }

    public CompletableFuture<DatapackResult> loadAllFromDirectory() {
        return CompletableFuture.supplyAsync(() -> {
            PackRepository repo = server.getPackRepository();
            Set<String> enabled = new HashSet<>(repo.getSelectedIds());
            List<String> newlyEnabled = new ArrayList<>();

            try (Stream<Path> stream = Files.list(datapacksPath)) {
                stream.filter(p -> Files.isDirectory(p) || p.toString().endsWith(".zip"))
                        .forEach(p -> {
                            String entryName = p.getFileName().toString();
                            String id = entryName.endsWith(".zip")
                                    ? entryName.substring(0, entryName.length() - 4)
                                    : entryName;
                            if (!enabled.contains(id)) {
                                enabled.add(id);
                                newlyEnabled.add(id);
                            }
                        });
            } catch (IOException e) {
                return DatapackResult.failure("Error scanning directory: " + e.getMessage());
            }

            repo.setSelected(enabled);
            server.reloadResources(enabled).join();
            UNALog.info("Enabled {} datapacks ({} newly enabled)", enabled.size(), newlyEnabled.size());
            return DatapackResult.success(
                    "Enabled " + enabled.size() + " datapacks (" + newlyEnabled.size() + " newly enabled)",
                    newlyEnabled);
        }, executor);
    }

    private String resolvePackId(String name) {
        Path packPath = findPackPath(name);
        if (packPath != null) {
            String entryName = packPath.getFileName().toString();
            return entryName.endsWith(".zip")
                    ? entryName.substring(0, entryName.length() - 4)
                    : entryName;
        }
        PackRepository repo = server.getPackRepository();
        for (Pack pack : repo.getAvailablePacks()) {
            if (pack.getId().equals(name)) return pack.getId();
        }
        return null;
    }

    private Path findPackPath(String name) {
        Path dirPath = datapacksPath.resolve(name);
        if (Files.exists(dirPath) && Files.isDirectory(dirPath)) return dirPath;
        Path zipPath = datapacksPath.resolve(name + ".zip");
        if (Files.exists(zipPath)) return zipPath;
        return null;
    }

    public List<String> getAvailableDatapacks() {
        List<String> available = new ArrayList<>();
        try (Stream<Path> stream = Files.list(datapacksPath)) {
            stream.filter(p -> Files.isDirectory(p) || p.toString().endsWith(".zip"))
                    .forEach(p -> {
                        String name = p.getFileName().toString();
                        if (name.endsWith(".zip")) {
                            name = name.substring(0, name.length() - 4);
                        }
                        available.add(name);
                    });
        } catch (IOException e) {
            UNALog.error("Failed to list available datapacks: {}", e.getMessage());
        }
        return available;
    }

    public List<DatapackInfo> getLoadedDatapacks() {
        List<DatapackInfo> loaded = new ArrayList<>();
        PackRepository repo = server.getPackRepository();
        Set<String> enabledIds = new HashSet<>(repo.getSelectedIds());
        try (Stream<Path> stream = Files.list(datapacksPath)) {
            stream.filter(p -> Files.isDirectory(p) || p.toString().endsWith(".zip"))
                    .forEach(p -> {
                        String entryName = p.getFileName().toString();
                        String id = entryName.endsWith(".zip")
                                ? entryName.substring(0, entryName.length() - 4)
                                : entryName;
                        if (enabledIds.contains(id)) {
                            loaded.add(new DatapackInfo(id, p, true, System.currentTimeMillis()));
                        }
                    });
        } catch (IOException e) {
            UNALog.error("Failed to list loaded datapacks: {}", e.getMessage());
        }
        return loaded;
    }

    public void shutdown() {
        executor.shutdown();
    }

    public record DatapackInfo(String name, Path sourcePath, boolean loaded, long loadedAt) {}

    public static class DatapackResult {
        private final boolean success;
        private final String message;
        private final Object data;

        private DatapackResult(boolean success, String message, Object data) {
            this.success = success;
            this.message = message;
            this.data = data;
        }

        public static DatapackResult success(String message) {
            return new DatapackResult(true, message, null);
        }

        public static DatapackResult success(String message, Object data) {
            return new DatapackResult(true, message, data);
        }

        public static DatapackResult failure(String message) {
            return new DatapackResult(false, message, null);
        }

        public boolean success() { return success; }
        public String message() { return message; }
        public Object data() { return data; }
    }
}
