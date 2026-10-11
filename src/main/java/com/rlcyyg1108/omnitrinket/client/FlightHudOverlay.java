package com.rlcyyg1108.omnitrinket.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * HUD 文字提示：切换飞行档位 / 各类开关（夜视、饱和、锁定飞行……）时，
 * 在<b>快捷栏上方</b>短暂显示一条消息。
 *
 * <p>纯客户端显示，不影响任何游戏逻辑。显示约 2 秒后自动淡出。</p>
 *
 * <p>位置取原版"动作栏消息"（{@code Gui} 里 overlayMessage）的那一行：
 * {@code 屏幕高度 - 68}，也就是快捷栏正上方、准星下方。</p>
 */
public final class FlightHudOverlay
{
    private static Component message;
    /** 剩余显示时长（tick） */
    private static int ticksLeft;

    private FlightHudOverlay()
    {
    }

    /** 显示一条 HUD 提示（2 秒 = 40 tick） */
    public static void show(Component text)
    {
        message = text;
        ticksLeft = 40;
    }

    public static void clear()
    {
        message = null;
        ticksLeft = 0;
    }

    /** 每 tick 递减 */
    public static void tick()
    {
        if (ticksLeft > 0)
        {
            ticksLeft--;
            if (ticksLeft == 0)
            {
                message = null;
            }
        }
    }

    /** 由 ClientEvents 在客户端 tick 末尾调用 */
    public static void render(GuiGraphics graphics)
    {
        if (message == null || ticksLeft <= 0)
        {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || mc.screen != null)
        {
            return;
        }

        // 最后 10 tick 淡出
        float alpha = ticksLeft > 10 ? 1.0F : ticksLeft / 10.0F;

        int x = graphics.guiWidth() / 2;
        // 原版动作栏消息的 y —— 快捷栏正上方
        int y = graphics.guiHeight() - 68;
        int color = 0xFFFFFF;
        int a = (int) (alpha * 255) << 24;

        PoseStack pose = graphics.pose();
        pose.pushPose();
        graphics.drawString(mc.font, message, x - mc.font.width(message) / 2, y, a | color, true);
        pose.popPose();
    }
}
