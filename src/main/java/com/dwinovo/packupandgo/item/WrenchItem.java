package com.dwinovo.packupandgo.item;

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
 * 框选扳手——潜行右键方块定角(两次成框),普通右键清空重来。
 *
 * <p>状态住 {@link SelectionStore}(服务端);线框预览渲染是客户端视效,W2 后期补——
 * 本刀先把「圈得出、报得准」的数据流走通。反馈全走聊天栏:每一步都有人话。
 */
public class WrenchItem extends Item {

    public WrenchItem(Properties props) {
        super(props);
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Level level = ctx.getLevel();
        Player player = ctx.getPlayer();
        if (player == null) {
            return InteractionResult.PASS;
        }
        // 视效/提示双端都会走,状态只改服务端一份
        if (level.isClientSide()) {
            return ctx.getPlayer() != null && ctx.getPlayer().isShiftKeyDown()
                    ? InteractionResult.SUCCESS : InteractionResult.PASS;
        }
        BlockPos pos = ctx.getClickedPos();

        if (!player.isShiftKeyDown()) {
            // 普通右键:清空——圈错了/不想圈了的逃生门
            SelectionStore.clear(player.getUUID());
            com.dwinovo.packupandgo.net.PackNetwork.syncSelection(
                    (net.minecraft.server.level.ServerPlayer) player, null, null, true);
            feedback(player, "框选已清空", ChatFormatting.GRAY);
            return InteractionResult.CONSUME;
        }

        SelectionStore.Selection cur = SelectionStore.get(player.getUUID());
        if (cur == null || cur.complete()) {
            // 新框选的第一角(或覆盖旧框选)
            SelectionStore.setFirst(player.getUUID(), pos, level.dimension().location());
            com.dwinovo.packupandgo.net.PackNetwork.syncSelection(
                    (net.minecraft.server.level.ServerPlayer) player, pos, null, false);
            feedback(player, "第一角: " + pos.toShortString() + "(再潜行右键定对角)", ChatFormatting.AQUA);
            return InteractionResult.CONSUME;
        }

        // 第二角
        if (!SelectionStore.setSecond(player.getUUID(), pos, level.dimension().location())) {
            feedback(player, "跨维度了——框选作废,重新从第一角开始", ChatFormatting.RED);
            SelectionStore.clear(player.getUUID());
            com.dwinovo.packupandgo.net.PackNetwork.syncSelection(
                    (net.minecraft.server.level.ServerPlayer) player, null, null, true);
            return InteractionResult.CONSUME;
        }
        SelectionStore.Selection done = SelectionStore.get(player.getUUID());
        com.dwinovo.packupandgo.net.PackNetwork.syncSelection(
                (net.minecraft.server.level.ServerPlayer) player, done.first(), done.second(), false);
        feedback(player, String.format("框选完成: %d×%d×%d ——拿提桶对任意一角右键装桶",
                done.sizeX(), done.sizeY(), done.sizeZ()), ChatFormatting.GREEN);
        return InteractionResult.CONSUME;
    }

    private static void feedback(Player player, String text, ChatFormatting color) {
        player.displayClientMessage(Component.literal("[扳手] " + text).withStyle(color), false);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level,
                                List<Component> lines, TooltipFlag flag) {
        lines.add(Component.literal("潜行右键×2 圈定对角 · 右键清空").withStyle(ChatFormatting.DARK_GRAY));
    }
}
