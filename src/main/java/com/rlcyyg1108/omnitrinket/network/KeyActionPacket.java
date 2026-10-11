package com.rlcyyg1108.omnitrinket.network;

import com.rlcyyg1108.omnitrinket.feature.impl.FlightSpeeds;
import com.rlcyyg1108.omnitrinket.setting.PlayerSettings;
import com.rlcyyg1108.omnitrinket.setting.PlayerSettingsHelper;
import com.rlcyyg1108.omnitrinket.util.TrinketHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 客户端 -&gt; 服务端的按键操作包。
 *
 * <p>飞行速度存在<b>每玩家</b>的 {@code PlayerSettings.FLY_SPEED} 里，客户端按 X 时
 * 只能发个请求，由服务端修改该玩家自己的设置并回发同步包；数值本身由
 * {@code FlightEvents} 每 tick 写进 {@code Abilities.flyingSpeed}
 * （再靠原版 abilities 包同步回客户端）。</p>
 */
public class KeyActionPacket
{
    /** 切换飞行速度档位（改自己的 {@code PlayerSettings.FLY_SPEED}，1/2/3/5/10 循环） */
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
        PlayerSettings settings = PlayerSettingsHelper.get(player).orElse(null);
        if (settings == null)
        {
            return;
        }

        switch (action)
        {
            // X 键：在 1/2/3/5/10 倍之间循环，改的是"自己"的飞行速度倍率。
            // 值本身由 FlightEvents 每 tick 写进 Abilities.flyingSpeed；
            // 这里再回发一次同步包，让客户端的 HUD 立刻拿到权威值。
            case ACTION_CYCLE_FLY_SPEED ->
            {
                double next = FlightSpeeds.next(settings.getDouble(PlayerSettings.FLY_SPEED));
                settings.setDouble(PlayerSettings.FLY_SPEED, next);
                settings.setDirty(true);
                SyncPlayerSettingsPacket.sendTo(player);
            }
            default ->
            {
            }
        }
    }
}
