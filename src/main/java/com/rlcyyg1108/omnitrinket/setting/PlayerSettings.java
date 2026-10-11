package com.rlcyyg1108.omnitrinket.setting;

import net.minecraft.nbt.CompoundTag;

/**
 * 玩家个人设置数据。
 *
 * 存储每个玩家独立的运行时设置（锁定飞行、夜视开关、移速/跳跃倍率），
 * 通过 Capability 挂载到 ServerPlayer 上，随玩家存档持久化。
 *
 * 与全局配置（Config）的区别：
 * - Config 是服主级的、全局唯一的
 * - PlayerSettings 是每个玩家各自一份的
 *
 * <p>所有可被客户端修改的字段都用 {@link #KEY_*} 常量作为 NBT 键名，
 * 服务端处理 {@code SetPlayerSettingsPacket} 时按这些键做白名单校验
 * （客户端<b>不能</b>改 {@code FlyGranted} 这类服务端状态）。</p>
 */
public class PlayerSettings
{
    // ==================== NBT 键名（同时也是网络包的白名单键） ====================
    public static final String KEY_FLY_GRANTED = "FlyGranted";
    public static final String KEY_FLY_LOCKED = "FlyLocked";
    public static final String KEY_NIGHT_VISION = "NightVision";
    public static final String KEY_JUMP_HEIGHT = "JumpHeightMultiplier";
    public static final String KEY_MOVE_SPEED = "MoveSpeedMultiplier";

    /** 夜视默认开启 */
    public static final boolean DEFAULT_NIGHT_VISION = true;
    /** 锁定飞行默认关闭 */
    public static final boolean DEFAULT_FLY_LOCKED = false;
    /** 跳跃高度倍率默认值 */
    public static final double DEFAULT_JUMP_HEIGHT_MULTIPLIER = 2.0D;
    /** 移动速度倍率默认值 */
    public static final double DEFAULT_MOVE_SPEED_MULTIPLIER = 2.0D;

    /** 跳跃高度倍率 / 移动速度倍率的取值范围（与设置界面输入框一致） */
    public static final double MIN_MOVE_MULTIPLIER = 1.0D;
    public static final double MAX_MOVE_MULTIPLIER = 100.0D;

    // ==================== 飞行 ====================
    /** 是否由本饰品授予了飞行能力（戴上后即使落地也保持，需显式关闭） */
    private boolean flyGranted = false;
    /**
     * 锁定飞行：开启后强制保持飞行状态、落地也不收起。
     *
     * <p>这是一个<b>可以随时翻转</b>的开关（界面里的开关直接改它），
     * 由 {@code FlightEvents} 每 tick 检查，因此随开随关，不需要重新装备。</p>
     */
    private boolean flyLocked = DEFAULT_FLY_LOCKED;

    // ==================== 增益 ====================
    /** 夜视开关（用药水效果实现，见 {@code feature.impl.BuffEvents}） */
    private boolean nightVision = DEFAULT_NIGHT_VISION;

    // ==================== 移动 ====================
    /**
     * 跳跃高度倍率：跳跃能达到的高度是原版的 {@code H} 倍，默认 2.0。
     *
     * <p><b>为什么放在 PlayerSettings 而不是 Config：</b>
     * {@code Config.COMMON} 在设置界面里改动<b>只影响客户端</b>
     * （Forge 的 COMMON 配置只在登录时从服务端同步一次）。
     * 而跳跃是"客户端预测 + 服务端验证"的：两端按不同倍率起跳，
     * 服务端会以位置异常为由把玩家拽回原高度 —— 表现就是"改了倍率没反应，
     * 甚至完全没效果"。放进服务端权威的 PlayerSettings 才能两端一致。</p>
     */
    private double jumpHeightMultiplier = DEFAULT_JUMP_HEIGHT_MULTIPLIER;
    /**
     * 移动速度倍率：走路与奔跑的移动速度是原版的 {@code N} 倍，默认 2.0。
     *
     * <p>与 {@link #jumpHeightMultiplier} 同理，必须是服务端权威的数据。</p>
     */
    private double moveSpeedMultiplier = DEFAULT_MOVE_SPEED_MULTIPLIER;

    // ==================== 标记 ====================
    /** 数据是否被修改过，用于决定是否需要同步到客户端 */
    private boolean dirty = false;

    // ------------------------------------------------------------------
    // Getter / Setter
    // ------------------------------------------------------------------

    public boolean isFlyGranted()
    {
        return flyGranted;
    }

    public void setFlyGranted(boolean granted)
    {
        if (this.flyGranted != granted)
        {
            this.flyGranted = granted;
            this.dirty = true;
        }
    }

    public boolean isFlyLocked()
    {
        return flyLocked;
    }

    public void setFlyLocked(boolean locked)
    {
        if (this.flyLocked != locked)
        {
            this.flyLocked = locked;
            this.dirty = true;
        }
    }

    public boolean isNightVision()
    {
        return nightVision;
    }

    public void setNightVision(boolean enabled)
    {
        if (this.nightVision != enabled)
        {
            this.nightVision = enabled;
            this.dirty = true;
        }
    }

    public double getJumpHeightMultiplier()
    {
        return jumpHeightMultiplier;
    }

    public void setJumpHeightMultiplier(double multiplier)
    {
        if (this.jumpHeightMultiplier != multiplier)
        {
            this.jumpHeightMultiplier = multiplier;
            this.dirty = true;
        }
    }

    public double getMoveSpeedMultiplier()
    {
        return moveSpeedMultiplier;
    }

    public void setMoveSpeedMultiplier(double multiplier)
    {
        if (this.moveSpeedMultiplier != multiplier)
        {
            this.moveSpeedMultiplier = multiplier;
            this.dirty = true;
        }
    }

    public boolean isDirty()
    {
        return dirty;
    }
    public void setDirty(boolean dirty)
    {
        this.dirty = dirty;
    }

    // ------------------------------------------------------------------
    // NBT 序列化
    // ------------------------------------------------------------------

    public void saveNBTData(CompoundTag tag)
    {
        tag.putBoolean(KEY_FLY_GRANTED, flyGranted);
        tag.putBoolean(KEY_FLY_LOCKED, flyLocked);
        tag.putBoolean(KEY_NIGHT_VISION, nightVision);
        tag.putDouble(KEY_JUMP_HEIGHT, jumpHeightMultiplier);
        tag.putDouble(KEY_MOVE_SPEED, moveSpeedMultiplier);
    }

    public void loadNBTData(CompoundTag tag)
    {
        this.flyGranted = tag.getBoolean(KEY_FLY_GRANTED);
        this.flyLocked = tag.contains(KEY_FLY_LOCKED)
                ? tag.getBoolean(KEY_FLY_LOCKED)
                : DEFAULT_FLY_LOCKED;
        // 旧存档里没有这些字段时回落到默认值，避免读成 0 导致"完全飞不动"
        this.nightVision = tag.contains(KEY_NIGHT_VISION)
                ? tag.getBoolean(KEY_NIGHT_VISION)
                : DEFAULT_NIGHT_VISION;
        this.jumpHeightMultiplier = tag.contains(KEY_JUMP_HEIGHT)
                ? tag.getDouble(KEY_JUMP_HEIGHT)
                : DEFAULT_JUMP_HEIGHT_MULTIPLIER;
        this.moveSpeedMultiplier = tag.contains(KEY_MOVE_SPEED)
                ? tag.getDouble(KEY_MOVE_SPEED)
                : DEFAULT_MOVE_SPEED_MULTIPLIER;
    }
}
