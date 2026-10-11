package com.rlcyyg1108.omnitrinket.setting;

import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.common.util.LazyOptional;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 玩家设置 Capability 的实现。
 *
 * 它同时承担两个职责：
 * - 作为 Capability 实例持有 PlayerSettings 数据
 * - 实现 ICapabilitySerializable 以便随玩家存档自动读写 NBT
 */
public class PlayerSettingsCapability implements IPlayerSettings, ICapabilitySerializable<CompoundTag>
{
    /** Capability 单例引用，注册后全局使用 */
    public static final Capability<IPlayerSettings> PLAYER_SETTINGS =
            CapabilityManager.get(new CapabilityToken<>() {});

    private final PlayerSettings settings = new PlayerSettings();
    private final LazyOptional<IPlayerSettings> lazyOptional = LazyOptional.of(() -> this);

    @Override
    public PlayerSettings getSettings()
    {
        return settings;
    }

    @Override
    public @NotNull <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side)
    {
        if (cap == PLAYER_SETTINGS)
        {
            return lazyOptional.cast();
        }
        return LazyOptional.empty();
    }

    @Override
    public CompoundTag serializeNBT()
    {
        CompoundTag tag = new CompoundTag();
        settings.saveNBTData(tag);
        return tag;
    }

    @Override
    public void deserializeNBT(CompoundTag nbt)
    {
        settings.loadNBTData(nbt);
    }

    /**
     * 让外部（如玩家断开连接时）可以主动让 LazyOptional 失效。
     */
    public void invalidate()
    {
        lazyOptional.invalidate();
    }
}
