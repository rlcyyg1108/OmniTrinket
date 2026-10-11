package com.rlcyyg1108.omnitrinket.network;

import com.rlcyyg1108.omnitrinket.setting.PlayerSettings;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * 客户端侧的玩家设置镜像。
 *
 * <p>HUD、设置界面都是纯客户端的，但锁定飞行 / 夜视开关 / 移速倍率 / 跳跃倍率
 * 存在服务端 Capability 里，因此服务端每次改动都会通过
 * {@link SyncPlayerSettingsPacket} 下发一份，存在这里供渲染与界面直接读取。</p>
 *
 * <p>飞行<b>速度</b>本身已经不走这里了（它直接写进原版 {@code Abilities.flyingSpeed}，
 * 由原版包同步）；这个镜像现在只承载"纯自定义"的那几个字段。</p>
 *
 * <p>{@link #setLocalBoolean} / {@link #setLocalDouble} 用于界面上的"乐观更新"：
 * 点一下开关立刻改本地镜像，手感零延迟；随后服务端的同步包会覆盖成权威值。
 * 键名统一用 {@link PlayerSettings#KEY_*}，所以覆盖是自然发生的。</p>
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

    /** 本地乐观写入：不等服务端回包就先改掉镜像 */
    public static void setLocalBoolean(String key, boolean value)
    {
        DATA.putBoolean(key, value);
    }

    /** 本地乐观写入：不等服务端回包就先改掉镜像 */
    public static void setLocalDouble(String key, double value)
    {
        DATA.putDouble(key, value);
    }

    /** 锁定飞行开关 */
    public static boolean flyLocked()
    {
        return DATA.contains(PlayerSettings.KEY_FLY_LOCKED)
                ? DATA.getBoolean(PlayerSettings.KEY_FLY_LOCKED)
                : PlayerSettings.DEFAULT_FLY_LOCKED;
    }

    /** 夜视开关 */
    public static boolean nightVision()
    {
        return DATA.contains(PlayerSettings.KEY_NIGHT_VISION)
                ? DATA.getBoolean(PlayerSettings.KEY_NIGHT_VISION)
                : PlayerSettings.DEFAULT_NIGHT_VISION;
    }

    /** 跳跃高度倍率（跳跃高度 = 原版 × 该值） */
    public static double jumpHeightMultiplier()
    {
        return DATA.contains(PlayerSettings.KEY_JUMP_HEIGHT)
                ? DATA.getDouble(PlayerSettings.KEY_JUMP_HEIGHT)
                : PlayerSettings.DEFAULT_JUMP_HEIGHT_MULTIPLIER;
    }

    /** 移动速度倍率（走路 / 奔跑速度 = 原版 × 该值） */
    public static double moveSpeedMultiplier()
    {
        return DATA.contains(PlayerSettings.KEY_MOVE_SPEED)
                ? DATA.getDouble(PlayerSettings.KEY_MOVE_SPEED)
                : PlayerSettings.DEFAULT_MOVE_SPEED_MULTIPLIER;
    }
}
