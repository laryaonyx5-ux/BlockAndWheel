package com.colorwheel;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;

import it.unimi.dsi.fastutil.longs.Long2IntMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.LevelResource;

/**
 * Toute la logique du mode (solo uniquement).
 *
 * Les champs "public static volatile" sont lus par l'affichage (HUD) cote client :
 * en solo, client et serveur tournent dans le meme programme, donc pas besoin de reseau.
 */
public final class Game {
	public enum Phase {
		IDLE,      // mode arrete
		COUNTDOWN, // on attend le prochain tirage
		SELECT,    // la roue tourne
		SHOW,      // resultat affiche (couleur ou bloc), la suppression arrive juste apres
		DONE,      // "Objectif reussi !"
		OBJSPIN,   // la roue des difficultes tourne
		OBJSHOW    // difficulte + objectif affiches
	}

	public static final int SPIN_TICKS = 100;
	public static final int WHEEL_SHOW_TICKS = 60;
	public static final int BLOCK_SHOW_TICKS = 100;
	public static final int DONE_TICKS = 100;
	public static final int OBJ_SPIN_TICKS = 100;
	public static final int OBJ_SHOW_TICKS = 100;

	/** setBlock : 2 = prevenir les clients, 16 = pas de mise a jour des voisins (donc aucun drop). */
	private static final int SET_FLAGS = 2 | 16;

	// ------------------------------------------------------------------ etat lu par le HUD
	public static volatile boolean running = false;
	public static volatile Phase phase = Phase.IDLE;
	public static volatile int phaseTick = 0;
	public static volatile int phaseDur = 1;
	public static volatile int countdownTicks = 0;
	public static volatile int remainingMask = WheelColor.ALL_MASK; // couleurs encore sur la roue
	public static volatile int spinMask = WheelColor.ALL_MASK;      // couleurs de la roue affichee
	public static volatile int chosenColor = -1;
	public static volatile float spinTotal = 0f;
	public static volatile boolean blockPhase = false;              // true = tirage en mode Bloc
	public static volatile Block chosenBlock = null;
	public static volatile String chosenBlockName = "";
	public static volatile int objectiveIndex = 0;
	public static volatile String objectiveText = "";
	public static volatile int objProgress = 0;
	public static volatile int objNeed = 0;
	public static volatile boolean regenerating = false;
	public static volatile int objTier = 0;           // difficulte tiree (index dans Objectives.Tier)
	public static volatile float objSpinTotal = 0f;
	public static volatile boolean objectiveChosen = false;
	public static volatile String statusMessage = "";
	public static volatile int statusTicks = 0;

	// ------------------------------------------------------------------ etat serveur
	private static final ConcurrentLinkedQueue<Consumer<MinecraftServer>> ACTIONS = new ConcurrentLinkedQueue<>();
	/** Blocs supprimes : dimension -> (position -> id de l'etat d'origine). */
	private static final Map<String, Long2IntOpenHashMap> REMOVED = new HashMap<>();
	private static final Set<String> KILLED = ConcurrentHashMap.newKeySet();
	private static final Random RND = new Random();

	private static RemovalJob removal;
	private static RestoreJob restore;
	private static Runnable restoreDone;
	private static Path savePath;
	private static Block lastBlock;
	private static int baseline;
	private static boolean dimSeenOutside;
	private static boolean needBaseline = true;
	private static int tickCounter;

	private Game() {
	}

	// ================================================================== API publique

	/** Poste une action a executer sur le thread du serveur (appelable depuis le menu). */
	public static void post(Consumer<MinecraftServer> action) {
		ACTIONS.add(action);
	}

	public static void requestStart() {
		post(server -> {
			running = true;
			needBaseline = true;
			if (objectiveChosen && Config.objectiveMode == 0) {
				startObjective();
				enterCountdown();
			} else {
				beginObjectiveDraw();
			}
			saveState();
		});
	}

	/** Appele par le menu quand tu changes le type d'objectif ou l'objectif choisi. */
	public static void requestApplyObjectiveSettings() {
		post(server -> {
			if (running) {
				beginObjectiveDraw();
			} else {
				objectiveChosen = false;
			}
			saveState();
		});
	}

	public static void requestStop() {
		post(server -> {
			running = false;
			phase = Phase.IDLE;
			saveState();
		});
	}

	/** Restaure le monde et remet la roue a zero, sans changer d'objectif. */
	public static void requestRestorePlayerBlocks() {
		post(server -> {
			int restored = PlayerBlockHistory.restore(server);
			setStatus("Blocs modifiés restaurés : " + restored, 100);
		});
	}

	public static void requestReset() {
		post(server -> {
			setStatus("Monde restauré, roue remise à zéro.", 100);
			startReset(server, () -> {
				if (running) {
					enterCountdown();
				}
			});
		});
	}

	/** Passe a l'objectif suivant (sans restauration). */
	public static void requestSkipObjective() {
		post(server -> {
			if (running) {
				beginObjectiveDraw();
			} else {
				objectiveIndex = RND.nextInt(Objectives.LIST.size());
				objectiveChosen = true;
				startObjective();
			}
			saveState();
		});
	}

	public static List<WheelColor> segments(int mask) {
		List<WheelColor> list = new ArrayList<>();
		for (WheelColor c : WheelColor.values()) {
			if ((mask & (1 << c.ordinal())) != 0) {
				list.add(c);
			}
		}
		return list;
	}

	// ================================================================== evenements

	public static void onServerStart(MinecraftServer server) {
		Config.load();
		resetRuntime();
		savePath = server.getWorldPath(LevelResource.ROOT).resolve("colorwheel.dat");
		loadState();
		startObjective();
		if (running) {
			if (objectiveChosen) {
				enterCountdown();
			} else {
				beginObjectiveDraw();
			}
		} else {
			phase = Phase.IDLE;
		}
	}

	public static void onServerStop(MinecraftServer server) {
		saveState();
		ACTIONS.clear();
		removal = null;
		restore = null;
	}

	public static void onKill(String entityPath) {
		KILLED.add(entityPath);
	}

	public static void onPlayerDeath() {
		post(server -> {
			if (!running) {
				return;
			}
			startObjective();
			setStatus("Tu es mort ! Le monde est restauré.", 120);
			enterCountdown();
			startReset(server, () -> {
				if (running) {
					if (Config.resetObjectivesOnDeath || !objectiveChosen) {
						beginObjectiveDraw();
					} else {
						enterCountdown();
					}
				}
			});
			saveState();
		});
	}

	// ================================================================== boucle principale

	public static void tick(MinecraftServer server) {
		Consumer<MinecraftServer> action;
		while ((action = ACTIONS.poll()) != null) {
			action.accept(server);
		}
		if (statusTicks > 0) {
			statusTicks--;
		}
		stepRestore();
		stepRemoval();

		if (!running) {
			return;
		}
		List<ServerPlayer> players = server.getPlayerList().getPlayers();
		if (players.isEmpty()) {
			return;
		}
		ServerPlayer player = players.get(0);
		if (player.isDeadOrDying()) {
			return;
		}
		tickCounter++;

		if (needBaseline) {
			Objectives.Objective o = Objectives.get(objectiveIndex);
			if (o.kind() == Objectives.Kind.ITEM) {
				baseline = Objectives.countItems(player, o.key(), o.suffix());
			}
			dimSeenOutside = !player.level().dimension().toString().contains(o.key());
			needBaseline = false;
		}

		boolean objectiveActive = Config.objectiveMode != 2 && (phase == Phase.COUNTDOWN || phase == Phase.SELECT || phase == Phase.SHOW);
		if (objectiveActive && !regenerating && tickCounter % 5 == 0) {
			checkObjective(server, player);
		}

		if (phase == Phase.COUNTDOWN) {
			if (!regenerating && --countdownTicks <= 0) {
				if (!Config.blockMode && remainingMask == 0) {
					// plus aucune couleur : c'est "rate", on restaure tout et on recommence
					setStatus("Plus de couleurs : monde restauré, on recommence !", 140);
					enterCountdown();
					startReset(server, () -> {
						if (running) {
							enterCountdown();
						}
					});
				} else {
					beginSelection(player);
				}
			}
		} else if (phase == Phase.SELECT) {
			if (++phaseTick >= phaseDur) {
				enter(Phase.SHOW, WHEEL_SHOW_TICKS);
			}
		} else if (phase == Phase.SHOW) {
			if (++phaseTick >= phaseDur) {
				finishShow(player);
			}
		} else if (phase == Phase.DONE) {
			if (phaseTick < phaseDur) {
				phaseTick++;
			} else if (!regenerating) {
				beginObjectiveDraw();
				saveState();
			}
		} else if (phase == Phase.OBJSPIN) {
			if (++phaseTick >= phaseDur) {
				Objectives.Tier tier = Objectives.Tier.values()[objTier];
				objectiveIndex = Objectives.randomIndex(tier, objectiveIndex, RND);
				objectiveChosen = true;
				startObjective();
				enter(Phase.OBJSHOW, OBJ_SHOW_TICKS);
				saveState();
			}
		} else if (phase == Phase.OBJSHOW) {
			if (++phaseTick >= phaseDur) {
				enterCountdown();
			}
		}
	}

	// ================================================================== phases

	private static void enter(Phase p, int duration) {
		phaseTick = 0;
		phaseDur = Math.max(1, duration);
		phase = p;
	}

	private static void enterCountdown() {
		countdownTicks = Config.intervalSeconds * 20;
		phaseTick = 0;
		phaseDur = 1;
		phase = Phase.COUNTDOWN;
	}

	private static void setStatus(String text, int ticks) {
		statusMessage = text;
		statusTicks = ticks;
	}

	/** Lance la roue des difficultes : vert = facile, jaune = moyen, orange = difficile, rouge = presque impossible. */
	private static void beginObjectiveDraw() {
		if (Config.objectiveMode == 1) { // objectif choisi par le joueur
			objectiveIndex = Math.floorMod(Config.fixedObjective, Objectives.LIST.size());
			objectiveChosen = true;
			startObjective();
			enterCountdown();
			return;
		}
		if (Config.objectiveMode == 3) { // objectif personnalise
			objectiveChosen = true;
			startObjective();
			enterCountdown();
			return;
		}
		if (Config.objectiveMode == 2) { // pas d'objectif
			objectiveChosen = true;
			startObjective();
			enterCountdown();
			return;
		}
		int n = Objectives.Tier.values().length;
		int idx = RND.nextInt(n);
		float w = 360f / n;
		float center = (idx + 0.5f) * w + (RND.nextFloat() - 0.5f) * 0.7f * w;
		objTier = idx;
		objSpinTotal = 360f * 5f + (360f - center);
		objectiveText = "";
		objProgress = 0;
		objNeed = 0;
		enter(Phase.OBJSPIN, OBJ_SPIN_TICKS);
	}

	private static void beginSelection(ServerPlayer player) {
		ServerLevel level = (ServerLevel) player.level();
		if (Config.blockMode) {
			Block block = pickBlock(level, player.blockPosition(), Config.radiusChunks * 16);
			if (block == null) {
				enterCountdown();
				return;
			}
			lastBlock = block;
			chosenBlock = block;
			chosenBlockName = block.getName().getString();
			blockPhase = true;
			enter(Phase.SHOW, BLOCK_SHOW_TICKS);
		} else {
			List<WheelColor> segs = segments(remainingMask);
			if (segs.isEmpty()) {
				setStatus("Plus aucune couleur sur la roue !", 100);
				enterCountdown();
				return;
			}
			int n = segs.size();
			int idx = RND.nextInt(n);
			float w = 360f / n;
			float center = (idx + 0.5f) * w + (RND.nextFloat() - 0.5f) * 0.7f * w;
			spinMask = remainingMask;
			chosenColor = segs.get(idx).ordinal();
			spinTotal = 360f * 5f + (360f - center);
			blockPhase = false;
			enter(Phase.SELECT, SPIN_TICKS);
		}
	}

	private static void finishShow(ServerPlayer player) {
		ServerLevel level = (ServerLevel) player.level();
		BlockPos center = player.blockPosition();
		int r = Config.radiusChunks * 16;
		if (blockPhase) {
			Block b = chosenBlock;
			if (b != null) {
				removal = new RemovalJob(level, center, r, null, b);
			}
		} else if (chosenColor >= 0) {
			removal = new RemovalJob(level, center, r, WheelColor.values()[chosenColor], null);
			remainingMask &= ~(1 << chosenColor);
		}
		enterCountdown();
	}

	// ================================================================== objectifs

	private static void startObjective() {
		Objectives.Objective o = Objectives.get(objectiveIndex);
		objectiveText = Config.objectiveMode == 2 ? "" : (Config.objectiveMode == 3 ? Config.customObjective.trim() : o.text());
		objNeed = (Config.objectiveMode != 2 && o.kind() == Objectives.Kind.ITEM && o.need() > 1) ? o.need() : 0;
		objProgress = 0;
		KILLED.clear();
		needBaseline = true;
	}

	private static void checkObjective(MinecraftServer server, ServerPlayer player) {
		if (Config.objectiveMode == 3) {
			String query = Config.customObjective == null ? "" : Config.customObjective.trim();
			if (query.isEmpty()) {
				return;
			}
			int count = Objectives.countItemsMatching(player, query);
			objProgress = count;
			objNeed = Math.max(1, Objectives.requestedCount(query));
			if (count >= objNeed) {
				player.sendSystemMessage(Component.literal("Objectif personnalisé réussi : " + query));
				enter(Phase.DONE, DONE_TICKS);
				startReset(server, null);
			}
			return;
		}
		Objectives.Objective o = Objectives.get(objectiveIndex);
		boolean done = false;
		if (o.kind() == Objectives.Kind.ITEM) {
			int cur = Objectives.countItems(player, o.key(), o.suffix());
			baseline = Math.min(baseline, cur);
			objProgress = Math.min(cur, o.need());
			done = cur >= o.need() && cur > baseline;
		} else if (o.kind() == Objectives.Kind.KILL) {
			done = KILLED.contains(o.key());
		} else if (o.kind() == Objectives.Kind.DIM) {
			boolean in = player.level().dimension().toString().contains(o.key());
			if (!in) {
				dimSeenOutside = true;
			}
			done = in && dimSeenOutside;
		}
		if (done) {
			objectiveDone(server, player, o);
		}
	}

	private static void objectiveDone(MinecraftServer server, ServerPlayer player, Objectives.Objective o) {
		player.sendSystemMessage(Component.literal("Objectif réussi : " + o.text()));
		enter(Phase.DONE, DONE_TICKS);
		startReset(server, null);
	}

	// ================================================================== suppression

	private static Block pickBlock(ServerLevel level, BlockPos c, int r) {
		Set<Block> found = new LinkedHashSet<>();
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		for (int i = 0; i < 800; i++) {
			int dx = RND.nextInt(2 * r + 1) - r;
			int dy = RND.nextInt(2 * r + 1) - r;
			int dz = RND.nextInt(2 * r + 1) - r;
			if (dx * dx + dy * dy + dz * dz > r * r) {
				continue;
			}
			p.set(c.getX() + dx, c.getY() + dy, c.getZ() + dz);
			if (!level.hasChunkAt(p)) {
				continue;
			}
			BlockState s = level.getBlockState(p);
			if (s.isAir() || (Config.protectContainers && s.hasBlockEntity())) {
				continue;
			}
			Block b = s.getBlock();
			if (b.asItem() == Items.AIR) {
				continue; // pas d'icone : eau, feu, portails...
			}
			found.add(b);
		}
		if (found.size() > 1 && lastBlock != null) {
			found.remove(lastBlock);
		}
		if (found.isEmpty()) {
			return null;
		}
		List<Block> list = new ArrayList<>(found);
		return list.get(RND.nextInt(list.size()));
	}

	private static Long2IntOpenHashMap removedFor(ServerLevel level) {
		return REMOVED.computeIfAbsent(level.dimension().toString(), k -> new Long2IntOpenHashMap());
	}

	/** Supprime les blocs tranche par tranche (une "vague") pour ne pas figer le jeu. */
	private static final class RemovalJob {
		private final ServerLevel level;
		private final BlockPos center;
		private final int r;
		private final WheelColor color;
		private final Block block;
		private final Long2IntOpenHashMap record;
		private int x;
		private int dy;
		private int dz;
		private int sinceSave;

		RemovalJob(ServerLevel level, BlockPos center, int r, WheelColor color, Block block) {
			this.level = level;
			this.center = center;
			this.r = r;
			this.color = color;
			this.block = block;
			this.record = removedFor(level);
			this.x = -r;
			this.dy = -r;
			this.dz = -r;
		}

		boolean step() {
			BlockState air = Blocks.AIR.defaultBlockState();
			BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
			// Budget de positions par tick : même un rayon de 64 chunks ne bloque pas
			// le thread serveur pendant plusieurs secondes d'un seul coup.
			int budget = 50_000;
			while (budget-- > 0 && x <= r) {
				if (x * x + dy * dy + dz * dz <= r * r) {
					pos.set(center.getX() + x, center.getY() + dy, center.getZ() + dz);
					if (level.hasChunkAt(pos)) {
						BlockState state = level.getBlockState(pos);
						if (!state.isAir() && !(Config.protectContainers && state.hasBlockEntity())) {
							Block b = state.getBlock();
							boolean match = block != null ? b == block : ColorMap.of(b) == color;
							if (match) {
								record.putIfAbsent(pos.asLong(), Block.getId(state));
								level.setBlock(pos, air, SET_FLAGS);
							}
						}
					}
				}
				// Avance lexicographique sur tout le volume, sans gros tableau temporaire.
				if (++dz > r) {
					dz = -r;
					if (++dy > r) {
						dy = -r;
						x++;
					}
				}
			}
			if (++sinceSave >= 200) {
				sinceSave = 0;
				saveState();
			}
			return x > r;
		}	}

	private static void stepRemoval() {
		if (removal != null && removal.step()) {
			removal = null;
			saveState();
		}
	}

	// ================================================================== restauration

	private static final class Batch {
		final ServerLevel level;
		final String dimKey;
		final long[] keys;
		final int[] ids;
		int pos;

		Batch(ServerLevel level, String dimKey, long[] keys, int[] ids) {
			this.level = level;
			this.dimKey = dimKey;
			this.keys = keys;
			this.ids = ids;
		}
	}

	private static final class RestoreJob {
		final List<Batch> batches;
		int current;

		RestoreJob(List<Batch> batches) {
			this.batches = batches;
		}
	}

	/** Remet tous les blocs supprimes, remet la roue a zero. onDone est lance a la fin. */
	private static void startReset(MinecraftServer server, Runnable onDone) {
		removal = null;
		remainingMask = WheelColor.ALL_MASK;
		spinMask = WheelColor.ALL_MASK;
		chosenColor = -1;
		chosenBlock = null;
		lastBlock = null;

		List<Batch> batches = new ArrayList<>();
		for (ServerLevel level : server.getAllLevels()) {
			String key = level.dimension().toString();
			Long2IntOpenHashMap m = REMOVED.get(key);
			if (m == null || m.isEmpty()) {
				continue;
			}
			long[] keys = new long[m.size()];
			int[] ids = new int[m.size()];
			int i = 0;
			for (Long2IntMap.Entry e : m.long2IntEntrySet()) {
				keys[i] = e.getLongKey();
				ids[i] = e.getIntValue();
				i++;
			}
			batches.add(new Batch(level, key, keys, ids));
		}
		restoreDone = onDone;
		if (batches.isEmpty()) {
			REMOVED.clear();
			regenerating = false;
			restore = null;
			saveState();
			if (onDone != null) {
				onDone.run();
			}
			return;
		}
		restore = new RestoreJob(batches);
		regenerating = true;
	}

	private static void stepRestore() {
		RestoreJob job = restore;
		if (job == null) {
			return;
		}
		int budget = 4000;
		while (budget > 0 && job.current < job.batches.size()) {
			Batch b = job.batches.get(job.current);
			int n = Math.min(budget, b.keys.length - b.pos);
			for (int i = 0; i < n; i++) {
				BlockPos p = BlockPos.of(b.keys[b.pos]);
				b.level.setBlock(p, Block.stateById(b.ids[b.pos]), SET_FLAGS);
				b.pos++;
			}
			budget -= n;
			if (b.pos >= b.keys.length) {
				job.current++;
			}
		}
		if (job.current >= job.batches.size()) {
			for (Batch b : job.batches) {
				REMOVED.remove(b.dimKey);
			}
			restore = null;
			regenerating = false;
			saveState();
			Runnable cb = restoreDone;
			restoreDone = null;
			if (cb != null) {
				cb.run();
			}
		}
	}

	// ================================================================== sauvegarde

	private static void resetRuntime() {
		running = false;
		phase = Phase.IDLE;
		remainingMask = WheelColor.ALL_MASK;
		spinMask = WheelColor.ALL_MASK;
		chosenColor = -1;
		chosenBlock = null;
		objectiveIndex = 0;
		objectiveText = "";
		objectiveChosen = false;
		regenerating = false;
		statusTicks = 0;
		REMOVED.clear();
		PlayerBlockHistory.clear();
		KILLED.clear();
		ACTIONS.clear();
		removal = null;
		restore = null;
		restoreDone = null;
		lastBlock = null;
		needBaseline = true;
	}

	private static synchronized void saveState() {
		if (savePath == null) {
			return;
		}
		Path tmp = savePath.resolveSibling("colorwheel.dat.tmp");
		try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(tmp)))) {
			out.writeInt(2); // version du format
			out.writeBoolean(running);
			out.writeInt(remainingMask);
			out.writeInt(objectiveIndex);
			out.writeBoolean(objectiveChosen);
			out.writeInt(REMOVED.size());
			for (Map.Entry<String, Long2IntOpenHashMap> e : REMOVED.entrySet()) {
				out.writeUTF(e.getKey());
				Long2IntOpenHashMap m = e.getValue();
				out.writeInt(m.size());
				for (Long2IntMap.Entry en : m.long2IntEntrySet()) {
					out.writeLong(en.getLongKey());
					out.writeInt(en.getIntValue());
				}
			}
		} catch (IOException e) {
			System.err.println("[colorwheel] sauvegarde impossible : " + e);
			return;
		}
		try {
			Files.move(tmp, savePath, StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException e) {
			System.err.println("[colorwheel] sauvegarde impossible : " + e);
		}
	}

	private static void loadState() {
		if (savePath == null || !Files.exists(savePath)) {
			return;
		}
		try (DataInputStream in = new DataInputStream(new BufferedInputStream(Files.newInputStream(savePath)))) {
			int version = in.readInt();
			if (version != 2) {
				return;
			}
			running = in.readBoolean();
			remainingMask = in.readInt();
			objectiveIndex = Math.floorMod(in.readInt(), Objectives.LIST.size());
			objectiveChosen = in.readBoolean();
			int dims = in.readInt();
			for (int d = 0; d < dims; d++) {
				String key = in.readUTF();
				int n = in.readInt();
				Long2IntOpenHashMap m = new Long2IntOpenHashMap(Math.max(16, n));
				for (int i = 0; i < n; i++) {
					long pos = in.readLong();
					int id = in.readInt();
					m.put(pos, id);
				}
				REMOVED.put(key, m);
			}
		} catch (IOException e) {
			System.err.println("[colorwheel] lecture de la sauvegarde impossible : " + e);
		}
	}
}
