package com.colorwheel.client;

import com.colorwheel.Config;
import com.colorwheel.Game;
import com.colorwheel.Objectives;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Menu de reglages : mode, rayon, frequence, demarrer / arreter, restaurer. */
public class ConfigScreen extends Screen {
	private static final int ROW = 24;

	private Button modeButton;
	private Button radiusLabel;
	private Button intervalLabel;
	private Button startButton;
	private Button objModeButton;
	private Button objPickButton;
	private EditBox customObjectiveField;
	private boolean runningShown;

	public ConfigScreen() {
		super(Component.literal("Color Wheel"));
	}

	@Override
	protected void init() {
		int cx = this.width / 2;
		int y = this.height / 2 - 142;
		runningShown = Game.running;

		// Mode : Roue / Bloc
		modeButton = Button.builder(modeText(), btn -> {
			Config.blockMode = !Config.blockMode;
			Config.save();
			btn.setMessage(modeText());
		}).bounds(cx - 100, y, 200, 20).build();
		this.addRenderableWidget(modeButton);
		y += ROW;

		// Rayon : [-] label [+]
		this.addRenderableWidget(Button.builder(Component.literal("-"), btn -> {
			Config.radiusChunks = Config.clamp(Config.radiusChunks - 1, Config.MIN_RADIUS_CHUNKS, Config.MAX_RADIUS_CHUNKS);
			Config.save();
			radiusLabel.setMessage(radiusText());
		}).bounds(cx - 100, y, 20, 20).build());
		radiusLabel = Button.builder(radiusText(), btn -> {
		}).bounds(cx - 78, y, 156, 20).build();
		radiusLabel.active = false;
		this.addRenderableWidget(radiusLabel);
		this.addRenderableWidget(Button.builder(Component.literal("+"), btn -> {
			Config.radiusChunks = Config.clamp(Config.radiusChunks + 1, Config.MIN_RADIUS_CHUNKS, Config.MAX_RADIUS_CHUNKS);
			Config.save();
			radiusLabel.setMessage(radiusText());
		}).bounds(cx + 80, y, 20, 20).build());
		y += ROW;

		// Frequence : [-] label [+]  (pas de 10 s)
		this.addRenderableWidget(Button.builder(Component.literal("-"), btn -> {
			Config.intervalSeconds = Config.clamp(Config.intervalSeconds - 10, Config.MIN_INTERVAL, Config.MAX_INTERVAL);
			Config.save();
			intervalLabel.setMessage(intervalText());
		}).bounds(cx - 100, y, 20, 20).build());
		intervalLabel = Button.builder(intervalText(), btn -> {
		}).bounds(cx - 78, y, 156, 20).build();
		intervalLabel.active = false;
		this.addRenderableWidget(intervalLabel);
		this.addRenderableWidget(Button.builder(Component.literal("+"), btn -> {
			Config.intervalSeconds = Config.clamp(Config.intervalSeconds + 10, Config.MIN_INTERVAL, Config.MAX_INTERVAL);
			Config.save();
			intervalLabel.setMessage(intervalText());
		}).bounds(cx + 80, y, 20, 20).build());
		y += ROW + 6;

		// Objectifs : roue auto / choisi / aucun / personnalisé
		objModeButton = Button.builder(objModeText(), btn -> {
			Config.objectiveMode = (Config.objectiveMode + 1) % 4;
			Config.save();
			btn.setMessage(objModeText());
			Game.requestApplyObjectiveSettings();
		}).bounds(cx - 100, y, 200, 20).build();
		this.addRenderableWidget(objModeButton);
		y += ROW;

		// Mon objectif (clique pour passer au suivant dans la liste)
		objPickButton = Button.builder(objPickText(), btn -> {
			Config.fixedObjective = (Config.fixedObjective + 1) % Objectives.LIST.size();
			Config.objectiveMode = 1;
			Config.save();
			btn.setMessage(objPickText());
			objModeButton.setMessage(objModeText());
			Game.requestApplyObjectiveSettings();
		}).bounds(cx - 100, y, 200, 20).build();
		this.addRenderableWidget(objPickButton);
		y += ROW;

		customObjectiveField = new EditBox(this.font, cx - 100, y, 200, 20, Component.literal("Objectif personnalisé"));
		customObjectiveField.setMaxLength(128);
		customObjectiveField.setValue(Config.customObjective);
		this.addRenderableWidget(customObjectiveField);
		y += ROW;
		this.addRenderableWidget(Button.builder(Component.literal("Utiliser cet objectif personnalisé"), btn -> {
			Config.customObjective = customObjectiveField.getValue().trim();
			Config.objectiveMode = 3;
			Config.save();
			objModeButton.setMessage(objModeText());
			Game.requestApplyObjectiveSettings();
		}).bounds(cx - 100, y, 200, 20).build());
		y += ROW + 4;

		// Demarrer / Arreter
		startButton = Button.builder(startText(), btn -> {
			if (runningShown) {
				Game.requestStop();
			} else {
				Game.requestStart();
			}
			runningShown = !runningShown;
			btn.setMessage(startText());
		}).bounds(cx - 100, y, 200, 20).build();
		this.addRenderableWidget(startButton);
		y += ROW;

		// Restaurer le monde
		this.addRenderableWidget(Button.builder(Component.literal("Restaurer le monde + roue à zéro"), btn -> {
			Game.requestReset();
		}).bounds(cx - 100, y, 200, 20).build());
		y += ROW;

		// Restaurer les blocs modifiés manuellement, indépendamment du reset du monde
		this.addRenderableWidget(Button.builder(Component.literal("Réinitialiser les blocs cassés ou posés"), btn -> {
			Game.requestRestorePlayerBlocks();
		}).bounds(cx - 100, y, 200, 20).build());
		y += ROW;

		// Objectif suivant
		this.addRenderableWidget(Button.builder(Component.literal("Passer à l'objectif suivant"), btn -> {
			Game.requestSkipObjective();
		}).bounds(cx - 100, y, 200, 20).build());
		y += ROW + 6;

		// Fermer
		this.addRenderableWidget(Button.builder(Component.literal("Fermer"), btn -> this.onClose())
			.bounds(cx - 100, y, 200, 20).build());
	}

	private Component modeText() {
		return Component.literal("Mode : " + (Config.blockMode ? "Bloc" : "Roue"));
	}

	private Component objModeText() {
		String t = switch (Config.objectiveMode) {
			case 1 -> "Objectif choisi par moi";
			case 2 -> "Aucun objectif";
			case 3 -> "Objectif personnalisé";
			default -> "Roue des difficultés (auto)";
		};
		return Component.literal("Objectifs : " + t);
	}

	private Component objPickText() {
		return Component.literal("Mon objectif : " + Objectives.get(Config.fixedObjective).text());
	}

	private Component radiusText() {
		return Component.literal("Distance : " + Config.radiusChunks + " chunks (" + (Config.radiusChunks * 16) + " blocs)");
	}

	private Component intervalText() {
		int s = Config.intervalSeconds;
		return Component.literal("Tirage toutes les " + (s / 60) + " min " + String.format("%02d", s % 60) + " s");
	}

	private Component startText() {
		return Component.literal(runningShown ? "Arrêter" : "Démarrer");
	}

	@Override
	public boolean isPauseScreen() {
		return false; // le jeu continue pendant que le menu est ouvert
	}

	@Override
	public void onClose() {
		this.minecraft.gui.setScreen(null);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		super.extractRenderState(graphics, mouseX, mouseY, delta);
		Font f = this.font;
		String title = "Color Wheel";
		graphics.text(f, title, this.width / 2 - f.width(title) / 2, this.height / 2 - 104 - 18, 0xFFFFFFFF, true);
	}
}
