package com.rlcyyg1108.omnitrinket.feature.impl;

import com.rlcyyg1108.omnitrinket.Config;
import com.rlcyyg1108.omnitrinket.OmniTrinket;
import com.rlcyyg1108.omnitrinket.setting.PlayerSettings;
import com.rlcyyg1108.omnitrinket.setting.PlayerSettingsHelper;
import com.rlcyyg1108.omnitrinket.util.TrinketHelper;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.UUID;

/**
 * 增益模块的运行时驱动。
 *
 * <h3>1. 夜视：用药水效果实现</h3>
 * <p>早先走的是客户端 Mixin 篡改 {@code LightTexture} 读取的伽马值。这条路修了三轮都不成：
 * {@code updateLightTexture} 又长又没有局部变量表，{@code @ModifyVariable} 的 ordinal
 * 根本不可靠，Forge 打补丁后方法体偏移又和原版对不上。**实现方式本身就是错的。**</p>
 *
 * <p>现在直接挂原版夜视效果（服务端权威、原版包自动同步）：</p>
 * <pre>
 *   每 100 tick（5 秒）给一次 mob_effects.night_vision，持续 400 tick（20 秒）
 * </pre>
 * <p><b>为什么是 20 秒而不是 15 秒：</b>原版在效果剩余时间不足 200 tick（10 秒）时，
 * 亮度会切换到 {@code 0.7 + sin(...)·0.3} 的闪烁公式（40%~100% 之间抖）。
 * 若时长取 15 秒、每 5 秒补一次，剩余时间就在 {@code 300 ↔ 200} 之间锯齿，
 * <b>谷底正好压在那条 200 的线上</b>；客户端与服务端只要差 1 tick 就会掉进闪烁区，
 * 看起来就是"黑屏一下"。取 20 秒则谷底是 300，离阈值有 100 tick 余量，永远不会闪。</p>
 *
 * <p>参数 {@code ambient=false, visible=false, showIcon=false}：不产生粒子、不占 HUD 图标。</p>
 *
 * <h3>2. 触及范围：改成每 tick 驱动</h3>
 * <p>早先写在 Curios 的 {@code ICurioItem#getAttributeModifiers()} 里 ——
 * 那个接口只在装备 / 卸下时被调用一次，所以改完配置必须重新装备才生效。
 * 现在改成和 {@link SprintEvents} 同样的做法：每 tick 主动挂载 / 卸载属性修饰符，
 * 于是改配置随改随生效。</p>
 */
@Mod.EventBusSubscriber(modid = OmniTrinket.MODID)
public final class BuffEvents
{
    /** 给药间隔（tick）：100 = 5 秒 */
    private static final int NIGHT_VISION_INTERVAL_TICKS = 100;
    /** 每次给的持续时间（tick）：400 = 20 秒（必须让剩余时间始终 &gt; 200，见类注释） */
    private static final int NIGHT_VISION_DURATION_TICKS = 400;

    /** 方块触及修饰符 UUID */
    private static final UUID BLOCK_REACH_UUID =
            UUID.fromString("a71c4e8d-3b06-4a92-8f5c-1d7e2b9a4c30");
    /** 实体触及修饰符 UUID */
    private static final UUID ENTITY_REACH_UUID =
            UUID.fromString("c50b9f27-6e13-4d8a-b2f7-38a1c6e0d954");

    private BuffEvents()
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

        // 触及范围：两端都要挂。客户端自己是本地交互距离的计算方，
        // 服务端则用于服务端的交互校验，两边一致才不会"看着能挖、服务端拒绝"。
        if (!player.isSpectator())
        {
            tickReach(player);
        }

        // 夜视：效果由服务端权威施加，客户端只负责接收
        if (!player.level().isClientSide())
        {
            tickNightVision(player);
        }
    }

    // ------------------------------------------------------------------
    // 夜视
    // ------------------------------------------------------------------

    private static void tickNightVision(Player player)
    {
        if (!Config.COMMON.buffEnabled.get())
        {
            return;
        }
        PlayerSettings settings = PlayerSettingsHelper.get(player).orElse(null);
        if (settings == null || !settings.isNightVision())
        {
            return;
        }
        if (!TrinketHelper.isEquipped(player))
        {
            return;
        }
        if (player.tickCount % NIGHT_VISION_INTERVAL_TICKS != 0)
        {
            return;
        }

        player.addEffect(new MobEffectInstance(
                MobEffects.NIGHT_VISION, NIGHT_VISION_DURATION_TICKS, 0,
                false,  // ambient
                false,  // visible —— 不要粒子
                false   // showIcon —— 不占 HUD 图标
        ));
    }

    // ------------------------------------------------------------------
    // 触及范围
    // ------------------------------------------------------------------

    private static void tickReach(Player player)
    {
        boolean active = Config.COMMON.buffEnabled.get() && TrinketHelper.isEquipped(player);
        double target = Config.COMMON.reachDistance.get();

        // Forge 的 BLOCK_REACH / ENTITY_REACH 本身带有原版默认值（方块 4.5、实体 3.0），
        // 配置值语义是"最终总格数"，所以修饰符增量 = 配置值 - 原版默认值。
        applyAddition(player, ForgeMod.BLOCK_REACH.get(), BLOCK_REACH_UUID,
                "omnitrinket:block_reach", target, active);
        applyAddition(player, ForgeMod.ENTITY_REACH.get(), ENTITY_REACH_UUID,
                "omnitrinket:entity_reach", target, active);
    }

    /**
     * 按需挂载 / 卸载"加到原版默认值之上"的修饰符。
     *
     * @param target 目标总格数（配置值）
     */
    private static void applyAddition(Player player, Attribute attribute, UUID uuid,
                                      String name, double target, boolean wanted)
    {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance == null)
        {
            return;
        }

        AttributeModifier existing = instance.getModifier(uuid);
        if (!wanted)
        {
            if (existing != null)
            {
                instance.removeModifier(uuid);
            }
            return;
        }

        double amount = target - attribute.getDefaultValue();

        if (existing != null)
        {
            if (Math.abs(existing.getAmount() - amount) < 1.0E-6D)
            {
                return;
            }
            instance.removeModifier(uuid);
        }
        if (amount == 0.0D)
        {
            return;
        }
        instance.addTransientModifier(new AttributeModifier(uuid, name, amount,
                AttributeModifier.Operation.ADDITION));
    }
}
