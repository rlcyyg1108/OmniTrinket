package com.rlcyyg1108.omnitrinket.network;

import com.rlcyyg1108.omnitrinket.setting.PlayerSettingKey;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * 客户端侧的玩家设置镜像。
 *
 * <p>HUD、设置界面都是纯客户端的，但每玩家设置存在服务端 Capability 里，
 * 因此服务端每次改动都会通过 {@link SyncPlayerSettingsPacket} 下发一份，
 * 存在这里供渲染与界面直接读取。</p>
 *
 * <p>取值一律走 {@link PlayerSettingKey} 描述符：<b>镜像里没有的键自动回落到
 * 描述符的默认值</b>，所以"同步包还没到"这件事天然是安全的。</p>
 *
 * <p>{@link #setBoolean} / {@link #setDouble} / {@link #setInt} 用于界面上的
 * "乐观更新"：点一下立刻改本地镜像，手感零延迟；随后服务端的同步包会覆盖成权威值。
 * 键名与描述符一致，所以覆盖是自然发生的。</p>
 */
@OnlyIn(Dist.CLIENT)
public final class ClientSettingsCache
{
    private static final CompoundTag DATA = new CompoundTag();

    private ClientSettingsCache()
    {
    }

    /** 服务端下发时写入 */
    public static void apply(CompoundTag tag)
    {
        // 先清空再整体替换，避免旧字段残留。
        // ⚠️ 不要写 `for (String key : DATA.getAllKeys()) DATA.remove(key);`：
        // CompoundTag.getAllKeys() 返回的是内部 Map 的 keySet() **实时视图**（字节码
        // 就是 `return this.tags.keySet();`），边遍历边删会抛 ConcurrentModificationException。
        // 那个异常曾让客户端每 tick 崩溃，进而所有设置项都"改不动"。
        // 直接对视图 clear() 既安全又确实清得掉 DATA。
        DATA.getAllKeys().clear();
        DATA.merge(tag);
    }

    /** 切换到别的存档 / 退出世界时清空，避免带着上局的数值显示 */
    public static void clear()
    {
        // 同上：getAllKeys() 是内部视图，必须用它的 clear() 而不是迭代 + remove
        DATA.getAllKeys().clear();
    }

    // ------------------------------------------------------------------
    // 读取（表驱动，缺键回落默认值）
    // ------------------------------------------------------------------

    public static boolean getBoolean(PlayerSettingKey<Boolean> key)
    {
        return DATA.contains(key.nbtKey()) ? DATA.getBoolean(key.nbtKey()) : key.defaultValue();
    }

    public static double getDouble(PlayerSettingKey<Double> key)
    {
        return DATA.contains(key.nbtKey()) ? DATA.getDouble(key.nbtKey()) : key.defaultValue();
    }

    public static int getInt(PlayerSettingKey<Integer> key)
    {
        return DATA.contains(key.nbtKey()) ? DATA.getInt(key.nbtKey()) : key.defaultValue();
    }

    // ------------------------------------------------------------------
    // 本地乐观写入（不等服务端回包就先改掉镜像）
    // ------------------------------------------------------------------

    public static void setBoolean(PlayerSettingKey<Boolean> key, boolean value)
    {
        DATA.putBoolean(key.nbtKey(), value);
    }

    public static void setDouble(PlayerSettingKey<Double> key, double value)
    {
        DATA.putDouble(key.nbtKey(), value);
    }

    public static void setInt(PlayerSettingKey<Integer> key, int value)
    {
        DATA.putInt(key.nbtKey(), value);
    }
}
