package com.rlcyyg1108.omnitrinket.feature.impl;

import com.rlcyyg1108.omnitrinket.setting.PlayerSettingsAccess;
import com.rlcyyg1108.omnitrinket.setting.PlayerSettings;
import com.rlcyyg1108.omnitrinket.OmniTrinket;
import com.rlcyyg1108.omnitrinket.util.TrinketHelper;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.MobEffectEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 生存模块的事件处理。
 *
 * 免伤、负面效果免疫和水下挖掘减速必须以事件方式实现，因为它们是原版逻辑的一部分，
 * 无法只在 curioTick 里改。
 */
@Mod.EventBusSubscriber(modid = OmniTrinket.MODID)
public class SurvivalEvents
{
    /**
     * 判断玩家是否佩戴了全能饰品且生存模块已启用。
     */
    private static boolean isWearingTrinket(Player player)
    {
        return PlayerSettingsAccess.bool(player, PlayerSettings.SURVIVAL_ENABLED)
                && TrinketHelper.isEquipped(player);
    }

    /**
     * 判断某个伤害源是否应当被免疫。
     *
     * @return true 表示应当免疫（取消）该伤害
     */
    private static boolean shouldImmune(Player player, DamageSource source)
    {
        int mode = PlayerSettingsAccess.integer(player, PlayerSettings.SURVIVAL_DAMAGE_MODE);
        if (mode <= 0)
        {
            return false;
        }
        if (!isWearingTrinket(player))
        {
            return false;
        }
        // 模式 1 允许虚空伤害，避免掉进虚空后永远无法脱身；模式 2 连虚空也免疫
        if (mode == 1 && source.is(DamageTypeTags.BYPASSES_INVULNERABILITY))
        {
            return false;
        }
        return true;
    }

    /**
     * 完全免伤（第一层）：在攻击判定阶段就拦截。
     *
     * 这一层能阻止伤害被"计算"，连受伤动画与击退都不会有。
     */
    @SubscribeEvent
    public static void onLivingAttack(LivingAttackEvent event)
    {
        if (!(event.getEntity() instanceof Player player))
        {
            return;
        }
        if (shouldImmune(player, event.getSource()))
        {
            event.setCanceled(true);
        }
    }

    /**
     * 完全免伤（第二层）：兜底拦截伤害。
     *
     * 某些伤害路径可能绕过 LivingAttackEvent，这一层确保万无一失。
     */
    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event)
    {
        if (!(event.getEntity() instanceof Player player))
        {
            return;
        }
        if (shouldImmune(player, event.getSource()))
        {
            event.setCanceled(true);
        }
    }

    /**
     * 负面效果免疫：按三态模式拒绝 HARMFUL / NEUTRAL。
     *
     * <p>模式 1 只拒绝 {@link MobEffectCategory#HARMFUL}；模式 2 连
     * {@link MobEffectCategory#NEUTRAL}（不祥之兆、袭击之兆、试炼之兆、发光等）一起拒绝；
     * {@link MobEffectCategory#BENEFICIAL} 永远放行。</p>
     */
    @SubscribeEvent
    public static void onEffectApplicable(MobEffectEvent.Applicable event)
    {
        if (!(event.getEntity() instanceof Player player))
        {
            return;
        }
        if (!isWearingTrinket(player))
        {
            return;
        }

        int mode = PlayerSettingsAccess.integer(player, PlayerSettings.SURVIVAL_NEGATIVE_MODE);
        if (mode <= 0)
        {
            return;
        }

        MobEffectCategory category = event.getEffectInstance().getEffect().getCategory();
        if (category == MobEffectCategory.BENEFICIAL)
        {
            return;
        }
        if (category == MobEffectCategory.HARMFUL
                || (mode >= 2 && category == MobEffectCategory.NEUTRAL))
        {
            event.setResult(Event.Result.DENY);
        }
    }

    // 水下挖掘不减速不在这里处理：
    // 原版的惩罚发生在 Player.getDigSpeed() 内部，而 BreakSpeed 事件的
    // getOriginalSpeed() 拿到的已经是惩罚之后的值，靠它还原不了。
    // 现在统一由 MiningPenaltyEvents 在 BreakSpeed 事件里把那两次 ÷5 乘回来。
}
