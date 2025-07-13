package org.MegaNoob.ultimatehotbars;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import net.minecraft.client.Minecraft;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

public class ClientDataConfig {
    private static final File FILE = new File(Minecraft.getInstance().gameDirectory, "config/ultimatehotbars_runtime.json");
    private static JsonObject root = new JsonObject();

    public static void load() {
        if (!FILE.exists()) return;

        try (FileReader reader = new FileReader(FILE)) {
            root = JsonParser.parseReader(reader).getAsJsonObject();
        } catch (IOException e) {
            System.err.println("[UltimateHotbars] Failed to load runtime config: " + e.getMessage());
        }
    }

    public static void save() {
        try (FileWriter writer = new FileWriter(FILE)) {
            writer.write(root.toString());
        } catch (IOException e) {
            System.err.println("[UltimateHotbars] Failed to save runtime config: " + e.getMessage());
        }
    }

    public static int getInt(String key, int defaultValue) {
        if (root.has(key)) {
            return root.get(key).getAsInt();
        }
        return defaultValue;
    }

    public static void setInt(String key, int value) {
        root.add(key, new JsonPrimitive(value));
    }
}
