package com.dwinovo.packupandgo.item;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

/**
 * 桶的装载状态读写——「已装载的桶」就是 ItemStack NBT 里有一份 {@code packup} 数据。
 *
 * <p>纯数据侧:不关心装载动作怎么发生(右键流程是扳手与桶的事),只定义
 * 「装了什么/装了多少/怎么在 tooltip 里自述」。
 *
 * <p>装载摘要(stats)冗余存一份在 NBT 顶层——tooltip 不必反序列化整个 blocks
 * 才能报「箱子几个、方块几颗」。家可以很大,看一眼不该贵。
 */
public final class BucketData {

    private BucketData() {}

    /** 桶是否已装载。 */
    public static boolean isLoaded(ItemStack stack) {
        return stack.hasTag() && stack.getTagElement("packup") != null;
    }

    /**
     * 取装载的完整数据(采集端 RegionCapture 的根 NBT);空桶返回 null。
     *
     * <p>超限落盘的桶(external)从磁盘回读——所以这里需要 level;文件丢失时
     * 返回 null 且 {@link #isExternalLost} 置真,交互层据此给出人话反馈而不是
     * 静默失败。
     */
    public static CompoundTag getPack(ItemStack stack, net.minecraft.server.level.ServerLevel level) {
        CompoundTag stub = stack.hasTag() ? stack.getTagElement("packup") : null;
        if (stub == null) {
            return null;
        }
        if (stub.getBoolean("external")) {
            try {
                CompoundTag full = PackStore.load(level, stub.getString("pack_id"));
                full.putString("pack_name", stub.getString("pack_name"));
                full.putBoolean("moved", stub.getBoolean("moved"));
                return full;
            } catch (java.io.IOException e) {
                externalLost = true;
                return null;
            }
        }
        return stub;
    }

    /** 上一次 getPack 是否因外部文件丢失而失败(一次性旗标,读完即清)。 */
    public static boolean isExternalLost() {
        boolean v = externalLost;
        externalLost = false;
        return v;
    }

    private static boolean externalLost = false;

    /**
     * 摘要读取(tooltip/尺寸/stats):inline 数据与 external 桩都能读——桩里冗余了
     * 名字/统计/尺寸。真正展开全文必须走带 level 的重载(external 桩回磁盘取)。
     */
    public static CompoundTag getPack(ItemStack stack) {
        return stack.hasTag() ? stack.getTagElement("packup") : null;
    }

    /**
     * 把采集数据装进桶。调用方负责:①桶是空的(已装载再装=拒绝,见设计)②尺寸
     * 不超这只桶的档位(BucketItem#maxSize)。名字可后改(铁砧),这里存的是采集名。
     * moved 标记决定展开时实体的 UUID 策略(拍板 2 红线):复制=重生成,移动=原号随迁。
     *
     * <p>超限落盘(设计 §四 风险①):压缩后超过 {@link PackStore#INLINE_LIMIT} 的
     * pack 不进 NBT——完整数据落 {@code data/packupandgo/},桶里只留指针桩
     * (名字/moved/stats/size 冗余在桩里,tooltip 不必回磁盘)。
     */
    public static void loadInto(ItemStack stack, CompoundTag root, String name, boolean moved,
                                net.minecraft.server.level.ServerLevel level) throws java.io.IOException {
        CompoundTag pack = root.copy();
        pack.putString("pack_name", name);
        pack.putBoolean("moved", moved);
        if (level != null && PackStore.compressedSize(pack) > PackStore.INLINE_LIMIT) {
            String id = PackStore.store(level, pack);
            CompoundTag stub = new CompoundTag();
            stub.putBoolean("external", true);
            stub.putString("pack_id", id);
            stub.putString("pack_name", name);
            stub.putBoolean("moved", moved);
            net.minecraft.nbt.CompoundTag stats = pack.getCompound("stats");
            if (!stats.isEmpty()) {
                stub.put("stats", stats);
            }
            net.minecraft.nbt.ListTag size = pack.getList("size", Tag.TAG_INT);
            if (size.size() >= 3) {
                stub.put("size", size);
            }
            stack.getOrCreateTag().put("packup", stub);
            return;
        }
        stack.getOrCreateTag().put("packup", pack);
    }

    /**
     * 卸下桶里的家。展开成功后调用:创造模式会把数量设回使用前,只 shrink
     * 的话 {@code packup} 还在,同一只桶可以再放一次。
     */
    public static void clearPack(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null) {
            return;
        }
        tag.remove("packup");
        if (tag.isEmpty()) {
            stack.setTag(null);
        }
    }

    /** 结构名:采集时装进去的(或铁砧改过的名字,展示层优先 CustomName,见 BucketItem)。 */
    public static String getPackName(ItemStack stack) {
        CompoundTag pack = getPack(stack);
        return pack == null ? "" : pack.getString("pack_name");
    }

    /** tooltip 四行的数据源:从冗余 stats 直读,不解 blocks。 */
    public static int stat(ItemStack stack, String key) {
        CompoundTag pack = getPack(stack);
        if (pack == null) {
            return -1;
        }
        CompoundTag stats = pack.getCompound("stats");
        return stats.contains(key, Tag.TAG_INT) ? stats.getInt(key) : -1;
    }

    /** 尺寸字符串 "WxHxD"。 */
    public static String sizeString(ItemStack stack) {
        CompoundTag pack = getPack(stack);
        if (pack == null) {
            return "";
        }
        net.minecraft.nbt.ListTag size = pack.getList("size", Tag.TAG_INT);
        if (size.size() < 3) {
            return "?";
        }
        return size.getInt(0) + "×" + size.getInt(1) + "×" + size.getInt(2);
    }
}
