package com.rlcyyg1108.omnitrinket.feature.impl;

import com.rlcyyg1108.omnitrinket.Config;
import com.rlcyyg1108.omnitrinket.OmniTrinket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 撞墙后自动恢复冲刺。
 *
 * <h3>要解决的问题</h3>
 * <p>原版里只要水平方向撞到方块（{@code horizontalCollision} 变为 true），
 * 就会立即终止冲刺状态。玩家在走廊、楼梯、密集方块地形里长距离奔跑时，
 * 必须反复重新按冲刺键，手感很差。</p>
 *
 * <h3>为什么用"边缘检测 + 延时补偿"，而不是"撞墙就置为冲刺中"</h3>
 * <p>{@code horizontalCollision} 有一个很容易踩的坑：<b>松开方向键时它同样会变false</b>。
 * 如果写成"撞墙就补一次冲刺"，玩家任何一次松手都会被误判成"离开墙面"，
 * 于是站着不动也会莫名其妙冲出去。</p>
 * <p>所以必须做成三段式：</p>
 * <pre>
 *   ① 撞上墙（false → true） ：记住"撞墙之前本来在不在冲刺"
 *   ② 离开墙（true → false）：若①记住了，则启动一段倒计时
 *   ③ 倒计时期间每 tick   ：条件不成立就立即清零（见下）
 * </pre>
 *
 * <p><b>关键：① 记住的标记必须一直保留到② 被消费</b>。
 * 玩家常常会沿着墙面滑动好几 tick（{@code horizontalCollision} 持续为 true），
 * 只有真正离开墙面的那一刻才应该补偿。所以这个标记不能每 tick 都清。</p>
 *
 * <h3>倒计时的续期与中止</h3>
 * <p>{@code resumeTicks} 期间每 tick 递减，只有<b>同时</b>满足下面两点才会继续：
 * 冲刺键<b>没有</b>按下、且前进键还按着。</p>
 * <ul>
 *   <li>玩家自己松了冲刺键 ⇒ 立刻停止补偿（尊重玩家意图，
 *       否则会出现"明明松手了还在自动冲"）；</li>
 *   <li>连前进键都松了 ⇒ 说明是主动停下，停止补偿。</li>
 * </ul>
 * 而向前移动期间（松冲刺键但继续按前进）会让计时器<b>保持原值</b>逐帧递减，
 * 这样玩家在长走廊里撞一次墙，就能一路保持冲刺，直到自己松手。
 *
 * <p>参考实现：Better Controls 1.20 分支的
 * {@code PlayerTicker.afterInputTick}（{@code resumeSprintingAfterHittingObstacle}）。</p>
 *
 * <h3>为什么是纯客户端</h3>
 * <p>和「无惯性」同理：玩家的移动本来就是客户端权威、服务端采信上报位置。
 * 这里只调整"客户端本地是否处于冲刺状态"，不产生任何服务端认可之外的速度，
 * 因此不需要网络包，也不会与服务端起冲突。</p>
 */
@Mod.EventBusSubscriber(modid = OmniTrinket.MODID, value = Dist.CLIENT)
public final class SprintResumeEvents
{
    /** 上一 tick 是否水平撞墙（用于边缘检测） */
    private static boolean wasHittingObstacle = false;

    /** 撞上墙之前玩家是否处于冲刺；<b>保留到离开墙面时才被消费</b> */
    private static boolean wasSprintingBeforeObstacle = false;

    /** 剩余补偿时长（tick） */
    private static int resumeTicks = 0;

    private SprintResumeEvents()
    {
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END)
        {
            return;
        }
        if (!(event.player instanceof LocalPlayer))
        {
            return;
        }

        LocalPlayer player = (LocalPlayer) event.player;
        Minecraft mc = Minecraft.getInstance();

        // 开关关闭 / 总开关关闭 / 界面打开 → 清空状态并退出。
        // 界面打开时玩家可能正在拖滑条或输入框，按键状态不代表真实意图。
        if (!Config.COMMON.sprintEnabled.get()
                || !Config.COMMON.resumeSprintAfterHittingObstacle.get()
                || mc.screen != null)
        {
            reset();
            return;
        }

        boolean sprintDown = mc.options.keySprint.isDown();
        boolean forwardDown = mc.options.keyUp.isDown();

        // ---- ① 倒计时：续期 / 中止 ----
        if (resumeTicks > 0)
        {
            if (sprintDown || !forwardDown)
            {
                // 玩家自己接管了（按了冲刺键）或已主动停下 ⇒ 结束补偿
                resumeTicks = 0;
            }
            else
            {
                // 未按冲刺键但仍在前进 ⇒ 自然递减，让长走廊里撞一次墙就能一路保持
                resumeTicks--;
            }
        }

        // ---- ② 撞墙边缘检测 ----
        boolean hitting = player.horizontalCollision;

        if (hitting)
        {
            if (!wasHittingObstacle)
            {
                // 刚撞上：记下撞墙前是否在冲刺（沿墙滑动期间保持不变）
                wasSprintingBeforeObstacle = player.isSprinting() || sprintDown;
            }
        }
        else if (wasHittingObstacle)
        {
            // 刚离开墙面：消费① 记住的信息
            if (wasSprintingBeforeObstacle)
            {
                resumeTicks = Config.COMMON.sprintResumeTicks.get();
            }
            wasSprintingBeforeObstacle = false;
        }

        wasHittingObstacle = hitting;

        // ---- ③ 实际施加补偿 ----
        if (resumeTicks > 0 && !player.isSprinting() && !sprintDown)
        {
            player.setSprinting(true);
        }
    }

    /** 开关被关 / 界面打开 时清空状态，避免残留的倒计时突然生效 */
    private static void reset()
    {
        wasHittingObstacle = false;
        wasSprintingBeforeObstacle = false;
        resumeTicks = 0;
    }
}
