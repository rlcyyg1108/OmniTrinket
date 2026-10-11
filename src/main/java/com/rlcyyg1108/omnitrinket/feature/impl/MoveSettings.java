package com.rlcyyg1108.omnitrinket.feature.impl;

import com.rlcyyg1108.omnitrinket.network.ClientSettingsCache;
import com.rlcyyg1108.omnitrinket.setting.PlayerSettings;
import com.rlcyyg1108.omnitrinket.setting.PlayerSettingsHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;

/**
 * 「移动」模块两个倍率的<b>双端</b>读取入口。
 *
 * <h3>为什么需要它</h3>
 * <p>{@code PlayerSettings} 挂在 Forge Capability 上，而 capability 只在<b>服务端</b>被写入
 * （{@code PlayerSettingsHandler} 用 {@code AttachCapabilitiesEvent<Entity>}，
 * 客户端玩家同样会被挂上一份，但那份永远是初始默认值，同步包只写
 * {@link ClientSettingsCache}、从不回写 capability）。</p>
 *
 * <p>所以凡是"客户端也要用到"的每玩家数据，都必须像这样分端取值。
 * 之前移速倍率 / 跳跃倍率放在 {@code Config.COMMON} 里看起来更简单，
 * 但那是<b>另一个更隐蔽的坑</b>：Forge 的 COMMON 配置只在登录时从服务端同步一次，
 * 设置界面在运行时改它<b>只改客户端</b>。跳跃是"客户端预测 + 服务端验证"的，
 * 两端按不同倍率起跳会被服务端以位置异常为由拽回 —— 表现就是"改了没反应"。</p>
 */
public final class MoveSettings
{
    private MoveSettings()
    {
    }

    /** 跳跃高度倍率（跳跃高度 = 原版 × 该值） */
    public static double jumpHeightMultiplier(Player player)
    {
        if (player == null)
        {
            return PlayerSettings.DEFAULT_JUMP_HEIGHT_MULTIPLIER;
        }
        if (player.level().isClientSide())
        {
            Double value = DistExecutor.unsafeCallWhenOn(Dist.CLIENT,
                    () -> ClientSettingsCache::jumpHeightMultiplier);
            return value == null ? PlayerSettings.DEFAULT_JUMP_HEIGHT_MULTIPLIER : value;
        }
        PlayerSettings settings = PlayerSettingsHelper.get(player).orElse(null);
        return settings == null
                ? PlayerSettings.DEFAULT_JUMP_HEIGHT_MULTIPLIER
                : settings.getJumpHeightMultiplier();
    }

    /** 移动速度倍率（走路 / 奔跑速度 = 原版 × 该值） */
    public static double moveSpeedMultiplier(Player player)
    {
        if (player == null)
        {
            return PlayerSettings.DEFAULT_MOVE_SPEED_MULTIPLIER;
        }
        if (player.level().isClientSide())
        {
            Double value = DistExecutor.unsafeCallWhenOn(Dist.CLIENT,
                    () -> ClientSettingsCache::moveSpeedMultiplier);
            return value == null ? PlayerSettings.DEFAULT_MOVE_SPEED_MULTIPLIER : value;
        }
        PlayerSettings settings = PlayerSettingsHelper.get(player).orElse(null);
        return settings == null
                ? PlayerSettings.DEFAULT_MOVE_SPEED_MULTIPLIER
                : settings.getMoveSpeedMultiplier();
    }
}
