package com.dwinovo.packupandgo.capture;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 每玩家的框选状态——扳手定的两个对角点存在这儿,桶装桶时来取。
 *
 * <p>生命周期:定第二角后一直有效,直到①再来一次完整框选覆盖②扳手不潜行右键
 * (手动清空)③跨维度(第二角与第一角不同世界=整组作废重来)。不做超时——
 * 玩家圈好家去拿桶的路上,选择不该蒸发。
 *
 * <p>这里的「跨维度作废」只约束框选和装桶:家必须在同一个维度里圈完、装完。
 * 装进桶之后可以拿到另一个维度放下,那条路不看这份内存选区。
 */
public final class SelectionStore {

    /** 两角齐了才 complete;dimension 跟着第一角走。 */
    public record Selection(BlockPos first, BlockPos second, ResourceLocation dimension) {
        public boolean complete() {
            return second != null;
        }

        public int sizeX() { return Math.abs(first.getX() - second.getX()) + 1; }
        public int sizeY() { return Math.abs(first.getY() - second.getY()) + 1; }
        public int sizeZ() { return Math.abs(first.getZ() - second.getZ()) + 1; }
    }

    private static final ConcurrentHashMap<UUID, Selection> BY_PLAYER = new ConcurrentHashMap<>();

    private SelectionStore() {}

    /** 定/换第一角(第二角清空——重新开始一次框选)。 */
    public static void setFirst(UUID player, BlockPos pos, ResourceLocation dimension) {
        BY_PLAYER.put(player, new Selection(pos, null, dimension));
    }

    /** 定第二角。前置:已有第一角、无第二角、同维度;不满足返回 false 由调用方提示。 */
    public static boolean setSecond(UUID player, BlockPos pos, ResourceLocation dimension) {
        Selection cur = BY_PLAYER.get(player);
        if (cur == null || cur.second() != null || !cur.dimension().equals(dimension)) {
            return false;
        }
        BY_PLAYER.put(player, new Selection(cur.first(), pos, dimension));
        return true;
    }

    public static Selection get(UUID player) {
        return BY_PLAYER.get(player);
    }

    public static void clear(UUID player) {
        BY_PLAYER.remove(player);
    }
}
