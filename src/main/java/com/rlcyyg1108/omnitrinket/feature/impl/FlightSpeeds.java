package com.rlcyyg1108.omnitrinket.feature.impl;

import java.util.List;

/**
 * 飞行速度的档位预设。
 *
 * <p>X 键在 1 / 2 / 3 / 5 / 10 倍之间循环，修改的是
 * {@code Config.COMMON.flySpeedMultiplier} 这一个值。</p>
 */
public final class FlightSpeeds
{
    /** 档位预设（严格递增） */
    public static final List<Double> PRESETS = List.of(1.0D, 2.0D, 3.0D, 5.0D, 10.0D);

    /** 判定"是不是档位"时允许的偏差：取最小档位间距（1.0）的一半 */
    private static final double TOLERANCE = 0.25D;

    private FlightSpeeds()
    {
    }

    /** 当前值落在第几档（0 起）；不在任何档位上返回 -1 */
    public static int levelOf(double multiplier)
    {
        int best = -1;
        double bestDistance = Double.MAX_VALUE;
        for (int i = 0; i < PRESETS.size(); i++)
        {
            double distance = Math.abs(PRESETS.get(i) - multiplier);
            if (distance < bestDistance)
            {
                bestDistance = distance;
                best = i;
            }
        }
        return bestDistance <= TOLERANCE ? best : -1;
    }

    /** 显示用文本：3.0 → "3x" */
    public static String format(double multiplier)
    {
        return (multiplier == Math.floor(multiplier))
                ? (long) multiplier + "x"
                : multiplier + "x";
    }

    /** 下一个档位（循环）；不在档位上时返回第一档 */
    public static double next(double multiplier)
    {
        int level = levelOf(multiplier);
        if (level < 0)
        {
            return PRESETS.get(0);
        }
        return PRESETS.get((level + 1) % PRESETS.size());
    }
}
