package com.colorwheel;

import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.imageio.ImageIO;

import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;

/**
 * La couleur dominante est calculée à partir de la texture du bloc quand une texture
 * portant le même identifiant est disponible. Les règles par nom restent un repli pour
 * les blocs dont le modèle utilise une texture partagée ou un nom différent.
 */
public final class ColorMap {
	private static final Map<Block, WheelColor> CACHE = new ConcurrentHashMap<>();
	private static volatile ResourceManager resources;

	/** Prefixes de teintures (laine, beton, verre teinte, lit, etc.). */
	private static final Map<String, WheelColor> DYES = new LinkedHashMap<>();
	/** Mots-cles dans le nom (testes dans l'ordre). */
	private static final Map<String, WheelColor> RULES = new LinkedHashMap<>();

	static {
		DYES.put("light_gray_", WheelColor.GRAY);
		DYES.put("light_blue_", WheelColor.BLUE);
		DYES.put("cyan_", WheelColor.BLUE);
		DYES.put("magenta_", WheelColor.PINK);
		DYES.put("lime_", WheelColor.GREEN);
		DYES.put("black_", WheelColor.BLACK);
		DYES.put("white_", WheelColor.WHITE);
		DYES.put("orange_", WheelColor.ORANGE);
		DYES.put("yellow_", WheelColor.YELLOW);
		DYES.put("pink_", WheelColor.PINK);
		DYES.put("gray_", WheelColor.GRAY);
		DYES.put("purple_", WheelColor.PURPLE);
		DYES.put("blue_", WheelColor.BLUE);
		DYES.put("brown_", WheelColor.BROWN);
		DYES.put("green_", WheelColor.GREEN);
		DYES.put("red_", WheelColor.RED);

		// --- cas particuliers a tester avant les mots generiques
		r("cherry_leaves", WheelColor.PINK);
		r("leaves", WheelColor.GREEN);
		r("end_stone", WheelColor.YELLOW);
		r("sandstone", WheelColor.YELLOW);
		r("blackstone", WheelColor.BLACK);
		r("redstone", WheelColor.RED);
		r("crying_obsidian", WheelColor.PURPLE);
		r("obsidian", WheelColor.BLACK);
		r("tinted_glass", WheelColor.BLACK);
		r("mycelium", WheelColor.PURPLE);
		r("oxidized", WheelColor.GREEN);
		r("weathered", WheelColor.GREEN);
		r("soul_campfire", WheelColor.BLUE);
		r("soul_torch", WheelColor.BLUE);
		r("soul_fire", WheelColor.BLUE);
		r("soul_lantern", WheelColor.BLUE);
		r("soul_sand", WheelColor.BROWN);
		r("soul_soil", WheelColor.BROWN);
		r("dead_bush", WheelColor.BROWN);
		r("dead_", WheelColor.GRAY);
		r("deepslate", WheelColor.GRAY);
		r("tuff", WheelColor.GRAY);
		r("stone_brick", WheelColor.GRAY);
		r("mud_brick", WheelColor.BROWN);
		r("mushroom_stem", WheelColor.WHITE);
		r("nether_gold_ore", WheelColor.YELLOW);
		r("wither", WheelColor.BLACK);

		// --- bois
		r("pale_oak", WheelColor.WHITE);
		r("dark_oak", WheelColor.BROWN);
		r("oak", WheelColor.BROWN);
		r("spruce", WheelColor.BROWN);
		r("jungle", WheelColor.BROWN);
		r("birch", WheelColor.YELLOW);
		r("acacia", WheelColor.ORANGE);
		r("mangrove", WheelColor.RED);
		r("cherry", WheelColor.PINK);
		r("crimson", WheelColor.RED);
		r("warped", WheelColor.BLUE);
		r("bamboo_sapling", WheelColor.GREEN);
		r("bamboo", WheelColor.YELLOW);

		// --- vert
		for (String s : new String[] {"grass", "fern", "moss", "cactus", "lily_pad", "vine", "slime", "emerald",
			"sugar_cane", "kelp", "seagrass", "melon", "sea_pickle", "sapling", "azalea", "dripleaf", "sweet_berry",
			"cave_vines", "clover"}) {
			r(s, WheelColor.GREEN);
		}

		// --- coraux
		r("tube_coral", WheelColor.BLUE);
		r("brain_coral", WheelColor.PINK);
		r("bubble_coral", WheelColor.PURPLE);
		r("fire_coral", WheelColor.RED);
		r("horn_coral", WheelColor.YELLOW);

		// --- marron
		for (String s : new String[] {"dirt", "mud", "podzol", "farmland", "chest", "barrel", "bookshelf",
			"crafting_table", "ladder", "debris", "dripstone", "composter", "jukebox", "note_block", "lectern",
			"loom", "cartography_table", "fletching_table", "smithing_table", "cocoa", "granite", "stem"}) {
			r(s, WheelColor.BROWN);
		}

		// --- jaune
		for (String s : new String[] {"sand", "glowstone", "gold", "torch", "hay", "sponge", "dandelion",
			"sunflower", "wheat", "glow_berries"}) {
			r(s, WheelColor.YELLOW);
		}

		// --- orange
		for (String s : new String[] {"magma", "lava", "fire", "pumpkin", "honey", "copper", "terracotta",
			"carrot", "campfire", "shroomlight", "torchflower", "jack_o_lantern", "lightning_rod"}) {
			r(s, WheelColor.ORANGE);
		}

		// --- bleu
		for (String s : new String[] {"water", "ice", "lapis", "diamond", "prismarine", "sea_lantern", "cornflower",
			"orchid", "pitcher", "conduit"}) {
			r(s, WheelColor.BLUE);
		}

		// --- violet
		for (String s : new String[] {"amethyst", "purpur", "chorus", "shulker", "portal", "allium", "lilac"}) {
			r(s, WheelColor.PURPLE);
		}

		// --- blanc
		for (String s : new String[] {"snow", "quartz", "bone_block", "calcite", "glass", "cobweb", "azure_bluet",
			"daisy", "lily_of_the_valley", "end_rod"}) {
			r(s, WheelColor.WHITE);
		}

		// --- rouge
		for (String s : new String[] {"tnt", "brick", "netherrack", "nether_brick", "mushroom", "rose", "poppy",
			"beetroot", "nether_wart", "tulip", "enchanting_table"}) {
			r(s, WheelColor.RED);
		}

		// --- noir
		for (String s : new String[] {"coal_block", "sculk", "netherite"}) {
			r(s, WheelColor.BLACK);
		}

		// --- rose
		for (String s : new String[] {"petals", "peony", "spore_blossom"}) {
			r(s, WheelColor.PINK);
		}
	}

	private ColorMap() {
	}

	private static void r(String key, WheelColor c) {
		RULES.putIfAbsent(key, c);
	}

	/** Appelé côté client une fois les ressources Minecraft chargées (solo : serveur intégré). */
	public static void initializeTextures(ResourceManager manager) {
		resources = manager;
		CACHE.clear();
	}

	public static WheelColor of(Block block) {
		return CACHE.computeIfAbsent(block, ColorMap::classify);
	}

	private static WheelColor classify(Block block) {
		String path = BuiltInRegistries.BLOCK.getKey(block).getPath();
		WheelColor textureColor = dominantTextureColor(path);
		if (textureColor != null) return textureColor;
		for (Map.Entry<String, WheelColor> e : DYES.entrySet()) {
			if (path.startsWith(e.getKey())) {
				return e.getValue();
			}
		}
		for (Map.Entry<String, WheelColor> e : RULES.entrySet()) {
			if (path.contains(e.getKey())) {
				return e.getValue();
			}
		}
		return WheelColor.GRAY;
	}

	private static WheelColor dominantTextureColor(String blockPath) {
		ResourceManager manager = resources;
		if (manager == null) return null;
		Identifier textureId = Identifier.fromNamespaceAndPath("minecraft", "textures/block/" + blockPath + ".png");
		try {
			var resource = manager.getResource(textureId);
			if (resource.isEmpty()) return null;
			try (InputStream in = resource.get().open()) {
				BufferedImage image = ImageIO.read(in);
				if (image == null) return null;
				Map<WheelColor, Integer> counts = new HashMap<>();
				int strideX = Math.max(1, image.getWidth() / 64);
				int strideY = Math.max(1, image.getHeight() / 64);
				for (int y = 0; y < image.getHeight(); y += strideY) {
					for (int x = 0; x < image.getWidth(); x += strideX) {
						int argb = image.getRGB(x, y);
						if (((argb >>> 24) & 0xff) < 32) continue;
						WheelColor category = classifyRgb((argb >> 16) & 255, (argb >> 8) & 255, argb & 255);
						counts.merge(category, 1, Integer::sum);
					}
				}
				WheelColor dominant = null;
				int max = 0;
				for (Map.Entry<WheelColor, Integer> entry : counts.entrySet()) {
					if (entry.getValue() > max) { dominant = entry.getKey(); max = entry.getValue(); }
				}
				return dominant;
			}
		} catch (Exception ignored) {
			return null;
		}
	}

	private static WheelColor classifyRgb(int r, int g, int b) {
		float rf = r / 255f, gf = g / 255f, bf = b / 255f;
		float max = Math.max(rf, Math.max(gf, bf));
		float min = Math.min(rf, Math.min(gf, bf));
		float delta = max - min;
		float light = (max + min) / 2f;
		float saturation = delta == 0 ? 0 : delta / (1f - Math.abs(2f * light - 1f));
		if (light < 0.16f) return WheelColor.BLACK;
		if (saturation < 0.16f) {
			if (light > 0.82f) return WheelColor.WHITE;
			if (light < 0.30f) return WheelColor.BLACK;
			return WheelColor.GRAY;
		}
		float hue;
		if (max == rf) hue = 60f * (((gf - bf) / delta) % 6f);
		else if (max == gf) hue = 60f * (((bf - rf) / delta) + 2f);
		else hue = 60f * (((rf - gf) / delta) + 4f);
		if (hue < 0) hue += 360f;
		if (hue < 12 || hue >= 345) return light < 0.34f ? WheelColor.BROWN : WheelColor.RED;
		if (hue < 35) return light < 0.55f ? WheelColor.BROWN : WheelColor.ORANGE;
		if (hue < 68) return WheelColor.YELLOW;
		if (hue < 165) return WheelColor.GREEN;
		if (hue < 205) return WheelColor.BLUE;
		if (hue < 255) return WheelColor.PURPLE;
		if (hue < 325) return light > 0.58f ? WheelColor.PINK : WheelColor.PURPLE;
		return WheelColor.RED;
	}
}
