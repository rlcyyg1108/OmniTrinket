package com.rlcyyg1108.omnitrinket.network;

import com.rlcyyg1108.omnitrinket.setting.PlayerSettings;
import com.rlcyyg1108.omnitrinket.setting.PlayerSettingsHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 玩家个人设置同步包（服务端 -&gt; 客户端）。
 *
 * 为什么需要它：锁定飞行、夜视开关、移速/跳跃倍率这些状态是**每个玩家各自一份**的
 * （存在服务端 Capability 里），但 HUD 与设置界面需要客户端知道当前值。
 * 服务端在设置发生变化时下发本包，客户端写入本地缓存。
 *
 * 用整份 NBT 直接搬运，新增字段无需改动协议。
 */
public class SyncPlayerSettingsPacket
{
    private final CompoundTag data;

    public SyncPlayerSettingsPacket(PlayerSettings settings)
    {
        CompoundTag tag = new CompoundTag();
        settings.saveNBTData(tag);
        this.data = tag;
    }

    public SyncPlayerSettingsPacket(CompoundTag data)
    {
        this.data = data;
    }

    public static void encode(SyncPlayerSettingsPacket msg, FriendlyByteBuf buf)
    {
        buf.writeNbt(msg.data);
    }

    public static SyncPlayerSettingsPacket decode(FriendlyByteBuf buf)
    {
        CompoundTag tag = buf.readNbt();
        return new SyncPlayerSettingsPacket(tag == null ? new CompoundTag() : tag);
    }

    public static void handle(SyncPlayerSettingsPacket msg, Supplier<NetworkEvent.Context> ctx)
    {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> ClientSettingsCache.apply(msg.data));
        context.setPacketHandled(true);
    }

    /**
     * 便捷方法：把玩家的设置同步给他自己。
     */
    public static void sendTo(ServerPlayer player)
    {
        PlayerSettingsHelper.get(player).ifPresent(settings ->
                NetworkHandler.CHANNEL.send(
                        net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> player),
                        new SyncPlayerSettingsPacket(settings)));
    }
}
