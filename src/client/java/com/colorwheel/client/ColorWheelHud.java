package com.colorwheel.client;

import java.util.List;

import com.colorwheel.Game;
import com.colorwheel.Objectives;
import com.colorwheel.WheelColor;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;

/** Tout ce qui s'affiche a l'ecran : roue, bloc en haut, objectif, compte a rebours. */
public final class ColorWheelHud {
	private static final int WHITE = 0xFFFFFFFF;
	private static final int YELLOW = 0xFFFFE066;
	private static final int GREEN = 0xFF55FF55;
	private static final int PANEL = 0x99000000;
	private static final int WHEEL_RADIUS = 54;
	private static final int CELL = 3;

	private static Block cachedBlock;
	private static ItemStack cachedStack = ItemStack.EMPTY;

	private ColorWheelHud() {
	}

	public static void register() {
		HudElementRegistry.attachElementBefore(
			VanillaHudElements.CHAT,
			Identifier.fromNamespaceAndPath("colorwheel", "main"),
			ColorWheelHud::extract
		);
	}

	private static void extract(GuiGraphicsExtractor g, DeltaTracker tracker) {
		if (!Game.running) {
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		Font font = mc.font;
		int screenW = mc.getWindow().getGuiScaledWidth();
		int screenH = mc.getWindow().getGuiScaledHeight();
		float partial = tracker.getGameTimeDeltaPartialTick(false);

		if (Game.phase != Game.Phase.OBJSPIN && Game.phase != Game.Phase.OBJSHOW && !Game.objectiveText.isEmpty()) {
			drawObjective(g, font);
		}
		drawCountdown(g, font, screenW);

		Game.Phase phase = Game.phase;
		int topY = 8;

		if (Game.regenerating) {
			drawCentered(g, font, "Restauration du monde...", screenW / 2, topY, YELLOW, true);
			topY += 14;
		}
		if (Game.statusTicks > 0 && !Game.statusMessage.isEmpty()) {
			drawCentered(g, font, Game.statusMessage, screenW / 2, topY, WHITE, true);
			topY += 14;
		}

		if (phase == Game.Phase.OBJSPIN || phase == Game.Phase.OBJSHOW) {
			drawObjectiveScene(g, font, screenW, screenH, partial, phase);
			return;
		}

		if (phase == Game.Phase.DONE) {
			drawCentered(g, font, "Objectif réussi !", screenW / 2, topY, GREEN, true);
			return;
		}

		if (Game.blockPhase && phase == Game.Phase.SHOW) {
			drawBlockAnnounce(g, font, screenW, topY);
			return;
		}

		if (!Game.blockPhase && (phase == Game.Phase.SELECT || phase == Game.Phase.SHOW)) {
			drawWheelScene(g, font, screenW, screenH, partial, phase);
		}
	}

	// ------------------------------------------------------------------ objectif / compte a rebours

	private static void drawObjective(GuiGraphicsExtractor g, Font font) {
		String title = "Objectif :";
		String text = Game.objectiveText;
		if (Game.objNeed > 0) {
			text = text + " (" + Game.objProgress + "/" + Game.objNeed + ")";
		}
		int w = Math.max(font.width(title), font.width(text));
		g.fill(3, 3, 3 + w + 6, 3 + 2 * 10 + 4, PANEL);
		g.text(font, title, 6, 6, YELLOW, true);
		g.text(font, text, 6, 16, WHITE, true);
	}

	private static void drawCountdown(GuiGraphicsExtractor g, Font font, int screenW) {
		if (Game.phase != Game.Phase.COUNTDOWN) {
			return;
		}
		int seconds = Math.max(0, (Game.countdownTicks + 19) / 20);
		String text = "Prochain tirage : " + (seconds / 60) + ":" + String.format("%02d", seconds % 60);
		int w = font.width(text);
		g.fill(screenW - w - 9, 3, screenW - 3, 17, PANEL);
		g.text(font, text, screenW - w - 6, 6, WHITE, true);
	}

	// ------------------------------------------------------------------ mode Bloc

	private static void drawBlockAnnounce(GuiGraphicsExtractor g, Font font, int screenW, int y) {
		Block block = Game.chosenBlock;
		if (block == null) {
			return;
		}
		if (block != cachedBlock) {
			cachedBlock = block;
			cachedStack = new ItemStack(block.asItem());
		}
		int secondsLeft = Math.max(0, (Game.phaseDur - Game.phaseTick + 19) / 20);
		String text = Game.chosenBlockName + " va disparaître dans " + secondsLeft + " s";
		int textW = font.width(text);
		int total = 16 + 6 + textW;
		int x = (screenW - total) / 2;
		g.fill(x - 4, y - 3, x + total + 4, y + 19, PANEL);
		g.item(cachedStack, x, y);
		g.text(font, text, x + 22, y + 4, WHITE, true);
	}

	// ------------------------------------------------------------------ mode Roue

	private static void drawWheelScene(GuiGraphicsExtractor g, Font font, int screenW, int screenH,
		float partial, Game.Phase phase) {
		int cx = screenW / 2;
		int cy = screenH / 2 - 10;

		float progress = 1f;
		if (phase == Game.Phase.SELECT) {
			progress = Math.min(1f, (Game.phaseTick + partial) / (float) Game.phaseDur);
		}
		float eased = 1f - (float) Math.pow(1f - progress, 3);
		float rotation = Game.spinTotal * eased;

		List<Integer> colors = new java.util.ArrayList<>();
		for (WheelColor wc : Game.segments(Game.spinMask)) {
			colors.add(wc.argb);
		}
		drawWheel(g, cx, cy, rotation, colors);

		int textY = cy + WHEEL_RADIUS + 10;
		if (phase == Game.Phase.SELECT) {
			drawCentered(g, font, "Sélection...", cx, textY, WHITE, true);
			int n = Integer.bitCount(Game.spinMask);
			String left = n + (n > 1 ? " couleurs restantes" : " couleur restante");
			drawCentered(g, font, left, cx, textY + 12, 0xFFDDDDDD, true);
		} else if (Game.chosenColor >= 0) {
			WheelColor c = WheelColor.values()[Game.chosenColor];
			drawCentered(g, font, "Couleur : " + c.fr + " !", cx, textY, c.argb | 0xFF000000, true);
			int secondsLeft = Math.max(0, (Game.phaseDur - Game.phaseTick + 19) / 20);
			drawCentered(g, font, "Disparition dans " + secondsLeft + " s", cx, textY + 12, 0xFFDDDDDD, true);
		}
	}

	// ------------------------------------------------------------------ roue des objectifs (difficulte)

	private static void drawObjectiveScene(GuiGraphicsExtractor g, Font font, int screenW, int screenH,
		float partial, Game.Phase phase) {
		int cx = screenW / 2;
		int cy = screenH / 2 - 10;
		float progress = 1f;
		if (phase == Game.Phase.OBJSPIN) {
			progress = Math.min(1f, (Game.phaseTick + partial) / (float) Game.phaseDur);
		}
		float eased = 1f - (float) Math.pow(1f - progress, 3);

		List<Integer> colors = new java.util.ArrayList<>();
		for (Objectives.Tier t : Objectives.Tier.values()) {
			colors.add(t.argb);
		}
		drawWheel(g, cx, cy, Game.objSpinTotal * eased, colors);

		int textY = cy + WHEEL_RADIUS + 10;
		if (phase == Game.Phase.OBJSPIN) {
			drawCentered(g, font, "Choix de la difficulté...", cx, textY, WHITE, true);
			drawCentered(g, font, "Vert : facile · Jaune : moyen · Orange : difficile · Rouge : presque impossible",
				cx, textY + 12, 0xFFDDDDDD, true);
		} else {
			Objectives.Tier t = Objectives.Tier.values()[Game.objTier];
			drawCentered(g, font, "Difficulté : " + t.fr, cx, textY, t.argb | 0xFF000000, true);
			drawCentered(g, font, "Objectif : " + Game.objectiveText, cx, textY + 12, WHITE, true);
		}
	}

	private static void drawWheel(GuiGraphicsExtractor g, int cx, int cy, float rotation, List<Integer> segs) {
		int n = segs.size();
		if (n == 0) {
			return;
		}
		float segW = 360f / n;
		float rim = WHEEL_RADIUS - CELL * 1.6f;
		float hub = 7f;

		for (int gy = -WHEEL_RADIUS; gy <= WHEEL_RADIUS; gy += CELL) {
			for (int gx = -WHEEL_RADIUS; gx <= WHEEL_RADIUS; gx += CELL) {
				float dx = gx + CELL / 2f;
				float dy = gy + CELL / 2f;
				float d2 = dx * dx + dy * dy;
				if (d2 > WHEEL_RADIUS * WHEEL_RADIUS) {
					continue;
				}
				int color;
				if (d2 > rim * rim) {
					color = 0xFF6B4423; // bordure en bois
				} else if (d2 < hub * hub) {
					color = 0xFF5A3A1E; // centre
				} else {
					double angle = Math.toDegrees(Math.atan2(dx, -dy)); // 0 = en haut, sens horaire
					double wheelAngle = ((angle - rotation) % 360.0 + 360.0) % 360.0;
					int idx = Math.min(n - 1, (int) (wheelAngle / segW));
					color = segs.get(idx);
				}
				g.fill(cx + gx, cy + gy, cx + gx + CELL, cy + gy + CELL, color);
			}
		}

		// fleche blanche en haut, pointee vers la roue
		for (int t = 0; t < 8; t++) {
			g.fill(cx - 8 + t, cy - WHEEL_RADIUS - 14 + t, cx + 8 - t, cy - WHEEL_RADIUS - 13 + t, WHITE);
		}
	}

	// ------------------------------------------------------------------ utilitaires

	private static void drawCentered(GuiGraphicsExtractor g, Font font, String text, int cx, int y, int color,
		boolean shadow) {
		g.text(font, text, cx - font.width(text) / 2, y, color, shadow);
	}
}
