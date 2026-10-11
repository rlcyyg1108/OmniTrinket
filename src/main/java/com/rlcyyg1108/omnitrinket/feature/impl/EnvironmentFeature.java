package com.rlcyyg1108.omnitrinket.feature.impl;

import com.rlcyyg1108.omnitrinket.Config;
import com.rlcyyg1108.omnitrinket.feature.TrinketFeature;
import com.rlcyyg1108.omnitrinket.setting.PlayerSettings;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.SlotContext;

/**
 * 环境模块。
 *
 * 只包含两个开关：
 * - 晴天锁定（强制清空天气并阻止天气更替）
 * - 终日为白（把时间冻结在 6000 tick = 正午）
 *
 * 两项都是**世界级**操作，仅对佩戴者所在维度生效。
 *
 * === 不做权限校验 ===
 * 本模组假定使用者在单人世界里游玩、或就是自己服务器的服主，
 * 因而不检查 OP 权限 —— 戴上饰品、开关打开即生效。
 *
 * 实际逻辑见 {@link EnvironmentEvents}（服务端，每 20 tick 一次就够）。
 */
public class EnvironmentFeature implements TrinketFeature
{
    public static final String ID = "environment";

    @Override
    public String getId()
    {
        return ID;
    }

    @Override
    public boolean isEnabled()
    {
        // 环境模块没有独立总开关：两个子项各自开关即可，
        // 这里返回"是否有任一子项开启"，用于 tooltip 的状态显示。
        return Config.COMMON.lockWeatherClear.get() || Config.COMMON.perpetualDay.get();
    }

    @Override
    public void curioTick(SlotContext ctx, ItemStack stack, PlayerSettings cfg)
    {
        // 状态由 EnvironmentEvents 的 ServerTickEvent 驱动（每 20 tick 一次），
        // 不放在 curioTick 里：curioTick 只在佩戴时触发，摘下后就无法恢复世界状态。
    }
}
