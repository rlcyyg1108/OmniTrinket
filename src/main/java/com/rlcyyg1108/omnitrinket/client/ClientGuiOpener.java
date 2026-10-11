package com.rlcyyg1108.omnitrinket.client;

import com.rlcyyg1108.omnitrinket.client.gui.OmniTrinketSettingsScreen;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * 客户端 GUI 打开入口。
 *
 * 这是唯一一处直接引用 {@code net.minecraft.client.*} 的"从通用代码进入客户端"
 * 的桥梁。调用方（OmniTrinketItem.use）用 DistExecutor 包住调用，
 * 确保专用服务端永远不会加载本类。
 */
@OnlyIn(Dist.CLIENT)
public final class ClientGuiOpener
{
    private ClientGuiOpener()
    {
    }

    public static void openSettingsScreen()
    {
        Minecraft mc = Minecraft.getInstance();
        mc.setScreen(new OmniTrinketSettingsScreen());
    }
}
