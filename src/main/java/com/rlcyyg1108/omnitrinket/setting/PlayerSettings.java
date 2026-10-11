package com.rlcyyg1108.omnitrinket.setting;

import net.minecraft.nbt.CompoundTag;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 玩家个人设置数据。
 *
 * <p>存储每个玩家独立的运行时设置（锁定飞行、夜视、移速 / 跳跃倍率等），
 * 通过 Capability 挂载到玩家身上，随存档持久化。</p>
 *
 * <p>与全局配置 {@code Config} 的区别：{@code Config} 是服主级、全局唯一的；
 * 本类是<b>每个玩家各自一份</b>。</p>
 *
 * <h3>字段描述表驱动</h3>
 * <p>所有设置项都声明成 {@link PlayerSettingKey} 常量，再按类型归进
 * {@link #BOOLEAN_KEYS} / {@link #DOUBLE_KEYS} / {@link #INT_KEYS} 三张表。
 * 于是下面这些事<b>全部由表驱动</b>，新增一个设置项只需在对应表里加一行：</p>
 * <ul>
 *   <li>存档读写（{@link #saveNBTData} / {@link #loadNBTData}）</li>
 *   <li>网络包的白名单与范围夹紧（{@code SetPlayerSettingsPacket}）</li>
 *   <li>客户端镜像取值（{@code ClientSettingsCache}）</li>
 *   <li>设置界面的"重置全部"</li>
 * </ul>
 *
 * <h3>取值语义</h3>
 * <p>内部是稀疏存储：<b>没存过的键自动回落到描述符里的默认值</b>，
 * 所以旧存档缺少新加的字段时天然读成默认值，不需要额外写兼容代码。</p>
 */
public class PlayerSettings
{
    /** 倍率类数值项的允许范围（与设置界面的输入框一致） */
    public static final double MIN_MULTIPLIER = 1.0D;
    public static final double MAX_MULTIPLIER = 100.0D;

    /** 飞行速度倍率的允许范围（比移速窄：原版飞行就慢，100 倍没有意义） */
    public static final double MIN_FLY_MULTIPLIER = 0.1D;
    public static final double MAX_FLY_MULTIPLIER = 10.0D;

    // ==================================================================
    // 设置项声明（新增字段 = 在下面加一个常量 + 加进对应类型表）
    // ==================================================================

    // ---------------- 飞行 ----------------

    /** 飞行模块总开关 */
    public static final PlayerSettingKey<Boolean> FLY_ENABLED =
            PlayerSettingKey.bool("FlyEnabled", true);

    /** 飞行无惯性：松开方向键立刻停住（纯客户端实现，见 {@code NoInertiaEvents}） */
    public static final PlayerSettingKey<Boolean> FLY_NO_INERTIA =
            PlayerSettingKey.bool("FlyNoInertia", true);

    /** 去除浮空挖掘速度惩罚（水中、飞行、悬空一并覆盖） */
    public static final PlayerSettingKey<Boolean> FLY_NO_MINING_SLOWDOWN =
            PlayerSettingKey.bool("FlyNoMiningSlowdown", true);

    /**
     * 飞行速度倍率（原版 = 1.0）。
     *
     * <p>服务端每 tick 把它写进原版 {@code Abilities.flyingSpeed}，
     * 由原版包同步 —— 水平与垂直同时生效。X 键档位切换改的就是这一个值。</p>
     */
    public static final PlayerSettingKey<Double> FLY_SPEED =
            PlayerSettingKey.dbl("FlySpeedMultiplier", 2.0D, MIN_FLY_MULTIPLIER, MAX_FLY_MULTIPLIER);

    /**
     * 冲刺飞行速度<b>倍率</b>：冲刺时的实际速度 = 飞行速度 × 这个值。
     *
     * <p>是独立乘区，不是替换（见 {@code FlightEvents.targetFlyingSpeed}）。</p>
     */
    public static final PlayerSettingKey<Double> FLY_SPRINT =
            PlayerSettingKey.dbl("FlySprintMultiplier", 2.0D, MIN_FLY_MULTIPLIER, MAX_FLY_MULTIPLIER);

    // ---------------- 移动 ----------------

    /** 移动模块总开关（行走加速 / 跳跃增高） */
    public static final PlayerSettingKey<Boolean> SPRINT_ENABLED =
            PlayerSettingKey.bool("SprintEnabled", true);

    /** 撞墙后自动恢复冲刺：离开墙面时补回一段短暂冲刺，避免长跑被墙角频繁打断（纯客户端） */
    public static final PlayerSettingKey<Boolean> SPRINT_RESUME =
            PlayerSettingKey.bool("ResumeSprintAfterObstacle", true);

    /** 撞墙补偿时长（tick）：10 tick = 0.5 秒，够跨过一格宽又不至于在墙根自己一直跑 */
    public static final PlayerSettingKey<Integer> SPRINT_RESUME_TICKS =
            PlayerSettingKey.integer("SprintResumeTicks", 10, 1, 100);

    // ---------------- 生存 ----------------

    /** 生存模块总开关 */
    public static final PlayerSettingKey<Boolean> SURVIVAL_ENABLED =
            PlayerSettingKey.bool("SurvivalEnabled", true);

    /** 免疫全部伤害 */
    public static final PlayerSettingKey<Boolean> SURVIVAL_NO_DAMAGE =
            PlayerSettingKey.bool("FullDamageImmunity", true);

    /** 是否保留虚空伤害（默认保留，避免掉进虚空后永远脱不了身） */
    public static final PlayerSettingKey<Boolean> SURVIVAL_VOID_DAMAGE =
            PlayerSettingKey.bool("AllowVoidDamage", true);

    /** 水下挖掘不减速 */
    public static final PlayerSettingKey<Boolean> SURVIVAL_NO_WATER_MINING =
            PlayerSettingKey.bool("NoUnderwaterMiningSlowdown", true);

    /** 水下不消耗氧气 */
    public static final PlayerSettingKey<Boolean> SURVIVAL_NO_OXYGEN =
            PlayerSettingKey.bool("NoOxygenConsumption", true);

    /** 去除着火屏幕特效（火焰遮罩，纯客户端） */
    public static final PlayerSettingKey<Boolean> SURVIVAL_NO_FIRE_OVERLAY =
            PlayerSettingKey.bool("RemoveFireOverlay", true);

    // ---------------- 增益 ----------------

    /** 夜视开关（用药水效果实现，见 {@code feature.impl.BuffEvents}） */
    public static final PlayerSettingKey<Boolean> NIGHT_VISION =
            PlayerSettingKey.bool("NightVision", true);

    /** 饱和开关：佩戴期间饱食度保持常满（与夜视同一套隐藏效果写法，见 {@code feature.impl.BuffEvents}） */
    public static final PlayerSettingKey<Boolean> SATURATION =
            PlayerSettingKey.bool("Saturation", true);

    /** 增益模块总开关（夜视 / 饱和 / 触及范围） */
    public static final PlayerSettingKey<Boolean> BUFF_ENABLED =
            PlayerSettingKey.bool("BuffEnabled", true);

    /** 触及范围：方块与实体的交互距离<b>总格数</b>（原版 4.5 / 3.0） */
    public static final PlayerSettingKey<Double> BUFF_REACH =
            PlayerSettingKey.dbl("ReachDistance", 15.0D, 0.0D, 100.0D);

    // ---------------- 状态 ----------------

    /**
     * 是否由本饰品授予了飞行能力（戴上后即使落地也保持，需显式关闭）。
     *
     * <p><b>客户端不可写</b>：这是纯服务端状态。</p>
     */
    public static final PlayerSettingKey<Boolean> FLY_GRANTED =
            PlayerSettingKey.serverBool("FlyGranted", false);

    /** 锁定飞行：开启后强制保持飞行状态、落地也不收起（可随时翻转，不必重新装备） */
    public static final PlayerSettingKey<Boolean> FLY_LOCKED =
            PlayerSettingKey.bool("FlyLocked", false);

    /** 跳跃高度倍率：跳跃能达到的高度是原版的 {@code N} 倍 */
    public static final PlayerSettingKey<Double> JUMP_HEIGHT =
            PlayerSettingKey.dbl("JumpHeightMultiplier", 2.0D, MIN_MULTIPLIER, MAX_MULTIPLIER);

    /** 移动速度倍率：走路与奔跑的速度是原版的 {@code N} 倍 */
    public static final PlayerSettingKey<Double> MOVE_SPEED =
            PlayerSettingKey.dbl("MoveSpeedMultiplier", 2.0D, MIN_MULTIPLIER, MAX_MULTIPLIER);

    // ---- 按类型分组，仅供遍历（顺序无关）----

    /** 全部布尔项 */
    public static final List<PlayerSettingKey<Boolean>> BOOLEAN_KEYS =
            List.of(FLY_ENABLED, FLY_NO_INERTIA, FLY_NO_MINING_SLOWDOWN,
                    SPRINT_ENABLED, SPRINT_RESUME,
                    SURVIVAL_ENABLED, SURVIVAL_NO_DAMAGE, SURVIVAL_VOID_DAMAGE,
                    SURVIVAL_NO_WATER_MINING, SURVIVAL_NO_OXYGEN, SURVIVAL_NO_FIRE_OVERLAY,
                    NIGHT_VISION, SATURATION, BUFF_ENABLED,
                    FLY_GRANTED, FLY_LOCKED);

    /** 全部浮点项 */
    public static final List<PlayerSettingKey<Double>> DOUBLE_KEYS =
            List.of(FLY_SPEED, FLY_SPRINT, JUMP_HEIGHT, MOVE_SPEED, BUFF_REACH);

    /** 全部整数项 */
    public static final List<PlayerSettingKey<Integer>> INT_KEYS =
            List.of(SPRINT_RESUME_TICKS);

    // ==================================================================
    // 数据
    // ==================================================================

    /** 稀疏存储：只放"被显式设过"的键，取不到的自动回落默认值 */
    private final Map<String, Boolean> booleans = new HashMap<>();
    private final Map<String, Double> doubles = new HashMap<>();
    private final Map<String, Integer> integers = new HashMap<>();

    /** 数据是否被修改过，用于决定是否需要同步到客户端 */
    private boolean dirty = false;

    // ------------------------------------------------------------------
    // 读取
    // ------------------------------------------------------------------

    public boolean getBoolean(PlayerSettingKey<Boolean> key)
    {
        Boolean value = booleans.get(key.nbtKey());
        return value != null ? value : key.defaultValue();
    }

    public double getDouble(PlayerSettingKey<Double> key)
    {
        Double value = doubles.get(key.nbtKey());
        return value != null ? value : key.defaultValue();
    }

    public int getInt(PlayerSettingKey<Integer> key)
    {
        Integer value = integers.get(key.nbtKey());
        return value != null ? value : key.defaultValue();
    }

    // ------------------------------------------------------------------
    // 写入（数值项在这里统一夹紧，是唯一的收口点）
    // ------------------------------------------------------------------

    public void setBoolean(PlayerSettingKey<Boolean> key, boolean value)
    {
        if (Boolean.valueOf(value).equals(booleans.get(key.nbtKey())))
        {
            return;
        }
        booleans.put(key.nbtKey(), value);
        this.dirty = true;
    }

    public void setDouble(PlayerSettingKey<Double> key, double value)
    {
        double clamped = key.clampDouble(value);
        if (Double.valueOf(clamped).equals(doubles.get(key.nbtKey())))
        {
            return;
        }
        doubles.put(key.nbtKey(), clamped);
        this.dirty = true;
    }

    public void setInt(PlayerSettingKey<Integer> key, int value)
    {
        int clamped = key.clampInt(value);
        if (Integer.valueOf(clamped).equals(integers.get(key.nbtKey())))
        {
            return;
        }
        integers.put(key.nbtKey(), clamped);
        this.dirty = true;
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
    // NBT 序列化（表驱动）
    // ------------------------------------------------------------------

    public void saveNBTData(CompoundTag tag)
    {
        for (PlayerSettingKey<Boolean> key : BOOLEAN_KEYS)
        {
            tag.putBoolean(key.nbtKey(), getBoolean(key));
        }
        for (PlayerSettingKey<Double> key : DOUBLE_KEYS)
        {
            tag.putDouble(key.nbtKey(), getDouble(key));
        }
        for (PlayerSettingKey<Integer> key : INT_KEYS)
        {
            tag.putInt(key.nbtKey(), getInt(key));
        }
    }

    public void loadNBTData(CompoundTag tag)
    {
        booleans.clear();
        doubles.clear();
        integers.clear();

        for (PlayerSettingKey<Boolean> key : BOOLEAN_KEYS)
        {
            // 旧存档缺这个键就跳过 —— 保持"没存过"的状态，取值时自动回落默认值
            if (tag.contains(key.nbtKey()))
            {
                booleans.put(key.nbtKey(), tag.getBoolean(key.nbtKey()));
            }
        }
        for (PlayerSettingKey<Double> key : DOUBLE_KEYS)
        {
            if (tag.contains(key.nbtKey()))
            {
                doubles.put(key.nbtKey(), key.clampDouble(tag.getDouble(key.nbtKey())));
            }
        }
        for (PlayerSettingKey<Integer> key : INT_KEYS)
        {
            if (tag.contains(key.nbtKey()))
            {
                integers.put(key.nbtKey(), key.clampInt(tag.getInt(key.nbtKey())));
            }
        }
    }
}
