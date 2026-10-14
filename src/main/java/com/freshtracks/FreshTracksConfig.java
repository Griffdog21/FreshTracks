package com.freshtracks;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Settings in config/freshtracks.json. Created with the defaults on first launch; edit it with any text editor and
 * restart. Server settings only matter on the server (or in single player); client settings only on your own game.
 * Unknown or missing entries fall back to the defaults, and the file is rewritten with every setting listed.
 */
public final class FreshTracksConfig {
	// ------------------------------------------------------------------ server / world
	/** Roll a new mountain weather event (powder day, blizzard...) every dawn. Off = vanilla weather only. */
	public static boolean weatherEvents = true;
	/** Fresh powder piles up around players while it snows. */
	public static boolean powderAccumulation = true;
	/** Cold weather freezes underdressed players (warm gear and hot cocoa help). */
	public static boolean coldExposure = true;
	/** Chairlift rope speed multiplier (1.0 = normal). */
	public static double liftSpeed = 1.0;
	/** Wind strength multiplier (0 = no wind). */
	public static double windStrength = 1.0;
	/** Ski and board shops can appear in snowy villages (new chunks only). */
	public static boolean villageShops = true;
	/** Landing tricks gives experience. */
	public static boolean trickXp = true;

	// ------------------------------------------------------------------ client
	/** "kmh" or "mph" for the speedometer and wind. */
	public static String speedUnits = "kmh";
	/** Show the speedometer while riding. */
	public static boolean showSpeedometer = true;
	/** Switch to third person when you click in. */
	public static boolean autoThirdPerson = true;

	/** Preferred riding view: helmet, chase, or keep the current Minecraft view. */
	public static String rideCamera = "chase";
	/** full, reduced (gentle FOV, no banking), or off. */
	public static String rideEffects = "full";
	/** Wind rush volume for the local skier, independently adjustable from world sounds. */
	public static double rideWindVolume = 1.0;

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private FreshTracksConfig() {
	}

	public static Path file() {
		return FabricLoader.getInstance().getConfigDir().resolve("freshtracks.json");
	}

	public static void load() {
		Path path = file();
		if (Files.exists(path)) {
			try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
				JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
				weatherEvents = bool(json, "weatherEvents", weatherEvents);
				powderAccumulation = bool(json, "powderAccumulation", powderAccumulation);
				coldExposure = bool(json, "coldExposure", coldExposure);
				liftSpeed = clamp(num(json, "liftSpeed", liftSpeed), 0.1, 4.0);
				windStrength = clamp(num(json, "windStrength", windStrength), 0.0, 3.0);
				villageShops = bool(json, "villageShops", villageShops);
				trickXp = bool(json, "trickXp", trickXp);
				speedUnits = "mph".equalsIgnoreCase(str(json, "speedUnits", speedUnits)) ? "mph" : "kmh";
				showSpeedometer = bool(json, "showSpeedometer", showSpeedometer);
				autoThirdPerson = bool(json, "autoThirdPerson", autoThirdPerson);
				rideCamera = choice(str(json, "rideCamera", autoThirdPerson ? "chase" : "keep"), "chase", "helmet", "chase", "keep");
				rideEffects = choice(str(json, "rideEffects", rideEffects), "full", "full", "reduced", "off");
				rideWindVolume = clamp(num(json, "rideWindVolume", rideWindVolume), 0, 1);
			} catch (IOException | RuntimeException e) {
				FreshTracks.LOG.warn("Could not read {} ({}); using defaults for anything unreadable", path, e.toString());
			}
		}
		save();
	}

	public static void save() {
		JsonObject json = new JsonObject();
		json.addProperty("_comment", "Fresh Tracks settings. Restart the game (or server) after editing.");
		json.addProperty("weatherEvents", weatherEvents);
		json.addProperty("powderAccumulation", powderAccumulation);
		json.addProperty("coldExposure", coldExposure);
		json.addProperty("liftSpeed", liftSpeed);
		json.addProperty("windStrength", windStrength);
		json.addProperty("villageShops", villageShops);
		json.addProperty("trickXp", trickXp);
		json.addProperty("speedUnits", speedUnits);
		json.addProperty("showSpeedometer", showSpeedometer);
		json.addProperty("autoThirdPerson", autoThirdPerson);
		json.addProperty("rideCamera", rideCamera);
		json.addProperty("rideEffects", rideEffects);
		json.addProperty("rideWindVolume", rideWindVolume);
		try {
			Files.createDirectories(file().getParent());
			try (Writer writer = Files.newBufferedWriter(file(), StandardCharsets.UTF_8)) {
				GSON.toJson(json, writer);
			}
		} catch (IOException e) {
			FreshTracks.LOG.warn("Could not write {}: {}", file(), e.toString());
		}
	}

	/** Speed in blocks/tick as a display string in the chosen units. */
	public static String speed(double blocksPerTick) {
		double kmh = blocksPerTick * 72.0;
		return "mph".equals(speedUnits) ? Math.round(kmh * 0.621371) + " mph" : Math.round(kmh) + " km/h";
	}

	private static boolean bool(JsonObject json, String key, boolean fallback) {
		return json.has(key) && json.get(key).isJsonPrimitive() ? json.get(key).getAsBoolean() : fallback;
	}

	private static double num(JsonObject json, String key, double fallback) {
		return json.has(key) && json.get(key).isJsonPrimitive() ? json.get(key).getAsDouble() : fallback;
	}

	private static String str(JsonObject json, String key, String fallback) {
		return json.has(key) && json.get(key).isJsonPrimitive() ? json.get(key).getAsString() : fallback;
	}

	private static String choice(String value, String fallback, String... choices) {
		for (String option : choices) {
			if (option.equalsIgnoreCase(value)) return option;
		}
		return fallback;
	}

	private static double clamp(double v, double lo, double hi) {
		return Double.isFinite(v) ? Math.max(lo, Math.min(hi, v)) : lo;
	}
}
