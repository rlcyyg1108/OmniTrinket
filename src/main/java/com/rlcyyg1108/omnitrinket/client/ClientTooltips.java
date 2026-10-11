package com.rlcyyg1108.omnitrinket.client;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * 需要在客户端才能构造的 tooltip 文本片段。
 *
 * <p>典型用途：显示玩家<b>实际绑定的键位</b>。键位表属于客户端，
 * 所以这些方法必须放在 client 包，并由公共代码通过
 * {@code DistExecutor} 调用 —— 专用服务端绝不能直接引用本类。</p>
 *
 * <h3>配色约定</h3>
 * <ul>
 *   <li><b>键位</b>：§5 深紫</li>
 *   <li>功能描述行：绿色</li>
 *   <li>操作提示行：橙色（+ 斜体）</li>
 * </ul>
 *
 * <p><b>为什么键位能"只染自己"</b>：{@code Component} 的参数在渲染时会按
 * {@code 参数自身样式.applyTo(父样式)} 合并，<b>参数里显式设置过的字段优先</b>。
 * 所以只要把键名单独包成一个带颜色的组件当翻译参数传进去，
 * 父级给整句上的颜色就不会盖掉它。</p>
 */
public final class ClientTooltips
{
    /** 键位颜色：§5 深紫 */
    private static final ChatFormatting KEY_COLOR = ChatFormatting.DARK_PURPLE;
    /** 功能描述行颜色 */
    private static final ChatFormatting DESC_COLOR = ChatFormatting.GREEN;
    /** 操作提示行颜色 */
    private static final ChatFormatting HINT_COLOR = ChatFormatting.GOLD;

    private ClientTooltips()
    {
    }

    /** 把键名染成深紫，做成独立组件（父级颜色盖不掉它） */
    private static MutableComponent key(Component keyName)
    {
        return keyName.copy().withStyle(KEY_COLOR);
    }

    /**
     * 飞行：档位切换键提示。键位深紫，其余绿色。
     *
     * <p>不硬编码字母：取 {@code KeyBindings.CYCLE_FLY_SPEED} 当前绑定的键，
     * 玩家在"控制"里改成什么键，这里就显示什么键。</p>
     */
    public static Component flightSpeedKeyHint()
    {
        return Component.translatable("tooltip.omnitrinket.flight.keyHint",
                        key(KeyBindings.CYCLE_FLY_SPEED.getTranslatedKeyMessage()))
                .withStyle(DESC_COLOR);
    }

    /**
     * 增益：夜视切换键提示。键位深紫，其余绿色。
     */
    public static Component nightVisionKeyHint()
    {
        return Component.translatable("tooltip.omnitrinket.buff.keyHint",
                        key(KeyBindings.TOGGLE_NIGHT_VISION.getTranslatedKeyMessage()))
                .withStyle(DESC_COLOR);
    }

    /**
     * 打开设置的提示行：潜行键 + 使用键 或 打开设置键。
     *
     * <p>键位<b>全部取玩家实际绑定</b>，没有一个写死：
     * 潜行键来自 {@code Options.keyShift}、右键来自 {@code Options.keyUse}、
     * 打开设置来自 {@code KeyBindings.OPEN_SETTINGS}。</p>
     *
     * <p>"潜行键 + 使用键"是一个整体组合键，所以把中间的 {@code +} 也一起
     * 包进这个参数里并<b>同样染成键位色</b> —— 否则 {@code +} 会落在外层
     * 提示色（橙色）上，看起来像被切断了。</p>
     *
     * <p>键位深紫，其余橙色 + 斜体。</p>
     */
    public static Component openSettingsHint()
    {
        Minecraft mc = Minecraft.getInstance();

        // 组合键：潜行键 + "+" + 使用键，三段统一键位色
        Component combo = key(mc.options.keyShift.getTranslatedKeyMessage())
                .append(Component.literal("+").withStyle(KEY_COLOR))
                .append(key(mc.options.keyUse.getTranslatedKeyMessage()));

        return Component.translatable("tooltip.omnitrinket.hint.settings",
                        combo,
                        key(KeyBindings.OPEN_SETTINGS.getTranslatedKeyMessage()))
                .withStyle(HINT_COLOR, ChatFormatting.ITALIC);
    }
}
