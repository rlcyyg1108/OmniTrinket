package com.rlcyyg1108.omnitrinket.setting;

import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.util.LazyOptional;

import java.util.Optional;

/**
 * 便捷访问玩家设置的静态工具类。
 */
public final class PlayerSettingsHelper
{
    private PlayerSettingsHelper()
    {
    }

    /**
     * 获取玩家的设置数据；若不存在则返回空 Optional。
     */
    public static Optional<PlayerSettings> get(Player player)
    {
        if (player == null)
        {
            return Optional.empty();
        }
        LazyOptional<IPlayerSettings> optional = player.getCapability(PlayerSettingsCapability.PLAYER_SETTINGS);
        return optional.map(IPlayerSettings::getSettings);
    }
}
