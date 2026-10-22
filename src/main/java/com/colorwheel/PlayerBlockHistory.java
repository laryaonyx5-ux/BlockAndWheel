
package com.colorwheel;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueInput;

/**
 * Historique des blocs que le joueur modifie pendant le défi.
 * Permet de restaurer leur état initial.
 */
public final class PlayerBlockHistory {

    private record Snapshot(BlockState state, CompoundTag blockEntityData) {}

    private static final Map<String, Map<Long, Snapshot>> CHANGES =
            new HashMap<>();

    private PlayerBlockHistory() {
    }

    /**
     * Enregistre l'état initial d'un bloc avant sa première modification.
     */
    public static void capture(LevelAccessor world, BlockPos pos) {
        if (!Game.running) {
            return;
        }

        if (world.isClientSide()
                || !(world instanceof ServerLevel level)
                || !level.hasChunkAt(pos)) {
            return;
        }

        BlockState state = level.getBlockState(pos);
        BlockEntity blockEntity = level.getBlockEntity(pos);

        CompoundTag data = blockEntity == null
                ? null
                : blockEntity.saveWithFullMetadata(level.registryAccess());

        CHANGES.computeIfAbsent(
                level.dimension().toString(),
                key -> new HashMap<>()
        ).putIfAbsent(
                pos.asLong(),
                new Snapshot(state, data == null ? null : data.copy())
        );
    }

    /**
     * Restaure les blocs enregistrés.
     *
     * @return nombre de blocs traités
     */
    public static int restore(MinecraftServer server) {
        int restored = 0;

        for (ServerLevel level : server.getAllLevels()) {
            Map<Long, Snapshot> map =
                    CHANGES.remove(level.dimension().toString());

            if (map == null) {
                continue;
            }

            for (Map.Entry<Long, Snapshot> entry : map.entrySet()) {
                BlockPos pos = BlockPos.of(entry.getKey());

                if (!level.hasChunkAt(pos)) {
                    continue;
                }

                Snapshot snapshot = entry.getValue();
                BlockState original = snapshot.state();

                if (!level.getBlockState(pos).equals(original)) {
                    level.setBlock(pos, original, 2 | 16);
                }

                CompoundTag savedData = snapshot.blockEntityData();

                if (savedData != null) {
                    BlockEntity blockEntity = level.getBlockEntity(pos);

                    if (blockEntity != null) {
                        blockEntity.loadWithComponents(
                                TagValueInput.create(
                                        ProblemReporter.DISCARDING,
                                        level.registryAccess(),
                                        savedData.copy()
                                )
                        );

                        blockEntity.setChanged();
                    }
                }

                restored++;
            }
        }

        return restored;
    }

    /**
     * Efface l'historique des modifications.
     */
    public static void clear() {
        CHANGES.clear();
    }
}
