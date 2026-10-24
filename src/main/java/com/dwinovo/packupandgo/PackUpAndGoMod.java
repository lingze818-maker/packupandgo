package com.dwinovo.packupandgo;

import net.minecraftforge.fml.common.Mod;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Pack Up & Go（提桶跑路）入口。
 *
 * <p>黄金法则:装进去什么样,放出来就什么样——箱子内容、熔炉燃料、告示牌文字,全保真。
 * v1 范围:方块+容器内容+植物(含作物状态)+驯服动物。v1.1 起已装载的桶可以在另一个维度展开
 * (高度或世界边界放不下就整单拒绝)。明确不做:野生生物、深度 mod 机器保真。
 * 框选的两个角仍必须在同一维度。
 *
 * <p>开发节奏见 dig/提桶跑路-mod设计.md §九;本类 W1 只做骨架认领(游戏 mod 列表能看见),
 * RegionCapture/RegionRestore 随后班次落地。
 */
@Mod(PackUpAndGoMod.MOD_ID)
public final class PackUpAndGoMod {

    public static final String MOD_ID = "packupandgo";
    public static final Logger LOG = LogManager.getLogger(MOD_ID);

    public PackUpAndGoMod() {
        var modBus = net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext.get().getModEventBus();
        com.dwinovo.packupandgo.init.ModItems.register(modBus);
        modBus.addListener(com.dwinovo.packupandgo.init.ModItems::addToCreative);
        com.dwinovo.packupandgo.net.PackNetwork.register();

        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(
                (net.minecraftforge.event.RegisterCommandsEvent e) ->
                        com.dwinovo.packupandgo.command.PackUpCommands.register(e.getDispatcher()));
        if (net.minecraftforge.fml.loading.FMLEnvironment.dist.isClient()) {
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(
                    com.dwinovo.packupandgo.client.SelectionBoxRenderer.class);
        }
        LOG.info("Pack Up & Go 就绪——要搬家的家,先装进桶里。/packup capture <pos1> <pos2> [name]");
    }
}
