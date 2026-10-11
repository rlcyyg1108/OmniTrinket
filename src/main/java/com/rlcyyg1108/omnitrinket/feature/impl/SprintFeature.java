package com.rlcyyg1108.omnitrinket.feature.impl;

import com.rlcyyg1108.omnitrinket.setting.PlayerSettingsAccess;
import com.rlcyyg1108.omnitrinket.setting.PlayerSettings;
import net.minecraft.world.entity.player.Player;
import com.rlcyyg1108.omnitrinket.feature.TrinketFeature;

/**
 * 移动模块。
 *
 * 包含：
 * - 移动速度倍率（默认 2 倍，可调 1~10），走路与奔跑同时生效
 *   —— 通过同比例放大 {@code Abilities.walkingSpeed} 与 {@code MOVEMENT_SPEED}
 *   基准值实现，因此视场角保持与原版完全一致（见 {@link SprintEvents} 的说明）
 * - 跳跃高度倍率（默认 2 倍，可调 1~10），常驻生效
 *   —— 由 {@link JumpEvents} 监听 {@code LivingEvent.LivingJumpEvent}，
 *   在起跳初速上做加法（对齐原版 jump_boost 药水的模型）
 *
 * <p>两个倍率都是<b>每玩家</b>的 {@code PlayerSettings} 字段而不是全局配置：
 * Forge 的 COMMON 配置只在登录时从服务端同步一次，设置界面在运行时改它只改客户端，
 * 而跳跃是"客户端预测 + 服务端验证"的，两端不一致会被服务端拽回。</p>
 *
 * <p>本类只承担"功能描述 / 总开关"的角色，实际逻辑分别在
 * {@link SprintEvents}（移速，PlayerTickEvent）与 {@link JumpEvents}（跳跃，Forge 事件）里，
 * 都不是 curioTick —— curioTick 只在佩戴时触发，摘下饰品后状态就回收不掉了。</p>
 */
public class SprintFeature implements TrinketFeature
{
    public static final String ID = "sprint";

    @Override
    public String getId()
    {
        return ID;
    }

    @Override
    public boolean isEnabled(Player player)
    {
        return PlayerSettingsAccess.bool(player, PlayerSettings.SPRINT_ENABLED);
    }

    // 注意：移速 / 跳跃的实际逻辑**不**在这里驱动。
    // 移速由 SprintEvents（PlayerTickEvent）每 tick 重算，跳跃由 JumpEvents（Forge 事件）处理——
    // 若挂在 curioTick（只在佩戴时触发）上，摘下饰品后加成就再也摘不掉了。
}
