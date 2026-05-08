package net.eclipce.somnium.event;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.eventbus.api.Event;

/**
 * Forge event fired when a power's level increases.
 * Subscribe on {@link net.minecraftforge.common.MinecraftForge#EVENT_BUS}.
 */
public class SomniumPowerLevelEvent extends Event {

    private final ServerPlayer player;
    private final ResourceLocation powerKey;
    private final int oldLevel;
    private final int newLevel;

    public SomniumPowerLevelEvent(ServerPlayer player, ResourceLocation powerKey,
                                  int oldLevel, int newLevel) {
        this.player = player;
        this.powerKey = powerKey;
        this.oldLevel = oldLevel;
        this.newLevel = newLevel;
    }

    public ServerPlayer getPlayer() { return player; }
    public ResourceLocation getPowerKey() { return powerKey; }
    public int getOldLevel() { return oldLevel; }
    public int getNewLevel() { return newLevel; }
}