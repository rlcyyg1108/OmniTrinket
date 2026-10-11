package com.rlcyyg1108.omnitrinket.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.rlcyyg1108.omnitrinket.OmniTrinket;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

/**
 * 按键绑定注册。
 *
 * - N：打开设置界面（界面打开时再按一次 = 视为"完成"并关闭，见 OmniTrinketSettingsScreen）
 * - X：切换飞行速度档位（1/2/3/5/10 倍循环）
 * - Z：切换夜视开关
 *
 * ⚠️ 用户明确表示"按键冲突先不管"，因此这里不注册 conflict context 之外的处理。
 */
@Mod.EventBusSubscriber(modid = OmniTrinket.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class KeyBindings
{
    public static final String CATEGORY = "key.categories.omnitrinket";

    /** 打开设置界面（默认 N） */
    public static final KeyMapping OPEN_SETTINGS = new KeyMapping(
            "key.omnitrinket.open_settings",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_N,
            CATEGORY
    );

    /**
     * 切换飞行速度档位（默认 X）。
     *
     * <p>改的是 {@code Config.flySpeedMultiplier} 这一个值（1/2/3/5/10 循环）。
     * 它写进原版 {@code Abilities.flyingSpeed}，所以<b>水平与垂直同时生效</b>。</p>
     */
    public static final KeyMapping CYCLE_FLY_SPEED = new KeyMapping(
            "key.omnitrinket.cycle_fly_speed",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_X,
            CATEGORY
    );

    /** 切换夜视开关（默认 Z） */
    public static final KeyMapping TOGGLE_NIGHT_VISION = new KeyMapping(
            "key.omnitrinket.toggle_night_vision",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_Z,
            CATEGORY
    );

    private KeyBindings()
    {
    }

    @SubscribeEvent
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event)
    {
        event.register(OPEN_SETTINGS);
        event.register(CYCLE_FLY_SPEED);
        event.register(TOGGLE_NIGHT_VISION);
    }
}
