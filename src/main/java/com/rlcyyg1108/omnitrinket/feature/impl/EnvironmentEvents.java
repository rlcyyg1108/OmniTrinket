package com.rlcyyg1108.omnitrinket.feature.impl;

import com.rlcyyg1108.omnitrinket.Config;
import com.rlcyyg1108.omnitrinket.OmniTrinket;
import com.rlcyyg1108.omnitrinket.util.TrinketHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 环境模块的运行时驱动（服务端）。
 *
 * === 为什么每 20 tick 而不是每 tick ===
 * 天气与时间都是**低频状态**，每 tick 去写只会平白增加服务器负担，
 * 而且会不停打断原版的天气/时间结算。20 tick（1 秒）足够及时。
 *
 * === 为什么不用 curioTick ===
 * 老规矩：curioTick 只在佩戴时触发。摘下饰品后必须把世界状态交还给原版，
 * 所以统一用 ServerTickEvent 处理"生效 / 恢复"两个方向。
 *
 * === 作用范围 ===
 * 只作用于"佩戴者所在的维度"。同一维度多个佩戴者时，以第一个生效的为准
 * （其余人的设置不再重复施加，避免互相打架）。
 *
 * === 不做权限校验 ===
 * 本模组假定使用者是单人世界的玩家或自己服务器的服主，因此
 * <b>不检查 OP 权限</b>：戴上饰品、开关打开，世界状态就直接被接管。
 */
@Mod.EventBusSubscriber(modid = OmniTrinket.MODID)
public final class EnvironmentEvents
{
    /** 检查间隔（tick） */
    private static final int INTERVAL = 20;

    /** 每个维度是否由本模组接管了天气（用于"收回时通知原版"） */
    private static final Map<net.minecraft.resources.ResourceKey<Level>, UUID> WEATHER_OWNER = new HashMap<>();
    private static final Map<net.minecraft.resources.ResourceKey<Level>, UUID> TIME_OWNER = new HashMap<>();

    private EnvironmentEvents()
    {
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END)
        {
            return;
        }
        if (event.getServer().getTickCount() % INTERVAL != 0)
        {
            return;
        }

        // 逐个维度结算：先看该维度有没有"合法的佩戴者"
        for (ServerLevel level : event.getServer().getAllLevels())
        {
            ServerPlayer owner = findOwner(level);
            applyWeather(level, owner);
            applyTime(level, owner);
        }
    }

    /**
     * 找出该维度里第一个"佩戴了饰品且开着环境开关"的玩家。
     *
     * @return null 表示该维度当前没有合法佩戴者
     */
    private static ServerPlayer findOwner(ServerLevel level)
    {
        for (ServerPlayer player : level.players())
        {
            if (isActive(player))
            {
                return player;
            }
        }
        return null;
    }

    private static boolean isActive(ServerPlayer player)
    {
        if (!TrinketHelper.isEquipped(player))
        {
            return false;
        }
        return Config.COMMON.lockWeatherClear.get() || Config.COMMON.perpetualDay.get();
    }

    // ------------------------------------------------------------------
    // 晴天锁定
    // ------------------------------------------------------------------

    private static void applyWeather(ServerLevel level, ServerPlayer owner)
    {
        var key = level.dimension();
        boolean want = owner != null && Config.COMMON.lockWeatherClear.get();

        if (want)
        {
            WEATHER_OWNER.put(key, owner.getUUID());
            // 强制清空：既清除正在下的雨/雷，也把计时器重置，
            // 让原版在下一次结算前不会立刻又下雨。
            if (level.isRaining() || level.isThundering())
            {
                level.setWeatherParameters(6000, 0, false, false);
            }
            else
            {
                // 即使没在下雨，也要持续压制"天气更替计时器"，
                // 否则雨天会在几秒后被原版安排上。
                level.setWeatherParameters(6000, 0, false, false);
            }
        }
        else if (WEATHER_OWNER.remove(key) != null)
        {
            // 由我们接管过、现在要交还：给一个正常的晴天时长即可，
            // 之后完全交回原版天气系统自由发挥。
            level.setWeatherParameters(6000, 0, false, false);
        }
    }

    // ------------------------------------------------------------------
    // 终日为白
    // ------------------------------------------------------------------

    private static void applyTime(ServerLevel level, ServerPlayer owner)
    {
        var key = level.dimension();
        boolean want = owner != null && Config.COMMON.perpetualDay.get();

        if (want)
        {
            TIME_OWNER.put(key, owner.getUUID());
            long target = Config.PERPETUAL_DAY_TIME;
            // 只改"当天时间"，不动 worldAge / totalTime，
            // 避免影响依赖天数计数的机制（例如幻翼、月相）。
            if (level.getDayTime() % 24000L != target)
            {
                level.setDayTime(target);
            }
        }
        else
        {
            TIME_OWNER.remove(key);
            // 时间无需"恢复"，下一 tick 原版就会继续推进。
        }
    }

    /**
     * 玩家离线时，如果他是某个维度的接管者，立刻把该维度交还。
     */
    @SubscribeEvent
    public static void onLoggedOut(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event)
    {
        if (!(event.getEntity() instanceof ServerPlayer player))
        {
            return;
        }
        UUID id = player.getUUID();
        WEATHER_OWNER.entrySet().removeIf(e -> e.getValue().equals(id));
        TIME_OWNER.entrySet().removeIf(e -> e.getValue().equals(id));
    }
}
