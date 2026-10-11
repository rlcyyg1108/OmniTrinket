package com.rlcyyg1108.omnitrinket.network;

import com.rlcyyg1108.omnitrinket.OmniTrinket;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

/**
 * 网络通道。
 *
 * 用于客户端与服务端之间同步玩家设置、UI 操作等。
 * 1.20.1 Forge 使用 NetworkRegistry + SimpleChannel。
 */
public final class NetworkHandler
{
    private static final String PROTOCOL_VERSION = "1";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            ResourceLocation.fromNamespaceAndPath(OmniTrinket.MODID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    private static int nextId = 0;

    private NetworkHandler()
    {
    }

    /**
     * 注册所有网络包。应在 mod 构造阶段调用。
     */
    public static void register()
    {
        CHANNEL.registerMessage(nextId++, SyncPlayerSettingsPacket.class,
                SyncPlayerSettingsPacket::encode,
                SyncPlayerSettingsPacket::decode,
                SyncPlayerSettingsPacket::handle);

        CHANNEL.registerMessage(nextId++, KeyActionPacket.class,
                KeyActionPacket::encode,
                KeyActionPacket::decode,
                KeyActionPacket::handle);

        CHANNEL.registerMessage(nextId++, SetPlayerSettingsPacket.class,
                SetPlayerSettingsPacket::encode,
                SetPlayerSettingsPacket::decode,
                SetPlayerSettingsPacket::handle);
    }
}
