package com.rlcyyg1108.omnitrinket.item;

import com.rlcyyg1108.omnitrinket.client.ClientTooltips;
import com.rlcyyg1108.omnitrinket.feature.FeatureRegistry;
import com.rlcyyg1108.omnitrinket.feature.TrinketFeature;
import com.rlcyyg1108.omnitrinket.setting.PlayerSettings;
import com.rlcyyg1108.omnitrinket.setting.PlayerSettingsHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import org.jetbrains.annotations.Nullable;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.capability.ICurioItem;

import java.util.List;

/**
 * 全能饰品。
 *
 * 本身不承载任何具体逻辑，所有功能都委托给 {@link FeatureRegistry} 中注册的
 * {@link TrinketFeature} 实现，保持本类精简。
 */
public class OmniTrinketItem extends Item implements ICurioItem
{
    public OmniTrinketItem()
    {
        super(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC));
    }

    // ------------------------------------------------------------------
    // 手持 Shift+右键：打开设置界面
    // ------------------------------------------------------------------

    /**
     * 手持饰品并潜行时右键 -> 打开设置界面。
     *
     * 之所以用"潜行 + 右键"而不是普通右键：普通右键在很多场景（容器、
     * 方块交互）会被占用，潜行右键是社区约定俗成的"配置菜单"手势。
     */
    @Override
    public net.minecraft.world.InteractionResultHolder<ItemStack> use(Level level, Player player,
                                                                      net.minecraft.world.InteractionHand hand)
    {
        ItemStack stack = player.getItemInHand(hand);
        if (player.isShiftKeyDown())
        {
            // 关键：用 DistExecutor 包一层，把客户端 GUI 类藏在 lambda 里，
            // 这样专用服务端加载本类时不会因为引用 Minecraft/Screen 而报
            // NoClassDefFoundError。
            net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(
                    net.minecraftforge.api.distmarker.Dist.CLIENT,
                    () -> () -> com.rlcyyg1108.omnitrinket.client.ClientGuiOpener.openSettingsScreen());
            return net.minecraft.world.InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }
        return super.use(level, player, hand);
    }

    // ------------------------------------------------------------------
    // Curios 生命周期：转发给各功能
    // ------------------------------------------------------------------

    @Override
    public void onUnequip(SlotContext slotContext, ItemStack newStack, ItemStack stack)
    {
        for (TrinketFeature feature : FeatureRegistry.all())
        {
            if (feature.isEnabled())
            {
                feature.onUnequip(slotContext, stack);
            }
        }
    }

    @Override
    public void curioTick(SlotContext slotContext, ItemStack stack)
    {
        // cfg 允许为 null：某些功能不依赖玩家个人设置（例如生存模块），
        // 不应因为取不到设置就整体跳过。
        PlayerSettings cfg = getSettings(slotContext);
        for (TrinketFeature feature : FeatureRegistry.all())
        {
            if (feature.isEnabled())
            {
                feature.curioTick(slotContext, stack, cfg);
            }
        }
    }

    private static @Nullable PlayerSettings getSettings(SlotContext ctx)
    {
        if (ctx.entity() instanceof Player player)
        {
            return PlayerSettingsHelper.get(player).orElse(null);
        }
        return null;
    }

    // ------------------------------------------------------------------
    // Tooltip
    // ------------------------------------------------------------------

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag)
    {
        // 第一行：金色加粗，醒目
        tooltip.add(Component.translatable("tooltip.omnitrinket.omni_trinket.header")
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));

        // 紧跟"生效"行之后：怎么打开设置界面
        tooltip.add(openSettingsHint());

        // 逐功能列出当前状态（读取各自的总开关）
        for (TrinketFeature feature : FeatureRegistry.all())
        {
            addFeatureTooltip(feature, tooltip);
        }
    }

    /**
     * 打开设置的操作提示行。
     *
     * <p>键位表只在客户端存在，所以必须用 {@code DistExecutor} 把
     * {@code ClientTooltips} 藏在 lambda 里 —— 专用服务端跑到这里时
     * 直接拿到 {@code null}，不会因为引用客户端类而崩。</p>
     */
    private static Component openSettingsHint()
    {
        Component hint = DistExecutor.unsafeCallWhenOn(Dist.CLIENT,
                () -> ClientTooltips::openSettingsHint);
        // 理论上不会走到这里（tooltip 只在客户端构建），留个兜底避免 NPE。
        return hint != null ? hint : Component.empty();
    }

    /**
     * 为单个功能追加 tooltip 行。
     *
     * <p>启用时用亮绿色显示描述，禁用时用深灰色显示"已禁用"，
     * 保证一眼就能分辨哪些功能正在生效。</p>
     *
     * <p><b>描述支持多行</b>：lang 里用 {@code \n} 分行。原版 tooltip 不会把
     * {@code \n} 当作换行（它会被当普通字符吃掉），所以这里显式拆分。</p>
     *
     * <p><b>快捷键行的颜色由各功能自己决定</b>（见 {@code ClientTooltips}：
     * 键位深紫、其余绿色），这里不再统一染色，否则会把键位的紫色盖掉。</p>
     */
    private static void addFeatureTooltip(TrinketFeature feature, List<Component> tooltip)
    {
        String key = "tooltip.omnitrinket." + feature.getId();
        if (feature.isEnabled())
        {
            tooltip.add(Component.translatable(key).withStyle(ChatFormatting.AQUA));

            // 描述：按 \n 拆成多行，逐行上色
            for (String line : Component.translatable(key + ".enabled").getString().split("\n", -1))
            {
                tooltip.add(Component.literal(line).withStyle(ChatFormatting.GREEN));
            }

            // 额外行（例如快捷键提示）：颜色已由构造方决定，直接加
            Component extra = feature.extraTooltip();
            if (extra != null)
            {
                tooltip.add(extra);
            }
        }
        else
        {
            tooltip.add(Component.translatable(key).withStyle(ChatFormatting.DARK_GRAY)
                    .append(Component.literal(" ")
                            .append(Component.translatable("tooltip.omnitrinket.hint.disabled")))
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
    }
}
