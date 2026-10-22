package com.colorwheel;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Historique séparé des blocs que le joueur modifie pendant le défi. */
public final class PlayerBlockHistory {
	private record Snapshot(BlockState state, CompoundTag blockEntityData) {}
	private static final Map<String, Map<Long, Snapshot>> CHANGES = new HashMap<>();

	private PlayerBlockHistory() {}

	public static void capture(LevelAccessor world, BlockPos pos) {
		if (!Game.running) return;
		if (world.isClientSide() || !(world instanceof ServerLevel level) || !level.hasChunkAt(pos)) return;
		BlockState state = level.getBlockState(pos);
		BlockEntity blockEntity = level.getBlockEntity(pos);
		CompoundTag data = blockEntity == null ? null : blockEntity.saveWithFullMetadata(level.registryAccess());
		CHANGES.computeIfAbsent(level.dimension().toString(), k -> new HashMap<>())
			.putIfAbsent(pos.asLong(), new Snapshot(state, data == null ? null : data.copy()));
	}

	public static int restore(MinecraftServer server) {
		int restored = 0;
		for (ServerLevel level : server.getAllLevels()) {
			Map<Long, Snapshot> map = CHANGES.remove(level.dimension().toString());
			if (map == null) continue;
			for (Map.Entry<Long, Snapshot> entry : map.entrySet()) {
				BlockPos pos = BlockPos.of(entry.getKey());
				if (!level.hasChunkAt(pos)) continue;
				Snapshot snapshot = entry.getValue();
				BlockState original = snapshot.state();
				if (!level.getBlockState(pos).equals(original)) {
					level.setBlock(pos, original, 2 | 16);
				}
				if (snapshot.blockEntityData() != null) {
					BlockEntity blockEntity = level.getBlockEntity(pos);
					if (blockEntity != null) {
						blockEntity.loadWithComponents(snapshot.blockEntityData().copy(), level.registryAccess());
						blockEntity.setChanged();
					}
				}
				restored++;
			}
		}
		return restored;
	}

	public static void clear() { CHANGES.clear(); }
}
