package com.dwinovo.packupandgo.client;

import net.minecraft.core.BlockPos;

/**
 * 客户端选区缓存——网络包的唯一消费者,渲染器的唯一数据源。
 *
 * <p>状态机:CLEARED(空)/ FIRST_ONLY(青小框)/ COMPLETE(绿框)/
 * FADING(刚清空,保留旧框红色渐隐 ~800ms)。
 */
public final class ClientSelectionCache {

    public enum State { EMPTY, FIRST_ONLY, COMPLETE, FADING }

    private static State state = State.EMPTY;
    private static BlockPos first;
    private static BlockPos second;
    private static long fadeStartMs;

    private ClientSelectionCache() {}

    public static void update(BlockPos newFirst, BlockPos newSecond, boolean cleared) {
        if (cleared) {
            state = (first != null || second != null) ? State.FADING : State.EMPTY;
            fadeStartMs = System.currentTimeMillis();
            return;
        }
        first = newFirst;
        second = newSecond;
        state = newSecond != null ? State.COMPLETE : State.FIRST_ONLY;
    }

    public static State state() {
        return state;
    }

    public static BlockPos first() {
        return first;
    }

    public static BlockPos second() {
        return second;
    }

    /** 渐隐进度 0(刚清空)→1(该消失);只在 FADING 态有意义。 */
    public static float fadeProgress() {
        return Math.min(1f, (System.currentTimeMillis() - fadeStartMs) / 800f);
    }

    /** 渐隐到期后归位到 EMPTY(渲染器每帧调)。 */
    public static void tickFade() {
        if (state == State.FADING && fadeProgress() >= 1f) {
            state = State.EMPTY;
            first = null;
            second = null;
        }
    }
}
