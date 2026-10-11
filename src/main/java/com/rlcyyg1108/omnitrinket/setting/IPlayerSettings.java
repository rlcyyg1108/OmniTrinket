package com.rlcyyg1108.omnitrinket.setting;

/**
 * 玩家设置 Capability 的对外接口。
 *
 * 之所以单独抽一个接口而不是直接暴露 PlayerSettings，是为了后续
 * 方便替换内部实现（例如未来改成从别处读取）而不影响调用方。
 */
public interface IPlayerSettings
{
    /**
     * 获取该玩家的设置数据对象。
     */
    PlayerSettings getSettings();
}
