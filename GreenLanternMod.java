package com.example.greenlantern;

import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

@Mod(GreenLanternMod.MODID)
public class GreenLanternMod {
    public static final String MODID = "greenlantern";

    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, MODID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MODID);
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, MODID);

    public static final RegistryObject<Block> BATTERY = BLOCKS.register("willpower_battery",
            () -> new BatteryBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GREEN)
                    .strength(3.0f, 6.0f).lightLevel(s -> 13).noOcclusion().sound(SoundType.METAL)));
    public static final RegistryObject<Item> BATTERY_ITEM = ITEMS.register("willpower_battery",
            () -> new BlockItem(BATTERY.get(), new Item.Properties().rarity(Rarity.RARE)));
    public static final RegistryObject<EntityType<JetEntity>> JET = ENTITIES.register("jet",
            () -> EntityType.Builder.<JetEntity>of(JetEntity::new, MobCategory.MISC)
                    .sized(2.2f, 1.0f).clientTrackingRange(10).updateInterval(1).build("jet"));
    public static final RegistryObject<EntityType<ColosoEntity>> COLOSO = ENTITIES.register("coloso",
            () -> EntityType.Builder.<ColosoEntity>of(ColosoEntity::new, MobCategory.MISC)
                    .sized(3.5f, 10.5f).clientTrackingRange(16).updateInterval(1).build("coloso"));

    public static final RegistryObject<Item> POWER_RING = ITEMS.register("power_ring",
            () -> new PowerRingItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant()));

    public GreenLanternMod() {
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        BLOCKS.register(bus);
        ITEMS.register(bus);
        ENTITIES.register(bus);
        bus.addListener(this::commonSetup);
        bus.addListener(this::addCreative);
        MinecraftForge.EVENT_BUS.register(new RingEvents());
    }

    private void commonSetup(FMLCommonSetupEvent e) {
        e.enqueueWork(ModNetwork::register);
    }

    private void addCreative(BuildCreativeModeTabContentsEvent e) {
        if (e.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            e.accept(POWER_RING);
            e.accept(BATTERY_ITEM);
        }
    }
}
