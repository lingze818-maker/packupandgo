package com.dwinovo.packupandgo.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.joml.Vector3f;

/**
 * 选区线框渲染——W2-3 挂账的视效补完。
 *
 * <p>三态:第一角=青色 1×1×1 小框;完整选区=绿色 AABB 框;刚清空=红色渐隐 800ms
 * (聊天栏文字反馈之外,给眼睛一个「框没了」的确认)。
 *
 * <p>画法:半透明阶段后手绘 12 条边(vanilla 结构方块同款观感,线宽 1px 不抢戏)。
 */
public final class SelectionBoxRenderer {

    private SelectionBoxRenderer() {}

    /** 两角(格坐标)之间的完整方块 AABB——max 面要 +1 才框住整格。 */
    private static AABB boxBetween(BlockPos a, BlockPos b) {
        return new AABB(
                Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ()),
                Math.max(a.getX(), b.getX()) + 1, Math.max(a.getY(), b.getY()) + 1, Math.max(a.getZ(), b.getZ()) + 1);
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }
        ClientSelectionCache.tickFade();
        ClientSelectionCache.State state = ClientSelectionCache.state();
        if (state == ClientSelectionCache.State.EMPTY) {
            return;
        }

        BlockPos first = ClientSelectionCache.first();
        BlockPos second = ClientSelectionCache.second();
        if (first == null) {
            return;
        }

        float r, g, b, a;
        AABB box;
        switch (state) {
            case FIRST_ONLY -> {
                box = boxBetween(first, first);
                r = 0.2f; g = 0.9f; b = 1.0f; a = 0.7f;   // 青:第一角
            }
            case COMPLETE -> {
                box = boxBetween(first, second);
                r = 0.3f; g = 1.0f; b = 0.4f; a = 0.7f;   // 绿:框选完成
            }
            case FADING -> {
                BlockPos other = second != null ? second : first;
                box = boxBetween(first, other);
                float t = ClientSelectionCache.fadeProgress();
                r = 1.0f; g = 0.25f; b = 0.25f; a = 0.7f * (1f - t);  // 红:渐隐
            }
            default -> {
                return;
            }
        }

        PoseStack pose = e.getPoseStack();
        Vector3f cam = e.getCamera().getPosition().toVector3f();
        pose.pushPose();
        pose.translate(-cam.x, -cam.y, -cam.z);

        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();  // 隔墙也看得见——搬家框选的可用性优先
        RenderSystem.lineWidth(2.0f);

        var buffer = net.minecraft.client.renderer.MultiBufferSource.immediate(
                com.mojang.blaze3d.vertex.Tesselator.getInstance().getBuilder());
        var consumer = buffer.getBuffer(net.minecraft.client.renderer.RenderType.lines());
        // RenderType.lines 顶点格式带法线——用 LevelRenderer.renderLineBox 免手写
        net.minecraft.client.renderer.LevelRenderer.renderLineBox(
                pose, consumer, box.inflate(0.002), r, g, b, a);
        buffer.endBatch(net.minecraft.client.renderer.RenderType.lines());

        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
        pose.popPose();
    }
}
