package com.dwinovo.packupandgo.capture;

import com.dwinovo.packupandgo.PackUpAndGoMod;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EndGatewayBlock;
import net.minecraft.world.level.block.EndPortalBlock;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.level.block.RespawnAnchorBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
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
 * <p>跨维度(v1.1):格式仍是 {@code packupandgo.v1},<b>没有维度锁</b>。v1.0 的桶
 * 不带 {@code source_dimension},照样能放;新桶记下的来源维度只用于聊天提示。
 * 落点超出目标维度的建筑高度或世界边界时整单拒绝——一格不写,调用方据此不消耗桶。
 * 床、重生锚、水、下界生物按原样放下(黄金法则),聊天栏说明原版接着会做什么。
 * 传送门若在第一遍被原版拆掉,全屋写完后再补一次;留下来的门不再写。
 *
 * <p>本类只吃一份 CompoundTag,不读磁盘、不看来源维度的存档目录。大件文件在
 * {@link com.dwinovo.packupandgo.item.PackStore},路径是世界根而不是某个维度。
 */
public final class RegionRestore {

    /**
     * 还原结果。{@code rejection != null} 表示一格都没写,调用方不得消耗桶。
     * {@code notices} 是放下之后的提醒(爆炸、水不蒸发、生物僵尸化),不拦展开。
     */
    public record Result(
            int placedBlocks,
            int restoredBlockEntities,
            int skippedCells,
            @Nullable String rejection,
            List<String> notices) {

        public static Result rejected(String message) {
            return new Result(0, 0, 0, message, List.of());
        }

        public boolean rejected() {
            return rejection != null;
        }
    }

    /** 传送门方块留到第二遍再写,好让黑曜石框先就位。 */
    private record DeferredPortal(BlockPos pos, BlockState state, @Nullable CompoundTag nbt) {}

    private RegionRestore() {}

    /**
     * 玩家能看懂的维度名。原版三维用中文,模组维度保持注册 id(没有本地化表可查)。
     */
    public static String dimensionLabel(String id) {
        return switch (id) {
            case "minecraft:overworld" -> "主世界";
            case "minecraft:the_nether" -> "下界";
            case "minecraft:the_end" -> "末地";
            default -> id;
        };
    }

    /**
     * 写入前的门禁。返回 null 表示可以写;非 null 是给玩家的人话,此时世界还没动。
     * 未知格式、缺尺寸、整栋高于目标维度、这次落点会探出建筑高度或世界边界,都走这里。
     */
    @Nullable
    public static String rejectionReason(ServerLevel level, BlockPos anchor, CompoundTag root) {
        String format = root.getString("format");
        if (!"packupandgo.v1".equals(format)) {
            return "未知格式: " + format + "——放下不了,桶没消耗";
        }
        ListTag sizeTag = root.getList("size", Tag.TAG_INT);
        if (sizeTag.size() < 3) {
            return "这桶没有尺寸数据,放下不了,桶没消耗";
        }
        int sizeX = sizeTag.getInt(0);
        int sizeY = sizeTag.getInt(1);
        int sizeZ = sizeTag.getInt(2);
        if (sizeX <= 0 || sizeY <= 0 || sizeZ <= 0) {
            return "这桶尺寸不合法,放下不了,桶没消耗";
        }

        int minBuild = level.getMinBuildHeight();
        int maxBuild = level.getMaxBuildHeight();
        int room = maxBuild - minBuild;
        if (sizeY > room) {
            return String.format(
                    "放不下:家高 %d 格,这个维度只能建 %d 格(y=%d–%d)。桶没消耗",
                    sizeY, room, minBuild, maxBuild - 1);
        }
        long yTop = (long) anchor.getY() + sizeY - 1L;
        if (anchor.getY() < minBuild || yTop >= maxBuild) {
            return String.format(
                    "放不下:家高 %d 格,这次会占 y=%d–%d,这个维度只能建 y=%d–%d。换个高度再放,桶没消耗",
                    sizeY, anchor.getY(), yTop, minBuild, maxBuild - 1);
        }

        long xFar = (long) anchor.getX() + sizeX - 1L;
        long zFar = (long) anchor.getZ() + sizeZ - 1L;
        if (xFar > Integer.MAX_VALUE || zFar > Integer.MAX_VALUE
                || xFar < Integer.MIN_VALUE || zFar < Integer.MIN_VALUE) {
            return "放不下:占地超出坐标范围。换个地方,桶没消耗";
        }
        BlockPos far = new BlockPos((int) xFar, anchor.getY(), (int) zFar);
        var border = level.getWorldBorder();
        if (!border.isWithinBounds(anchor) || !border.isWithinBounds(far)) {
            return String.format(
                    "放不下:占地 x=%d–%d, z=%d–%d 超出这个维度的世界边界。换个地方,桶没消耗",
                    anchor.getX(), far.getX(), anchor.getZ(), far.getZ());
        }
        return null;
    }

    public static Result restore(ServerLevel level, BlockPos anchor, CompoundTag root) {
        String rejection = rejectionReason(level, anchor, root);
        if (rejection != null) {
            PackUpAndGoMod.LOG.info("place rejected @ {} in {}: {}",
                    anchor, level.dimension().location(), rejection);
            return Result.rejected(rejection);
        }

        List<BlockState> palette;
        try {
            palette = decodePalette(root.getList("palette", Tag.TAG_COMPOUND));
        } catch (IllegalArgumentException e) {
            return Result.rejected(e.getMessage() + "——桶没消耗");
        }

        String src = root.contains("source_dimension", Tag.TAG_STRING)
                ? root.getString("source_dimension") : "";
        String here = level.dimension().location().toString();
        if (!src.isEmpty() && !src.equals(here)) {
            PackUpAndGoMod.LOG.info("cross-dimension place {} -> {} @ {}", src, here, anchor);
        }

        ListTag blocks = root.getList("blocks", Tag.TAG_COMPOUND);
        ListTag entities = root.getList("entities", Tag.TAG_COMPOUND);
        List<String> notices = collectNotices(level, src, here, palette, entities);

        int placed = 0;
        int restoredBe = 0;
        int skipped = 0;
        int outOfWorld = 0;
        List<DeferredPortal> portals = new ArrayList<>();

        for (Tag t : blocks) {
            CompoundTag cell = (CompoundTag) t;
            ListTag rel = cell.getList("pos", Tag.TAG_INT);
            if (rel.size() < 3) {
                skipped++;
                continue;
            }
            BlockPos pos = new BlockPos(
                    anchor.getX() + rel.getInt(0),
                    anchor.getY() + rel.getInt(1),
                    anchor.getZ() + rel.getInt(2));
            int idx = cell.getInt("state");
            if (idx < 0 || idx >= palette.size()) {
                skipped++;
                continue;
            }
            // 尺寸字段和格子对不上(手改过的档)时,探出高度/边界的格子丢掉并说明,不写坏世界。
            if (level.isOutsideBuildHeight(pos) || !level.getWorldBorder().isWithinBounds(pos)) {
                skipped++;
                outOfWorld++;
                continue;
            }
            BlockState state = palette.get(idx);
            CompoundTag nbt = cell.contains("nbt", Tag.TAG_COMPOUND) ? cell.getCompound("nbt") : null;
            restoredBe += writeCell(level, pos, state, nbt);
            placed++;
            if (isPortalBlock(state)) {
                portals.add(new DeferredPortal(pos.immutable(), state, nbt == null ? null : nbt.copy()));
            }
        }
        if (placed == 0 && !blocks.isEmpty()) {
            // 尺寸字段说放得下,但每一格都探出高度或边界(手改过的档)。一格没写,别消耗桶。
            return Result.rejected("一格都没写上(格子超出建筑高度或世界边界)。桶没消耗");
        }
        if (outOfWorld > 0) {
            notices.add(String.format("有 %d 格超出建筑高度或世界边界,没有写入。", outOfWorld));
        }

        // 第二遍只补「第一遍里被原版拆掉」的传送门。还在的门不再写一遍,
        // 同维度没有门、或门好好地留着的家,写格次数和以前一样。
        // 框要是完整的,这一遍能把中途被拆掉的门补回去;框不在桶里的门仍会留不住。
        for (DeferredPortal portal : portals) {
            if (level.getBlockState(portal.pos()).getBlock() == portal.state().getBlock()) {
                continue;
            }
            writeCell(level, portal.pos(), portal.state(), portal.nbt());
        }
        int lostPortals = 0;
        for (DeferredPortal portal : portals) {
            if (level.getBlockState(portal.pos()).getBlock() != portal.state().getBlock()) {
                lostPortals++;
            }
        }
        if (lostPortals > 0) {
            notices.add(String.format(
                    "有 %d 格传送门没留下来(框不在这桶里,或原版在落成时把它拆了)。", lostPortals));
        }

        // 驯服动物放生:随家走第二程。复制模式(默认)必须重生成 UUID——
        // 撞号会损坏存档(拍板 2 红线);移动模式原号随迁(原区实体已 discard)。
        int pets = 0;
        int entityFailed = 0;
        boolean moved = root.getBoolean("moved");
        for (Tag t : entities) {
            CompoundTag e = (CompoundTag) t;
            ResourceLocation id = ResourceLocation.tryParse(e.getString("id"));
            if (id == null) {
                entityFailed++;
                PackUpAndGoMod.LOG.warn("生物 id 读不了: {}", e.getString("id"));
                continue;
            }
            EntityType<?> type = ForgeRegistries.ENTITY_TYPES.getValue(id);
            if (type == null) {
                entityFailed++;
                continue;
            }
            Entity entity;
            try {
                entity = type.create(level);
            } catch (RuntimeException ex) {
                entityFailed++;
                PackUpAndGoMod.LOG.warn("生物创建失败 id={}: {}", id, ex.toString());
                continue;
            }
            if (entity == null) {
                entityFailed++;
                continue;
            }
            try {
                CompoundTag data = e.getCompound("data").copy();
                if (!moved) {
                    data.putUUID("UUID", java.util.UUID.randomUUID());
                }
                // NBT 里的 Pos 是打包世界的绝对坐标,load 之后按锚点重写。
                // 实体自己的 NBT 不带维度字段,不会被送回原维度。
                entity.load(data);
                entity.setPos(
                        anchor.getX() + e.getDouble("rel_x"),
                        anchor.getY() + e.getDouble("rel_y"),
                        anchor.getZ() + e.getDouble("rel_z"));
                if (!level.addFreshEntity(entity)) {
                    entityFailed++;
                    continue;
                }
                pets++;
            } catch (RuntimeException ex) {
                entityFailed++;
                PackUpAndGoMod.LOG.warn("生物放生失败 id={}: {}", id, ex.toString());
            }
        }
        if (entityFailed > 0) {
            notices.add(String.format("有 %d 只生物没放出来(缺模组或数据读不了),方块已经放下。", entityFailed));
        }
        return new Result(placed, restoredBe + pets, skipped, null, List.copyOf(notices));
    }

    /**
     * 按目标维度的规则提醒,不改方块、不改生物 NBT。
     * 床会炸、水不蒸发、猪灵僵尸化这些只看「放到哪」,旧桶没有来源维度也提醒。
     * 传送门的连接方式只在确知是跨维度时说,避免同维度每次放下都刷一行。
     */
    private static List<String> collectNotices(
            ServerLevel level, String src, String here, List<BlockState> palette, ListTag entities) {
        List<String> notes = new ArrayList<>();
        boolean cross = !src.isEmpty() && !src.equals(here);
        if (cross) {
            notes.add(String.format("跨维度放下:装自 %s,现在在 %s。方块和生物按原样写回。",
                    dimensionLabel(src), dimensionLabel(here)));
        }

        boolean ultraWarm = level.dimensionType().ultraWarm();
        boolean water = false;
        boolean wetSponge = false;
        boolean bed = false;
        boolean anchor = false;
        boolean netherPortal = false;
        boolean endPortal = false;
        boolean gateway = false;
        for (BlockState state : palette) {
            Block block = state.getBlock();
            if (state.getFluidState().is(FluidTags.WATER)) {
                water = true;
            }
            if (block == Blocks.WET_SPONGE) {
                wetSponge = true;
            } else if (block instanceof BedBlock) {
                bed = true;
            } else if (block instanceof RespawnAnchorBlock) {
                anchor = true;
            } else if (block instanceof NetherPortalBlock) {
                netherPortal = true;
            } else if (block instanceof EndPortalBlock) {
                endPortal = true;
            } else if (block instanceof EndGatewayBlock) {
                gateway = true;
            }
        }
        if (ultraWarm && water) {
            notes.add("这里是高温维度:水桶里的水会蒸发,但这桶是按方块原样放下的,水不会蒸发。"
                    + "它仍可能流动,碰到岩浆会变成石头。");
        }
        if (ultraWarm && wetSponge) {
            notes.add("湿海绵在高温维度会立刻晾干,并可能吸走周围的水(原版)。其余方块照常放下。");
        }
        if (bed && !level.dimensionType().bedWorks()) {
            notes.add("这个维度里右键床会爆炸,不能睡。床已经原样放下,别去点它。");
        }
        if (anchor && !level.dimensionType().respawnAnchorWorks()) {
            notes.add("这个维度里右键重生锚会爆炸。锚已经原样放下,别去点它。");
        }
        if (cross && netherPortal) {
            notes.add("下界传送门按原版规则连接下界和主世界;框不完整的门可能会被拆掉。");
        }
        if (cross && endPortal) {
            notes.add("末地传送门方块踩上去会按原版传送(末地和主世界之间),在哪个维度放下都一样。");
        }
        if (cross && gateway) {
            notes.add("末地折跃门会按原版把实体传送走。");
        }

        boolean hoglin = false;
        boolean piglin = false;
        boolean strider = false;
        for (Tag tag : entities) {
            if (!(tag instanceof CompoundTag entityTag)) {
                continue;
            }
            switch (entityTag.getString("id")) {
                case "minecraft:hoglin" -> hoglin = true;
                case "minecraft:piglin" -> piglin = true;
                case "minecraft:strider" -> strider = true;
                default -> {
                }
            }
        }
        if (!level.dimensionType().piglinSafe() && (hoglin || piglin)) {
            if (hoglin && piglin) {
                notes.add("猪灵和猪灵兽在这个维度大约 15 秒后会僵尸化(原版)。想保住它们就放回下界。");
            } else if (hoglin) {
                notes.add("猪灵兽在这个维度大约 15 秒后会变成僵尸猪灵兽(原版)。想保住就放回下界。");
            } else {
                notes.add("猪灵在这个维度大约 15 秒后会僵尸化(原版)。想保住就放回下界。");
            }
        }
        if (strider && !ultraWarm) {
            // 发抖看的是脚下有没有岩浆(strider_warm_blocks),不是维度。下界里离开岩浆也会抖。
            notes.add("炽足兽在这个维度不会死。离开岩浆会发抖、变慢(下界里也一样)。想让它们正常走,脚下留岩浆。");
        }
        return notes;
    }

    private static boolean isPortalBlock(BlockState state) {
        Block block = state.getBlock();
        return block instanceof NetherPortalBlock
                || block instanceof EndPortalBlock
                || block instanceof EndGatewayBlock;
    }

    /** @return 成功装回的方块实体数(0 或 1)。 */
    private static int writeCell(ServerLevel level, BlockPos pos, BlockState state, @Nullable CompoundTag nbt) {
        // flag 3 = 邻居更新+客户端同步;采集是全量的,空气格也写(展开时清地形)
        level.setBlock(pos, state, 3);
        if (nbt != null && state.hasBlockEntity()) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be != null) {
                applyBlockEntityData(level, pos, state, nbt);
                return 1;
            }
        }
        return 0;
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
        PackUpAndGoMod.LOG.warn("丢弃未知属性 {}.{}", state.getBlock(), key);
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
    private static void applyBlockEntityData(ServerLevel level, BlockPos pos, BlockState placed, CompoundTag data) {
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
            level.sendBlockUpdated(pos, placed, placed, Block.UPDATE_CLIENTS);
        } catch (RuntimeException e) {
            // 单格数据坏不该让整栋楼停工——与采集端同口径
            PackUpAndGoMod.LOG.warn("方块实体数据装回失败 @ {}: {}", pos, e.toString());
        }
    }
}
