package com.rlcyyg1108.omnitrinket.feature;

import net.minecraft.world.entity.player.Player;
import com.rlcyyg1108.omnitrinket.setting.PlayerSettings;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import top.theillusivec4.curios.api.SlotContext;

/**
 * 饰品功能接口。
 *
 * <p>每个功能（飞行、奔跑、生存、Buff、环境）都应实现本接口并注册到
 * {@link FeatureRegistry}，由主循环统一遍历调用。这样避免所有逻辑堆积在
 * OmniTrinketItem 中。</p>
 *
 * <p>所有方法都提供了默认空实现，功能类只需覆写自己关心的方法。</p>
 */
public interface TrinketFeature
{
    /**
     * 功能的唯一标识，例如 "flight"、"sprint"。
     */
    String getId();

    /**
     * 当饰品被卸下时调用。
     */
    default void onUnequip(SlotContext ctx, ItemStack stack)
    {
    }

    /**
     * 每 tick 调用（仅当饰品处于装备状态）。
     *
     * @param ctx  Curios 槽位上下文，可取 entity()/identifier()/index()
     * @param cfg  该玩家的个人设置
     */
    default void curioTick(SlotContext ctx, ItemStack stack, PlayerSettings cfg)
    {
    }

    /**
     * 本功能对某个玩家是否启用。
     *
     * <p>总开关也是"每玩家一份"的，所以判定必须带上玩家：
     * 服务端读 capability、客户端读本机镜像（见 {@code PlayerSettingsAccess}）。</p>
     *
     * @param player 目标玩家；tooltip 这类拿不到玩家的场合会传 {@code null}，
     *               实现应按默认值处理
     */
    default boolean isEnabled(@Nullable Player player)
    {
        return true;
    }

    /**
     * 该功能额外的一行 tooltip（可选），例如快捷键提示。
     *
     * <p>返回 {@code null} 表示没有额外行。<b>颜色由构造方自己决定</b>
     * （见 {@code ClientTooltips}：键位深紫、其余绿色/橙色），
     * {@code OmniTrinketItem} 不再统一染色，否则会把键位的紫色盖掉。</p>
     *
     * <p>用途：让"快捷键提示"跟着功能走，且能显示玩家<b>实际绑定的键位</b>
     * 而不是硬编码的字母。需要客户端信息的实现应当用
     * {@code DistExecutor} 包一层，避免专用服务端加载客户端类。</p>
     */
    default @Nullable Component extraTooltip()
    {
        return null;
    }
}
