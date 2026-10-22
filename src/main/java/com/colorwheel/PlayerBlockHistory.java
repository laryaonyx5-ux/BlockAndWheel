package com.colorwheel;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.storage.TagValueInput;

/** Sauvegarde les blocs que le joueur casse ou remplace, même lorsque la roue est arrêtée. */
public final class PlayerBlockHistory {
    private record Snapshot(BlockState state, CompoundTag blockEntityData) {}
    private static final Map<String, Map<Long, Snapshot>> CHANGES = new HashMap<>();

    private PlayerBlockHistory() {}

    public static void capture(LevelAccessor world, BlockPos pos) {
        if (world.isClientSide() || !(world instanceof ServerLevel level) || !level.hasChunkAt(pos)) return;
        BlockState state = level.getBlockState(pos);
        BlockEntity blockEntity = level.getBlockEntity(pos);
        CompoundTag data = blockEntity == null ? null : blockEntity.saveWithFullMetadata(level.registryAccess());
        CHANGES.computeIfAbsent(level.dimension().toString(), k -> new HashMap<>())
            .putIfAbsent(pos.asLong(), new Snapshot(state, data == null ? null : data.copy()));
    }

    /** Restaure uniquement le chunk demandé et conserve l'historique des autres chunks. */
    public static int restoreChunk(ServerLevel level, int chunkX, int chunkZ) {
        Map<Long, Snapshot> map = CHANGES.get(level.dimension().toString());
        if (map == null || map.isEmpty()) return 0;
        int restored = 0;
        Iterator<Map.Entry<Long, Snapshot>> iterator = map.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Long, Snapshot> entry = iterator.next();
            BlockPos pos = BlockPos.of(entry.getKey());
            if ((pos.getX() >> 4) != chunkX || (pos.getZ() >> 4) != chunkZ || !level.hasChunkAt(pos)) continue;
            Snapshot snapshot = entry.getValue();
            level.setBlock(pos, snapshot.state(), 2 | 16);
            if (snapshot.blockEntityData() != null) {
                BlockEntity blockEntity = level.getBlockEntity(pos);
                if (blockEntity != null) {
                    blockEntity.loadWithComponents(TagValueInput.create(
                        ProblemReporter.DISCARDING,
                        level.registryAccess(),
                        snapshot.blockEntityData().copy()
                    ));
                    blockEntity.setChanged();
                }
            }
            iterator.remove();
            restored++;
        }
        if (map.isEmpty()) CHANGES.remove(level.dimension().toString());
        return restored;
    }

    public static void clear() { CHANGES.clear(); }
}
