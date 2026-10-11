package com.rlcyyg1108.omnitrinket.feature.impl;

import com.rlcyyg1108.omnitrinket.Config;
import com.rlcyyg1108.omnitrinket.OmniTrinket;
import com.rlcyyg1108.omnitrinket.setting.PlayerSettings;
import com.rlcyyg1108.omnitrinket.setting.PlayerSettingsHelper;
import com.rlcyyg1108.omnitrinket.util.TrinketHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Abilities;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 飞行模块的运行时驱动（服务端）。
 *
 * <h3>1. 授予 / 收回飞行能力</h3>
 * <p>原版 {@code Abilities.mayfly} 决定"玩家是否有资格飞"。授予后必须调用
 * {@code ServerPlayer.onUpdateAbilities()} 才能把新的 Abilities <b>同步给客户端</b>，
 * 否则客户端 LocalPlayer 的 mayfly 仍是 false，双击跳跃会被原版逻辑拒绝。</p>
 *
 * <h3>2. 飞行速度</h3>
 * <p>服务端每 tick 把"倍率 × 原版基准速度"写进 {@code Abilities.flyingSpeed}，
 * 再由 {@code onUpdateAbilities()} 走原版 {@code ClientboundPlayerAbilitiesPacket}
 * 同步到客户端 —— 零 Mixin、零自定义同步。水平与垂直共用同一个
 * {@code getFlyingSpeed()}，所以这一个值同时对两轴生效；冲刺时先乘
 * "冲刺飞行速度倍率"、再除以 2（抵消原版硬编码的 ×2）。</p>
 *
 * <h3>3. 为什么每 tick 检查而不是只在 curioTick</h3>
 * <p>curioTick 只在佩戴时触发，摘下后就不再执行，留下的状态回收不掉。
 * 所以统一用 {@link TickEvent.PlayerTickEvent}，无论是否佩戴每 tick 都会跑。</p>
 */
@Mod.EventBusSubscriber(modid = OmniTrinket.MODID)
public final class FlightEvents
{

    /** 原版 {@code Abilities.flyingSpeed} 的默认值 */
    private static final float VANILLA_FLYING_SPEED = 0.05F;

    private FlightEvents()
    {
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END)
        {
            return;
        }
        Player player = event.player;
        if (player.level().isClientSide())
        {
            return;
        }
        tick(player);
    }

    /**
     * 每 tick 重算飞行状态与飞行速度。
     */
    public static void tick(Player player)
    {
        PlayerSettings settings = PlayerSettingsHelper.get(player).orElse(null);
        if (settings == null)
        {
            return;
        }

        boolean featureOn = settings.getBoolean(PlayerSettings.FLY_ENABLED);
        boolean wearing = TrinketHelper.isEquipped(player);

        if (!featureOn || !wearing)
        {
            revoke(player, settings);
            return;
        }

        Abilities abilities = player.getAbilities();
        boolean changed = false;

        // 标记"本饰品已授予飞行能力"；只用于摘饰品时判断该不该收回。
        // 注意：不要在这里读任何配置决定飞行状态 —— 早先"锁定飞行"是在这里
        // 从 Config 读一次，导致必须重新装备才生效。现在锁定飞行是一个
        // 可以随时翻转的 PlayerSettings 字段，下面每 tick 直接检查它。
        if (!settings.getBoolean(PlayerSettings.FLY_GRANTED))
        {
            settings.setBoolean(PlayerSettings.FLY_GRANTED, true);
        }

        if (!abilities.mayfly)
        {
            abilities.mayfly = true;
            changed = true;
        }

        // 锁定飞行：强制保持飞行中，不允许落地收起。
        // flyLocked 是 PlayerSettings 字段，界面开关 / 快捷键改的就是它，
        // 这一段每 tick 都会跑，所以随开随关。
        if (settings.getBoolean(PlayerSettings.FLY_LOCKED) && !abilities.flying)
        {
            abilities.flying = true;
            changed = true;
        }

        // 飞行速度：把倍率写进原版 Abilities.flyingSpeed。
        //
        // ★ 这是"最笨但最可靠"的做法（也是本项目早期能正常工作的那一版）：
        //   改完之后由 onUpdateAbilities() 走原版 ClientboundPlayerAbilitiesPacket
        //   同步到客户端，两端天然一致 —— 不需要任何 Mixin，也不需要自定义同步。
        //
        //   水平与垂直同时生效：原版两者读的是同一个 getFlyingSpeed()。
        //   冲刺时：原版 getFlyingSpeed() 自己会再乘 2，所以这里要把
        //   写入值除以 2，让"最终倍率"恰好等于 FLY_SPRINT。
        float target = targetFlyingSpeed(player, settings);
        if (Math.abs(abilities.getFlyingSpeed() - target) > 1.0E-6F)
        {
            abilities.setFlyingSpeed(target);
            changed = true;
        }


        // Abilities 只有在显式同步后才会到达客户端
        if (changed && player instanceof ServerPlayer serverPlayer)
        {
            serverPlayer.onUpdateAbilities();
        }

        syncSettingsIfNeeded(player, settings);
    }

    /**
     * 写入 {@code Abilities.flyingSpeed} 的目标值。
     *
     * <p><b>非冲刺</b>：{@code 0.05 × FLY_SPEED}。</p>
     *
     * <p><b>冲刺时</b>：{@code 0.05 × FLY_SPEED × FLY_SPRINT}，
     * 即"冲刺飞行速度倍率"是<b>乘</b>在飞行速度之上的独立乘区
     * （不是替换飞行速度）。</p>
     *
     * <p><b>为什么要除以 2</b>：原版 {@code Player.getFlyingSpeed()} 里对飞行中的
     * 冲刺硬编码了 ×2（{@code isSprinting() ? abilities.getFlyingSpeed() * 2 : ...}），
     * 若不抵消，最终速度会变成目标的 2 倍。这里先把写入值除以 2，
     * 原版再乘 2 之后恰好回到我们想要的倍率。</p>
     */
    private static float targetFlyingSpeed(Player player, PlayerSettings settings)
    {
        double multiplier = settings.getDouble(PlayerSettings.FLY_SPEED);

        if (player.isSprinting())
        {
            // 冲刺倍率是"乘区"，不是替换
            multiplier *= settings.getDouble(PlayerSettings.FLY_SPRINT);
            // 抵消原版 getFlyingSpeed() 里硬编码的冲刺 ×2
            multiplier /= 2.0D;
        }

        return (float) (VANILLA_FLYING_SPEED * multiplier);
    }

    /**
     * 收回飞行能力并复位飞行速度。
     */
    private static void revoke(Player player, PlayerSettings settings)
    {
        Abilities abilities = player.getAbilities();
        boolean changed = false;

        if (settings.getBoolean(PlayerSettings.FLY_GRANTED))
        {
            settings.setBoolean(PlayerSettings.FLY_GRANTED, false);
            settings.setBoolean(PlayerSettings.FLY_LOCKED, false);

            // 只收回"我们授予的"，创造 / 旁观自带的飞行不动
            if (!player.isCreative() && !player.isSpectator())
            {
                if (abilities.mayfly)
                {
                    abilities.mayfly = false;
                    changed = true;
                }
                if (abilities.flying)
                {
                    abilities.flying = false;
                    changed = true;
                }
            }
        }


        // 速度一并复位成原版值
        if (Math.abs(abilities.getFlyingSpeed() - VANILLA_FLYING_SPEED) > 1.0E-6F)
        {
            abilities.setFlyingSpeed(VANILLA_FLYING_SPEED);
            changed = true;
        }

        if (changed && player instanceof ServerPlayer serverPlayer)
        {
            serverPlayer.onUpdateAbilities();
        }
        syncSettingsIfNeeded(player, settings);
    }

    /**
     * 设置有变化时同步给客户端（HUD 与设置界面需要）。
     */
    private static void syncSettingsIfNeeded(Player player, PlayerSettings settings)
    {
        if (settings.isDirty() && player instanceof ServerPlayer serverPlayer)
        {
            settings.setDirty(false);
            com.rlcyyg1108.omnitrinket.network.SyncPlayerSettingsPacket.sendTo(serverPlayer);
        }
    }

    /**
     * 玩家登录 / 换维度 / 重生后重新同步一次。
     */
    @Mod.EventBusSubscriber(modid = OmniTrinket.MODID)
    public static class SyncOnJoin
    {
        @SubscribeEvent
        public static void onLogin(PlayerEvent.PlayerLoggedInEvent event)
        {
            resync(event.getEntity());
        }

        @SubscribeEvent
        public static void onRespawn(PlayerEvent.PlayerRespawnEvent event)
        {
            resync(event.getEntity());
        }

        @SubscribeEvent
        public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event)
        {
            resync(event.getEntity());
        }

        private static void resync(Player player)
        {
            if (player instanceof ServerPlayer serverPlayer)
            {
                com.rlcyyg1108.omnitrinket.network.SyncPlayerSettingsPacket.sendTo(serverPlayer);
            }
        }
    }
}
