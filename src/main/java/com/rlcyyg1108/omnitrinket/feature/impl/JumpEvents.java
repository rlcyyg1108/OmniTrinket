package com.rlcyyg1108.omnitrinket.feature.impl;

import com.rlcyyg1108.omnitrinket.Config;
import com.rlcyyg1108.omnitrinket.OmniTrinket;
import com.rlcyyg1108.omnitrinket.util.TrinketHelper;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 跳跃高度倍率（双端，纯 Forge 事件，<b>没有任何 Mixin</b>）。
 *
 * <h3>为什么用事件而不是 Mixin</h3>
 * <p>早先版本 Mixin 注入 {@code LivingEntity.getJumpPower()} 的 RETURN 再乘倍率。
 * 目标方法本身是对的（{@code Player.jumpFromGround} → {@code LivingEntity.jumpFromGround}
 * → {@code getJumpPower}），refmap 也正确，但依然反馈"没效果"。
 * 真正的根因是<b>倍率放在 {@code Config.COMMON} 里</b>：设置界面改它只改客户端，
 * 两端按不同倍率起跳 → 服务端以位置异常为由把玩家拽回原高度。
 * 现在倍率存在服务端权威的 {@code PlayerSettings}，配合本事件（双端触发、逻辑完全对称）
 * 才真正一致。</p>
 *
 * <h3>实现思路（对齐原版 jump_boost 药水的加法模型）</h3>
 * <p>原版 {@code LivingEntity.jumpFromGround}：</p>
 * <pre>
 *   this.setDeltaMovement(x, (double) this.getJumpPower(), z);
 *   ...
 *   ForgeHooks.onLivingJump(this);        // ← Forge 在这里补了一个事件
 * </pre>
 * <p>而 {@code getJumpPower() = 0.42F × getBlockJumpFactor() + getJumpBoostPower()}，
 * 原版 jump_boost 药水的加成就是靠 {@code getJumpBoostPower()} 那 0.1 一项<b>加法</b>加上去的。</p>
 * <p>本类照同样的思路，在事件里给 {@code deltaMovement.y} 追加一份：</p>
 * <pre>
 *   事件触发时 deltaMovement.y 已经等于原版起跳初速
 *   自由落体 h = v²/(2g) ⇒ 高度 ×H ⇒ 初速 ×√H ⇒ 增量 = base × (√H − 1)
 * </pre>
 * <p>直接用 {@code getDeltaMovement().y} 当基准而<b>不硬编码 0.42</b>，
 * 于是自动兼容 {@code getBlockJumpFactor()}（蜂蜜块、台阶等）与跳跃提升药水。</p>
 *
 * <p>本事件在客户端（预测）与服务端（验证）都会触发，两端算式完全相同，
 * 所以<b>不能</b>加 {@code Dist.CLIENT} 守卫。</p>
 */
@Mod.EventBusSubscriber(modid = OmniTrinket.MODID)
public final class JumpEvents
{
    private JumpEvents()
    {
    }

    @SubscribeEvent
    public static void onLivingJump(LivingEvent.LivingJumpEvent event)
    {
        LivingEntity entity = event.getEntity();
        if (!(entity instanceof Player player))
        {
            return;
        }
        if (!Config.COMMON.sprintEnabled.get())
        {
            return;
        }
        if (!TrinketHelper.isEquipped(player))
        {
            return;
        }

        double heightMultiplier = MoveSettings.jumpHeightMultiplier(player);
        if (heightMultiplier <= 1.0D)
        {
            return;
        }

        // 事件触发时 deltaMovement.y 就是原版起跳初速
        double base = player.getDeltaMovement().y;
        if (base <= 0.0D)
        {
            return;
        }

        player.push(0.0D, base * (Math.sqrt(heightMultiplier) - 1.0D), 0.0D);
    }
}
