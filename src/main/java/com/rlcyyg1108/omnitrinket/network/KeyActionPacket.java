package com.rlcyyg1108.omnitrinket.network;

import com.rlcyyg1108.omnitrinket.Config;
import com.rlcyyg1108.omnitrinket.feature.impl.FlightSpeeds;
import com.rlcyyg1108.omnitrinket.util.TrinketHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 客户端 -&gt; 服务端的按键操作包。
 *
 * <p>飞行速度存在服务端的全局配置里，客户端按 X 时只能发个请求，
 * 由服务端实际修改配置并写入 {@code Abilities.flyingSpeed}
 * （再靠原版 abilities 包同步回客户端）。</p>
 */
public class KeyActionPacket
{
    /** 切换飞行速度档位（改 flySpeedMultiplier，1/2/3/5/10 循环） */
    public static final int ACTION_CYCLE_FLY_SPEED = 0;

    private final int action;

    public KeyActionPacket(int action)
    {
        this.action = action;
    }

    public static void encode(KeyActionPacket msg, FriendlyByteBuf buf)
    {
        buf.writeVarInt(msg.action);
    }

    public static KeyActionPacket decode(FriendlyByteBuf buf)
    {
        return new KeyActionPacket(buf.readVarInt());
    }

    public static void handle(KeyActionPacket msg, Supplier<NetworkEvent.Context> ctx)
    {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() ->
        {
            ServerPlayer player = context.getSender();
            if (player == null)
            {
                return;
            }
            apply(player, msg.action);
        });
        context.setPacketHandled(true);
    }

    private static void apply(ServerPlayer player, int action)
    {
        if (!TrinketHelper.isEquipped(player))
        {
            return;
        }

        switch (action)
        {
            // X 键：在 1/2/3/5/10 倍之间循环，改的是"飞行速度"这一个值。
            // 值本身由 FlightEvents 每 tick 写进 Abilities.flyingSpeed，
            // 所以这里改完配置就够了，不需要额外的下行同步。
            case ACTION_CYCLE_FLY_SPEED ->
            {
                double next = FlightSpeeds.next(Config.COMMON.flySpeedMultiplier.get());
                Config.COMMON.flySpeedMultiplier.set(next);
                Config.COMMON.flySpeedMultiplier.save();
            }
            default ->
            {
            }
        }
    }
}
