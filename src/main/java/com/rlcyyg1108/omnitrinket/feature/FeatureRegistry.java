package com.rlcyyg1108.omnitrinket.feature;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 功能注册表。
 *
 * 所有 TrinketFeature 实现都注册到这里，由 OmniTrinketItem 统一遍历调用。
 * 新增功能只需在 {@link #init()} 中加一行注册即可。
 */
public final class FeatureRegistry
{
    private static final Map<String, TrinketFeature> FEATURES = new LinkedHashMap<>();
    private static boolean initialized = false;

    private FeatureRegistry()
    {
    }

    /**
     * 注册所有功能。应在 mod 构造阶段调用一次。
     */
    public static synchronized void init()
    {
        if (initialized)
        {
            return;
        }
        initialized = true;

        // 生存模块：完全免伤 / 水下挖掘不减速 / 水下不消耗氧气 / 去掉屏幕火焰动画
        register(new com.rlcyyg1108.omnitrinket.feature.impl.SurvivalFeature());

        // 移动模块：行走加速 / 跳跃增高（倍率为每玩家的 PlayerSettings 字段）
        register(new com.rlcyyg1108.omnitrinket.feature.impl.SprintFeature());

        // Buff 模块：夜视（药水效果） / 触及范围
        register(new com.rlcyyg1108.omnitrinket.feature.impl.BuffFeature());

        // 飞行模块：自带飞行 / 无惯性 / 飞行速度倍率（档位）/ 锁定飞行 / 去除浮空挖掘速度惩罚
        register(new com.rlcyyg1108.omnitrinket.feature.impl.FlightFeature());

        // 环境模块：晴天锁定 / 终日为白
        register(new com.rlcyyg1108.omnitrinket.feature.impl.EnvironmentFeature());
    }

    /**
     * 注册一个功能。id 重复时后者覆盖前者并给出警告。
     */
    public static void register(TrinketFeature feature)
    {
        FEATURES.put(feature.getId(), feature);
    }

    /**
     * 按 id 获取功能。
     */
    public static TrinketFeature get(String id)
    {
        return FEATURES.get(id);
    }

    /**
     * 获取全部已注册功能（不可变视图）。
     */
    public static List<TrinketFeature> all()
    {
        return Collections.unmodifiableList(new ArrayList<>(FEATURES.values()));
    }
}
