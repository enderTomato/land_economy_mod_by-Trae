package cn.autoforged.land_economy_mod_1783600667.client.screen;

import com.google.gson.*;
import net.minecraft.client.Minecraft;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 客户端路标管理器（本地存储，不同步网络）。
 * 存储到 .minecraft/land_economy/waypoints.json
 */
public final class WaypointManager {

    private static final List<WaypointData> waypoints = new CopyOnWriteArrayList<>();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private WaypointManager() {}

    public static void add(WaypointData wp) {
        waypoints.add(wp);
        save();
    }

    public static void remove(WaypointData wp) {
        waypoints.remove(wp);
        save();
    }

    public static List<WaypointData> getAll() {
        return Collections.unmodifiableList(waypoints);
    }

    public static List<WaypointData> getInDimension(String dimId) {
        List<WaypointData> result = new ArrayList<>();
        for (WaypointData wp : waypoints) {
            if (wp.getDimension().equals(dimId)) {
                result.add(wp);
            }
        }
        return result;
    }

    public static void load() {
        waypoints.clear();
        Path file = getSaveFile();
        if (!Files.exists(file)) return;

        try (Reader reader = Files.newBufferedReader(file)) {
            JsonArray arr = GSON.fromJson(reader, JsonArray.class);
            if (arr != null) {
                for (JsonElement elem : arr) {
                    JsonObject obj = elem.getAsJsonObject();
                    String name = obj.get("name").getAsString();
                    int x = obj.get("x").getAsInt();
                    int z = obj.get("z").getAsInt();
                    int color = obj.has("color") ? obj.get("color").getAsInt() : 0xFFFFFF00;
                    String dim = obj.get("dimension").getAsString();
                    boolean death = obj.has("death") && obj.get("death").getAsBoolean();
                    waypoints.add(new WaypointData(name, x, z, color, dim, death));
                }
            }
        } catch (Exception e) {
            // 文件损坏，忽略
        }
    }

    public static void save() {
        Path file = getSaveFile();
        try {
            Files.createDirectories(file.getParent());
            JsonArray arr = new JsonArray();
            for (WaypointData wp : waypoints) {
                JsonObject obj = new JsonObject();
                obj.addProperty("name", wp.getName());
                obj.addProperty("x", wp.getBlockX());
                obj.addProperty("z", wp.getBlockZ());
                obj.addProperty("color", wp.getColor());
                obj.addProperty("dimension", wp.getDimension());
                obj.addProperty("death", wp.isDeathPoint());
                arr.add(obj);
            }
            try (Writer writer = Files.newBufferedWriter(file)) {
                GSON.toJson(arr, writer);
            }
        } catch (IOException e) {
            // 静默失败
        }
    }

    private static Path getSaveFile() {
        return Minecraft.getInstance().gameDirectory.toPath()
                .resolve("land_economy").resolve("waypoints.json");
    }
}