package com.dwinovo.packupandgo.init;

import com.dwinovo.packupandgo.PackUpAndGoMod;
import com.dwinovo.packupandgo.item.BucketItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * 物品注册表——四种提桶(档位=尺寸上限)+框选扳手。
 *
 * <p>金桶 Rarity.EPIC:它是空岛搬家档,且拍板了附魔流光(装载指示,W2 后期特效)——
 * 稀有度先给上,流光随装桶行为一起做。星桶旗舰,W1 期配方外不可得。
 */
public final class ModItems {

    private ModItems() {}

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, PackUpAndGoMod.MOD_ID);

    public static final RegistryObject<BucketItem> WOODEN_BUCKET =
            ITEMS.register("wooden_bucket", () -> new BucketItem(new Item.Properties(), 16));
    public static final RegistryObject<BucketItem> IRON_BUCKET =
            ITEMS.register("iron_bucket", () -> new BucketItem(new Item.Properties(), 32));
    public static final RegistryObject<BucketItem> GOLDEN_BUCKET =
            ITEMS.register("golden_bucket", () -> new BucketItem(
                    new Item.Properties().rarity(Rarity.EPIC), 48, true));
    public static final RegistryObject<BucketItem> STAR_BUCKET =
            ITEMS.register("star_bucket", () -> new BucketItem(
                    new Item.Properties().rarity(Rarity.EPIC).fireResistant(), 64, true));
    public static final RegistryObject<com.dwinovo.packupandgo.item.WrenchItem> SELECTION_WRENCH =
            ITEMS.register("selection_wrench",
                    () -> new com.dwinovo.packupandgo.item.WrenchItem(new Item.Properties()));

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
    }

    /** 挂进原版「功能物品」页签(红石机械那一栏)——不单开 tab,W1 少一块配置。 */
    public static void addToCreative(BuildCreativeModeTabContentsEvent e) {
        if (e.getTabKey() == net.minecraft.world.item.CreativeModeTabs.TOOLS_AND_UTILITIES) {
            e.accept(SELECTION_WRENCH);
            e.accept(WOODEN_BUCKET);
            e.accept(IRON_BUCKET);
            e.accept(GOLDEN_BUCKET);
            e.accept(STAR_BUCKET);
        }
    }
}
