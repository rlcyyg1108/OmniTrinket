package com.rlcyyg1108.omnitrinket.client;

import com.rlcyyg1108.omnitrinket.OmniTrinket;
import com.rlcyyg1108.omnitrinket.client.gui.OmniTrinketSettingsScreen;
import com.rlcyyg1108.omnitrinket.feature.impl.FlightSpeeds;
import com.rlcyyg1108.omnitrinket.network.ClientSettingsCache;
import com.rlcyyg1108.omnitrinket.network.KeyActionPacket;
import com.rlcyyg1108.omnitrinket.network.NetworkHandler;
import com.rlcyyg1108.omnitrinket.network.SetPlayerSettingsPacket;
import com.rlcyyg1108.omnitrinket.setting.PlayerSettingKey;
import com.rlcyyg1108.omnitrinket.setting.PlayerSettings;
import com.rlcyyg1108.omnitrinket.util.TrinketHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

/**
 * 客户端事件：按键响应。
 *
 * - N：打开设置界面（界面已打开时由界面自己处理为"完成"，见
 *   {@link OmniTrinketSettingsScreen#keyPressed}）
 * - X：快捷切换飞行档位（1/2/3/5/10 循环）
 * - Z：切换夜视开关
 * - 其余开关默认<b>未绑定</b>，可在"控制"里自行绑定：饱和 / 锁定飞行 / 无惯性 /
 *   不消耗氧气 / 撞墙后恢复冲刺；免伤键为三态循环（见 {@code TOGGLE_BINDINGS} / {@code MODE_BINDINGS}）
 *
 * <p>飞行档位这类需要"服务端状态才能算出下一个值"的操作只发包请求；
 * 开关类（夜视 / 饱和 / …）客户端已知目标值，先本地乐观更新（手感零延迟），
 * 服务端确认后会回发权威值覆盖。</p>
 */
@Mod.EventBusSubscriber(modid = OmniTrinket.MODID, value = Dist.CLIENT)
public final class ClientEvents
{
    /** 上一 tick 是否还在世界里；用于只在"刚离开世界"时清一次缓存 */
    private static boolean wasInWorld = false;

    /**
     * 「可绑定开关」对照表：快捷键 → 设置项 → HUD 文案。
     *
     * <p>表驱动：以后再加可绑定开关，只需在这里加一行 —— 按键消费与队列排空
     * 都会自动覆盖。默认键位约定见 {@code KeyBindings}：只有 N / X / Z 带默认键，
     * 其余全部未绑定（未绑定的键不会产生点击，天然无副作用）。</p>
     */
    private record ToggleBinding(KeyMapping key, PlayerSettingKey<Boolean> setting, String hudKey)
    {
    }

    private static final List<ToggleBinding> TOGGLE_BINDINGS = List.of(
            new ToggleBinding(KeyBindings.TOGGLE_NIGHT_VISION, PlayerSettings.NIGHT_VISION,
                    "omnitrinket.hud.night_vision"),
            new ToggleBinding(KeyBindings.TOGGLE_SATURATION, PlayerSettings.SATURATION,
                    "omnitrinket.hud.saturation"),
            new ToggleBinding(KeyBindings.TOGGLE_FLY_LOCKED, PlayerSettings.FLY_LOCKED,
                    "omnitrinket.hud.fly_locked"),
            new ToggleBinding(KeyBindings.TOGGLE_FLY_NO_INERTIA, PlayerSettings.FLY_NO_INERTIA,
                    "omnitrinket.hud.fly_no_inertia"),
            new ToggleBinding(KeyBindings.TOGGLE_NO_OXYGEN, PlayerSettings.SURVIVAL_NO_OXYGEN,
                    "omnitrinket.hud.no_oxygen"),
            new ToggleBinding(KeyBindings.TOGGLE_SPRINT_RESUME, PlayerSettings.SPRINT_RESUME,
                    "omnitrinket.hud.sprint_resume"));

    /** 三态模式快捷键：按键循环 0 → 1 → 2 */
    private record ModeBinding(KeyMapping key, PlayerSettingKey<Integer> setting,
                               String hudKey, String[] modeKeys)
    {
    }

    private static final List<ModeBinding> MODE_BINDINGS = List.of(
            new ModeBinding(KeyBindings.TOGGLE_DAMAGE_IMMUNITY,
                    PlayerSettings.SURVIVAL_DAMAGE_MODE,
                    "omnitrinket.hud.damage_mode",
                    new String[]{
                            "omnitrinket.gui.off",
                            "omnitrinket.gui.survival.damageMode.allowVoid",
                            "omnitrinket.gui.survival.damageMode.full"
                    }));

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
        // 速度是服务端每 tick 写进 Abilities.flyingSpeed 的，所以必须发包给服务端改；
        // 改完由原版 abilities 包同步回客户端（客户端本地改不会有任何效果）。
        while (KeyBindings.CYCLE_FLY_SPEED.consumeClick())
        {
            if (wearing)
            {
                // HUD 显示的下一档直接取自本机镜像（服务端权威值的副本）。
                // ★ 不再从本机数据去"猜"——那样在多人服务器上会显示错值。
                double predicted = FlightSpeeds.next(
                        ClientSettingsCache.getDouble(PlayerSettings.FLY_SPEED));
                // 乐观写回镜像（与夜视开关同一套做法）：连按 X 时下一次预测才不会算错。
                // 服务端改完会回发同步包覆盖成本机权威值。
                ClientSettingsCache.setDouble(PlayerSettings.FLY_SPEED, predicted);
                NetworkHandler.CHANNEL.sendToServer(
                        new KeyActionPacket(KeyActionPacket.ACTION_CYCLE_FLY_SPEED));
                FlightHudOverlay.show(Component.translatable(
                        "omnitrinket.hud.fly_speed",
                        FlightSpeeds.format(predicted)));
            }
        }

        // 开关类快捷键（表驱动）：默认只有 Z（夜视）带键，其余（饱和 / 锁定飞行 /
        // 无惯性 / 不消耗氧气 / 撞墙后恢复冲刺）默认未绑定，玩家在"控制"里按需绑定。
        // 未绑定的键不会产生点击，天然无副作用。
        for (ToggleBinding t : TOGGLE_BINDINGS)
        {
            while (t.key().consumeClick())
            {
                if (wearing)
                {
                    toggleFlag(t);
                }
            }
        }

        // 三态模式：免伤键循环 0 → 1 → 2
        for (ModeBinding m : MODE_BINDINGS)
        {
            while (m.key().consumeClick())
            {
                if (wearing)
                {
                    cycleMode(m);
                }
            }
        }
    }

    /**
     * 翻转一个开关。
     *
     * <p>目标值客户端自己就知道，所以先改本地镜像（手感零延迟），
     * 再发包让服务端真正改；服务端改完回发权威值，天然对齐。</p>
     *
     * <p>随后在快捷栏上方弹一条提示（复用"开 / 关"文案键，不额外造词）。</p>
     */
    private static void toggleFlag(ToggleBinding t)
    {
        boolean next = !ClientSettingsCache.getBoolean(t.setting());
        ClientSettingsCache.setBoolean(t.setting(), next);
        NetworkHandler.CHANNEL.sendToServer(SetPlayerSettingsPacket.ofFlag(t.setting(), next));
        FlightHudOverlay.show(Component.translatable(t.hudKey(),
                Component.translatable(next ? "omnitrinket.gui.on" : "omnitrinket.gui.off")));
    }

    /**
     * 循环切换一个三态模式，并在 HUD 显示当前模式。
     */
    private static void cycleMode(ModeBinding m)
    {
        int max = m.modeKeys().length - 1;
        int current = Math.max(0, Math.min(max, ClientSettingsCache.getInt(m.setting())));
        int next = (current + 1) % m.modeKeys().length;
        ClientSettingsCache.setInt(m.setting(), next);
        NetworkHandler.CHANNEL.sendToServer(SetPlayerSettingsPacket.ofInt(m.setting(), next));
        FlightHudOverlay.show(Component.translatable(m.hudKey(),
                Component.translatable(m.modeKeys()[next])));
    }

    /** 把全部按键的点击队列排空 */
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
        for (ToggleBinding t : TOGGLE_BINDINGS)
        {
            // 开关类：同样只排空；未绑定的键不产生点击，天然无影响
            while (t.key().consumeClick())
            {
            }
        }
        for (ModeBinding m : MODE_BINDINGS)
        {
            while (m.key().consumeClick())
            {
            }
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
