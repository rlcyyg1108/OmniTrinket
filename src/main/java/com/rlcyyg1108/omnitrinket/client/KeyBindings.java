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
 * <h3>默认键位约定</h3>
 * <ul>
 *   <li><b>N</b>：打开设置界面（界面打开时再按一次 = 视为"完成"并关闭）</li>
 *   <li><b>X</b>：切换飞行速度档位（1/2/3/5/10 倍循环）</li>
 *   <li><b>Z</b>：切换夜视开关</li>
 * </ul>
 * <p>除以上三个高频键外，其余"开关类"键位<b>默认全部未绑定</b>，
 * 把键位选择权留给玩家 —— 在"控制"的"全能饰品"分类里按需绑定。</p>
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
     * <p>改的是<b>自己</b>的 {@code PlayerSettings.FLY_SPEED}（1/2/3/5/10 循环）。
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

    // ------------------------------------------------------------------
    // 以下"开关类"键位默认全部未绑定（"控制"里显示"未绑定"），键位交由玩家自选。
    // 绑定后行为与 Z（夜视）完全一致：翻转该项设置 + 快捷栏上方弹提示。
    // ------------------------------------------------------------------

    /** 切换饱和开关（默认未绑定） */
    public static final KeyMapping TOGGLE_SATURATION = new KeyMapping(
            "key.omnitrinket.toggle_saturation",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_UNKNOWN,
            CATEGORY
    );

    /** 切换锁定飞行（默认未绑定） */
    public static final KeyMapping TOGGLE_FLY_LOCKED = new KeyMapping(
            "key.omnitrinket.toggle_fly_locked",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_UNKNOWN,
            CATEGORY
    );

    /** 切换无惯性（默认未绑定） */
    public static final KeyMapping TOGGLE_FLY_NO_INERTIA = new KeyMapping(
            "key.omnitrinket.toggle_fly_no_inertia",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_UNKNOWN,
            CATEGORY
    );

    /** 切换免疫全部伤害（默认未绑定） */
    public static final KeyMapping TOGGLE_DAMAGE_IMMUNITY = new KeyMapping(
            "key.omnitrinket.toggle_damage_immunity",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_UNKNOWN,
            CATEGORY
    );

    /** 切换不消耗氧气（默认未绑定） */
    public static final KeyMapping TOGGLE_NO_OXYGEN = new KeyMapping(
            "key.omnitrinket.toggle_no_oxygen",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_UNKNOWN,
            CATEGORY
    );

    /** 切换撞墙后恢复冲刺（默认未绑定） */
    public static final KeyMapping TOGGLE_SPRINT_RESUME = new KeyMapping(
            "key.omnitrinket.toggle_sprint_resume",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_UNKNOWN,
            CATEGORY
    );

    /** 切换保留虚空伤害（默认未绑定） */
    public static final KeyMapping TOGGLE_VOID_DAMAGE = new KeyMapping(
            "key.omnitrinket.toggle_void_damage",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_UNKNOWN,
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
        event.register(TOGGLE_SATURATION);
        event.register(TOGGLE_FLY_LOCKED);
        event.register(TOGGLE_FLY_NO_INERTIA);
        event.register(TOGGLE_DAMAGE_IMMUNITY);
        event.register(TOGGLE_NO_OXYGEN);
        event.register(TOGGLE_SPRINT_RESUME);
        event.register(TOGGLE_VOID_DAMAGE);
    }
}
