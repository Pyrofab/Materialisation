package me.shedaniel.materialisation.config;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import me.shedaniel.materialisation.Materialisation;
import me.shedaniel.materialisation.api.*;
import me.shedaniel.materialisation.config.MaterialisationConfig.ConfigIngredients;
import me.shedaniel.materialisation.config.MaterialisationConfig.ConfigMaterial;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.Identifier;
import net.minecraft.util.Pair;

import java.io.*;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class ConfigHelper {

    public static final ExecutorService EXECUTOR_SERVICE = Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, "Materialisation"));
    public static final File CONFIG_DIRECTORY = new File(FabricLoader.getInstance().getConfigDirectory(), "materialisation");
    public static final File MATERIALS_DIRECTORY = new File(CONFIG_DIRECTORY, "material");
    public static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File OLD_MATERIALS_DIRECTORY = new File(CONFIG_DIRECTORY, "materials");
    public static boolean loading = false;
    public static final Map<String, PartMaterial> MATERIAL_CACHE = new HashMap<>();

    public static void loadDefault() throws IOException {
        if (OLD_MATERIALS_DIRECTORY.exists())
            OLD_MATERIALS_DIRECTORY.renameTo(new File(CONFIG_DIRECTORY, "materials_old"));
        if (!CONFIG_DIRECTORY.exists() || !MATERIALS_DIRECTORY.exists())
            fillDefaultConfigs();
    }

    public static void loadConfig() {
        loading = true;
        try {
            List<PartMaterial> defaultMaterials = Lists.newArrayList();
            List<MaterialsPack> defaultPacks = Lists.newArrayList();
            List<MaterialsPack> loadedPacks = Lists.newArrayList();
            List<Pair<ConfigPack, ConfigMaterial>> knownMaterials = Lists.newArrayList();
            List<JsonObject> overrides = Lists.newArrayList();
            MATERIAL_CACHE.clear();
            PartMaterials.clearMaterials();
            try {
                FabricLoader.getInstance().getEntrypoints("materialisation_default", DefaultMaterialSupplier.class).stream().forEach(supplier -> {
                    try {
                        defaultMaterials.addAll(supplier.getMaterials());
                    } catch (Throwable throwable) {
                        throwable.printStackTrace();
                    }
                    try {
                        defaultPacks.addAll(supplier.getMaterialPacks());
                    } catch (Throwable throwable) {
                        throwable.printStackTrace();
                    }
                });
                for (MaterialsPack defaultPack : defaultPacks) {
                    ConfigPack pack = new ConfigPack(defaultPack.getConfigPackInfo(), Maps.newLinkedHashMap());
                    loadedPacks.add(pack);
                    for (Map.Entry<String, PartMaterial> entry : defaultPack.getKnownMaterialMap().entrySet()) {
                        knownMaterials.add(new Pair<>(pack, new ConfigMaterial(entry.getValue())));
                    }
                    Materialisation.LOGGER.info("[Materialisation] Loading default pack: " + pack.getIdentifier().toString());
                }
                for (PartMaterial partMaterial : defaultMaterials) {
                    ConfigMaterial material = new ConfigMaterial(partMaterial);
                    knownMaterials.add(new Pair<>(PartMaterials.getDefaultPack(), material));
                    Materialisation.LOGGER.info("[Materialisation] Loading default material: " + material.getIdentifier().toString());
                }
                for (File file : MATERIALS_DIRECTORY.listFiles()) {
                    // Load Old
                    if (file.isFile() && file.getName().toLowerCase(Locale.ROOT).endsWith(".json")) {
                        try {
                            JsonObject object = GSON.fromJson(new FileReader(file), JsonObject.class);
                            if (!object.has("type") || object.get("type").getAsString().equalsIgnoreCase("material")) {
                                Materialisation.LOGGER.info("[Materialisation] Loading material file: " + file.getName());
                                knownMaterials.add(new Pair<>(PartMaterials.getDefaultPack(), GSON.fromJson(object, ConfigMaterial.class)));
                            } else {
                                String type = object.get("type").getAsString();
                                if (type.equalsIgnoreCase("override")) {
                                    Materialisation.LOGGER.info("[Materialisation] Loading override file: " + file.getName());
                                    overrides.add(object);
                                } else {
                                    Materialisation.LOGGER.warn("[Materialisation] Cancelled loading unknown file: " + file.getName());
                                }
                            }
                        } catch (Exception e) {
                            Materialisation.LOGGER.error("[Materialisation] Failed to load material.", e);
                        }
                    } else
                        // Load Packs
                        if (file.isDirectory()) {
                            try {
                                File packInfoFile = new File(file, "materials.info.json");
                                if (packInfoFile.exists()) {
                                    ConfigPackInfo packInfo = GSON.fromJson(new FileReader(packInfoFile), ConfigPackInfo.class);
                                    ConfigPack configPack = new ConfigPack(packInfo, Maps.newLinkedHashMap());
                                    loadedPacks.add(configPack);
                                    Materialisation.LOGGER.info("[Materialisation] Loading material pack: " + packInfo.getIdentifier());
                                    for (File listFile : file.listFiles()) {
                                        if (listFile.isFile() && listFile.getName().toLowerCase(Locale.ROOT).endsWith(".json") && !listFile.getName().equals("materials.info.json")) {
                                            try {
                                                JsonObject object = GSON.fromJson(new FileReader(listFile), JsonObject.class);
                                                if (!object.has("type") || object.get("type").getAsString().equalsIgnoreCase("material")) {
                                                    Materialisation.LOGGER.info("[Materialisation] Loading material file: " + listFile.getName());
                                                    knownMaterials.add(new Pair<>(configPack, GSON.fromJson(object, ConfigMaterial.class)));
                                                } else {
                                                    String type = object.get("type").getAsString();
                                                    if (type.equalsIgnoreCase("override")) {
                                                        Materialisation.LOGGER.info("[Materialisation] Loading override file: " + listFile.getName());
                                                        overrides.add(object);
                                                        configPack.getOverrides().incrementAndGet();
                                                    } else {
                                                        Materialisation.LOGGER.warn("[Materialisation] Cancelled loading unknown file: " + listFile.getName());
                                                    }
                                                }
                                            } catch (Exception e) {
                                                Materialisation.LOGGER.error("[Materialisation] Failed to load material.", e);
                                            }
                                        }
                                    }
                                }
                            } catch (Exception e) {
                                Materialisation.LOGGER.error("[Materialisation] Failed to load material pack.", e);
                            }
                        } else if (file.isFile() && file.getName().toLowerCase(Locale.ROOT).endsWith(".materialpack")) {
                            try (ZipFile zipFile = new ZipFile(file)) {
                                ZipEntry packInfoEntry = zipFile.getEntry("materials.info.json");
                                if (packInfoEntry != null) {
                                    ConfigPackInfo packInfo = GSON.fromJson(new InputStreamReader(zipFile.getInputStream(packInfoEntry)), ConfigPackInfo.class);
                                    ConfigPack configPack = new ConfigPack(packInfo, Maps.newLinkedHashMap());
                                    loadedPacks.add(configPack);
                                    Materialisation.LOGGER.info("[Materialisation] Loading material pack: " + packInfo.getIdentifier());
                                    final Enumeration<? extends ZipEntry> entries = zipFile.entries();
                                    while (entries.hasMoreElements()) {
                                        final ZipEntry zipEntry = entries.nextElement();
                                        if (!zipEntry.isDirectory() && zipEntry.getName().toLowerCase(Locale.ROOT).endsWith(".json") && !zipEntry.getName().equals("materials.info.json")) {
                                            try {
                                                JsonObject object = GSON.fromJson(new InputStreamReader(zipFile.getInputStream(zipEntry)), JsonObject.class);
                                                if (!object.has("type") || object.get("type").getAsString().equalsIgnoreCase("material")) {
                                                    Materialisation.LOGGER.info("[Materialisation] Loading material file: " + zipEntry.getName());
                                                    knownMaterials.add(new Pair<>(configPack, GSON.fromJson(object, ConfigMaterial.class)));
                                                } else {
                                                    String type = object.get("type").getAsString();
                                                    if (type.equalsIgnoreCase("override")) {
                                                        Materialisation.LOGGER.info("[Materialisation] Loading override file: " + zipEntry.getName());
                                                        overrides.add(object);
                                                        configPack.getOverrides().incrementAndGet();
                                                    } else {
                                                        Materialisation.LOGGER.warn("[Materialisation] Cancelled loading unknown file: " + zipEntry.getName());
                                                    }
                                                }
                                            } catch (Exception e) {
                                                Materialisation.LOGGER.error("[Materialisation] Failed to load material.", e);
                                            }
                                        }
                                    }
                                }
                            } catch (Exception e) {
                                Materialisation.LOGGER.error("[Materialisation] Failed to load material pack.", e);
                            }
                        }
                }
                overrides.sort(Comparator.comparingDouble(value -> value.has("priority") ? value.get("priority").getAsDouble() : 0d));
                for (JsonObject override : overrides)
                    try {
                        Identifier identifier = new Identifier(override.get("name").getAsString());
                        for (Map.Entry<String, JsonElement> entry : override.entrySet())
                            if (!entry.getKey().equalsIgnoreCase("type") && !entry.getKey().equalsIgnoreCase("name") && !entry.getKey().equalsIgnoreCase("priority")) {
                                ConfigMaterial material = null;
                                for (Pair<ConfigPack, ConfigMaterial> knownMaterial : knownMaterials) {
                                    if (new Identifier(knownMaterial.getRight().name).equals(identifier)) {
                                        material = knownMaterial.getRight();
                                        break;
                                    }
                                }
                                if (material == null)
                                    throw new NullPointerException("Material " + identifier.toString() + " not found!");
                                String key = entry.getKey();
                                boolean replaced = false;
                                for (Field declaredField : ConfigMaterial.class.getDeclaredFields())
                                    if (Modifier.isPublic(declaredField.getModifiers()) && !Modifier.isTransient(declaredField.getModifiers()))
                                        if (declaredField.getName().equalsIgnoreCase(key)) {
                                            declaredField.setAccessible(true);
                                            declaredField.set(material, GSON.fromJson(entry.getValue(), Object.class));
                                            replaced = true;
                                            break;
                                        }
                                if (!replaced)
                                    throw new NullPointerException("Failed to place field '" + key + "' of material " + material.getIdentifier().toString() + "!");
                            }
                    } catch (Exception e) {
                        Materialisation.LOGGER.error("[Materialisation] Failed to load override.", e);
                    }
            } catch (Exception e) {
                Materialisation.LOGGER.error("[Materialisation] Failed to load config.", e);
            }
            for (Pair<ConfigPack, ConfigMaterial> knownMaterial : knownMaterials) {
                ConfigPack pack = knownMaterial.getLeft();
                ConfigMaterial right = knownMaterial.getRight();
                if (right.enabled)
                    pack.getKnownMaterialMap().put(right.name, right);
            }
            List<MaterialsPack> packs = Lists.newArrayList();
            for (MaterialsPack loadedPack : loadedPacks) {
                boolean a = true;
                for (String requiredMod : loadedPack.getConfigPackInfo().getRequiredMods()) {
                    if (!FabricLoader.getInstance().isModLoaded(requiredMod)) {
                        a = false;
                    }
                }
                if (a) {
                    packs.add(loadedPack);
                    Materialisation.LOGGER.info(String.format("[Materialisation] Finished loading material pack: %s with %d material(s).", loadedPack.getIdentifier().toString(), loadedPack.getKnownMaterials().count()));
                }
            }
            List<String> packIds = Lists.newArrayList();
            for (MaterialsPack pack : packs) {
                String id = pack.getIdentifier().toString().toLowerCase(Locale.ROOT);
                if (packIds.stream().anyMatch(s -> s.equals(id)))
                    throw new IllegalStateException("Duplicate Pack Ids: " + id);
                packIds.add(id);
            }
            List<String> materialIds = Lists.newArrayList();
            for (MaterialsPack pack : packs) {
                pack.getKnownMaterials().forEach(partMaterial -> {
                    String id = partMaterial.getIdentifier().toString().toLowerCase(Locale.ROOT);
                    if (materialIds.stream().anyMatch(s -> s.equals(id)))
                        throw new IllegalStateException("Duplicate Material Ids: " + id);
                    materialIds.add(id);
                });
            }
            packs.forEach(PartMaterials::registerPack);
            Materialisation.LOGGER.info("[Materialisation] Finished loading material(s): " + PartMaterials.getKnownMaterials().map(PartMaterial::getIdentifier).map(Identifier::toString).collect(Collectors.joining(", ")));
            if (FabricLoader.getInstance().isDevelopmentEnvironment()) {
                File autoGen = new File(CONFIG_DIRECTORY, "materialisation-dev-autogen");
                autoGen.mkdirs();
                for (File file : CONFIG_DIRECTORY.listFiles())
                    if (file.isFile() && file.getName().toLowerCase(Locale.ROOT).endsWith(".json"))
                        file.delete();
                for (PartMaterial defaultMaterial : defaultMaterials)
                    try {
                        String s = GSON.toJson(new ConfigMaterial(defaultMaterial));
                        FileWriter writer = new FileWriter(new File(autoGen, defaultMaterial.getIdentifier().toString() + ".json"), false);
                        writer.write(s);
                        writer.close();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
            }
        } catch (Throwable throwable) {
            throwable.printStackTrace();
        }
        MATERIAL_CACHE.clear();
        loading = false;
    }

    private static void fillDefaultConfigs() throws IOException {
        MATERIALS_DIRECTORY.mkdirs();
    }

    public static List<ConfigIngredients> fromMap(Map<BetterIngredient, Float> map) {
        return map.entrySet().stream().map(entry -> new ConfigIngredients(entry.getKey().toConfigIngredient(), entry.getValue())).collect(Collectors.toList());
    }

    public static Map<BetterIngredient, Float> fromJson(List<ConfigIngredients> jsonObjects) {
        LinkedHashMap<BetterIngredient, Float> map = Maps.newLinkedHashMap();
        jsonObjects.forEach(configIngredients -> map.put(configIngredients.ingredient.toBetterIngredient(), configIngredients.multiplier));
        return map;
    }

}
