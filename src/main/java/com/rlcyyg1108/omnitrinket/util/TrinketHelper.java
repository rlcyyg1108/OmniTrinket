package com.rlcyyg1108.omnitrinket.util;

import com.rlcyyg1108.omnitrinket.OmniTrinket;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import top.theillusivec4.curios.api.CuriosApi;

/**
 * 饰品佩戴状态的统一查询入口。
 *
 * 之前每个 Feature / 事件类都各写一份 {@code CuriosApi.getCuriosInventory(...).map(...)}，
 * 既啰嗦又容易漏掉 {@code isEquipped} 的语义差异，这里收敛成一处。
 */
public final class TrinketHelper
{
    private TrinketHelper()
    {
    }

    /**
     * 该实体是否佩戴了全能饰品（不检查任何功能开关）。
     */
    public static boolean isEquipped(LivingEntity entity)
    {
        if (entity == null || OmniTrinket.OMNI_TRINKET.get() == null)
        {
            return false;
        }
        return CuriosApi.getCuriosInventory(entity)
                .map(handler -> handler.isEquipped(OmniTrinket.OMNI_TRINKET.get()))
                .orElse(false);
    }

    /**
     * 该实体身上（<b>背包 / 快捷栏 / 饰品栏</b>）是否有全能饰品。
     *
     * <p>与 {@link #isEquipped} 的区别：本方法<b>不要求装备</b>，
     * 只要持有就算。用于"N 键打开设置界面"这类只需持有即可的操作 ——
     * 玩家不必先戴上饰品才能改配置。</p>
     */
    public static boolean isInInventory(LivingEntity entity)
    {
        Item trinket = OmniTrinket.OMNI_TRINKET.get();
        if (entity == null || trinket == null)
        {
            return false;
        }

        // 饰品栏
        if (isEquipped(entity))
        {
            return true;
        }

        // 主背包 + 快捷栏
        if (entity instanceof Player player)
        {
            Inventory inventory = player.getInventory();
            for (int i = 0; i < inventory.getContainerSize(); i++)
            {
                if (inventory.getItem(i).is(trinket))
                {
                    return true;
                }
            }
        }
        return false;
    }
}
