package com.dwinovo.packupandgo.net;

import com.dwinovo.packupandgo.PackUpAndGoMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

/**
 * 网络层——只有一条 S2C:框选同步(线框视效的数据源)。
 *
 * <p>服务端是选区的事实源({@link com.dwinovo.packupandgo.capture.SelectionStore}),
 * 框选变化的三个时机(第一角/完成/清空)各发一包;客户端拿到后驱动
 * {@link com.dwinovo.packupandgo.client.SelectionBoxRenderer} 画青/绿线框。
 */
public final class PackNetwork {

    private static final String PROTOCOL = "1";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(PackUpAndGoMod.MOD_ID, "main"),
            () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);

    private PackNetwork() {}

    public static void register() {
        int id = 0;
        CHANNEL.messageBuilder(SelectionSyncPacket.class, id++)
                .encoder(SelectionSyncPacket::encode)
                .decoder(SelectionSyncPacket::decode)
                .consumerMainThread(SelectionSyncPacket::handle)
                .add();
    }

    /** 服务端 → 指定玩家:推送当前选区(second=null 表示只有第一角;cleared=true 表示刚清空,客户端红框渐隐)。 */
    public static void syncSelection(net.minecraft.server.level.ServerPlayer player,
                                      net.minecraft.core.BlockPos first,
                                      net.minecraft.core.BlockPos second,
                                      boolean cleared) {
        CHANNEL.send(net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> player),
                new SelectionSyncPacket(first, second, cleared));
    }
}
