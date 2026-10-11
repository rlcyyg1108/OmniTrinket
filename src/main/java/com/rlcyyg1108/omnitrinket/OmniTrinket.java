package com.rlcyyg1108.omnitrinket;

import com.rlcyyg1108.omnitrinket.feature.FeatureRegistry;
import com.rlcyyg1108.omnitrinket.item.OmniTrinketItem;
import com.rlcyyg1108.omnitrinket.network.NetworkHandler;
import com.mojang.logging.LogUtils;
import com.rlcyyg1108.omnitrinket.client.FlightHudOverlay;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.slf4j.Logger;

// The value here should match an entry in the META-INF/mods.toml file
@Mod(OmniTrinket.MODID)
public class OmniTrinket
{
    // Define mod id in a common place for everything to reference
    public static final String MODID = "omnitrinket";
    // Directly reference a slf4j logger
    private static final Logger LOGGER = LogUtils.getLogger();
    // Create a Deferred Register to hold Items which will all be registered under the "omnitrinket" namespace
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MODID);
    // Create a Deferred Register to hold CreativeModeTabs which will all be registered under the "omnitrinket" namespace
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);

    // 全能饰品：id "omnitrinket:omni_trinket"
    public static final RegistryObject<Item> OMNI_TRINKET = ITEMS.register("omni_trinket", OmniTrinketItem::new);

    // 创造模式物品栏标签页：id "omnitrinket:main"
    public static final RegistryObject<CreativeModeTab> OMNI_TAB = CREATIVE_MODE_TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.omnitrinket.main"))
            .icon(() -> new ItemStack(OMNI_TRINKET.get()))
            .displayItems((parameters, output) -> output.accept(OMNI_TRINKET.get()))
            .build());

    public OmniTrinket(FMLJavaModLoadingContext context)
    {
        IEventBus modEventBus = context.getModEventBus();

        // Register the commonSetup method for modloading
        modEventBus.addListener(this::commonSetup);

        // Register the Deferred Register to the mod event bus so items get registered
        ITEMS.register(modEventBus);
        // Register the Deferred Register to the mod event bus so tabs get registered
        CREATIVE_MODE_TABS.register(modEventBus);

        // 初始化功能注册表（各功能在 FeatureRegistry 中集中注册）
        FeatureRegistry.init();

        // Register ourselves for server and other game events we are interested in
        MinecraftForge.EVENT_BUS.register(this);

        // Register our mod's ForgeConfigSpec so that Forge can create and load the config file for us
        context.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }

    private void commonSetup(final FMLCommonSetupEvent event)
    {
        // 网络包注册需要在线程安全的阶段进行
        event.enqueueWork(NetworkHandler::register);
        LOGGER.info("OmniTrinket common setup");
    }

    // You can use SubscribeEvent and let the Event Bus discover methods to call
    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event)
    {
        // Do something when the server starts
        LOGGER.info("OmniTrinket: server starting");
    }

    // You can use EventBusSubscriber to automatically register all static methods in the class annotated with @SubscribeEvent
    @Mod.EventBusSubscriber(modid = MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ClientModEvents
    {
        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event)
        {
            LOGGER.info("OmniTrinket client setup");
            LOGGER.info("MINECRAFT NAME >> {}", Minecraft.getInstance().getUser().getName());

            // 档位 HUD 浮层（按 X 切档时短暂显示新档位）
            MinecraftForge.EVENT_BUS.addListener(OmniTrinket::onRenderGuiOverlay);
        }
    }

    /** GUI 渲染层：飞行档位 HUD */
    private static void onRenderGuiOverlay(RenderGuiOverlayEvent.Pre event)
    {
        if (event.getOverlay() != VanillaGuiOverlay.HOTBAR.type())
        {
            return;
        }
        FlightHudOverlay.render(event.getGuiGraphics());
    }
}
