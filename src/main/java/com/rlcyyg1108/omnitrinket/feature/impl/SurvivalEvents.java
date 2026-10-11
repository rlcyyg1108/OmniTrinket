package com.rlcyyg1108.omnitrinket.feature.impl;

import com.rlcyyg1108.omnitrinket.Config;
import com.rlcyyg1108.omnitrinket.OmniTrinket;
import com.rlcyyg1108.omnitrinket.util.TrinketHelper;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 生存模块的事件处理。
 *
 * 免伤和水下挖掘减速必须以事件方式实现，因为它们是原版逻辑的一部分，
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
        return Config.COMMON.survivalEnabled.get() && TrinketHelper.isEquipped(player);
    }

    /**
     * 判断某个伤害源是否应当被免疫。
     *
     * @return true 表示应当免疫（取消）该伤害
     */
    private static boolean shouldImmune(Player player, DamageSource source)
    {
        if (!Config.COMMON.fullDamageImmunity.get())
        {
            return false;
        }
        if (!isWearingTrinket(player))
        {
            return false;
        }
        // 虚空伤害例外：配置允许时放行，避免掉进虚空后永远无法脱身
        if (Config.COMMON.allowVoidDamage.get()
                && source.is(DamageTypeTags.BYPASSES_INVULNERABILITY))
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

    // 水下挖掘不减速不在这里处理：
    // 原版的惩罚发生在 Player.getDigSpeed() 内部，而 BreakSpeed 事件的
    // getOriginalSpeed() 拿到的已经是惩罚之后的值，靠它还原不了。
    // 现在统一由 MiningPenaltyEvents 在 BreakSpeed 事件里把那两次 ÷5 乘回来。
}
