package com.rlcyyg1108.omnitrinket.network;

import com.rlcyyg1108.omnitrinket.setting.PlayerSettings;
import com.rlcyyg1108.omnitrinket.setting.PlayerSettingsHelper;
import com.rlcyyg1108.omnitrinket.util.TrinketHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 修改玩家个人设置（客户端 -&gt; 服务端）。
 *
 * <h3>为什么用"补丁式 NBT"而不是给每个字段写一个包</h3>
 * <p>锁定飞行、夜视开关、移速倍率、跳跃倍率……这些都是"每玩家一份"的数据，
 * 存在服务端的 {@link PlayerSettings} 里，客户端改不了。早先每加一个字段就要
 * 新造一个包并改协议，容易漏、也容易忘注册。</p>
 *
 * <p>现在统一成一个补丁包：<b>客户端只把要改的键塞进 CompoundTag</b>，
 * 服务端按白名单逐项应用，然后回发 {@link SyncPlayerSettingsPacket} 让客户端
 * 刷新镜像。新增字段不需要改协议。</p>
 *
 * <h3>安全</h3>
 * <p>服务端只接受四个键：{@link PlayerSettings#KEY_FLY_LOCKED} /
 * {@link PlayerSettings#KEY_NIGHT_VISION} / {@link PlayerSettings#KEY_MOVE_SPEED} /
 * {@link PlayerSettings#KEY_JUMP_HEIGHT} ——
 * {@code FlyGranted} 这类服务端状态客户端永远写不了。</p>
 */
public class SetPlayerSettingsPacket
{
    private final CompoundTag patch;

    public SetPlayerSettingsPacket(CompoundTag patch)
    {
        this.patch = patch;
    }

    /** 构造一个"只改某个布尔开关"的补丁 */
    public static SetPlayerSettingsPacket ofFlag(String key, boolean value)
    {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean(key, value);
        return new SetPlayerSettingsPacket(tag);
    }

    /** 构造一个"只改某个数值"的补丁 */
    public static SetPlayerSettingsPacket ofDouble(String key, double value)
    {
        CompoundTag tag = new CompoundTag();
        tag.putDouble(key, value);
        return new SetPlayerSettingsPacket(tag);
    }

    public static void encode(SetPlayerSettingsPacket msg, FriendlyByteBuf buf)
    {
        buf.writeNbt(msg.patch);
    }

    public static SetPlayerSettingsPacket decode(FriendlyByteBuf buf)
    {
        CompoundTag tag = buf.readNbt();
        return new SetPlayerSettingsPacket(tag == null ? new CompoundTag() : tag);
    }

    public static void handle(SetPlayerSettingsPacket msg, Supplier<NetworkEvent.Context> ctx)
    {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() ->
        {
            ServerPlayer player = context.getSender();
            if (player == null || !TrinketHelper.isEquipped(player))
            {
                return;
            }
            PlayerSettings settings = PlayerSettingsHelper.get(player).orElse(null);
            if (settings == null)
            {
                return;
            }

            CompoundTag patch = msg.patch;
            boolean changed = false;

            if (patch.contains(PlayerSettings.KEY_FLY_LOCKED))
            {
                settings.setFlyLocked(patch.getBoolean(PlayerSettings.KEY_FLY_LOCKED));
                changed = true;
            }
            if (patch.contains(PlayerSettings.KEY_NIGHT_VISION))
            {
                settings.setNightVision(patch.getBoolean(PlayerSettings.KEY_NIGHT_VISION));
                changed = true;
            }
            if (patch.contains(PlayerSettings.KEY_JUMP_HEIGHT))
            {
                settings.setJumpHeightMultiplier(clampMove(
                        patch.getDouble(PlayerSettings.KEY_JUMP_HEIGHT)));
                changed = true;
            }
            if (patch.contains(PlayerSettings.KEY_MOVE_SPEED))
            {
                settings.setMoveSpeedMultiplier(clampMove(
                        patch.getDouble(PlayerSettings.KEY_MOVE_SPEED)));
                changed = true;
            }

            if (changed)
            {
                settings.setDirty(true);
                SyncPlayerSettingsPacket.sendTo(player);
            }
        });
        context.setPacketHandled(true);
    }

    /** 把跳跃 / 移速倍率夹到 1~10（与设置界面输入框一致），并做 NaN 保护 */
    private static double clampMove(double value)
    {
        if (Double.isNaN(value))
        {
            return 1.0D;
        }
        return Math.max(PlayerSettings.MIN_MOVE_MULTIPLIER,
                Math.min(PlayerSettings.MAX_MOVE_MULTIPLIER, value));
    }
}
