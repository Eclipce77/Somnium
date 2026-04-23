package net.eclipce.somnium.core.tag;

import net.eclipce.somnium.Somnium;
import net.eclipce.somnium.core.data.SomniumCapability;
import net.eclipce.somnium.core.data.SomniumPlayerData;
import net.eclipce.somnium.core.power.Power;
import net.eclipce.somnium.core.registry.SomniumRegistries;
import net.eclipce.somnium.network.SomniumNetwork;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.registries.IForgeRegistry;

import javax.annotation.Nullable;

/**
 * Handles player tag management with auto-grant/revoke processing.
 *
 * <p>When a tag is added or removed from a player, this handler scans all
 * registered powers for matching {@code autoGrantTag} values and automatically
 * grants or revokes the corresponding powers.</p>
 *
 * <h3>Usage for addon developers</h3>
 * <pre>{@code
 * // Grant a tag to a player (e.g., from an item use or event)
 * TagHandler.addTag(player, new ResourceLocation("mymod", "fire_user"));
 *
 * // This will automatically grant any power that has:
 * // .autoGrantTag(new ResourceLocation("mymod", "fire_user"))
 *
 * // Remove a tag (auto-revokes associated powers)
 * TagHandler.removeTag(player, new ResourceLocation("mymod", "fire_user"));
 * }</pre>
 *
 * @see Power.Builder#autoGrantTag(ResourceLocation)
 * @see Power.Builder#requiredTag(ResourceLocation)
 */
public final class TagHandler {

    /**
     * Adds a tag to a player and processes auto-grants.
     * Scans all registered powers for matching autoGrantTag values
     * and grants them if the player doesn't already have them.
     *
     * @param player the server-side player
     * @param tag    the tag to add
     * @return true if the tag was newly added (false if already present)
     */
    public static boolean addTag(ServerPlayer player, ResourceLocation tag) {
        SomniumPlayerData data = SomniumCapability.get(player);
        if (data == null) return false;

        boolean added = data.addTag(tag);
        if (!added) return false;

        // Scan all registered powers for auto-grant matches
        IForgeRegistry<Power> registry = SomniumRegistries.getPowerRegistry();
        if (registry != null) {
            for (Power power : registry) {
                ResourceLocation autoTag = power.getAutoGrantTag();
                if (tag.equals(autoTag) && !data.hasPower(power)) {
                    data.grantPower(power);
                    Somnium.LOGGER.debug("Auto-granted power {} to {} via tag {}",
                            SomniumRegistries.getPowerKey(power), player.getName().getString(), tag);
                }
            }
        }

        SomniumNetwork.syncToClient(player);
        return true;
    }

    /**
     * Removes a tag from a player and processes auto-revokes.
     * Scans all registered powers for matching autoGrantTag values
     * and revokes them if the player has them.
     *
     * @param player the server-side player
     * @param tag    the tag to remove
     * @return true if the tag was present and removed
     */
    public static boolean removeTag(ServerPlayer player, ResourceLocation tag) {
        SomniumPlayerData data = SomniumCapability.get(player);
        if (data == null) return false;

        boolean removed = data.removeTag(tag);
        if (!removed) return false;

        // Scan all registered powers for auto-revoke matches
        IForgeRegistry<Power> registry = SomniumRegistries.getPowerRegistry();
        if (registry != null) {
            for (Power power : registry) {
                ResourceLocation autoTag = power.getAutoGrantTag();
                if (tag.equals(autoTag) && data.hasPower(power)) {
                    data.revokePower(power);
                    Somnium.LOGGER.debug("Auto-revoked power {} from {} via tag {}",
                            SomniumRegistries.getPowerKey(power), player.getName().getString(), tag);
                }
            }
        }

        SomniumNetwork.syncToClient(player);
        return true;
    }

    /**
     * Checks if a player meets the required tag for a power.
     * If the power has no requiredTag, returns true.
     *
     * @param data  the player's Somnium data
     * @param power the power to check
     * @return true if the player has the required tag (or no tag is required)
     */
    public static boolean meetsRequiredTag(SomniumPlayerData data, Power power) {
        ResourceLocation requiredTag = power.getRequiredTag();
        return requiredTag == null || data.hasTag(requiredTag);
    }

    private TagHandler() {}
}