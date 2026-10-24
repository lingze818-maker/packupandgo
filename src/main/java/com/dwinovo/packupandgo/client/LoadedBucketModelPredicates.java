package com.dwinovo.packupandgo.client;

import com.dwinovo.packupandgo.PackUpAndGoMod;
import com.dwinovo.packupandgo.init.ModItems;
import com.dwinovo.packupandgo.item.BucketData;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.registries.RegistryObject;

import java.util.List;

/**
 * 装载状态 → 模型切换的谓词注册（2026-10-16 灵泽点名的差分贴图）。
 *
 * <p>为四种提桶各挂一个 {@code packupandgo:loaded} 谓词：桶 NBT 里有
 * {@code packup} 数据（装了家）时返回 1.0，模型 JSON 的 override 据此
 * 切到 {@code xxx_bucket_loaded} 贴图——空桶朴素、装载桶桶口见家，
 * 金/星桶再叠流光，三层信号一眼分明。
 *
 * <p>纯客户端注册；谓词读 NBT 是只读操作，主线程安全。
 */
@Mod.EventBusSubscriber(modid = PackUpAndGoMod.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class LoadedBucketModelPredicates {

    private static final List<RegistryObject<? extends Item>> BUCKETS = List.of(
            ModItems.WOODEN_BUCKET, ModItems.IRON_BUCKET,
            ModItems.GOLDEN_BUCKET, ModItems.STAR_BUCKET);

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            for (RegistryObject<? extends Item> ro : BUCKETS) {
                ResourceLocation id = new ResourceLocation(PackUpAndGoMod.MOD_ID, ro.getId().getPath() + "_loaded_predicate");
                ItemProperties.register(ro.get(), id,
                        (stack, level, entity, seed) ->
                                BucketData.isLoaded(stack) ? 1.0F : 0.0F);
            }
            PackUpAndGoMod.LOG.info("[packupandgo] loaded-bucket model predicates registered (4)");
        });
    }

    private LoadedBucketModelPredicates() {}
}
