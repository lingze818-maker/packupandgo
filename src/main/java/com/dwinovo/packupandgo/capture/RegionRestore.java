package com.dwinovo.packupandgo.capture;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 区域还原:一份采集 NBT + 一个锚点 → 逐块写回世界。
 *
 * <p>与 {@link RegionCapture} 互为镜像。方块实体恢复的顺序与坑位照抄 numen
 * BuildCompanionTask 的实践(那是被真机验证过的):先 setBlock 再取 BlockEntity,
 * NBT 拷贝+落位点坐标覆写+load,最后补 sendBlockUpdated——没有这一包,告示牌的
 * 字在客户端要等区块重载才出现。
 *
 * <p>W1 为一次性同步写回(采集端同口径);分帧与 30 秒硬直留 W2 交互期。
 */
public final class RegionRestore {

    /** 还原结果:写入格数/方块实体恢复数/跳过明细数。 */
    public record Result(int placedBlocks, int restoredBlockEntities, int skippedCells) {}

    private RegionRestore() {}

    public static Result restore(ServerLevel level, BlockPos anchor, CompoundTag root) {
        if (!"packupandgo.v1".equals(root.getString("format"))) {
            throw new IllegalArgumentException("未知格式: " + root.getString("format"));
        }
        List<BlockState> palette = decodePalette(root.getList("palette", Tag.TAG_COMPOUND));
        ListTag blocks = root.getList("blocks", Tag.TAG_COMPOUND);

        int placed = 0;
        int restoredBe = 0;
        int skipped = 0;

        for (Tag t : blocks) {
            CompoundTag cell = (CompoundTag) t;
            ListTag rel = cell.getList("pos", Tag.TAG_INT);
            BlockPos pos = new BlockPos(
                    anchor.getX() + rel.getInt(0),
                    anchor.getY() + rel.getInt(1),
                    anchor.getZ() + rel.getInt(2));
            int idx = cell.getInt("state");
            if (idx < 0 || idx >= palette.size()) {
                skipped++;
                continue;
            }
            BlockState state = palette.get(idx);
            // flag 3 = 邻居更新+客户端同步;采集是全量的,空气格也写(展开时清地形)
            level.setBlock(pos, state, 3);
            placed++;

            if (cell.contains("nbt", Tag.TAG_COMPOUND) && state.hasBlockEntity()) {
                BlockEntity be = level.getBlockEntity(pos);
                if (be != null) {
                    applyBlockEntityData(level, pos, state, cell.getCompound("nbt"));
                    restoredBe++;
                }
            }
        }

        // 驯服动物放生:随家走第二程。复制模式(默认)必须重生成 UUID——
        // 撞号会损坏存档(拍板 2 红线);移动模式原号随迁(原区实体已 discard)。
        int pets = 0;
        ListTag entities = root.getList("entities", Tag.TAG_COMPOUND);
        boolean moved = root.getBoolean("moved");
        for (Tag t : entities) {
            CompoundTag e = (CompoundTag) t;
            var type = net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.getValue(
                    new ResourceLocation(e.getString("id")));
            if (type == null) {
                continue;
            }
            var entity = type.create(level);
            if (entity == null) {
                continue;
            }
            CompoundTag data = e.getCompound("data").copy();
            if (!moved) {
                data.putUUID("UUID", java.util.UUID.randomUUID());
            }
            // NBT 恢复后按锚点平移——存的相对偏移是打包世界的绝对位
            entity.load(data);
            entity.setPos(
                    anchor.getX() + e.getDouble("rel_x"),
                    anchor.getY() + e.getDouble("rel_y"),
                    anchor.getZ() + e.getDouble("rel_z"));
            level.addFreshEntity(entity);
            pets++;
        }
        return new Result(placed, restoredBe + pets, skipped);
    }

    /** palette 条目 {Name, Properties} → BlockState;解析失败抛出(存档坏了该让操作者看见)。 */
    private static List<BlockState> decodePalette(ListTag palette) {
        List<BlockState> out = new ArrayList<>(palette.size());
        for (Tag t : palette) {
            CompoundTag entry = (CompoundTag) t;
            Block block = ForgeRegistries.BLOCKS.getValue(
                    new ResourceLocation(entry.getString("Name")));
            if (block == null) {
                throw new IllegalArgumentException("palette 里的方块不存在: " + entry.getString("Name"));
            }
            BlockState state = block.defaultBlockState();
            if (entry.contains("Properties", Tag.TAG_COMPOUND)) {
                CompoundTag props = entry.getCompound("Properties");
                for (String key : props.getAllKeys()) {
                    state = applyProperty(state, key, props.getString(key));
                }
            }
            out.add(state);
        }
        return out;
    }

    private static BlockState applyProperty(BlockState state, String key, String value) {
        for (Property<?> p : state.getProperties()) {
            if (p.getName().equals(key)) {
                return setValue(state, p, value);
            }
        }
        // 未知属性(采集端版本更新后删除的属性):丢弃该属性,方块本身仍还原——
        // 保真度损失记日志而非炸整次还原
        com.dwinovo.packupandgo.PackUpAndGoMod.LOG.warn(
                "丢弃未知属性 {}.{}", state.getBlock(), key);
        return state;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static BlockState setValue(BlockState state, Property prop, String value) {
        Optional<?> parsed = prop.getValue(value);
        if (parsed.isEmpty()) {
            return state;
        }
        return state.setValue(prop, (Comparable) parsed.get());
    }

    /**
     * 方块实体数据装回:坐标覆写为落位点(采集时剥离过),load 之后补客户端同步包。
     * 坐标覆写的必要性、sendBlockUpdated 的必要性,注释见 numen 同名方法——
     * 这里是从那边验证过的实践,不重新发明。
     */
    private static void applyBlockEntityData(
            net.minecraft.server.level.ServerLevel level, BlockPos pos, BlockState placed, CompoundTag data) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be == null) {
            return;
        }
        try {
            CompoundTag copy = data.copy();
            copy.putInt("x", pos.getX());
            copy.putInt("y", pos.getY());
            copy.putInt("z", pos.getZ());
            be.load(copy);   // 1.20.1:方块实体读档不带注册表参数
            be.setChanged();
            level.sendBlockUpdated(pos, placed, placed,
                    net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
        } catch (RuntimeException e) {
            // 单格数据坏不该让整栋楼停工——与采集端同口径
            com.dwinovo.packupandgo.PackUpAndGoMod.LOG.warn(
                    "方块实体数据装回失败 @ {}: {}", pos, e.toString());
        }
    }
}
