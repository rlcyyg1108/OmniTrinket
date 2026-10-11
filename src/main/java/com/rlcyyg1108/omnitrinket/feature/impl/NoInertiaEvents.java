package com.rlcyyg1108.omnitrinket.feature.impl;

import com.rlcyyg1108.omnitrinket.Config;
import com.rlcyyg1108.omnitrinket.OmniTrinket;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 飞行"无惯性"：松开方向键立刻停（纯客户端，单一开关）。
 *
 * <h3>为什么用 Forge 事件、而不是 Mixin</h3>
 * <p>曾经试过挂在 {@code LocalPlayer.aiStep} 的 TAIL 上做可调的乘性衰减，
 * 实测<b>完全无效</b>——{@code aiStep} 之后原版还有浮空状态、碰撞、摩擦等
 * 一整条每 tick 的移动修正，改好的 {@code deltaMovement} 会被再改一遍。
 * 放在 {@code TickEvent.PlayerTickEvent(END)} 才是"整条管线已跑完"的稳定位置。</p>
 * <p>放在 {@code TickEvent.PlayerTickEvent(END)} 才是稳定位置：此时本 tick 的
 * 整条移动管线已跑完，速度就是最终值。</p>
 *
 * <h3>为什么必须在客户端，而且必须用 xxa / zza</h3>
 * <p>{@code ServerPlayer} 的移动由客户端权威决定，服务端 tick 时
 * {@code xxa/zza} 恒为 0、传给 {@code travel} 的向量也恒为 (0,0,0)。
 * 若在服务端做同样的清零，等于服务端每 tick 都在压水平速度，
 * 和客户端真实移动互相拉扯，结果就是"看起来完全没效果"。</p>
 * <p>客户端不同：{@code xxa/zza} 由 {@code KeyboardInput} 每 tick 写入，
 * 原版在 {@code aiStep} 里会做 {@code *= 0.98} 的衰减，所以
 * <b>"有输入"时永远不等于 0、"松手"时正好是 0</b> —— 一个稳定的判据。</p>
 *
 * <p>参考实现：GTL 整合包 GTLCore 的 {@code disableDrift}。</p>
 */
@Mod.EventBusSubscriber(modid = OmniTrinket.MODID, value = Dist.CLIENT)
public final class NoInertiaEvents
{
    private NoInertiaEvents()
    {
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END)
        {
            return;
        }
        if (!Config.COMMON.flyNoInertia.get())
        {
            return;
        }

        Player player = event.player;

        // 服务端 tick 里 xxa/zza 恒为 0，若不排除会在服务端错误地压速度
        if (!(player instanceof LocalPlayer))
        {
            return;
        }
        if (!player.getAbilities().flying)
        {
            return;
        }

        // 还有方向输入 → 交给原版处理
        if (player.xxa != 0.0F || player.zza != 0.0F)
        {
            return;
        }

        Vec3 motion = player.getDeltaMovement();
        if (motion.x == 0.0D && motion.z == 0.0D)
        {
            return;
        }

        // 松手即停：只清水平残留，垂直方向仍交给原版的悬浮处理
        player.setDeltaMovement(0.0D, motion.y, 0.0D);
    }
}
