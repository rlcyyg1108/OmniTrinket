package com.rlcyyg1108.omnitrinket.setting;

import com.rlcyyg1108.omnitrinket.OmniTrinket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 玩家设置的 Capability 注册与生命周期管理。
 *
 * 负责：
 * - 把 Capability 注册到 Forge 的能力系统
 * - 玩家创建时挂载 Capability
 * - 玩家死亡重生 / 换维度时复制设置
 */
@Mod.EventBusSubscriber(modid = OmniTrinket.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class PlayerSettingsHandler
{
    private static final ResourceLocation CAP_ID =
            ResourceLocation.fromNamespaceAndPath(OmniTrinket.MODID, "player_settings");

    /**
     * 在 MOD 事件总线上注册 Capability。
     */
    @SubscribeEvent
    public static void registerCapabilities(RegisterCapabilitiesEvent event)
    {
        event.register(IPlayerSettings.class);
    }

    /**
     * 游戏事件总线（注意用的是 Forge 事件总线，不是 Mod 总线）。
     */
    @Mod.EventBusSubscriber(modid = OmniTrinket.MODID)
    public static class ForgeEvents
    {
        /**
         * 玩家实体创建时挂载设置 Capability。
         */
        @SubscribeEvent
        public static void attachCapabilities(AttachCapabilitiesEvent<Entity> event)
        {
            if (event.getObject() instanceof Player)
            {
                PlayerSettingsCapability provider = new PlayerSettingsCapability();
                event.addCapability(CAP_ID, provider);
                // 让 LazyOptional 在能力失效时自动清理
                event.addListener(provider::invalidate);
            }
        }

        /**
         * 玩家死亡重生或从末地返回时，把设置复制到新实体上。
         */
        @SubscribeEvent
        public static void clonePlayer(PlayerEvent.Clone event)
        {
            // 旧实体在死亡时 capability 已失效，需要先恢复
            event.getOriginal().reviveCaps();
            event.getOriginal().getCapability(PlayerSettingsCapability.PLAYER_SETTINGS).ifPresent(oldSettings ->
                    event.getEntity().getCapability(PlayerSettingsCapability.PLAYER_SETTINGS).ifPresent(newSettings ->
                    {
                        net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
                        oldSettings.getSettings().saveNBTData(tag);
                        newSettings.getSettings().loadNBTData(tag);
                    }));
            event.getOriginal().invalidateCaps();
        }
    }
}
