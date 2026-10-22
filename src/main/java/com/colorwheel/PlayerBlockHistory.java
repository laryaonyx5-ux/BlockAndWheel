package com.colorwheel;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.storage.TagValueInput;

/** Sépare les blocs cassés des emplacements où le joueur a posé un bloc. */
public final class PlayerBlockHistory {
    private record Snapshot(BlockState state, CompoundTag blockEntityData) {}
    private static final Map<String, Map<Long, Snapshot>> BROKEN = new HashMap<>();
    private static final Map<String, Map<Long, Snapshot>> PLACED = new HashMap<>();
    private static Path savePath;

    /** Attache l'historique au monde courant et recharge les modifications précédemment enregistrées. */
    public static void open(Path path) {
        clear();
        savePath = path;
        load();
    }

    public static void save() {
        if (savePath == null) return;
        Path tmp = savePath.resolveSibling(savePath.getFileName() + ".tmp");
        try {
            Path parent = savePath.getParent();
            if (parent != null) Files.createDirectories(parent);
            try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(tmp)))) {
                out.writeInt(1);
                writeMap(out, BROKEN);
                writeMap(out, PLACED);
            }
            Files.move(tmp, savePath, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            System.err.println("[colorwheel] sauvegarde de l'historique joueur impossible : " + e);
        }
    }

    private static void writeMap(DataOutputStream out, Map<String, Map<Long, Snapshot>> source) throws IOException {
        out.writeInt(source.size());
        for (var dimension : source.entrySet()) {
            out.writeUTF(dimension.getKey());
            out.writeInt(dimension.getValue().size());
            for (var entry : dimension.getValue().entrySet()) {
                out.writeLong(entry.getKey());
                out.writeInt(net.minecraft.world.level.block.Block.getId(entry.getValue().state()));
                CompoundTag data = entry.getValue().blockEntityData();
                out.writeBoolean(data != null);
                if (data != null) out.writeUTF(data.toString());
            }
        }
    }

    private static void load() {
        if (savePath == null || !Files.exists(savePath)) return;
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(Files.newInputStream(savePath)))) {
            if (in.readInt() != 1) return;
            readMap(in, BROKEN);
            readMap(in, PLACED);
        } catch (Exception e) {
            System.err.println("[colorwheel] lecture de l'historique joueur impossible : " + e);
        }
    }

    private static void readMap(DataInputStream in, Map<String, Map<Long, Snapshot>> target) throws Exception {
        int dimensions = in.readInt();
        for (int d = 0; d < dimensions; d++) {
            String dimension = in.readUTF();
            int count = in.readInt();
            Map<Long, Snapshot> map = target.computeIfAbsent(dimension, k -> new HashMap<>());
            for (int i = 0; i < count; i++) {
                long pos = in.readLong();
                BlockState state = net.minecraft.world.level.block.Block.stateById(in.readInt());
               CompoundTag data = in.readBoolean() ? TagParser.parseCompoundFully(in.readUTF()) : null;
                map.put(pos, new Snapshot(state, data));
            }
        }
    }

    private PlayerBlockHistory() {}

    public static void captureBroken(LevelAccessor world, BlockPos pos) {
        captureInto(BROKEN, world, pos);
    }

    public static void capturePlaced(LevelAccessor world, BlockPos pos) {
        captureInto(PLACED, world, pos);
    }

    private static void captureInto(Map<String, Map<Long, Snapshot>> target, LevelAccessor world, BlockPos pos) {
        if (world.isClientSide() || !(world instanceof ServerLevel level) || !level.hasChunkAt(pos)) return;
        BlockState state = level.getBlockState(pos);
        BlockEntity blockEntity = level.getBlockEntity(pos);
        CompoundTag data = blockEntity == null ? null : blockEntity.saveWithFullMetadata(level.registryAccess());
        Map<Long, Snapshot> map = target.computeIfAbsent(level.dimension().toString(), k -> new HashMap<>());
        if (!map.containsKey(pos.asLong())) {
            map.put(pos.asLong(), new Snapshot(state, data == null ? null : data.copy()));
            save();
        }
    }

    public static boolean isPlayerPlaced(LevelAccessor world, BlockPos pos) {
        if (!(world instanceof ServerLevel level)) return false;
        Map<Long, Snapshot> map = PLACED.get(level.dimension().toString());
        return map != null && map.containsKey(pos.asLong());
    }

    public static int restoreBroken(ServerLevel level, int chunkX, int chunkZ) {
        return restoreFrom(BROKEN, level, chunkX, chunkZ);
    }

    public static int restorePlaced(ServerLevel level, int chunkX, int chunkZ) {
        return restoreFrom(PLACED, level, chunkX, chunkZ);
    }

    public static int restoreChunk(ServerLevel level, int chunkX, int chunkZ) {
        return restoreBroken(level, chunkX, chunkZ) + restorePlaced(level, chunkX, chunkZ);
    }

    private static int restoreFrom(Map<String, Map<Long, Snapshot>> source, ServerLevel level, int chunkX, int chunkZ) {
        Map<Long, Snapshot> map = source.get(level.dimension().toString());
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
        if (map.isEmpty()) source.remove(level.dimension().toString());
        if (restored > 0) save();
        return restored;
    }

    public static void clear() {
        BROKEN.clear();
        PLACED.clear();
    }
}
