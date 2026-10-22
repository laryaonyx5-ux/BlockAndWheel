package com.colorwheel;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import net.fabricmc.loader.api.FabricLoader;

/** Reglages du mod, sauvegardes dans config/colorwheel.properties. */
public final class Config {
	public static volatile int radiusChunks = 1;       // rayon en chunks (1 chunk = 16 blocs)
	public static volatile String customObjective = ""; // objectif personnalise saisi par le joueur
	public static volatile int intervalSeconds = 120; // temps entre deux tirages
	public static volatile boolean blockMode = false; // false = Roue, true = Bloc
	public static volatile boolean resetObjectivesOnDeath = false; // mort -> retour a l'objectif 1
	public static volatile boolean protectContainers = true;
	/** 0 = roue auto, 1 = objectif prédéfini, 2 = aucun, 3 = personnalisé. */
	public static volatile int objectiveMode = 0;
	public static volatile int fixedObjective = 6; // index dans Objectives.LIST (6 = diamant)       // ne pas supprimer coffres, fours, etc.

	public static final int MIN_RADIUS_CHUNKS = 1;
	public static final int MAX_RADIUS_CHUNKS = 64;
	public static final int MIN_INTERVAL = 10;
	public static final int MAX_INTERVAL = 3600;

	private Config() {
	}

	private static Path file() {
		return FabricLoader.getInstance().getConfigDir().resolve("colorwheel.properties");
	}

	public static void load() {
		Path f = file();
		if (!Files.exists(f)) {
			save();
			return;
		}
		Properties p = new Properties();
		try (InputStream in = Files.newInputStream(f)) {
			p.load(in);
			radiusChunks = clamp(parse(p.getProperty("radiusChunks"), Math.max(1, parse(p.getProperty("radius"), 16) / 16)), MIN_RADIUS_CHUNKS, MAX_RADIUS_CHUNKS);
			customObjective = p.getProperty("customObjective", customObjective);
			intervalSeconds = clamp(parse(p.getProperty("intervalSeconds"), intervalSeconds), MIN_INTERVAL, MAX_INTERVAL);
			blockMode = "block".equalsIgnoreCase(p.getProperty("mode", blockMode ? "block" : "wheel"));
			resetObjectivesOnDeath = Boolean.parseBoolean(p.getProperty("resetObjectivesOnDeath", "false"));
			protectContainers = Boolean.parseBoolean(p.getProperty("protectContainers", "true"));
			objectiveMode = clamp(parse(p.getProperty("objectiveMode"), objectiveMode), 0, 3);
			fixedObjective = Math.floorMod(parse(p.getProperty("fixedObjective"), fixedObjective), Objectives.LIST.size());
		} catch (IOException | RuntimeException e) {
			System.err.println("[colorwheel] config illisible, valeurs par defaut : " + e);
		}
	}

	public static synchronized void save() {
		Properties p = new Properties();
		p.setProperty("radiusChunks", Integer.toString(radiusChunks));
		p.setProperty("customObjective", customObjective);
		p.setProperty("intervalSeconds", Integer.toString(intervalSeconds));
		p.setProperty("mode", blockMode ? "block" : "wheel");
		p.setProperty("resetObjectivesOnDeath", Boolean.toString(resetObjectivesOnDeath));
		p.setProperty("protectContainers", Boolean.toString(protectContainers));
		p.setProperty("objectiveMode", Integer.toString(objectiveMode)); // 0 auto, 1 choisi, 2 aucun
		p.setProperty("fixedObjective", Integer.toString(fixedObjective));
		try (OutputStream out = Files.newOutputStream(file())) {
			p.store(out, "Color Wheel - mode: wheel | block");
		} catch (IOException e) {
			System.err.println("[colorwheel] impossible d'ecrire la config : " + e);
		}
	}

	public static int clamp(int v, int min, int max) {
		return Math.max(min, Math.min(max, v));
	}

	private static int parse(String s, int def) {
		try {
			return s == null ? def : Integer.parseInt(s.trim());
		} catch (NumberFormatException e) {
			return def;
		}
	}
}
