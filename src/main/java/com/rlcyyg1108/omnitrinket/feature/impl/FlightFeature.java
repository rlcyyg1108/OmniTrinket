package com.rlcyyg1108.omnitrinket.feature.impl;

import net.minecraft.world.entity.player.Player;
import com.rlcyyg1108.omnitrinket.setting.PlayerSettingsAccess;
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
 * 飞行模块。
 *
 * <p>包含：自带飞行 / 无惯性 / 飞行速度（水平与垂直同时生效）/ 锁定飞行 /
 * 去除浮空挖掘速度惩罚。</p>
 *
 * <h3>实际逻辑分布</h3>
 * <ul>
 *   <li>授予 / 收回飞行能力、锁定飞行、写入飞行速度：
 *       {@link FlightEvents}（服务端每 tick）。</li>
 *   <li>无惯性：{@link NoInertiaEvents}（客户端 PlayerTickEvent END）。</li>
 *   <li>浮空挖掘惩罚：{@link MiningPenaltyEvents}（PlayerEvent.BreakSpeed）。</li>
 * </ul>
 *
 * <h3>飞行速度怎么生效（零 Mixin）</h3>
 * <p>服务端每 tick 把倍率写进原版 {@code Abilities.flyingSpeed}，
 * 再由 {@code onUpdateAbilities()} 走原版 {@code ClientboundPlayerAbilitiesPacket}
 * 同步到客户端 —— 两端天然一致，不需要 Mixin，也不需要自定义同步。</p>
 * <p>水平与垂直共用一个 {@code getFlyingSpeed()}，所以这一个值同时对两个方向生效；
 * 冲刺时按"冲刺飞行速度倍率"再乘一档（并抵消原版硬编码的 ×2）。</p>
 */
public class FlightFeature implements TrinketFeature
{
    public static final String ID = "flight";

    @Override
    public String getId()
    {
        return ID;
    }

    @Override
    public boolean isEnabled(@Nullable Player player)
    {
        return PlayerSettingsAccess.bool(player, PlayerSettings.FLY_ENABLED);
    }

    @Override
    public void onUnequip(SlotContext ctx, ItemStack stack)
    {
        // 摘下饰品的当 tick 由 FlightEvents 收回飞行能力，这里不重复处理，
        // 避免两处逻辑相互覆盖。
    }

    @Override
    public void curioTick(SlotContext ctx, ItemStack stack, PlayerSettings cfg)
    {
        // 飞行状态的每 tick 重算放在 FlightEvents 的 PlayerTickEvent 里：
        // curioTick 只在佩戴时触发，摘下后无法回收状态（血泪教训）。
    }

    /**
     * 快捷键提示行：显示玩家<b>实际绑定</b>的档位切换键（而不是硬编码的 X）。
     *
     * <p>键位查询是客户端专有的，所以用 {@code DistExecutor} 包一层，
     * 避免专用服务端加载 {@code ClientTooltips} 时报 NoClassDefFoundError。</p>
     */
    @Override
    public @Nullable Component extraTooltip()
    {
        return DistExecutor.unsafeCallWhenOn(Dist.CLIENT,
                () -> ClientTooltips::flightSpeedKeyHint);
    }
}
