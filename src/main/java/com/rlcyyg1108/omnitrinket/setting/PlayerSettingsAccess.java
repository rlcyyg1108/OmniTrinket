package com.rlcyyg1108.omnitrinket.setting;

import com.rlcyyg1108.omnitrinket.network.ClientSettingsCache;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;

/**
 * 「每玩家设置」的<b>双端</b>读取入口。
 *
 * <h3>为什么必须分端</h3>
 * <p>{@link PlayerSettings} 挂在 Forge Capability 上，而 capability 只在
 * <b>服务端</b>被写入（{@code PlayerSettingsHandler} 用
 * {@code AttachCapabilitiesEvent<Entity>}，客户端玩家同样会被挂上一份，
 * 但那份永远是初始默认值，同步包只写 {@link ClientSettingsCache}、
 * 从不回写 capability）。</p>
 *
 * <p>所以凡是"客户端也要用到"的每玩家数据，都必须像这样分端取值：
 * <b>服务端读 capability，客户端读本机镜像</b>。</p>
 *
 * <p>之前移速 / 跳跃倍率放在 {@code Config.COMMON} 里看起来更简单，但那是
 * <b>另一个更隐蔽的坑</b>：Forge 的 COMMON 配置只在登录时从服务端同步一次，
 * 设置界面在运行时改它<b>只改客户端</b>。跳跃是"客户端预测 + 服务端验证"的，
 * 两端按不同倍率起跳会被服务端以位置异常为由拽回 —— 表现就是"改了没反应"。</p>
 *
 * <h3>用法</h3>
 * <pre>{@code
 * double speed = PlayerSettingsAccess.dbl(player, PlayerSettings.MOVE_SPEED);
 * }</pre>
 */
public final class PlayerSettingsAccess
{
    private PlayerSettingsAccess()
    {
    }

    /** 读布尔项：服务端走 capability，客户端走本机镜像 */
    public static boolean bool(Player player, PlayerSettingKey<Boolean> key)
    {
        if (player == null)
        {
            return key.defaultValue();
        }
        Boolean value = player.level().isClientSide()
                ? DistExecutor.unsafeCallWhenOn(Dist.CLIENT, () -> () -> ClientSettingsCache.getBoolean(key))
                : serverBool(player, key);
        return value == null ? key.defaultValue() : value;
    }

    /** 读浮点项：服务端走 capability，客户端走本机镜像 */
    public static double dbl(Player player, PlayerSettingKey<Double> key)
    {
        if (player == null)
        {
            return key.defaultValue();
        }
        Double value = player.level().isClientSide()
                ? DistExecutor.unsafeCallWhenOn(Dist.CLIENT, () -> () -> ClientSettingsCache.getDouble(key))
                : serverDouble(player, key);
        return value == null ? key.defaultValue() : value;
    }

    /** 读整数项：服务端走 capability，客户端走本机镜像 */
    public static int integer(Player player, PlayerSettingKey<Integer> key)
    {
        if (player == null)
        {
            return key.defaultValue();
        }
        Integer value = player.level().isClientSide()
                ? DistExecutor.unsafeCallWhenOn(Dist.CLIENT, () -> () -> ClientSettingsCache.getInt(key))
                : serverInt(player, key);
        return value == null ? key.defaultValue() : value;
    }

    // ------------------------------------------------------------------
    // 内部：服务端取值（取不到 capability 时返回 null，由调用方回落默认值）
    // ------------------------------------------------------------------

    private static Boolean serverBool(Player player, PlayerSettingKey<Boolean> key)
    {
        PlayerSettings settings = PlayerSettingsHelper.get(player).orElse(null);
        return settings == null ? null : settings.getBoolean(key);
    }

    private static Double serverDouble(Player player, PlayerSettingKey<Double> key)
    {
        PlayerSettings settings = PlayerSettingsHelper.get(player).orElse(null);
        return settings == null ? null : settings.getDouble(key);
    }

    private static Integer serverInt(Player player, PlayerSettingKey<Integer> key)
    {
        PlayerSettings settings = PlayerSettingsHelper.get(player).orElse(null);
        return settings == null ? null : settings.getInt(key);
    }
}
