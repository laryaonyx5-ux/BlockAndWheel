package com.colorwheel;

import java.util.List;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/** Liste des objectifs, du plus facile au (presque) impossible. */
public final class Objectives {
	public enum Kind {
		ITEM, // avoir N objets dans l'inventaire
		KILL, // tuer une creature
		DIM   // entrer dans une dimension
	}

	public enum Tier {
		EASY("Facile", WheelColor.GREEN.argb),
		MEDIUM("Moyen", WheelColor.YELLOW.argb),
		HARD("Difficile", WheelColor.ORANGE.argb),
		EXTREME("Presque impossible", WheelColor.RED.argb);

		public final String fr;
		public final int argb;

		Tier(String fr, int argb) {
			this.fr = fr;
			this.argb = argb;
		}
	}

	/**
	 * @param tier   difficulte (couleur de la roue des objectifs)
	 * @param text   texte affiche a l'ecran
	 * @param kind   type d'objectif
	 * @param key    nom registre de l'objet / creature / dimension (sans "minecraft:")
	 * @param suffix pour ITEM : true = le nom de l'objet doit se terminer par key (ex: "_log")
	 * @param need   quantite necessaire (ITEM)
	 */
	public record Objective(Tier tier, String text, Kind kind, String key, boolean suffix, int need) {
	}

	public static final List<Objective> LIST = List.of(
		// --- facile (vert)
		item(Tier.EASY, "Obtenir 8 bûches", "_log", true, 8),
		item(Tier.EASY, "Fabriquer une table de craft", "crafting_table", false, 1),
		item(Tier.EASY, "Fabriquer une pioche en pierre", "stone_pickaxe", false, 1),
		item(Tier.EASY, "Obtenir 3 lingots de fer", "iron_ingot", false, 3),
		kill(Tier.EASY, "Tuer un zombie", "zombie"),
		item(Tier.EASY, "Fabriquer un bouclier", "shield", false, 1),
		// --- moyen (jaune)
		item(Tier.MEDIUM, "Trouver un diamant", "diamond", false, 1),
		item(Tier.MEDIUM, "Fabriquer un plastron en diamant", "diamond_chestplate", false, 1),
		dim(Tier.MEDIUM, "Entrer dans le Nether", "the_nether"),
		item(Tier.MEDIUM, "Obtenir 7 bâtons de blaze", "blaze_rod", false, 7),
		// --- difficile (orange)
		item(Tier.HARD, "Obtenir 12 yeux d'Ender", "ender_eye", false, 12),
		dim(Tier.HARD, "Entrer dans l'End", "the_end"),
		kill(Tier.HARD, "Tuer l'Ender Dragon", "ender_dragon"),
		item(Tier.HARD, "Obtenir des élytres", "elytra", false, 1),
		// --- presque impossible (rouge)
		kill(Tier.EXTREME, "Tuer le Wither", "wither"),
		item(Tier.EXTREME, "Fabriquer une balise", "beacon", false, 1),
		item(Tier.EXTREME, "Obtenir une pomme d'or enchantée", "enchanted_golden_apple", false, 1),
		item(Tier.EXTREME, "Obtenir l'œuf de dragon", "dragon_egg", false, 1),
		kill(Tier.EXTREME, "Tuer un Warden", "warden"),
		item(Tier.EXTREME, "Fabriquer un bloc de netherite", "netherite_block", false, 1)
	);

	private Objectives() {
	}

	private static Objective item(Tier t, String text, String key, boolean suffix, int need) {
		return new Objective(t, text, Kind.ITEM, key, suffix, need);
	}

	private static Objective kill(Tier t, String text, String key) {
		return new Objective(t, text, Kind.KILL, key, false, 1);
	}

	private static Objective dim(Tier t, String text, String key) {
		return new Objective(t, text, Kind.DIM, key, false, 1);
	}

	/** Index d'un objectif au hasard dans la difficulte donnee (different de "except" si possible). */
	public static int randomIndex(Tier tier, int except, java.util.Random rnd) {
		List<Integer> ids = new java.util.ArrayList<>();
		for (int i = 0; i < LIST.size(); i++) {
			if (LIST.get(i).tier() == tier && i != except) {
				ids.add(i);
			}
		}
		if (ids.isEmpty()) {
			for (int i = 0; i < LIST.size(); i++) {
				if (LIST.get(i).tier() == tier) {
					ids.add(i);
				}
			}
		}
		return ids.get(rnd.nextInt(ids.size()));
	}

	public static Objective get(int index) {
		return LIST.get(Math.floorMod(index, LIST.size()));
	}

	/** Compte les objets dont le nom ou l'identifiant correspond au texte saisi. */
	public static int countItemsMatching(ServerPlayer player, String query) {
		Inventory inv = player.getInventory();
		String normalized = normalize(query);
		String searchable = normalized.replaceAll("\\b\\d+\\b", " ")
			.replace("avoir", " ").replace("obtenir", " ").replace("posseder", " ").replace("posséder", " ")
			.replace("trouver", " ").replace("ramasser", " ").replace("fabriquer", " ")
			.replace("un ", " ").replace("une ", " ").replace("des ", " ").replace("de ", " ").replace("du ", " ")
			.replace("bloc de ", " ").replace("bloc d ", " ").replace("bloc ", " ").replaceAll("\\bl\\s+", " ").trim();
		searchable = frenchAlias(searchable);
		int total = 0;
		for (int i = 0; i < inv.getContainerSize(); i++) {
			ItemStack stack = inv.getItem(i);
			if (stack.isEmpty()) continue;
			String path = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath().replace('_', ' ');
			String display = normalize(stack.getHoverName().getString());
			if (!searchable.isEmpty() && (path.contains(searchable) || searchable.contains(path) || display.contains(searchable) || searchable.contains(display))) {
				total += stack.getCount();
			}
		}
		return total;
	}

	private static String frenchAlias(String query) {
		return switch (query) {
			case "herbe", "herbe haute", "bloc d herbe" -> "grass";
			case "terre" -> "dirt";
			case "pierre" -> "stone";
			case "sable" -> "sand";
			case "bois", "buches", "bûches" -> "log";
			case "diamant", "diamants" -> "diamond";
			case "fer" -> "iron";
			case "charbon" -> "coal";
			case "eau" -> "water";
			case "feuilles" -> "leaves";
			default -> query;
		};
	}

	public static int requestedCount(String query) {
		java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\d+").matcher(query);
		return m.find() ? Math.max(1, Integer.parseInt(m.group())) : 1;
	}

	private static String normalize(String value) {
		return java.text.Normalizer.normalize(value.toLowerCase(java.util.Locale.ROOT), java.text.Normalizer.Form.NFD)
			.replaceAll("\\p{M}", "").replaceAll("[^a-z0-9_ ]", " ").replaceAll("\\s+", " ").trim();
	}

	/** Combien d'objets correspondants le joueur porte-t-il ? */
	public static int countItems(ServerPlayer player, String key, boolean suffix) {
		Inventory inv = player.getInventory();
		int total = 0;
		for (int i = 0; i < inv.getContainerSize(); i++) {
			ItemStack stack = inv.getItem(i);
			if (stack.isEmpty()) {
				continue;
			}
			String path = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
			if (suffix ? path.endsWith(key) : path.equals(key)) {
				total += stack.getCount();
			}
		}
		return total;
	}
}
