package com.rlcyyg1108.omnitrinket.feature.impl;

import com.rlcyyg1108.omnitrinket.Config;
import com.rlcyyg1108.omnitrinket.OmniTrinket;
import com.rlcyyg1108.omnitrinket.util.TrinketHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 去除浮空挖掘速度惩罚（同时承担生存模块的"水下挖掘不减速"）。
 *
 * === 原版/Forge 到底在哪里惩罚 ===
 * 反编译 Forge 补丁后的 {@code Player} 可以看到：
 * <pre>
 *   // 已弃用的转发壳（游戏内部已不再调用）
 *   public float getDestroySpeed(BlockState state) { return this.getDigSpeed(state, null); }
 *
 *   // 真正的实现
 *   public float getDigSpeed(BlockState state, BlockPos pos) {
 *       ... 各种加成 ...
 *       if (isEyeInFluid(WATER) &amp;&amp; !hasAquaAffinity(this)) speed /= 5.0F;   // 水下
 *       if (!onGround())                                    speed /= 5.0F;   // 悬空 / 飞行
 *       return ForgeEventFactory.getBreakSpeed(this, state, speed, pos);     // ← 事件在最后！
 *   }
 * </pre>
 * 破坏方块的实际链路是 {@code BlockStateBase.getDestroyProgress} → {@code Player.getDigSpeed}，
 * 也就是说 {@code getDestroySpeed} 是**死方法**（没有任何原版调用者）。
 *
 * === 旧实现为什么没效果（两次都错）===
 * 1. 曾经把 Mixin 打在 {@code getDestroySpeed} 上 —— 那是个没人调用的转发壳，永远不会执行；
 * 2. 曾经用 {@code event.setNewSpeed(event.getOriginalSpeed())} ——
 *    {@code getOriginalSpeed()} 拿到的**已经是惩罚之后的值**，
 *    等于什么都没改（这也是"水下挖掘不减速"一直无效的根因）。
 *
 * === 现在 ===
 * 既然事件是在两次 ÷5 **之后**才触发的，最简洁正确的做法就是在事件里把惩罚乘回去：
 * {@code setNewSpeed(getNewSpeed() * 5.0F)}。反编译确认 {@code getBreakSpeed}
 * 在整个 Player 里只出现一次（就在 getDigSpeed 末尾），因此每个破坏判定只会乘一次，
 * 不存在重复放大的问题。
 *
 * 覆盖范围按需求"只要减速的都算"：水中、飞行、悬空一律还原。
 * 生存模块的"水下挖掘不减速"与飞行模块的"去除浮空挖掘速度惩罚"共用这一处判定，
 * 用布尔合取，确保两个开关同时打开时同一个惩罚也只还原一次（不会乘出 25 倍）。
 */
@Mod.EventBusSubscriber(modid = OmniTrinket.MODID)
public final class MiningPenaltyEvents
{
    private MiningPenaltyEvents()
    {
    }

    @SubscribeEvent
    public static void onBreakSpeed(PlayerEvent.BreakSpeed event)
    {
        Player player = event.getEntity();
        if (!TrinketHelper.isEquipped(player))
        {
            return;
        }

        // 与 getDigSpeed 里的两个判断一一对应
        // 注：isEyeInFluid(TagKey) 在 Forge 里已标记为 @Deprecated（替代品是 isEyeInFluidType），
        //     这里改用语义等价的 Entity.isUnderWater()，避免编译期 deprecation 警告。
        boolean waterPenalty = player.isUnderWater()
                && !EnchantmentHelper.hasAquaAffinity(player);
        boolean floatingPenalty = !player.onGround();
        if (!waterPenalty && !floatingPenalty)
        {
            return;
        }

        // 生存模块：水下挖掘不减速
        boolean survivalOk = Config.COMMON.survivalEnabled.get()
                && Config.COMMON.noUnderwaterMiningSlowdown.get();
        // 飞行模块：去除浮空挖掘速度惩罚（水中、飞行、悬空一并覆盖）
        boolean flightOk = Config.COMMON.flyEnabled.get()
                && Config.COMMON.flyNoMiningSlowdown.get();

        float speed = event.getNewSpeed();
        boolean changed = false;
        if (waterPenalty && (survivalOk || flightOk))
        {
            speed *= 5.0F;
            changed = true;
        }
        if (floatingPenalty && flightOk)
        {
            speed *= 5.0F;
            changed = true;
        }
        if (changed)
        {
            event.setNewSpeed(speed);
        }
    }
}
