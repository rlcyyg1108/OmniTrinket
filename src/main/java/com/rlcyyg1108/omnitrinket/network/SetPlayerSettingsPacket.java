package com.rlcyyg1108.omnitrinket.network;

import com.rlcyyg1108.omnitrinket.setting.PlayerSettingKey;
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
 * <p>每玩家设置存在服务端的 {@link PlayerSettings} 里，客户端改不了。早先每加一个
 * 字段就要新造一个包并改协议，容易漏、也容易忘注册。</p>
 *
 * <p>现在统一成一个补丁包：<b>客户端只把要改的键塞进 CompoundTag</b>，
 * 服务端按白名单逐项应用，然后回发 {@link SyncPlayerSettingsPacket} 让客户端
 * 刷新镜像。新增字段不需要改协议。</p>
 *
 * <h3>白名单与范围夹紧都是表驱动的</h3>
 * <p>服务端只接受 {@link PlayerSettings} 里声明过的键，而且
 * <b>跳过 {@code serverOnly} 的项</b>（例如 {@code FlyGranted} 这类服务端状态，
 * 客户端永远写不了）。数值项的上下限来自描述符，夹紧在
 * {@code PlayerSettings.setXxx} 里统一收口。</p>
 */
public class SetPlayerSettingsPacket
{
    private final CompoundTag patch;

    public SetPlayerSettingsPacket(CompoundTag patch)
    {
        this.patch = patch;
    }

    /** 构造一个"只改某个布尔开关"的补丁 */
    public static SetPlayerSettingsPacket ofFlag(PlayerSettingKey<Boolean> key, boolean value)
    {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean(key.nbtKey(), value);
        return new SetPlayerSettingsPacket(tag);
    }

    /** 构造一个"只改某个浮点值"的补丁 */
    public static SetPlayerSettingsPacket ofDouble(PlayerSettingKey<Double> key, double value)
    {
        CompoundTag tag = new CompoundTag();
        tag.putDouble(key.nbtKey(), value);
        return new SetPlayerSettingsPacket(tag);
    }

    /** 构造一个"只改某个整数值"的补丁 */
    public static SetPlayerSettingsPacket ofInt(PlayerSettingKey<Integer> key, int value)
    {
        CompoundTag tag = new CompoundTag();
        tag.putInt(key.nbtKey(), value);
        return new SetPlayerSettingsPacket(tag);
    }

    /**
     * 构造一份"把所有客户端可写的项都恢复默认值"的补丁。
     *
     * <p>设置界面的"重置全部"用这一个包就够了，不用逐项发包。</p>
     */
    public static SetPlayerSettingsPacket allDefaults()
    {
        CompoundTag tag = new CompoundTag();
        for (PlayerSettingKey<Boolean> key : PlayerSettings.BOOLEAN_KEYS)
        {
            if (key.clientWritable())
            {
                tag.putBoolean(key.nbtKey(), key.defaultValue());
            }
        }
        for (PlayerSettingKey<Double> key : PlayerSettings.DOUBLE_KEYS)
        {
            tag.putDouble(key.nbtKey(), key.defaultValue());
        }
        for (PlayerSettingKey<Integer> key : PlayerSettings.INT_KEYS)
        {
            tag.putInt(key.nbtKey(), key.defaultValue());
        }
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

            // 逐类型遍历描述表：白名单 = 表里声明过 && 客户端可写
            for (PlayerSettingKey<Boolean> key : PlayerSettings.BOOLEAN_KEYS)
            {
                if (!key.clientWritable() || !patch.contains(key.nbtKey()))
                {
                    continue;
                }
                settings.setBoolean(key, patch.getBoolean(key.nbtKey()));
                changed = true;
            }
            for (PlayerSettingKey<Double> key : PlayerSettings.DOUBLE_KEYS)
            {
                if (!patch.contains(key.nbtKey()))
                {
                    continue;
                }
                settings.setDouble(key, patch.getDouble(key.nbtKey()));
                changed = true;
            }
            for (PlayerSettingKey<Integer> key : PlayerSettings.INT_KEYS)
            {
                if (!patch.contains(key.nbtKey()))
                {
                    continue;
                }
                settings.setInt(key, patch.getInt(key.nbtKey()));
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
}
