package com.rlcyyg1108.omnitrinket.client;

import com.rlcyyg1108.omnitrinket.Config;
import com.rlcyyg1108.omnitrinket.OmniTrinket;
import com.rlcyyg1108.omnitrinket.client.gui.OmniTrinketSettingsScreen;
import com.rlcyyg1108.omnitrinket.feature.impl.FlightSpeeds;
import com.rlcyyg1108.omnitrinket.network.ClientSettingsCache;
import com.rlcyyg1108.omnitrinket.network.KeyActionPacket;
import com.rlcyyg1108.omnitrinket.network.NetworkHandler;
import com.rlcyyg1108.omnitrinket.network.SetPlayerSettingsPacket;
import com.rlcyyg1108.omnitrinket.setting.PlayerSettings;
import com.rlcyyg1108.omnitrinket.util.TrinketHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 客户端事件：按键响应。
 *
 * - N：打开设置界面（界面已打开时由界面自己处理为"完成"，见
 *   {@link OmniTrinketSettingsScreen#keyPressed}）
 * - X：快捷切换飞行档位；Shift+X：重置为默认档位
 * - Z：切换夜视开关
 *
 * <p>飞行档位这类需要"服务端状态才能算出下一个值"的操作只发包请求；
 * 夜视开关这种客户端已知目标值的操作则先本地乐观更新（手感零延迟），
 * 服务端确认后会回发权威值覆盖。</p>
 */
@Mod.EventBusSubscriber(modid = OmniTrinket.MODID, value = Dist.CLIENT)
public final class ClientEvents
{
    /** 上一 tick 是否还在世界里；用于只在"刚离开世界"时清一次缓存 */
    private static boolean wasInWorld = false;

    private ClientEvents()
    {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END)
        {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null)
        {
            // 离开世界：清掉 HUD 状态，避免下次进入显示上局的数据。
            // 用标记位保证只在"从有玩家变成没玩家"的那一刻清一次——
            // 原先是无条件每 tick 清，既浪费又会因为 clear() 里的并发删除
            // 直接把客户端打崩（ConcurrentModificationException → FATAL）。
            if (wasInWorld)
            {
                wasInWorld = false;
                ClientSettingsCache.clear();
            }
            return;
        }
        wasInWorld = true;
        FlightHudOverlay.tick();
        if (mc.screen != null)
        {
            // 界面打开时不响应任何快捷键，但必须把按键队列排空：
            // 否则关闭界面的那一瞬间会把之前攒下的点击一次性消费掉，
            // 表现就是"按 N 关闭后立刻又被弹开"。
            drainKeybinds();
            return;
        }

        // 装备状态：飞行/夜视这类功能必须是"戴着"才生效
        boolean wearing = isWearingTrinket(mc.player);
        // 持有状态：背包 / 快捷栏 / 饰品栏任意位置有饰品即可
        boolean holding = TrinketHelper.isInInventory(mc.player);

        // 消费所有排队的按键事件，避免一次按下触发多次
        // N —— 打开设置界面：只要"持有"就能开，不必先戴上
        while (KeyBindings.OPEN_SETTINGS.consumeClick())
        {
            if (holding)
            {
                mc.setScreen(new OmniTrinketSettingsScreen());
            }
        }

        // X：切换飞行速度档位（1/2/3/5/10 循环）。
        // 速度是服务端每 tick 写进 Abilities.flyingSpeed 的，所以必须发包给服务端改，
        // 改完由原版 abilities 包同步回客户端。客户端本地改 toml 不会有任何效果。
        while (KeyBindings.CYCLE_FLY_SPEED.consumeClick())
        {
            if (wearing)
            {
                // 客户端从本机 toml 预测下一档用于 HUD 即时反馈；
                // 实际生效由服务端改配置并写入 Abilities。
                double predicted = FlightSpeeds.next(Config.COMMON.flySpeedMultiplier.get());
                NetworkHandler.CHANNEL.sendToServer(
                        new KeyActionPacket(KeyActionPacket.ACTION_CYCLE_FLY_SPEED));
                FlightHudOverlay.show(Component.translatable(
                        "omnitrinket.hud.fly_speed",
                        FlightSpeeds.format(predicted)));
            }
        }

        while (KeyBindings.TOGGLE_NIGHT_VISION.consumeClick())
        {
            if (wearing)
            {
                toggleNightVision();
            }
        }
    }

    /**
     * 切换夜视开关。
     *
     * <p>目标值客户端自己就知道，所以先改本地镜像（开关手感零延迟），
     * 再发包让服务端真正改；服务端改完回发权威值，天然对齐。</p>
     *
     * <p>与切档位一致：在快捷栏上方弹一条文字提示。</p>
     */
    private static void toggleNightVision()
    {
        boolean next = !ClientSettingsCache.nightVision();
        ClientSettingsCache.setLocalBoolean(PlayerSettings.KEY_NIGHT_VISION, next);
        NetworkHandler.CHANNEL.sendToServer(
                SetPlayerSettingsPacket.ofFlag(PlayerSettings.KEY_NIGHT_VISION, next));
        // 复用已有的"开 / 关"文案键，不额外造词
        FlightHudOverlay.show(Component.translatable("omnitrinket.hud.night_vision",
                Component.translatable(next ? "omnitrinket.gui.on" : "omnitrinket.gui.off")));
    }

    /** 把三个按键的点击队列排空 */
    private static void drainKeybinds()
    {
        while (KeyBindings.OPEN_SETTINGS.consumeClick())
        {
            // 界面自己会处理 N，这里只负责丢掉队列
        }
        while (KeyBindings.CYCLE_FLY_SPEED.consumeClick())
        {
            // 同上：界面里不处理 X，只丢掉排队，避免关闭界面的瞬间误触切档
        }
        while (KeyBindings.TOGGLE_NIGHT_VISION.consumeClick())
        {
            // 同上
        }
    }

    /**
     * 打开界面的前置条件：佩戴饰品。
     */
    public static boolean isWearingTrinket(Player player)
    {
        return TrinketHelper.isEquipped(player);
    }
}
