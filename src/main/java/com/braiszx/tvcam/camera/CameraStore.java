package com.braiszx.tvcam.camera;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.braiszx.tvcam.TVCam;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Guarda ajustes y camaras en config/tvcam.json. Las camaras van agrupadas por
 * servidor y dimension, para que al volver a entrar sigan donde las dejaste.
 */
public final class CameraStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type WORLDS_TYPE =
            new com.google.gson.reflect.TypeToken<Map<String, List<CameraPoint>>>() {}.getType();
    private static final Type FIELDS_TYPE =
            new com.google.gson.reflect.TypeToken<Map<String, List<Field>>>() {}.getType();
    private static final Type ACTIVE_TYPE =
            new com.google.gson.reflect.TypeToken<Map<String, String>>() {}.getType();

    private final Path file;
    private final Map<String, List<CameraPoint>> byWorld = new HashMap<>();
    private final Map<String, List<Field>> fieldsByWorld = new HashMap<>();
    private final Map<String, String> activeFieldByWorld = new HashMap<>();
    private TVCamSettings settings = new TVCamSettings();

    public CameraStore() {
        this.file = FabricLoader.getInstance().getConfigDir().resolve("tvcam.json");
        load();
    }

    public TVCamSettings settings() {
        return settings;
    }

    public List<CameraPoint> get(String worldKey) {
        return byWorld.computeIfAbsent(worldKey, key -> new ArrayList<>());
    }

    public List<Field> fields(String worldKey) {
        return fieldsByWorld.computeIfAbsent(worldKey, key -> new ArrayList<>());
    }

    public String activeFieldName(String worldKey) {
        return activeFieldByWorld.get(worldKey);
    }

    public void setActiveFieldName(String worldKey, String name) {
        if (name == null) {
            activeFieldByWorld.remove(worldKey);
        } else {
            activeFieldByWorld.put(worldKey, name);
        }
    }

    private void load() {
        if (!Files.isRegularFile(file)) {
            return;
        }
        try (Reader reader = Files.newBufferedReader(file)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            Map<String, List<CameraPoint>> worlds;
            if (root.has("worlds")) {
                worlds = GSON.fromJson(root.get("worlds"), WORLDS_TYPE);
                if (root.has("settings")) {
                    TVCamSettings loaded = GSON.fromJson(root.get("settings"), TVCamSettings.class);
                    if (loaded != null) {
                        settings = loaded.normalized();
                    }
                }
            } else {
                // Fichero de una version anterior: era solo el mapa de mundos.
                worlds = GSON.fromJson(root, WORLDS_TYPE);
            }
            if (root.has("fields")) {
                Map<String, List<Field>> fields = GSON.fromJson(root.get("fields"), FIELDS_TYPE);
                if (fields != null) {
                    fieldsByWorld.putAll(fields);
                }
            }
            if (root.has("activeField")) {
                Map<String, String> active = GSON.fromJson(root.get("activeField"), ACTIVE_TYPE);
                if (active != null) {
                    activeFieldByWorld.putAll(active);
                }
            }
            if (worlds != null) {
                worlds.forEach((key, cameras) -> {
                    List<CameraPoint> normalized = new ArrayList<>(cameras.size());
                    for (CameraPoint camera : cameras) {
                        normalized.add(camera.normalized());
                    }
                    byWorld.put(key, normalized);
                });
            }
        } catch (IOException | RuntimeException e) {
            TVCam.LOGGER.warn("No se pudo leer tvcam.json, empiezo de cero", e);
        }
    }

    public void save() {
        try {
            Files.createDirectories(file.getParent());
            JsonObject root = new JsonObject();
            root.add("settings", GSON.toJsonTree(settings));
            root.add("worlds", GSON.toJsonTree(byWorld, WORLDS_TYPE));
            root.add("fields", GSON.toJsonTree(fieldsByWorld, FIELDS_TYPE));
            root.add("activeField", GSON.toJsonTree(activeFieldByWorld, ACTIVE_TYPE));
            try (Writer writer = Files.newBufferedWriter(file)) {
                GSON.toJson(root, writer);
            }
        } catch (IOException e) {
            TVCam.LOGGER.error("No se pudieron guardar las camaras", e);
        }
    }
}
