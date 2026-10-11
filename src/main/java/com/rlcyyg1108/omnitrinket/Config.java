package com.rlcyyg1108.omnitrinket;

import net.minecraftforge.common.ForgeConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

/**
 * OmniTrinket 的全局配置（服主级）。
 *
 * <p><b>现在只剩环境模块</b>：飞行 / 移动 / 生存 / 增益的设置都已经改成
 * "每玩家一份"，见 {@code setting.PlayerSettings}。</p>
 *
 * <p><b>为什么环境必须留在全局</b>：环境的两项（锁定晴天 / 终日为白）是
 * <b>维度级</b>改动，会影响该维度里所有人。如果按玩家来，多人服务器上就变成
 * "谁先戴上谁说了算"的抢权问题；保持全局才符合它的语义。</p>
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
        // ==================== 环境 ====================
        // 只有两个开关。不额外做权限门控（本模组假定使用者就是服主），
        // 也不需要"锁几点"这种细调。
        public final ForgeConfigSpec.BooleanValue lockWeatherClear;
        public final ForgeConfigSpec.BooleanValue perpetualDay;

        Common(ForgeConfigSpec.Builder builder)
        {
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
