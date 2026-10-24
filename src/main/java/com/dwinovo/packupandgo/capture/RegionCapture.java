package com.dwinovo.packupandgo.capture;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 区域采集:两个对角点之间的全部方块 → 一份 CompoundTag。
 *
 * <p>格式对齐原版 structure template 的核心字段(size/palette/blocks),但语义是
 * <b>全量保真</b>——这是提桶跑路的黄金法则:箱子里的东西、告示牌的字、熔炉的火,
 * 一颗不许少。原版蓝图系的 {@code BlueprintSafety} 白名单在这里<b>不适用</b>:
 * 那防的是"外来文件白送物品",这里搬的是玩家自己的家。
 *
 * <p>硬底线仍保留一条:{@code onlyOpCanSetNbt} 的方块实体(命令方块/结构方块/拼图)
 * 不采 NBT——提桶不该成为搬运可执行命令的容器。
 *
 * <p>W1 为一次性同步遍历:48³ ≈ 11 万格,命令场景可接受;分帧批量留 W2 交互期。
 */
public final class RegionCapture {

    /** 采集结果:根 NBT + 统计(给命令回显与后续验收用)。 */
    public record Result(CompoundTag root, int totalCells, int blockCount, int blockEntityCount) {}

    private RegionCapture() {}

    public static Result capture(ServerLevel level, BlockPos pos1, BlockPos pos2) {
        BlockPos min = new BlockPos(
                Math.min(pos1.getX(), pos2.getX()),
                Math.min(pos1.getY(), pos2.getY()),
                Math.min(pos1.getZ(), pos2.getZ()));
        BlockPos max = new BlockPos(
                Math.max(pos1.getX(), pos2.getX()),
                Math.max(pos1.getY(), pos2.getY()),
                Math.max(pos1.getZ(), pos2.getZ()));

        ListTag palette = new ListTag();
        Map<BlockState, Integer> paletteIndex = new HashMap<>();
        ListTag blocks = new ListTag();
        int blockEntityCount = 0;
        int blockCount = 0;

        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            BlockState state = level.getBlockState(pos);
            if (!state.isAir()) {
                blockCount++;
            }
            int idx = paletteIndexOf(palette, paletteIndex, state);

            CompoundTag cell = new CompoundTag();
            ListTag rel = new ListTag();
            // ListTag.add(int,Tag) 是"按下标插入"语义——旧代码把 TAG_INT(=3)当成了
            // 元素类型声明,空列表插入 index 3 直接 IndexOutOfBounds(10-16 真机实锤)。
            // 正确姿势:直接 add(Tag),元素类型由首元素自动锁定。
            rel.add(net.minecraft.nbt.IntTag.valueOf(pos.getX() - min.getX()));
            rel.add(net.minecraft.nbt.IntTag.valueOf(pos.getY() - min.getY()));
            rel.add(net.minecraft.nbt.IntTag.valueOf(pos.getZ() - min.getZ()));
            cell.put("pos", rel);
            cell.putInt("state", idx);

            BlockEntity be = level.getBlockEntity(pos);
            if (be != null) {
                CompoundTag beTag = be.saveWithFullMetadata();
                if (beTag != null && !be.onlyOpCanSetNbt()) {
                    // 坐标剥离:存的是导出时的世界坐标,展开时按落位点重写(见 RegionRestore)
                    beTag.remove("x");
                    beTag.remove("y");
                    beTag.remove("z");
                    cell.put("nbt", beTag);
                    blockEntityCount++;
                }
            }
            blocks.add(cell);
        }

        int totalCells = (max.getX() - min.getX() + 1)
                * (max.getY() - min.getY() + 1)
                * (max.getZ() - min.getZ() + 1);

        CompoundTag root = new CompoundTag();
        root.putString("format", "packupandgo.v1");
        ListTag sizeTag = new ListTag();
        sizeTag.add(net.minecraft.nbt.IntTag.valueOf(max.getX() - min.getX() + 1));
        sizeTag.add(net.minecraft.nbt.IntTag.valueOf(max.getY() - min.getY() + 1));
        sizeTag.add(net.minecraft.nbt.IntTag.valueOf(max.getZ() - min.getZ() + 1));
        root.put("size", sizeTag);
        root.put("palette", palette);
        root.put("blocks", blocks);

        CompoundTag stats = new CompoundTag();
        stats.putInt("total_cells", totalCells);
        stats.putInt("blocks", blockCount);
        stats.putInt("block_entities", blockEntityCount);
        stats.putLong("captured_at", System.currentTimeMillis());
        root.put("stats", stats);

        // 驯服动物上车(拍板 2):isTamed 的猫猫狗狗随家走——owner/名字/坐姿/血量
        // 全在 entity NBT 里(saveWithoutId 全量)。位置存相对锚点的浮点偏移:
        // 动物不站在格心,整格坐标会让它们挤到方块角上。
        ListTag entities = new ListTag();
        net.minecraft.world.phys.AABB box = new net.minecraft.world.phys.AABB(min, max.offset(1, 1, 1));
        // 随迁范围(2026-10-16 扩):驯服动物(狼/猫/鹦鹉) + 马系全家(马/驴/骡/驼羊——
        // 10-16 灵泽的马漏乘案:AbstractHorse 不是 TamableAnimal,旧过滤器直接漏光)
        // + 农场动物(牛/羊/猪/鸡——搬家不带农场等于重新畜牧)
        for (net.minecraft.world.entity.Entity pet : level.getEntitiesOfClass(
                net.minecraft.world.entity.Entity.class, box,
                pet -> {
                    if (pet instanceof net.minecraft.world.entity.TamableAnimal t)
                        return t.getOwnerUUID() != null;   // 有主人=驯服(isTamed 的官方映射在部分版本不可见,owner 判定等效)
                    if (pet instanceof net.minecraft.world.entity.animal.horse.AbstractHorse h)
                        return h.isTamed();
                    return pet instanceof net.minecraft.world.entity.animal.Animal;
                })) {
            CompoundTag e = new CompoundTag();
            e.putString("id", net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES
                    .getKey(pet.getType()).toString());
            CompoundTag data = new CompoundTag();
            pet.saveWithoutId(data);
            e.put("data", data);
            e.putDouble("rel_x", pet.getX() - min.getX());
            e.putDouble("rel_y", pet.getY() - min.getY());
            e.putDouble("rel_z", pet.getZ() - min.getZ());
            entities.add(e);
        }
        root.put("entities", entities);
        stats.putInt("pets", entities.size());

        return new Result(root, totalCells, blockCount, blockEntityCount);
    }

    /** palette 条目 = {Name: 方块 id, Properties: {属性: 值}};同 state 只入一次。 */
    private static int paletteIndexOf(ListTag palette, Map<BlockState, Integer> index, BlockState state) {
        Integer cached = index.get(state);
        if (cached != null) {
            return cached;
        }
        CompoundTag entry = new CompoundTag();
        entry.putString("Name", net.minecraftforge.registries.ForgeRegistries.BLOCKS
                .getKey(state.getBlock()).toString());
        if (!state.getValues().isEmpty()) {
            CompoundTag props = new CompoundTag();
            for (Map.Entry<Property<?>, Comparable<?>> e : state.getValues().entrySet()) {
                props.putString(e.getKey().getName(), valueToString(e.getValue()));
            }
            entry.put("Properties", props);
        }
        palette.add(entry);
        int idx = palette.size() - 1;
        index.put(state, idx);
        return idx;
    }

    private static String valueToString(Comparable<?> value) {
        // 1.20.1 的 Property#getName(Comparable) 做同一件事,但泛型签名绕;
        // 直接 toString 对所有原版属性值(枚举名/整数/布尔)即规范形。
        return value.toString();
    }
}
