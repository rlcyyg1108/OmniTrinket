package com.rlcyyg1108.omnitrinket;

import net.minecraftforge.common.ForgeConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

/**
 * OmniTrinket 的全局配置（服主级）。
 *
 * 这里定义的是全局唯一的设置：各功能的总开关、默认值、数值上下限、黑白名单等。
 * 玩家个人运行时状态（锁定飞行、夜视等）请见 setting.PlayerSettings。
 *
 * 所有条目均可在游戏内设置 UI 中读写（UI 修改后立即生效并同步到本配置）。
 */
public class Config
{
    public static final ForgeConfigSpec SPEC;
    public static final Common COMMON;

    /**
     * "终日为白"冻结到的时间点（tick）。
     *
     * <p>6000 = 正午。这是<b>固定值</b>，不再做成配置项 ——
     * 环境模块只保留"锁定晴天 / 终日为白"两个开关。</p>
     */
    public static final int PERPETUAL_DAY_TIME = 6000;

    static
    {
        Pair<Common, ForgeConfigSpec> pair = new ForgeConfigSpec.Builder().configure(Common::new);
        COMMON = pair.getLeft();
        SPEC = pair.getRight();
    }

    public static class Common
    {
        // ==================== 飞行 ====================
        public final ForgeConfigSpec.BooleanValue flyEnabled;

        /**
         * 飞行无惯性：松开方向键立刻停住。
         *
         * <p>纯客户端实现（见 {@code NoInertiaEvents}）。曾试过做成可调的乘性衰减
         * 并挂在 {@code LocalPlayer.aiStep} 的 TAIL，实测完全无效——因为原版在
         * {@code Player.travel} 里有一行会把垂直速度强制覆盖
         * （{@code setDeltaMovement(x, d * 0.6, z)}），TAIL 位于其后，
         * 改好的速度又会被原版后续处理改回去。故退回 TickEvent + 硬清零这一版。</p>
         */
        public final ForgeConfigSpec.BooleanValue flyNoInertia;

        public final ForgeConfigSpec.BooleanValue flyNoMiningSlowdown;

        // -------- 飞行速度 --------
        // 服务端每 tick 把倍率写进原版 Abilities.flyingSpeed，由原版
        // ClientboundPlayerAbilitiesPacket 同步到客户端。
        // 好处：水平与垂直同时生效（沿用原版行为），零 Mixin，无同步链路问题。

        /**
         * 飞行速度倍率（原版 = 1.0）。
         *
         * <p>直接写进 {@code Abilities.flyingSpeed}，由原版包同步，
         * 客户端与服务端天然一致，不需要任何自定义同步或 Mixin。</p>
         *
         * <p>X 键档位切换改的就是这一个值（1/ 2 / 3 / 5 / 10 倍）。</p>
         */
        public final ForgeConfigSpec.DoubleValue flySpeedMultiplier;

        /**
         * 冲刺时的飞行速度倍率（默认 2.0）。
         *
         * <p>原版对飞行中的玩家硬编码了冲刺 ×2（{@code Player.getFlyingSpeed()} 里
         * {@code isSprinting() ? abilities.getFlyingSpeed() * 2 : ...}），
         * 所以这个值就是「按住疾跑键时的实际倍率」。</p>
         */
        public final ForgeConfigSpec.DoubleValue flySprintMultiplier;

        // ==================== 奔跑 ====================
        /**
         * 移动模块总开关。
         *
         * <p>注意：<b>移速倍率与跳跃高度倍率不在这里</b>，它们是每玩家的
         * {@code PlayerSettings} 字段。原因是 Forge 的 COMMON 配置只在登录时
         * 从服务端同步一次，设置界面在运行时改它只改客户端 ⇒ 两端值不一致
         * ⇒ 跳跃会被服务端以位置异常为由拽回。</p>
         */
        public final ForgeConfigSpec.BooleanValue sprintEnabled;

        /**
         * 撞墙后自动恢复冲刺。
         *
         * <p>原版撞到方块会直接终止冲刺状态，走两步就得重新按一下冲刺键。
         * 开启后在"离开墙面"的那一刻补回一段短暂的冲刺，
         * 让长距离奔跑不会被墙角频繁打断。</p>
         *
         * <p>纯客户端实现（见 {@code SprintResumeEvents}）：只影响本地预测，
         * 不改服务端认可的速度。</p>
         */
        public final ForgeConfigSpec.BooleanValue resumeSprintAfterHittingObstacle;

        /**
         * 撞墙补偿时长（tick）。
         *
         * <p>默认 10 tick = 0.5 秒，够跨过一格宽的障碍又不至于在墙根下"自己一直跑"。</p>
         */
        public final ForgeConfigSpec.IntValue sprintResumeTicks;

        // ==================== 生存 ====================
        public final ForgeConfigSpec.BooleanValue survivalEnabled;
        public final ForgeConfigSpec.BooleanValue fullDamageImmunity;
        public final ForgeConfigSpec.BooleanValue allowVoidDamage;
        public final ForgeConfigSpec.BooleanValue noUnderwaterMiningSlowdown;
        public final ForgeConfigSpec.BooleanValue noOxygenConsumption;
        public final ForgeConfigSpec.BooleanValue removeFireOverlay;

        // ==================== Buff ====================
        public final ForgeConfigSpec.BooleanValue buffEnabled;
        public final ForgeConfigSpec.DoubleValue reachDistance;

        // ==================== 环境 ====================
        // 只有两个开关。终日为白固定冻结在 PERPETUAL_DAY_TIME（正午），
        // 不做成可调项 —— 本模组假定使用者在单人/自己的服务器里拥有 OP，
        // 不额外做权限门控，也不需要"锁几点"这种细调。
        public final ForgeConfigSpec.BooleanValue lockWeatherClear;
        public final ForgeConfigSpec.BooleanValue perpetualDay;

        Common(ForgeConfigSpec.Builder builder)
        {
            // ---------------- 飞行 ----------------
            builder.comment("Flight settings").push("flight");
            flyEnabled = builder
                    .comment("Enable the flight feature")
                    .define("enabled", true);
            flyNoInertia = builder
                    .comment("Flight without inertia: stop immediately when releasing the movement keys")
                    .define("noInertia", true);
            flyNoMiningSlowdown = builder
                    .comment("Remove the floating/flight mining speed penalty (also covers the underwater penalty)")
                    .define("noMiningSlowdown", true);
            flySpeedMultiplier = builder
                    .comment("Flight speed multiplier (vanilla = 1.0). Applied to Abilities.flyingSpeed, "
                            + "so it affects both horizontal and vertical flight, synced by vanilla packets. "
                            + "X key cycles through 1/2/3/5/10.")
                    .defineInRange("speedMultiplier", 2.0D, 0.1D, 10.0D);
            flySprintMultiplier = builder
                    .comment("Flight speed multiplier while sprinting (holding the sprint key).")
                    .defineInRange("sprintMultiplier", 2.0D, 0.1D, 10.0D);
            builder.pop();

            // ---------------- 奔跑 ----------------
            builder.comment("Sprint settings").push("sprint");
            sprintEnabled = builder
                    .comment("Enable the movement feature (speed / jump multipliers live in per-player PlayerSettings)")
                    .define("enabled", true);
            resumeSprintAfterHittingObstacle = builder
                    .comment("Resume sprinting automatically after walking away from an obstacle")
                    .define("resumeSprintAfterHittingObstacle", true);
            sprintResumeTicks = builder
                    .comment("Duration of the sprint resume after leaving an obstacle, in ticks")
                    .defineInRange("sprintResumeTicks", 10, 1, 100);
            builder.pop();

            // ---------------- 生存 ----------------
            builder.comment("Survival settings").push("survival");
            survivalEnabled = builder
                    .comment("Enable the survival feature")
                    .define("enabled", true);
            fullDamageImmunity = builder
                    .comment("Make the wearer immune to all damage")
                    .define("fullDamageImmunity", true);
            allowVoidDamage = builder
                    .comment("Whether the void damage is still applied (default true)")
                    .define("allowVoidDamage", true);
            noUnderwaterMiningSlowdown = builder
                    .comment("No mining slowdown underwater")
                    .define("noUnderwaterMiningSlowdown", true);
            noOxygenConsumption = builder
                    .comment("No oxygen consumption underwater")
                    .define("noOxygenConsumption", true);
            removeFireOverlay = builder
                    .comment("Remove the on-fire screen overlay")
                    .define("removeFireOverlay", true);
            builder.pop();

            // ---------------- Buff ----------------
            builder.comment("Buff settings").push("buff");
            buffEnabled = builder
                    .comment("Enable the buff feature")
                    .define("enabled", true);
            reachDistance = builder
                    .comment("Block/entity interaction range in blocks")
                    .defineInRange("reachDistance", 15.0D, 0.0D, 100.0D);
            builder.pop();

            // ---------------- 环境 ----------------
            builder.comment("Environment settings").push("environment");
            lockWeatherClear = builder
                    .comment("Lock weather to clear and prevent weather changes")
                    .define("lockWeatherClear", false);
            perpetualDay = builder
                    .comment("Enable perpetual day (freeze the time of day at noon, 6000 ticks)")
                    .define("perpetualDay", false);
            builder.pop();
        }
    }
}
