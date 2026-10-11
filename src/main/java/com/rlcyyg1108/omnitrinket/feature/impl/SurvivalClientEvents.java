package com.rlcyyg1108.omnitrinket.feature.impl;

import com.rlcyyg1108.omnitrinket.setting.PlayerSettingsAccess;
import com.rlcyyg1108.omnitrinket.setting.PlayerSettings;
import com.rlcyyg1108.omnitrinket.OmniTrinket;
import com.rlcyyg1108.omnitrinket.util.TrinketHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderBlockScreenEffectEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 生存模块的客户端渲染处理。
 *
 * 目前只负责"去除着火屏幕特效"（屏幕上的火焰遮罩）。
 * 使用 Forge 官方的 RenderBlockScreenEffectEvent（OverlayType.FIRE），
 * 无需 Mixin 即可取消火焰遮罩渲染。
 *
 * 注意：这里只影响视觉遮罩，不改变玩家真实的着火状态。
 */
@Mod.EventBusSubscriber(modid = OmniTrinket.MODID, value = Dist.CLIENT)
public class SurvivalClientEvents
{
    @SubscribeEvent
    public static void onRenderBlockScreenEffect(RenderBlockScreenEffectEvent event)
    {
        if (event.getOverlayType() != RenderBlockScreenEffectEvent.OverlayType.FIRE)
        {
            return;
        }
        // 每玩家开关：本事件只在客户端触发，所以读的是本机镜像
        Player player = event.getPlayer();
        if (!PlayerSettingsAccess.bool(player, PlayerSettings.SURVIVAL_NO_FIRE_OVERLAY))
        {
            return;
        }
        if (TrinketHelper.isEquipped(player))
        {
            event.setCanceled(true);
        }
    }
}
