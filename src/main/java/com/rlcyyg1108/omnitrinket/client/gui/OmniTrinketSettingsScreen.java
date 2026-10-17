package com.rlcyyg1108.omnitrinket.client.gui;

import com.mojang.blaze3d.platform.NativeImage;
import com.rlcyyg1108.omnitrinket.Config;
import com.rlcyyg1108.omnitrinket.client.KeyBindings;
import com.rlcyyg1108.omnitrinket.network.ClientSettingsCache;
import com.rlcyyg1108.omnitrinket.network.NetworkHandler;
import com.rlcyyg1108.omnitrinket.network.SetPlayerSettingsPacket;
import com.rlcyyg1108.omnitrinket.setting.PlayerSettingKey;
import com.rlcyyg1108.omnitrinket.setting.PlayerSettings;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 全能饰品设置界面。
 *
 * <h3>外观：原版容器风格</h3>
 * <pre>
 * ┌──────────────────────────────────────────┐ ← 原版 inventory.png 九宫格（黑边 + 高光/阴影）
 * │            全能饰品 - 设置 UI             │ ← 标题居中
 * │ ┌────────┐  ┌──────────────────────────┐ │
 * │ │  飞行   │  │ 启用飞行             开  │ │
 * │ │  移动   │  │ 无惯性               开  │ │
 * │ │  生存   │  │ 飞行速度          [-] 2 [+]│ │
 * │ │  增益   │  │                          │ │
 * │ │  环境   │  │                          │ │
 * │ └────────┘  └──────────────────────────┘ │
 * │ [重置全部]  (红框)    [      完成      ]  │
 * └──────────────────────────────────────────┘ ← 原版 inventory.png 九宫格（阴影）
 * </pre>
 *
 * <p>面板底板取自原版 {@code inventory.png} 的九宫格；直接绘制在面板上的文字颜色
 * 按面板亮度自动选择：浅色面板用深灰、深色面板用白色 + 阴影，保证两种材质包下都可读。</p>
 *
 * <h3>尺寸</h3>
 * <p>面板采用<b>固定尺寸</b>（按最大分类的行数预留），保证侧边栏 5 个分类按钮
 * 与最长的内容列都能完整放下，不会互相重叠。只有屏幕确实放不下时才出现滑条，
 * 且滑条<b>不影响</b>底部按钮与侧边栏（它们不在滚动集合内）。</p>
 *
 * <h3>交互</h3>
 * <ul>
 *   <li>左键开关 / 输入框 → 正常修改</li>
 *   <li><b>右键</b>开关 → 重置该项；<b>右键</b>输入框 → 清空并聚焦（可直接输入）</li>
 *   <li><b>[R]</b> → 输入框恢复默认值</li>
 *   <li><b>重置全部</b> → 弹出确认对话框（模态，避免误触）</li>
 *   <li><b>完成</b> → 关闭界面</li>
 * </ul>
 * <p>输入框失焦时归一化：清空后没输入 → 还原原值；非法内容 → 还原原值。</p>
 */
public class OmniTrinketSettingsScreen extends Screen
{
    // ---- 尺寸 ----
    /** 面板外边界留白 */
    private static final int MARGIN = 12;
    /** 面板宽度（固定） */
    private static final int PANEL_W = 440;
    /** 标题区高 */
    private static final int TITLE_H = 22;
    /**
     * 底部区高：只有一行 —— 按钮 + 两行提示共用这一行。
     * <pre>
     *   [重置全部]   右键选项可重置该项     [完成]
     *                右键输入框可清空内容
     * </pre>
     */
    private static final int FOOTER_H = 30;
    /** 行高（含间距） */
    private static final int ROW_H = 24;
    /** 控件高 */
    private static final int ROW_CTRL_H = 20;
    /** 数值输入框宽 */
    private static final int VALUE_W = 52;
    /** 小按钮边长 */
    private static final int BTN_W = 18;
    /** 控件之间的间隔 */
    private static final int GAP = 3;
    /** 滑条宽 */
    private static final int SCROLL_W = 6;
    /** 侧边栏宽 */
    private static final int SIDEBAR_W = 78;
    /** 内容区左右内边距 */
    private static final int PAD = 8;
    /**
     * 内容区预留行数 —— 取所有分类中的最大值。
     * <p>飞行 6 / 生存 6 / 移动 7 / 增益 4 / 环境 2 ⇒ 取 7。
     * 这样面板高度固定，侧边栏（5 行）永远不会溢出。</p>
     */
    private static final int MAX_ROWS = 7;

    // ---- 面板贴图（原版容器 GUI，材质包可覆盖）----
    /**
     * 原版容器面板贴图。
     *
     * <p>材质包替换 {@code minecraft:textures/gui/container/inventory.png} 后，
     * 本界面的面板会一起变化 —— 这是"跟随材质包"的关键。</p>
     */
    private static final ResourceLocation PANEL_TEXTURE =
            ResourceLocation.fromNamespaceAndPath("minecraft", "textures/gui/container/inventory.png");
    /** 原版 GUI 图集边长（{@code blit} 的 textureWidth / textureHeight） */
    private static final int PANEL_TEXTURE_SIZE = 256;
    /** 原版面板在 inventory.png 中的宽高 */
    private static final int VANILLA_PANEL_WIDTH = 176;
    private static final int VANILLA_PANEL_HEIGHT = 166;
    /** 九宫格边宽：4px 覆盖原版黑边 + 高光 / 阴影 */
    private static final int PANEL_BORDER = 4;

    // ---- 颜色（原版容器 GUI 标准配色）----
    /** 滚动条滑块高光（面板已改用原版贴图，这里只服务滚动条） */
    private static final int COLOR_HIGHLIGHT = 0xFFFFFFFF;
    /** 滚动条滑块阴影（面板已改用原版贴图，这里只服务滚动条） */
    private static final int COLOR_SHADOW = 0xFF555555;
    /** 浅色面板上的文字：深灰（原版容器配色） */
    private static final int COLOR_TEXT_ON_LIGHT = 0x404040;
    /** 深色面板上的文字：白色 */
    private static final int COLOR_TEXT_ON_DARK = 0xFFFFFF;
    /** 面板亮度阈值：高于它视为浅色面板（0~1） */
    private static final double LIGHT_PANEL_LUMINANCE = 0.55D;
    private static final int COLOR_SCROLL_TRACK = 0xFF8B8B8B;
    private static final int COLOR_SCROLL_THUMB = 0xFFFFFFFF;
    private static final int COLOR_DIVIDER = 0xFF8B8B8B;

    private static final List<Component> CATEGORIES = List.of(
            Component.translatable("omnitrinket.gui.category.flight"),
            Component.translatable("omnitrinket.gui.category.sprint"),
            Component.translatable("omnitrinket.gui.category.survival"),
            Component.translatable("omnitrinket.gui.category.buff"),
            Component.translatable("omnitrinket.gui.category.environment"));

    /** 上次停留的分类；关闭界面再打开时恢复（仅本次游戏会话） */
    private static int lastCategory = 0;

    private int currentCategory = 0;

    /** 当前面板底色对应的文字颜色与阴影（init 时按材质包贴图计算） */
    private int textColor = COLOR_TEXT_ON_LIGHT;
    private boolean textShadow = false;

    private int panelLeft;
    private int panelTop;
    private int panelRight;
    private int panelBottom;
    private int contentTop;
    private int contentBottom;

    /** 内容控件（可滚动） */
    private final List<AbstractWidget> rows = new ArrayList<>();
    /** 内容控件的基准 Y（未滚动） */
    private final Map<AbstractWidget, Integer> baseYMap = new LinkedHashMap<>();
    /** 侧边栏按钮（固定） */
    private final List<AbstractWidget> sidebarButtons = new ArrayList<>();
    /** 底部按钮（固定，不参与滚动） */
    private final List<AbstractWidget> footerWidgets = new ArrayList<>();
    /** 名称标签（drawString，随内容滚动） */
    private final List<LabelRow> labelRows = new ArrayList<>();

    private final List<NumberRow> numberRows = new ArrayList<>();
    private final List<ToggleRow> toggleRows = new ArrayList<>();
    private final List<ModeRow> modeRows = new ArrayList<>();

    // ---- 滚动 ----
    private int contentH;
    private int scrollOffset;
    private int maxScroll;
    private int dragStartMouseY;
    private int dragStartOffset;
    private boolean draggingScrollBar;

    private Button resetAllButton;
    private Button doneButton;

    /**
     * 防止 setValue 触发 responder 造成回环。
     * <p>照搬 simpleinfo 的写法：程序化写值时置 true，responder 里直接 return。</p>
     */
    private boolean updatingFields = false;

    // ------------------------------------------------------------------
    // 内部记录类型
    // ------------------------------------------------------------------

    /** 数值行 */
    private static final class NumberRow
    {
        EditBox box;
        final java.util.function.DoubleSupplier getter;
        final java.util.function.DoubleConsumer setter;
        final double min;
        final double max;
        final int decimals;
        final double defaultValue;

        NumberRow(EditBox box,
                  java.util.function.DoubleSupplier getter,
                  java.util.function.DoubleConsumer setter,
                  double min, double max, int decimals, double defaultValue)
        {
            this.box = box;
            this.getter = getter;
            this.setter = setter;
            this.min = min;
            this.max = max;
            this.decimals = decimals;
            this.defaultValue = defaultValue;
        }

        double get()
        {
            return getter.getAsDouble();
        }

        void set(double v)
        {
            if (!Double.isFinite(v))
            {
                v = defaultValue;
            }
            setter.accept(Math.max(min, Math.min(max, v)));
        }

        String display()
        {
            if (decimals <= 0)
            {
                return String.valueOf((long) Math.round(get()));
            }
            String s = String.format(java.util.Locale.ROOT, "%." + decimals + "f", get());
            if (s.indexOf('.') >= 0)
            {
                s = s.replaceAll("0+$", "");
                if (s.endsWith("."))
                {
                    s = s.substring(0, s.length() - 1);
                }
            }
            return s;
        }
    }

    /** 开关行（右键重置用） */
    private static final class ToggleRow
    {
        final Button button;
        final java.util.function.BooleanSupplier getter;
        final java.util.function.Consumer<Boolean> setter;
        final boolean defaultValue;
        final boolean perPlayer;
        /** 每玩家开关对应的描述符；全局(Config)开关为 null */
        final PlayerSettingKey<Boolean> settingsKey;

        ToggleRow(Button button,
                  java.util.function.BooleanSupplier getter,
                  java.util.function.Consumer<Boolean> setter,
                  boolean defaultValue, boolean perPlayer, PlayerSettingKey<Boolean> settingsKey)
        {
            this.button = button;
            this.getter = getter;
            this.setter = setter;
            this.defaultValue = defaultValue;
            this.perPlayer = perPlayer;
            this.settingsKey = settingsKey;
        }

        void refresh()
        {
            button.setMessage(onOff(getter.getAsBoolean()));
        }
    }

    /** 三态模式行（右键重置用） */
    private static final class ModeRow
    {
        final Button button;
        final PlayerSettingKey<Integer> settingsKey;
        final int defaultValue;
        final String[] labelKeys;

        ModeRow(Button button, PlayerSettingKey<Integer> settingsKey,
                int defaultValue, String[] labelKeys)
        {
            this.button = button;
            this.settingsKey = settingsKey;
            this.defaultValue = defaultValue;
            this.labelKeys = labelKeys;
        }

        void refresh()
        {
            int mode = Math.max(0, Math.min(labelKeys.length - 1,
                    ClientSettingsCache.getInt(settingsKey)));
            button.setMessage(Component.translatable(labelKeys[mode]));
        }
    }

    /** 名称标签 */
    private static final class LabelRow
    {
        final Component text;
        final int x;
        final int baseY;

        LabelRow(Component text, int x, int baseY)
        {
            this.text = text;
            this.x = x;
            this.baseY = baseY;
        }
    }

    public OmniTrinketSettingsScreen()
    {
        super(Component.translatable("omnitrinket.gui.title"));
        // 恢复上次停留的分类；列表长度变化时夹紧，避免越界
        currentCategory = Math.max(0, Math.min(CATEGORIES.size() - 1, lastCategory));
    }

    private Component t(String key)
    {
        return Component.translatable(key);
    }

    /** 开关文字：开 / 关（不带冒号、不带前缀） */
    private static Component onOff(boolean on)
    {
        return Component.translatable(on ? "omnitrinket.gui.on" : "omnitrinket.gui.off");
    }

    // ------------------------------------------------------------------
    // 初始化
    // ------------------------------------------------------------------

    @Override
    protected void init()
    {
        // 按当前材质包的面板底色决定文字颜色（浅色黑字 / 深色白字）
        resolveTextColor();

        // 固定面板尺寸（按最大行数预留），保证侧边栏永不溢出
        int w = Math.max(280, Math.min(this.width - 2 * MARGIN, PANEL_W));
        int neededH = TITLE_H + MAX_ROWS * ROW_H + PAD + FOOTER_H;
        int availH = Math.max(120, this.height - 2 * MARGIN);
        int h = Math.min(neededH, availH);

        panelLeft = (this.width - w) / 2;
        panelTop = (this.height - h) / 2;
        panelRight = panelLeft + w;
        panelBottom = panelTop + h;

        contentTop = panelTop + TITLE_H;
        contentBottom = panelBottom - FOOTER_H;

        buildSidebar();
        buildContent();
    }

    /**
     * 按当前材质包的面板底色选择文字颜色。
     *
     * <p>材质包可以替换 {@code inventory.png} 的填充色：原版是浅灰，深色包是深灰/黑。
     * 这里读取面板填充区的第一个不透明像素并算亮度：浅色面板用深灰字、不加深色阴影；
     * 深色面板用白字 + 深色阴影。于是同一套界面在浅色/深色材质包下都可读。</p>
     *
     * <p>读不到贴图（或采样点全透明）时回退到浅色面板的深灰字。</p>
     */
    private void resolveTextColor()
    {
        int pixel = samplePanelFillPixel();
        if (pixel == 0)
        {
            textColor = COLOR_TEXT_ON_LIGHT;
            textShadow = false;
            return;
        }

        // NativeImage 的像素是 ABGR；这里取低 3 字节算亮度，通道顺序对灰色面板无影响
        int c0 = pixel & 0xFF;
        int c1 = (pixel >> 8) & 0xFF;
        int c2 = (pixel >> 16) & 0xFF;
        double luminance = (0.299D * c0 + 0.587D * c1 + 0.114D * c2) / 255.0D;

        boolean light = luminance > LIGHT_PANEL_LUMINANCE;
        textColor = light ? COLOR_TEXT_ON_LIGHT : COLOR_TEXT_ON_DARK;
        textShadow = !light;
    }

    /**
     * 从当前 {@link #PANEL_TEXTURE} 的填充区取一个不透明像素（ABGR）。
     *
     * <p>采样点沿左上角对角线从 4px 开始，跳过原版圆角的透明像素；遇到不透明像素即采用。
     * 读不到资源或解码失败时返回 0，由调用方回退。</p>
     */
    private static int samplePanelFillPixel()
    {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null)
        {
            return 0;
        }

        // 1.20.1 的 ResourceManager#getResource 返回 Optional<Resource>
        var optional = mc.getResourceManager().getResource(PANEL_TEXTURE);
        if (optional.isEmpty())
        {
            return 0;
        }

        Resource resource = optional.get();
        try (var stream = resource.open())
        {
            NativeImage image = NativeImage.read(stream);
            try
            {
                int[] samples = {PANEL_BORDER, PANEL_BORDER * 2, PANEL_BORDER * 4, PANEL_BORDER * 8};
                for (int offset : samples)
                {
                    if (offset >= image.getWidth() || offset >= image.getHeight())
                    {
                        break;
                    }
                    int pixel = image.getPixelRGBA(offset, offset);
                    if (((pixel >> 24) & 0xFF) >= 128)
                    {
                        return pixel;
                    }
                }
            }
            finally
            {
                image.close();
            }
        }
        catch (IOException | RuntimeException ignored)
        {
            // 资源读取或图片解码失败：回退浅色面板配色
        }
        return 0;
    }

    private void buildSidebar()
    {
        for (AbstractWidget w : sidebarButtons)
        {
            removeWidget(w);
        }
        sidebarButtons.clear();

        int y = panelTop + TITLE_H + 2;
        for (int i = 0; i < CATEGORIES.size(); i++)
        {
            final int index = i;
            Button btn = Button.builder(CATEGORIES.get(i), b ->
            {
                if (currentCategory != index)
                {
                    commitEditing();
                    currentCategory = index;
                    lastCategory = index;
                    buildContent();
                }
            }).bounds(panelLeft + 6, y, SIDEBAR_W - 12, ROW_CTRL_H).build();
            addRenderableWidget(btn);
            sidebarButtons.add(btn);
            y += ROW_H;
        }
    }

    private void clearContent()
    {
        for (AbstractWidget w : rows)
        {
            removeWidget(w);
        }
        rows.clear();
        baseYMap.clear();
        labelRows.clear();
        numberRows.clear();
        toggleRows.clear();
        modeRows.clear();

        for (AbstractWidget w : footerWidgets)
        {
            removeWidget(w);
        }
        footerWidgets.clear();
        resetAllButton = null;
        doneButton = null;
    }

    private void buildContent()
    {
        clearContent();

        int x = panelLeft + SIDEBAR_W;
        int right = panelRight - PAD - SCROLL_W;
        int y = contentTop + PAD / 2;
        int w = right - x;

        scrollOffset = 0;
        switch (currentCategory)
        {
            case 0 -> buildFlight(x, y, w);
            case 1 -> buildSprint(x, y, w);
            case 2 -> buildSurvival(x, y, w);
            case 3 -> buildBuff(x, y, w);
            case 4 -> buildEnvironment(x, y, w);
            default -> { }
        }

        contentH = 0;
        for (AbstractWidget wd : rows)
        {
            baseYMap.put(wd, wd.getY());
            contentH = Math.max(contentH, wd.getY() + ROW_H - y);
        }

        buildFooter();

        int viewport = contentBottom - contentTop;
        maxScroll = Math.max(0, contentH - viewport);
        scrollOffset = Math.min(scrollOffset, maxScroll);
        applyScroll();
    }

    /**
     * 底部：重置全部（<b>左下角</b>）+ 完成（<b>右下角</b>）。
     * 两者固定，不参与滚动；两行提示由 {@code render()} 绘制在它们上方。
     */
    private void buildFooter()
    {
        // 按钮行紧贴面板底部
        int footY = panelBottom - ROW_CTRL_H - 5;

        // 重置全部 —— 左下角。<b>与侧边栏分类按钮同 x 同宽</b>，保证左边缘对齐。
        resetAllButton = Button.builder(
                        Component.translatable("omnitrinket.gui.button.resetAll")
                                .withStyle(ChatFormatting.RED),
                        b -> confirmResetAll())
                .bounds(panelLeft + 6, footY, SIDEBAR_W - 12, ROW_CTRL_H)
                .build();
        addRenderableWidget(resetAllButton);
        footerWidgets.add(resetAllButton);

        // 完成 —— 右下角
        doneButton = Button.builder(t("omnitrinket.gui.button.done"), b -> onClose())
                .bounds(panelRight - PAD - 60, footY, 60, ROW_CTRL_H)
                .build();
        addRenderableWidget(doneButton);
        footerWidgets.add(doneButton);
    }

    // ------------------------------------------------------------------
    // 各类别控件
    // ------------------------------------------------------------------

    private void buildFlight(int x, int y, int w)
    {
        y = addPlayerToggle(x, y, w, "omnitrinket.gui.flight.enabled",
                () -> ClientSettingsCache.getBoolean(PlayerSettings.FLY_ENABLED),
                PlayerSettings.FLY_ENABLED);
        y = addPlayerToggle(x, y, w, "omnitrinket.gui.flight.noInertia",
                () -> ClientSettingsCache.getBoolean(PlayerSettings.FLY_NO_INERTIA),
                PlayerSettings.FLY_NO_INERTIA);
        y = addPlayerToggle(x, y, w, "omnitrinket.gui.flight.noMiningSlowdown",
                () -> ClientSettingsCache.getBoolean(PlayerSettings.FLY_NO_MINING_SLOWDOWN),
                PlayerSettings.FLY_NO_MINING_SLOWDOWN);
        y = addPlayerToggle(x, y, w, "omnitrinket.gui.flight.locked",
                () -> ClientSettingsCache.getBoolean(PlayerSettings.FLY_LOCKED),
                PlayerSettings.FLY_LOCKED);
        y = addPlayerNumber(x, y, w, "omnitrinket.gui.flight.speed", PlayerSettings.FLY_SPEED);
        addPlayerNumber(x, y, w, "omnitrinket.gui.flight.sprintMultiplier", PlayerSettings.FLY_SPRINT);
    }

    private void buildSprint(int x, int y, int w)
    {
        y = addPlayerToggle(x, y, w, "omnitrinket.gui.sprint.enabled",
                () -> ClientSettingsCache.getBoolean(PlayerSettings.SPRINT_ENABLED),
                PlayerSettings.SPRINT_ENABLED);
        y = addPlayerToggle(x, y, w, "omnitrinket.gui.sprint.resumeSprint",
                () -> ClientSettingsCache.getBoolean(PlayerSettings.SPRINT_RESUME),
                PlayerSettings.SPRINT_RESUME);
        y = addPlayerToggle(x, y, w, "omnitrinket.gui.sprint.stepAssist",
                () -> ClientSettingsCache.getBoolean(PlayerSettings.MOVE_STEP_ASSIST),
                PlayerSettings.MOVE_STEP_ASSIST);
        y = addPlayerToggle(x, y, w, "omnitrinket.gui.sprint.noKnockback",
                () -> ClientSettingsCache.getBoolean(PlayerSettings.MOVE_NO_KNOCKBACK),
                PlayerSettings.MOVE_NO_KNOCKBACK);
        y = addPlayerNumber(x, y, w, "omnitrinket.gui.sprint.speed", PlayerSettings.MOVE_SPEED);
        y = addPlayerNumber(x, y, w, "omnitrinket.gui.sprint.jump", PlayerSettings.JUMP_HEIGHT);
        addPlayerInt(x, y, w, "omnitrinket.gui.sprint.resumeSprintTicks",
                PlayerSettings.SPRINT_RESUME_TICKS);
    }

    private void buildSurvival(int x, int y, int w)
    {
        y = addPlayerToggle(x, y, w, "omnitrinket.gui.survival.enabled",
                () -> ClientSettingsCache.getBoolean(PlayerSettings.SURVIVAL_ENABLED),
                PlayerSettings.SURVIVAL_ENABLED);
        y = addPlayerMode(x, y, w, "omnitrinket.gui.survival.damageMode",
                PlayerSettings.SURVIVAL_DAMAGE_MODE,
                "omnitrinket.gui.off",
                "omnitrinket.gui.survival.damageMode.allowVoid",
                "omnitrinket.gui.survival.damageMode.full");
        y = addPlayerMode(x, y, w, "omnitrinket.gui.survival.negativeMode",
                PlayerSettings.SURVIVAL_NEGATIVE_MODE,
                "omnitrinket.gui.off",
                "omnitrinket.gui.survival.negativeMode.harmful",
                "omnitrinket.gui.survival.negativeMode.all");
        y = addPlayerToggle(x, y, w, "omnitrinket.gui.survival.noUnderwaterMiningSlowdown",
                () -> ClientSettingsCache.getBoolean(PlayerSettings.SURVIVAL_NO_WATER_MINING),
                PlayerSettings.SURVIVAL_NO_WATER_MINING);
        y = addPlayerToggle(x, y, w, "omnitrinket.gui.survival.noOxygenConsumption",
                () -> ClientSettingsCache.getBoolean(PlayerSettings.SURVIVAL_NO_OXYGEN),
                PlayerSettings.SURVIVAL_NO_OXYGEN);
        addPlayerToggle(x, y, w, "omnitrinket.gui.survival.removeFireOverlay",
                () -> ClientSettingsCache.getBoolean(PlayerSettings.SURVIVAL_NO_FIRE_OVERLAY),
                PlayerSettings.SURVIVAL_NO_FIRE_OVERLAY);
    }

    private void buildBuff(int x, int y, int w)
    {
        y = addPlayerToggle(x, y, w, "omnitrinket.gui.buff.enabled",
                () -> ClientSettingsCache.getBoolean(PlayerSettings.BUFF_ENABLED),
                PlayerSettings.BUFF_ENABLED);
        y = addPlayerToggle(x, y, w, "omnitrinket.gui.buff.nightVision",
                () -> ClientSettingsCache.getBoolean(PlayerSettings.NIGHT_VISION),
                PlayerSettings.NIGHT_VISION);
        y = addPlayerToggle(x, y, w, "omnitrinket.gui.buff.saturation",
                () -> ClientSettingsCache.getBoolean(PlayerSettings.SATURATION),
                PlayerSettings.SATURATION);
        addPlayerNumber(x, y, w, "omnitrinket.gui.buff.reach", PlayerSettings.BUFF_REACH);
    }

    private void buildEnvironment(int x, int y, int w)
    {
        var cfg = Config.COMMON;
        y = addToggle(x, y, w, "omnitrinket.gui.env.lockWeather",
                cfg.lockWeatherClear::get, cfg.lockWeatherClear::set, false);
        addToggle(x, y, w, "omnitrinket.gui.env.perpetualDay",
                cfg.perpetualDay::get, cfg.perpetualDay::set, false);
    }

    // ------------------------------------------------------------------
    // 控件工厂
    // ------------------------------------------------------------------

    private static int numberGroupW()
    {
        return BTN_W + GAP + BTN_W + GAP + VALUE_W + GAP + BTN_W;
    }

    private int addToggle(int x, int y, int w, String key,
                          java.util.function.BooleanSupplier getter,
                          java.util.function.Consumer<Boolean> setter,
                          boolean defaultValue)
    {
        int groupW = numberGroupW();
        Button btn = Button.builder(onOff(getter.getAsBoolean()), b ->
                {
                    commitEditing();
                    setter.accept(!getter.getAsBoolean());
                    refreshToggles();
                })
                .bounds(x + w - groupW, y, groupW, ROW_CTRL_H)
                .build();
        addRenderableWidget(btn);
        rows.add(btn);
        toggleRows.add(new ToggleRow(btn, getter, setter, defaultValue, false, null));
        labelRows.add(new LabelRow(t(key), x, y + (ROW_CTRL_H - 8) / 2 + 1));
        return y + ROW_H;
    }

    /**
     * 每玩家开关行。
     *
     * <p>点击后先乐观改本地镜像（手感零延迟），再发包让服务端改权威值，
     * 服务端确认后会回发同步包覆盖。默认值是描述符自带的，调用方不必再传。</p>
     */
    private int addPlayerToggle(int x, int y, int w, String key,
                                java.util.function.BooleanSupplier getter,
                                PlayerSettingKey<Boolean> settingsKey)
    {
        int groupW = numberGroupW();
        Button btn = Button.builder(onOff(getter.getAsBoolean()), b ->
                {
                    commitEditing();
                    boolean next = !getter.getAsBoolean();
                    ClientSettingsCache.setBoolean(settingsKey, next);
                    NetworkHandler.CHANNEL.sendToServer(
                            SetPlayerSettingsPacket.ofFlag(settingsKey, next));
                    refreshToggles();
                })
                .bounds(x + w - groupW, y, groupW, ROW_CTRL_H)
                .build();
        addRenderableWidget(btn);
        rows.add(btn);
        toggleRows.add(new ToggleRow(btn, getter, null,
                settingsKey.defaultValue(), true, settingsKey));
        labelRows.add(new LabelRow(t(key), x, y + (ROW_CTRL_H - 8) / 2 + 1));
        return y + ROW_H;
    }

    private void refreshToggles()
    {
        for (ToggleRow tr : toggleRows)
        {
            tr.refresh();
        }
        refreshModes();
    }

    private void refreshModes()
    {
        for (ModeRow mr : modeRows)
        {
            mr.refresh();
        }
    }

    /**
     * 每玩家三态模式行：左键循环 0 → 1 → 2，右键恢复默认值。
     */
    private int addPlayerMode(int x, int y, int w, String key,
                              PlayerSettingKey<Integer> settingsKey, String... modeKeys)
    {
        int groupW = numberGroupW();
        int initial = Math.max(0, Math.min(modeKeys.length - 1,
                ClientSettingsCache.getInt(settingsKey)));
        Button btn = Button.builder(Component.translatable(modeKeys[initial]), b ->
                {
                    commitEditing();
                    int current = Math.max(0, Math.min(modeKeys.length - 1,
                            ClientSettingsCache.getInt(settingsKey)));
                    int next = (current + 1) % modeKeys.length;
                    ClientSettingsCache.setInt(settingsKey, next);
                    NetworkHandler.CHANNEL.sendToServer(
                            SetPlayerSettingsPacket.ofInt(settingsKey, next));
                    refreshModes();
                })
                .bounds(x + w - groupW, y, groupW, ROW_CTRL_H)
                .build();
        addRenderableWidget(btn);
        rows.add(btn);
        modeRows.add(new ModeRow(btn, settingsKey, settingsKey.defaultValue(), modeKeys));
        labelRows.add(new LabelRow(t(key), x, y + (ROW_CTRL_H - 8) / 2 + 1));
        return y + ROW_H;
    }

    private int addNumber(int x, int y, int w, String key,
                          java.util.function.DoubleSupplier getter,
                          java.util.function.DoubleConsumer setter,
                          int decimals, double defaultValue, double min, double max)
    {
        int resetX = x + w - BTN_W;
        int plusX = resetX - GAP - BTN_W;
        int boxX = plusX - GAP - VALUE_W;
        int minusX = boxX - GAP - BTN_W;

        NumberRow row = new NumberRow(null, getter, setter, min, max, decimals, defaultValue);
        labelRows.add(new LabelRow(t(key), x, y + (ROW_CTRL_H - 8) / 2 + 1));

        // [−]
        Button minus = Button.builder(Component.literal("-"), b ->
        {
            commitEditing();
            row.set(row.get() - currentStep(row.get()));
            syncBox(row);
        }).bounds(minusX, y, BTN_W, ROW_CTRL_H).build();
        addRenderableWidget(minus);
        rows.add(minus);

        // 输入框
        EditBox box = new EditBox(this.font, boxX, y, VALUE_W, ROW_CTRL_H, t(key));
        box.setMaxLength(8);
        box.setValue(row.display());
        addRenderableWidget(box);
        rows.add(box);
        row.box = box;

        // 边打字边写回（合法才写）；updatingFields 期间不响应，避免回环
        box.setResponder(s ->
        {
            if (updatingFields || s.isEmpty() || ".".equals(s))
            {
                return;
            }
            try
            {
                row.set(Double.parseDouble(s));
            }
            catch (NumberFormatException ignored)
            {
                // 失焦时会归一化
            }
        });

        // [+]
        Button plus = Button.builder(Component.literal("+"), b ->
        {
            commitEditing();
            row.set(row.get() + currentStep(row.get()));
            syncBox(row);
        }).bounds(plusX, y, BTN_W, ROW_CTRL_H).build();
        addRenderableWidget(plus);
        rows.add(plus);

        // [R] 恢复默认值
        Button reset = Button.builder(Component.literal("R"), b ->
        {
            commitEditing();
            row.set(defaultValue);
            syncBox(row);
        }).bounds(resetX, y, BTN_W, ROW_CTRL_H).build();
        addRenderableWidget(reset);
        rows.add(reset);

        numberRows.add(row);
        return y + ROW_H;
    }

    /** 程序化刷新输入框显示（不触发 responder 回环） */
    private void syncBox(NumberRow row)
    {
        updatingFields = true;
        row.box.setValue(row.display());
        updatingFields = false;
    }

    private double currentStep(double current)
    {
        return current < 1.0D ? 0.1D : 1.0D;
    }

    // ------------------------------------------------------------------
    // 网络
    // ------------------------------------------------------------------

    /** 每玩家数值项：先乐观改本地镜像，再发包给服务端 */
    private void setPlayerDouble(PlayerSettingKey<Double> key, double value)
    {
        ClientSettingsCache.setDouble(key, value);
        NetworkHandler.CHANNEL.sendToServer(SetPlayerSettingsPacket.ofDouble(key, value));
    }

    /** 每玩家整数项：先乐观改本地镜像，再发包给服务端 */
    private void setPlayerInt(PlayerSettingKey<Integer> key, int value)
    {
        ClientSettingsCache.setInt(key, value);
        NetworkHandler.CHANNEL.sendToServer(SetPlayerSettingsPacket.ofInt(key, value));
    }

    /** 每玩家整数行（界面仍用数值输入框，写回时取整） */
    private int addPlayerInt(int x, int y, int w, String key, PlayerSettingKey<Integer> field)
    {
        return addNumber(x, y, w, key,
                () -> ClientSettingsCache.getInt(field),
                v -> setPlayerInt(field, (int) Math.round(v)),
                0, field.defaultValue(), field.min(), field.max());
    }

    /** 每玩家数值行：取值器 / 写入器 / 默认值 / 上下限全部来自描述符，调用方只给一项 */
    private int addPlayerNumber(int x, int y, int w, String key, PlayerSettingKey<Double> field)
    {
        return addNumber(x, y, w, key,
                () -> ClientSettingsCache.getDouble(field),
                v -> setPlayerDouble(field, v),
                2, field.defaultValue(), field.min(), field.max());
    }

    // ------------------------------------------------------------------
    // 滚动
    // ------------------------------------------------------------------

    private void applyScroll()
    {
        for (AbstractWidget w : rows)
        {
            Integer base = baseYMap.get(w);
            if (base != null)
            {
                w.setY(base + scrollOffset);
            }
        }
    }

    private void scrollBy(int delta)
    {
        if (maxScroll <= 0)
        {
            return;
        }
        commitEditing();
        int before = scrollOffset;
        scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset + delta));
        if (scrollOffset != before)
        {
            applyScroll();
        }
    }

    private int thumbHeight()
    {
        int trackH = contentBottom - contentTop;
        if (contentH <= 0)
        {
            return trackH;
        }
        return Math.max(16, trackH * trackH / Math.max(1, contentH));
    }

    private int scrollBarX()
    {
        return panelRight - PAD - SCROLL_W;
    }

    // ------------------------------------------------------------------
    // 输入框归一化（照搬 simpleinfo 的做法）
    // ------------------------------------------------------------------

    private NumberRow rowOf(EditBox box)
    {
        for (NumberRow r : numberRows)
        {
            if (r.box == box)
            {
                return r;
            }
        }
        return null;
    }

    /**
     * 归一化输入框：内容为空或非法 → 还原为当前真实值；合法 → 夹紧后回填。
     *
     * <p>对应需求「右键清空后没输入就点别处 → 恢复原本内容」。</p>
     */
    private void restoreNumberBox(EditBox box)
    {
        NumberRow row = rowOf(box);
        if (row == null)
        {
            return;
        }
        String s = box.getValue().trim();
        if (!s.isEmpty() && !".".equals(s))
        {
            try
            {
                row.set(Double.parseDouble(s));
            }
            catch (NumberFormatException ignored)
            {
                // 非法 → 下面 syncBox 会还原
            }
        }
        syncBox(row);
    }

    /** 提交当前正在编辑的输入框 */
    private void commitEditing()
    {
        if (getFocused() instanceof EditBox box)
        {
            restoreNumberBox(box);
        }
    }

    /**
     * 失焦时归一化。
     *
     * <p>重写 {@code setFocused}：焦点从一个 EditBox 移走时立刻归一化它的内容 ——
     * 这正是「点别处就取消/还原」的关键。</p>
     */
    @Override
    public void setFocused(GuiEventListener listener)
    {
        GuiEventListener previous = getFocused();
        super.setFocused(listener);
        if (previous != null && previous != listener && previous instanceof EditBox box)
        {
            restoreNumberBox(box);
        }
    }

    private boolean isOverAnyEditBox(double mx, double my)
    {
        for (NumberRow r : numberRows)
        {
            if (r.box.isMouseOver(mx, my))
            {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------
    // 事件
    // ------------------------------------------------------------------

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button)
    {
        // ---- 右键：输入框清空并聚焦 / 开关重置 ----
        if (button == 1)
        {
            for (NumberRow r : numberRows)
            {
                if (r.box.isMouseOver(mouseX, mouseY))
                {
                    updatingFields = true;
                    r.box.setValue("");
                    updatingFields = false;
                    setFocused(r.box);      // ★ 聚焦，出现光标，可直接输入
                    return true;
                }
            }
            for (ToggleRow tr : toggleRows)
            {
                if (tr.button.isMouseOver(mouseX, mouseY))
                {
                    if (tr.perPlayer)
                    {
                        ClientSettingsCache.setBoolean(tr.settingsKey, tr.defaultValue);
                        NetworkHandler.CHANNEL.sendToServer(
                                SetPlayerSettingsPacket.ofFlag(tr.settingsKey, tr.defaultValue));
                    }
                    else
                    {
                        tr.setter.accept(tr.defaultValue);
                    }
                    tr.refresh();
                    return true;
                }
            }
            for (ModeRow mr : modeRows)
            {
                if (mr.button.isMouseOver(mouseX, mouseY))
                {
                    ClientSettingsCache.setInt(mr.settingsKey, mr.defaultValue);
                    NetworkHandler.CHANNEL.sendToServer(
                            SetPlayerSettingsPacket.ofInt(mr.settingsKey, mr.defaultValue));
                    mr.refresh();
                    return true;
                }
            }
            return true;    // 右键不外传
        }

        // ---- 滑条拖动 ----
        if (button == 0 && maxScroll > 0)
        {
            int barX = scrollBarX();
            if (mouseX >= barX - 2 && mouseX <= barX + SCROLL_W + 2
                    && mouseY >= contentTop && mouseY <= contentBottom)
            {
                draggingScrollBar = true;
                dragStartMouseY = (int) mouseY;
                dragStartOffset = scrollOffset;
                scrollFromThumb(mouseY);
                return true;
            }
        }

        boolean handled = super.mouseClicked(mouseX, mouseY, button);

        // 点到任何输入框以外 → 失焦（触发归一化 / 还原）
        if (button == 0 && getFocused() instanceof EditBox && !isOverAnyEditBox(mouseX, mouseY))
        {
            setFocused(null);
        }
        return handled;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button)
    {
        if (button == 0)
        {
            draggingScrollBar = false;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY)
    {
        if (draggingScrollBar && maxScroll > 0)
        {
            int trackH = contentBottom - contentTop;
            int thumbH = thumbHeight();
            int dy = (int) mouseY - dragStartMouseY;
            int offset = dragStartOffset + (int) ((double) dy * maxScroll / Math.max(1, trackH - thumbH));
            scrollOffset = Math.max(0, Math.min(maxScroll, offset));
            applyScroll();
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    private void scrollFromThumb(double mouseY)
    {
        if (maxScroll <= 0)
        {
            return;
        }
        int trackH = contentBottom - contentTop;
        int thumbH = thumbHeight();
        int travel = trackH - thumbH;
        if (travel <= 0)
        {
            return;
        }
        double ratio = (mouseY - contentTop - thumbH / 2.0D) / travel;
        ratio = Math.max(0.0D, Math.min(1.0D, ratio));
        scrollOffset = (int) (ratio * maxScroll);
        applyScroll();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta)
    {
        scrollBy((int) (-delta * ROW_H));
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers)
    {
        if (KeyBindings.OPEN_SETTINGS.matches(keyCode, scanCode))
        {
            onClose();
            return true;
        }
        if (getFocused() instanceof EditBox && (keyCode == 257 || keyCode == 335 || keyCode == 258))
        {
            commitEditing();
            setFocused(null);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    // ------------------------------------------------------------------
    // 渲染
    // ------------------------------------------------------------------

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick)
    {
        renderBackground(graphics);
        drawVanillaPanel(graphics);

        // 侧边栏与内容的分隔线
        int divX = panelLeft + SIDEBAR_W - 3;
        graphics.fill(divX, panelTop + TITLE_H, divX + 1, panelBottom - FOOTER_H, COLOR_DIVIDER);

        super.render(graphics, mouseX, mouseY, partialTick);

        // 名称文字（随内容滚动）
        for (LabelRow lr : labelRows)
        {
            int ly = lr.baseY + scrollOffset;
            if (ly + 8 >= contentTop && ly <= contentBottom)
            {
                graphics.drawString(this.font, lr.text, lr.x, ly, textColor, textShadow);
            }
        }

        // 标题居中
        Component title = t("omnitrinket.gui.title");
        int tw = this.font.width(title);
        graphics.drawString(this.font, title,
                panelLeft + (panelRight - panelLeft - tw) / 2,
                panelTop + 7, textColor, textShadow);

        // 底部两行提示：移到<b>按钮所在那一行</b>，水平居中于两按钮之间
        Component hint1 = t("omnitrinket.gui.hint.line1");
        Component hint2 = t("omnitrinket.gui.hint.line2");
        int hintY = panelBottom - ROW_CTRL_H - 5 + 1;
        graphics.drawString(this.font, hint1,
                panelLeft + (panelRight - panelLeft - this.font.width(hint1)) / 2,
                hintY, textColor, textShadow);
        graphics.drawString(this.font, hint2,
                panelLeft + (panelRight - panelLeft - this.font.width(hint2)) / 2,
                hintY + 10, textColor, textShadow);

        if (maxScroll > 0)
        {
            drawScrollBar(graphics);
        }
    }

    /**
     * 面板底板：原版 {@code inventory.png} 的手动九宫格。
     *
     * <p>面板颜色不再手写，而是从原版容器贴图取 <b>4 个真实角 + 4 条边 + 中心 1×1</b>
     * 共 9 次 {@code blit} 拼出来。这样材质包替换原版 {@code inventory.png} 后，
     * 面板的黑边、高光、阴影与底色会自动跟随。</p>
     *
     * <p><b>为什么不用 {@code blitNineSliced}：</b>它要求四角在同一张独立九宫格源图内；
     * 若直接裁 {@code inventory.png} 左上角 9×9，右上、左下、右下会取到填充色而不是
     * 真实边框。这里改为按原版 176×166 布局分别取四个真实的角。</p>
     *
     * <p><b>为什么不用 {@code widgets.png}：</b>那张图是画按钮用的，边框只有 1~3px，
     * 拉大到整块面板后视觉上就是"一个巨型按钮"，早先已否决。</p>
     */
    private void drawVanillaPanel(GuiGraphics graphics)
    {
        // 外沿与旧手绘黑框对齐：旧框画在 panelLeft-1 .. panelRight+1
        int x = panelLeft - 1;
        int y = panelTop - 1;
        int w = (panelRight - panelLeft) + 2;
        int h = (panelBottom - panelTop) + 2;
        int right = x + w - PANEL_BORDER;
        int bottom = y + h - PANEL_BORDER;

        // 四角：取原版面板的四个真实角
        graphics.blit(PANEL_TEXTURE, x, y,
                0, 0, PANEL_BORDER, PANEL_BORDER);
        graphics.blit(PANEL_TEXTURE, right, y,
                VANILLA_PANEL_WIDTH - PANEL_BORDER, 0, PANEL_BORDER, PANEL_BORDER);
        graphics.blit(PANEL_TEXTURE, x, bottom,
                0, VANILLA_PANEL_HEIGHT - PANEL_BORDER, PANEL_BORDER, PANEL_BORDER);
        graphics.blit(PANEL_TEXTURE, right, bottom,
                VANILLA_PANEL_WIDTH - PANEL_BORDER,
                VANILLA_PANEL_HEIGHT - PANEL_BORDER,
                PANEL_BORDER, PANEL_BORDER);

        // 四边：取 1px 源条拉伸，保留原版黑边与高光/阴影
        graphics.blit(PANEL_TEXTURE, x + PANEL_BORDER, y,
                w - 2 * PANEL_BORDER, PANEL_BORDER,
                PANEL_BORDER, 0, 1, PANEL_BORDER,
                PANEL_TEXTURE_SIZE, PANEL_TEXTURE_SIZE);
        graphics.blit(PANEL_TEXTURE, x + PANEL_BORDER, bottom,
                w - 2 * PANEL_BORDER, PANEL_BORDER,
                PANEL_BORDER, VANILLA_PANEL_HEIGHT - PANEL_BORDER, 1, PANEL_BORDER,
                PANEL_TEXTURE_SIZE, PANEL_TEXTURE_SIZE);
        graphics.blit(PANEL_TEXTURE, x, y + PANEL_BORDER,
                PANEL_BORDER, h - 2 * PANEL_BORDER,
                0, PANEL_BORDER, PANEL_BORDER, 1,
                PANEL_TEXTURE_SIZE, PANEL_TEXTURE_SIZE);
        graphics.blit(PANEL_TEXTURE, right, y + PANEL_BORDER,
                PANEL_BORDER, h - 2 * PANEL_BORDER,
                VANILLA_PANEL_WIDTH - PANEL_BORDER, PANEL_BORDER, PANEL_BORDER, 1,
                PANEL_TEXTURE_SIZE, PANEL_TEXTURE_SIZE);

        // 中心：1×1 填充色拉伸，避免把原版格子/玩家模型带进来
        graphics.blit(PANEL_TEXTURE, x + PANEL_BORDER, y + PANEL_BORDER,
                w - 2 * PANEL_BORDER, h - 2 * PANEL_BORDER,
                PANEL_BORDER, PANEL_BORDER, 1, 1,
                PANEL_TEXTURE_SIZE, PANEL_TEXTURE_SIZE);
    }

    private void drawScrollBar(GuiGraphics graphics)
    {
        int barX = scrollBarX();
        graphics.fill(barX, contentTop, barX + SCROLL_W, contentBottom, COLOR_SCROLL_TRACK);
        int thumbH = thumbHeight();
        int travel = (contentBottom - contentTop) - thumbH;
        int thumbY = contentTop + (maxScroll == 0 ? 0 : (int) ((double) scrollOffset / maxScroll * travel));
        graphics.fill(barX, thumbY, barX + SCROLL_W, thumbY + thumbH, COLOR_SCROLL_THUMB);
        graphics.fill(barX, thumbY, barX + SCROLL_W, thumbY + 1, COLOR_SHADOW);
        graphics.fill(barX, thumbY + thumbH - 1, barX + SCROLL_W, thumbY + thumbH, COLOR_HIGHLIGHT);
    }

    // ------------------------------------------------------------------
    // 重置全部：弹出确认对话框（模态，避免误触）
    // ------------------------------------------------------------------

    private void confirmResetAll()
    {
        if (minecraft == null)
        {
            return;
        }
        commitEditing();
        minecraft.setScreen(new ConfirmScreen(
                confirmed ->
                {
                    if (confirmed)
                    {
                        doResetAll();
                    }
                    minecraft.setScreen(this);
                },
                Component.translatable("omnitrinket.gui.confirm.resetAll.title"),
                Component.translatable("omnitrinket.gui.confirm.resetAll.body"),
                Component.translatable("omnitrinket.gui.confirm.yes")
                        .withStyle(ChatFormatting.RED),
                Component.translatable("omnitrinket.gui.confirm.cancel")));
    }

    /** 重置全部：五个分类所有设置项恢复默认 */
    private void doResetAll()
    {
        var cfg = Config.COMMON;

        // 飞行 / 移动 / 生存 / 增益：**全部**是每玩家项，
        // 由下面的 allDefaults() 补丁统一负责，这里不用逐个 set。

        // 环境：唯一的全局配置，仍然是每项手动 set
        cfg.lockWeatherClear.set(false);
        cfg.perpetualDay.set(false);

        saveAllConfig();

        // 每玩家选项：本地镜像逐项恢复默认，再发一个"全部恢复默认"的补丁包。
        // 表驱动 ⇒ 以后新增设置项会自动包含进来，不用改这里。
        for (PlayerSettingKey<Boolean> key : PlayerSettings.BOOLEAN_KEYS)
        {
            if (key.clientWritable())
            {
                ClientSettingsCache.setBoolean(key, key.defaultValue());
            }
        }
        for (PlayerSettingKey<Double> key : PlayerSettings.DOUBLE_KEYS)
        {
            ClientSettingsCache.setDouble(key, key.defaultValue());
        }
        for (PlayerSettingKey<Integer> key : PlayerSettings.INT_KEYS)
        {
            ClientSettingsCache.setInt(key, key.defaultValue());
        }
        NetworkHandler.CHANNEL.sendToServer(SetPlayerSettingsPacket.allDefaults());

        buildContent();
    }

    private void saveAllConfig()
    {
        Config.COMMON.lockWeatherClear.save();
        Config.COMMON.perpetualDay.save();
    }

    @Override
    public void onClose()
    {
        commitEditing();
        super.onClose();
    }

    @Override
    public boolean isPauseScreen()
    {
        return false;
    }
}
