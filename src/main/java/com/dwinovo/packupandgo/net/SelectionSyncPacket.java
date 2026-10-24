package com.dwinovo.packupandgo.net;

import com.dwinovo.packupandgo.client.ClientSelectionCache;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * S2C 框选同步包。first=null 且 cleared=true → 客户端进入红色渐隐;
 * second=null → 只有第一角(青色小框);second 非空 → 完整选区(绿框)。
 */
public class SelectionSyncPacket {

    private final BlockPos first;
    private final BlockPos second;
    private final boolean cleared;

    public SelectionSyncPacket(BlockPos first, BlockPos second, boolean cleared) {
        this.first = first;
        this.second = second;
        this.cleared = cleared;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBoolean(first != null);
        if (first != null) {
            buf.writeBlockPos(first);
        }
        buf.writeBoolean(second != null);
        if (second != null) {
            buf.writeBlockPos(second);
        }
        buf.writeBoolean(cleared);
    }

    public static SelectionSyncPacket decode(FriendlyByteBuf buf) {
        BlockPos first = buf.readBoolean() ? buf.readBlockPos() : null;
        BlockPos second = buf.readBoolean() ? buf.readBlockPos() : null;
        boolean cleared = buf.readBoolean();
        return new SelectionSyncPacket(first, second, cleared);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> ClientSelectionCache.update(first, second, cleared));
        ctx.get().setPacketHandled(true);
    }
}
