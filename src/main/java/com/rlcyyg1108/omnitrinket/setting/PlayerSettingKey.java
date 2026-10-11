package com.rlcyyg1108.omnitrinket.setting;

/**
 * 一个"每玩家设置项"的描述符。
 *
 * <p>把 <b>NBT 键名 / 默认值 / 允许范围 / 客户端能否修改</b> 这几件事绑在一个常量上，
 * 于是存档读写、网络包白名单、设置界面三者都不需要各自再维护一份字段清单 ——
 * 只在 {@link PlayerSettings} 里声明一次即可。</p>
 *
 * <h3>关于 nbtKey</h3>
 * <p>它同时是<b>存档键名</b>和<b>网络包键名</b>。改名字会让旧存档里的值读不出来
 * （回落成默认值），所以已经发布过的键名不要再动。</p>
 *
 * @param <T> 值类型，只支持 {@code Boolean} / {@code Double} / {@code Integer}
 */
public final class PlayerSettingKey<T>
{
    private final String nbtKey;
    private final T defaultValue;
    private final double min;
    private final double max;
    private final boolean clientWritable;

    private PlayerSettingKey(String nbtKey, T defaultValue, double min, double max, boolean clientWritable)
    {
        this.nbtKey = nbtKey;
        this.defaultValue = defaultValue;
        this.min = min;
        this.max = max;
        this.clientWritable = clientWritable;
    }

    /** 布尔项（客户端可以改） */
    public static PlayerSettingKey<Boolean> bool(String nbtKey, boolean defaultValue)
    {
        return new PlayerSettingKey<>(nbtKey, defaultValue, 0.0D, 0.0D, true);
    }

    /**
     * 布尔项，但<b>客户端不可写</b>。
     *
     * <p>用于纯服务端状态（例如"是否已由饰品授予飞行"），网络包的白名单会跳过它，
     * 客户端永远改不了。</p>
     */
    public static PlayerSettingKey<Boolean> serverBool(String nbtKey, boolean defaultValue)
    {
        return new PlayerSettingKey<>(nbtKey, defaultValue, 0.0D, 0.0D, false);
    }

    /** 浮点项（客户端可以改），写入时夹到 {@code [min, max]} */
    public static PlayerSettingKey<Double> dbl(String nbtKey, double defaultValue, double min, double max)
    {
        return new PlayerSettingKey<>(nbtKey, defaultValue, min, max, true);
    }

    /** 整数项（客户端可以改），写入时夹到 {@code [min, max]} */
    public static PlayerSettingKey<Integer> integer(String nbtKey, int defaultValue, int min, int max)
    {
        return new PlayerSettingKey<>(nbtKey, defaultValue, min, max, true);
    }

    // ------------------------------------------------------------------
    // 元数据
    // ------------------------------------------------------------------

    /** 存档键名，同时也是网络补丁的键名 */
    public String nbtKey()
    {
        return nbtKey;
    }

    /** 默认值：没存过、或旧存档缺这个键时用的值 */
    public T defaultValue()
    {
        return defaultValue;
    }

    /** 允许的最大值（仅数值项有意义） */
    public double max()
    {
        return max;
    }

    /** 允许的最小值（仅数值项有意义） */
    public double min()
    {
        return min;
    }

    /** 客户端能否通过 {@code SetPlayerSettingsPacket} 改这一项 */
    public boolean clientWritable()
    {
        return clientWritable;
    }

    // ------------------------------------------------------------------
    // 夹紧（仅数值项有意义）
    // ------------------------------------------------------------------

    /** 夹到 {@code [min, max]}；{@code NaN} 回落到默认值 */
    public double clampDouble(double value)
    {
        if (Double.isNaN(value))
        {
            return defaultValue instanceof Double fallback ? fallback : min;
        }
        return Math.max(min, Math.min(max, value));
    }

    /** 夹到 {@code [min, max]} */
    public int clampInt(int value)
    {
        return (int) Math.max(min, Math.min(max, value));
    }
}
