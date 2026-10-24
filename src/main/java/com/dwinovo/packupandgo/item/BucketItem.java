package com.dwinovo.packupandgo.item;

import com.dwinovo.packupandgo.capture.RegionCapture;
import com.dwinovo.packupandgo.capture.RegionRestore;
import com.dwinovo.packupandgo.capture.SelectionStore;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/**
 * 提桶——物品本体。档位=尺寸上限(构造入参),装载状态住在 NBT(见 {@link BucketData})。
 *
 * <p>右键是它的全部语言:空桶对着框选好的家右键=装桶(采集整个区域进 NBT),
 * 已装载的桶对着地面右键=放家(原样展开)。装与放都消耗桶(设计 §五:桶是压缩
 * 空间的容器,展开即释放——防无限白嫖)。
 *
 * <p>金桶的紫流光=装载指示(拍板):空桶无光,装了家的桶亮。木桶朴素/铁桶实用
 * 不亮;星桶 10-10 灵泽拍板同样装载流光(天青渐变+白星,装了家发亮正配"海里有家")。
 */
public class BucketItem extends Item {

    private final int maxSize;
    private final boolean glowWhenLoaded;

    public BucketItem(Properties props, int maxSize) {
        this(props, maxSize, false);
    }

    public BucketItem(Properties props, int maxSize, boolean glowWhenLoaded) {
        super(props);
        this.maxSize = maxSize;
        this.glowWhenLoaded = glowWhenLoaded;
    }

    /** 该桶能装下的最大边长(格)。 */
    public int maxSize() {
        return maxSize;
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return glowWhenLoaded && BucketData.isLoaded(stack);
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Level level = ctx.getLevel();
        Player player = ctx.getPlayer();
        if (player == null) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide()) {
            // 客户端只管发包,成败由服务端裁决并同步反馈(2026-10-16 修复:
            // 旧版空桶在此返回 PASS——客户端不发右键包,服务端装桶逻辑
            // 永远不执行,表现为"拿着桶右键毫无反应")
            return InteractionResult.SUCCESS;
        }
        // 诊断信标(2026-10-16):服务端收到右键必回一声——分叉定位客户端/服务端
        feedback(player, "[诊断] 服务端收到右键, 装载=" + BucketData.isLoaded(ctx.getItemInHand())
                + ", 框选=" + (SelectionStore.get(player.getUUID()) != null
                        && SelectionStore.get(player.getUUID()).complete() ? "有效" : "无"), ChatFormatting.YELLOW);
        return BucketData.isLoaded(ctx.getItemInHand())
                ? placeHome(ctx, player)
                : packHome(ctx, player);
    }

    /** 空桶右键:把框选好的家装进桶。 */
    private InteractionResult packHome(UseOnContext ctx, Player player) {
        ItemStack stack = ctx.getItemInHand();
        SelectionStore.Selection sel = SelectionStore.get(player.getUUID());
        if (sel == null || !sel.complete()) {
            feedback(player, "还没框选——先用扳手潜行右键定两个对角", ChatFormatting.RED);
            return InteractionResult.CONSUME;
        }
        if (!sel.dimension().equals(ctx.getLevel().dimension().location())) {
            feedback(player, "框选在另一个维度——去那边装,或重新圈", ChatFormatting.RED);
            return InteractionResult.CONSUME;
        }
        int edge = Math.max(sel.sizeX(), Math.max(sel.sizeY(), sel.sizeZ()));
        if (edge > maxSize) {
            feedback(player, String.format("家是 %d³,这只桶上限 %d³——换大桶或分几段装",
                    edge, maxSize), ChatFormatting.RED);
            return InteractionResult.CONSUME;
        }

        RegionCapture.Result r = RegionCapture.capture(
                (net.minecraft.server.level.ServerLevel) ctx.getLevel(), sel.first(), sel.second());
        String name = String.format("%d×%d×%d", sel.sizeX(), sel.sizeY(), sel.sizeZ());
        // 拍板 2:默认复制模式(原区域的家原样保留);shift=移动模式,装走后原地清空
        boolean moved = player.isShiftKeyDown();
        try {
            BucketData.loadInto(stack, r.root(), name, moved,
                    (net.minecraft.server.level.ServerLevel) ctx.getLevel());
        } catch (java.io.IOException e) {
            feedback(player, "家太大,落盘失败(磁盘写入出错)——家还在原地,桶没消耗,再试一次", ChatFormatting.RED);
            return InteractionResult.CONSUME;
        }
        stack.shrink(1);
        if (moved) {
            clearRegion((net.minecraft.server.level.ServerLevel) ctx.getLevel(), sel);
        }
        SelectionStore.clear(player.getUUID());
        com.dwinovo.packupandgo.net.PackNetwork.syncSelection(
                (net.minecraft.server.level.ServerPlayer) player, null, null, true);
        feedback(player, String.format("装桶完成:%s · %d 方块 · %d 方块实体——家都在桶里了%s",
                name, r.blockCount(), r.blockEntityCount(),
                moved ? "(原地已清空,一路走好)" : "(原来的家原样保留,复制模式)"),
                ChatFormatting.GREEN);
        return InteractionResult.CONSUME;
    }

    /** 移动模式的收尾:选区清成空气,驯服动物一并 discard(它们已经在桶里了)。 */
    private static void clearRegion(net.minecraft.server.level.ServerLevel level,
                                     SelectionStore.Selection sel) {
        BlockPos min = new BlockPos(
                Math.min(sel.first().getX(), sel.second().getX()),
                Math.min(sel.first().getY(), sel.second().getY()),
                Math.min(sel.first().getZ(), sel.second().getZ()));
        BlockPos max = new BlockPos(
                Math.max(sel.first().getX(), sel.second().getX()),
                Math.max(sel.first().getY(), sel.second().getY()),
                Math.max(sel.first().getZ(), sel.second().getZ()));
        net.minecraft.world.level.block.state.BlockState air =
                net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            level.setBlock(pos, air, 3);
        }
        net.minecraft.world.phys.AABB box = new net.minecraft.world.phys.AABB(min, max.offset(1, 1, 1));
        for (net.minecraft.world.entity.TamableAnimal pet : level.getEntitiesOfClass(
                net.minecraft.world.entity.TamableAnimal.class, box,
                pet -> pet.getOwnerUUID() != null)) {
            pet.discard();
        }
    }

    /** 已装载的桶右键地面:放下这个家。锚点=点击面上方那格,以最小角落位。 */
    private InteractionResult placeHome(UseOnContext ctx, Player player) {
        ItemStack stack = ctx.getItemInHand();
        net.minecraft.nbt.CompoundTag pack = BucketData.getPack(stack,
                (net.minecraft.server.level.ServerLevel) ctx.getLevel());
        if (pack == null) {
            if (BucketData.isExternalLost()) {
                feedback(player, "这桶家的大件数据丢失了(存档 data/packupandgo/ 被动过)——桶没消耗,数据没法救回", ChatFormatting.RED);
            } else {
                feedback(player, "桶里的数据读不出来——再试一次,还失败就别把这桶丢进岩浆", ChatFormatting.RED);
            }
            return InteractionResult.CONSUME;
        }
        BlockPos anchor = ctx.getClickedPos().relative(ctx.getClickedFace());
        RegionRestore.Result r = RegionRestore.restore(
                (net.minecraft.server.level.ServerLevel) ctx.getLevel(), anchor, pack);
        consumeFilledBucket(player, stack);
        feedback(player, String.format("家已放下 @ %s:写入 %d 格(方块实体 %d)——桶已消耗,不能再放一次",
                anchor.toShortString(), r.placedBlocks(), r.restoredBlockEntities()),
                ChatFormatting.GREEN);
        return InteractionResult.CONSUME;
    }

    /**
     * 展开成功后消耗这只已装载的桶。
     *
     * <p>生存模式 {@code shrink} 会留下:叠放时只少一只,其余还装着同一份家。
     * 创造模式则不行——{@code ServerPlayerGameMode#useItemOn} 在使用结束后
     * 把「调用前那只栈」的数量设回去,shrink 会被撤销,而 Forge 会把使用后的
     * NBT 写回同一只栈。载荷还在就能无限展开。所以创造模式(以及最后一只
     * 被 shrink 到空)要卸掉 {@code packup}:数量就算被加回,也只是空桶。
     *
     * <p>改的是玩家手里的那只栈本身。Forge 若取消多方块放置,会把数量和 NBT
     * 滚回使用前——家还在桶里,块也退回,不会白丢。
     */
    private static void consumeFilledBucket(Player player, ItemStack stack) {
        stack.shrink(1);
        if (stack.isEmpty() || creativeRestoresCount(player)) {
            BucketData.clearPack(stack);
        }
    }

    /** 与 {@code ServerPlayerGameMode#isCreative} 同一条件:创造模式会恢复物品数量。 */
    private static boolean creativeRestoresCount(Player player) {
        return player instanceof net.minecraft.server.level.ServerPlayer serverPlayer
                && serverPlayer.gameMode.isCreative();
    }

    private static void feedback(Player player, String text, ChatFormatting color) {
        player.displayClientMessage(Component.literal("[提桶] " + text).withStyle(color), false);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level,
                                List<Component> lines, TooltipFlag flag) {
        if (BucketData.isLoaded(stack)) {
            lines.add(Component.literal("装载: " + BucketData.getPackName(stack))
                    .withStyle(ChatFormatting.GOLD));
            lines.add(Component.literal(String.format("%s · %d 方块 · %d 方块实体",
                            BucketData.sizeString(stack),
                            BucketData.stat(stack, "blocks"),
                            BucketData.stat(stack, "block_entities")))
                    .withStyle(ChatFormatting.GRAY));
            lines.add(Component.literal("右键地面放下这个家,放下后这只桶即消耗")
                    .withStyle(ChatFormatting.DARK_GRAY));
        } else {
            lines.add(Component.literal(String.format("空桶(上限 %d³)——框选后对家右键装桶",
                            maxSize))
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
    }
}
