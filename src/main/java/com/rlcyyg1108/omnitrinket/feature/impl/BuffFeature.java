package com.rlcyyg1108.omnitrinket.feature.impl;

import com.rlcyyg1108.omnitrinket.Config;
import com.rlcyyg1108.omnitrinket.client.ClientTooltips;
import com.rlcyyg1108.omnitrinket.feature.TrinketFeature;
import com.rlcyyg1108.omnitrinket.setting.PlayerSettings;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import org.jetbrains.annotations.Nullable;
import top.theillusivec4.curios.api.SlotContext;

/**
 * 增益模块。
 *
 * 包含：
 * - 夜视（药水效果，每 5 秒续一次 20 秒的 night_vision，无粒子）
 * - 触及范围（方块 + 实体，配置值语义为"最终总格数"）
 *
 * <p>两者的实际逻辑都在 {@link BuffEvents} 里，由 {@code PlayerTickEvent} 每 tick 驱动。</p>
 *
 * <p><b>为什么不走 Curios 的 {@code getAttributeModifiers}：</b>那个接口只在
 * 装备 / 卸下时调用一次，改完配置必须重新装备才生效。改成每 tick 主动挂载后
 * 就是随改随生效（触及范围早先"要重穿才生效"的根因）。</p>
 */
public class BuffFeature implements TrinketFeature
{
    public static final String ID = "buff";

    @Override
    public String getId()
    {
        return ID;
    }

    @Override
    public boolean isEnabled()
    {
        return Config.COMMON.buffEnabled.get();
    }

    @Override
    public void curioTick(SlotContext ctx, ItemStack stack, PlayerSettings cfg)
    {
        // 触及范围与夜视都由 BuffEvents 的 PlayerTickEvent 驱动：
        // curioTick 只在佩戴时触发，摘下后状态回收不掉（血泪教训）。
    }

    /**
     * 快捷键提示行：显示玩家<b>实际绑定</b>的夜视切换键（而不是硬编码的 Z）。
     *
     * <p>键位查询是客户端专有的，所以用 {@code DistExecutor} 包一层，
     * 避免专用服务端加载 {@code ClientTooltips} 时报 NoClassDefFoundError。</p>
     */
    @Override
    public @Nullable Component extraTooltip()
    {
        return DistExecutor.unsafeCallWhenOn(Dist.CLIENT,
                () -> ClientTooltips::nightVisionKeyHint);
    }
}
