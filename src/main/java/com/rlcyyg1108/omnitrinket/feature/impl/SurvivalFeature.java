package com.rlcyyg1108.omnitrinket.feature.impl;

import com.rlcyyg1108.omnitrinket.setting.PlayerSettingsAccess;
import com.rlcyyg1108.omnitrinket.feature.TrinketFeature;
import com.rlcyyg1108.omnitrinket.setting.PlayerSettings;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.SlotContext;

/**
 * 生存模块。
 *
 * 包含：
 * - 完全免伤（含虚空伤害开关）
 * - 水下挖掘不减速
 * - 水下不消耗氧气
 * - 去掉屏幕上的火焰动画
 *
 * 其中"免伤"和"水下挖掘"需要监听 Forge 事件（见 SurvivalEvents），
 * 判定某个玩家是否佩戴本饰品由 CuriosApi 完成；
 * "氧气"和"火焰动画"在 curioTick 中处理。
 */
public class SurvivalFeature implements TrinketFeature
{
    public static final String ID = "survival";

    @Override
    public String getId()
    {
        return ID;
    }

    @Override
    public boolean isEnabled(Player player)
    {
        return PlayerSettingsAccess.bool(player, PlayerSettings.SURVIVAL_ENABLED);
    }

    @Override
    public void curioTick(SlotContext ctx, ItemStack stack, PlayerSettings cfg)
    {
        if (!(ctx.entity() instanceof Player player))
        {
            return;
        }

        // 水下不消耗氧气：持续把空气值补满
        if (PlayerSettingsAccess.bool(player, PlayerSettings.SURVIVAL_NO_OXYGEN))
        {
            int maxAir = player.getMaxAirSupply();
            if (player.getAirSupply() < maxAir)
            {
                player.setAirSupply(maxAir);
            }
        }
    }
}
