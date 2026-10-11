package com.rlcyyg1108.omnitrinket.feature.impl;

import com.rlcyyg1108.omnitrinket.OmniTrinket;
import com.rlcyyg1108.omnitrinket.setting.PlayerSettings;
import com.rlcyyg1108.omnitrinket.setting.PlayerSettingsAccess;
import com.rlcyyg1108.omnitrinket.util.TrinketHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Abilities;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 移动模块的运行时驱动（双端）。
 *
 * <h3>移动速度倍率：改"基准值"，而不是加属性修饰符</h3>
 *
 * <p>早先的做法是给 {@code MOVEMENT_SPEED} 挂一个 MULTIPLY_BASE 修饰符。
 * 速度是变快了，但视场角也跟着被拉大 —— 因为原版的 FOV 公式是：</p>
 * <pre>
 *   f *= (MOVEMENT_SPEED / abilities.getWalkingSpeed() + 1) / 2
 * </pre>
 * <p>分子被我们放大了 N 倍，分母没动，比值就从 1.0 变成 N —— 视野自然被拉大。
 * 之前是靠一个客户端 Mixin 事后"修正"回去，多一层修正就多一层出错机会
 * （原版奔跑的 ×1.3 是 MULTIPLY_TOTAL，会和 MULTIPLY_BASE 相乘，还原公式很难写对）。</p>
 *
 * <h3>换个思路：让分母跟着一起动（这正是"飞行"为什么不会拉 FOV）</h3>
 * <p>飞行速度存在 {@code Abilities.flyingSpeed} 里，而 FOV 公式根本不读它，
 * 所以飞行再快视野都不会变 —— 关键是<b>不要让速度加成泄露到 FOV 的公式里</b>。</p>
 *
 * <p>反编译发现原版自己就有一条不变式：</p>
 * <pre>
 *   // Player.readAdditionalSaveData()
 *   this.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue((double)this.getAbilities().getWalkingSpeed());
 * </pre>
 * <p>也就是说 <b>{@code MOVEMENT_SPEED} 的基准值本来就等于 {@code Abilities.walkingSpeed}</b>
 * （默认都是 0.1）。而 {@code getWalkingSpeed()} 在整个原版里只被三处用到：
 * 这一处持久化，以及 FOV 公式的<b>分母</b>和它的 NaN 保护。</p>
 *
 * <p>于是做法变得非常简单：把 <b>两个值同步放大 N 倍</b>。</p>
 * <pre>
 *   MOVEMENT_SPEED.base = 0.1 × N       // 实际速度变成 N 倍
 *   abilities.walkingSpeed = 0.1 × N    // FOV 的分母同步放大
 *
 *   走路 FOV： (0.1N / 0.1N + 1) / 2 = 1.00   ← 与原版完全一致
 *   奔跑 FOV： (0.13N / 0.1N + 1) / 2 = 1.15  ← 与原版完全一致（原版奔跑的 ×1.3 会自动跟着走）
 * </pre>
 *
 * <p>收益：<b>不再需要任何 FOV 修正 Mixin</b>（{@code AbstractClientPlayerFovMixin}
 * 与 {@code SprintFovHandler} 已删除），速度药水、灵魂疾行等原版加成也自动保持正确的比例。
 * 而且因为原版本来就用 walkingSpeed 存移动速度，倍率能随存档自然保留。</p>
 *
 * <p>{@code walkingSpeed} 会随原版 {@code ClientboundPlayerAbilitiesPacket} 同步给客户端，
 * 所以两端算出的 FOV 一致，不会出现"服务端一个值、客户端一个值"的抖动。</p>
 *
 * <p><b>跳跃高度</b>不在本类 —— 它由 {@link JumpEvents} 用 Forge 事件实现。</p>
 */
@Mod.EventBusSubscriber(modid = OmniTrinket.MODID)
public final class SprintEvents
{
    /** 原版玩家的基础移动速度：Abilities.walkingSpeed 与 MOVEMENT_SPEED 默认值都是 0.1 */
    public static final double VANILLA_WALK_SPEED = 0.1D;

    private SprintEvents()
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
        // 两端都要跑：客户端自己是本地玩家移动与 FOV 的计算方。
        // 服务端跑是为了让服务端的行走速度校验（"moved too quickly"）用上同一个基准值。
        if (player.isSpectator())
        {
            return;
        }
        tick(player);
    }

    /**
     * 每 tick 把"行走基准速度"调整到应有的值。
     */
    public static void tick(Player player)
    {
        boolean active = PlayerSettingsAccess.bool(player, PlayerSettings.SPRINT_ENABLED)
                && TrinketHelper.isEquipped(player);
        // 倍率是每玩家值：服务端读 capability，客户端读本机镜像（见 PlayerSettingsAccess）
        double factor = active ? PlayerSettingsAccess.dbl(player, PlayerSettings.MOVE_SPEED) : 1.0D;
        if (factor < PlayerSettings.MIN_MULTIPLIER)
        {
            factor = 1.0D;
        }
        applyWalkSpeed(player, (float) (VANILLA_WALK_SPEED * factor));
    }

    /**
     * 同步设置 {@code Abilities.walkingSpeed} 与 {@code MOVEMENT_SPEED} 基准值。
     *
     * <p>两者必须始终相等 —— 这是原版自己的不变式，也是 FOV 能保持原样的前提。</p>
     */
    private static void applyWalkSpeed(Player player, float target)
    {
        Abilities abilities = player.getAbilities();
        boolean changed = false;

        if (Math.abs(abilities.getWalkingSpeed() - target) > 1.0E-6F)
        {
            abilities.setWalkingSpeed(target);
            changed = true;
        }

        AttributeInstance instance = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (instance != null && Math.abs(instance.getBaseValue() - (double) target) > 1.0E-6D)
        {
            instance.setBaseValue((double) target);
            changed = true;
        }

        // walkingSpeed 走原版 Abilities 包同步；只在真正变化时发，避免每 tick 一个包
        if (changed && player instanceof ServerPlayer serverPlayer)
        {
            serverPlayer.onUpdateAbilities();
        }
    }
}
