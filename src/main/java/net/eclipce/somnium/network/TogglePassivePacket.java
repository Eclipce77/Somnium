package net.eclipce.somnium.network;

import net.eclipce.somnium.core.ability.AbilityActivationContext;
import net.eclipce.somnium.core.ability.AbilityInstance;
import net.eclipce.somnium.core.ability.AbilityType;
import net.eclipce.somnium.core.ability.ActivationType;
import net.eclipce.somnium.core.data.SomniumCapability;
import net.eclipce.somnium.core.data.SomniumPlayerData;
import net.eclipce.somnium.core.registry.SomniumRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Client → Server packet sent when the player clicks a passive ability
 * in the Passives tab of the ability inventory to toggle it on or off.
 *
 * <p>The packet carries the ability's registry name. The server validates
 * that the ability exists, is a passive, and is unlocked for the player,
 * then toggles its enabled state and fires the appropriate callback
 * ({@code onEnabled} or {@code onDisabled}).</p>
 */
public class TogglePassivePacket {

    private final String abilityId;

    /**
     * Creates a passive toggle packet.
     *
     * @param abilityId the registry name of the passive ability to toggle
     */
    public TogglePassivePacket(ResourceLocation abilityId) {
        this.abilityId = abilityId.toString();
    }

    private TogglePassivePacket(String abilityId) {
        this.abilityId = abilityId;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(abilityId);
    }

    public static TogglePassivePacket decode(FriendlyByteBuf buf) {
        return new TogglePassivePacket(buf.readUtf(256));
    }

    /**
     * Handles this packet on the server side. Validates and toggles the
     * passive ability's enabled state.
     */
    public void handle(Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) return;

            SomniumPlayerData data = SomniumCapability.get(player);
            if (data == null) return;

            ResourceLocation key = new ResourceLocation(abilityId);
            AbilityType type = SomniumRegistries.getAbilityValue(key);
            if (type == null) return;

            // Validate: must be a passive ability and must be unlocked
            if (type.getActivationType() != ActivationType.PASSIVE) return;
            if (!data.isAbilityUnlocked(type)) return;

            // Toggle the state
            boolean currentlyEnabled = data.isPassiveEnabled(type);
            boolean newState = !currentlyEnabled;
            data.setPassiveEnabled(type, newState);

            // Fire the appropriate callback
            AbilityInstance instance = data.getAbilityInstance(type);
            if (instance != null) {
                AbilityActivationContext activationCtx = new AbilityActivationContext(
                        player, player.level(), instance);

                if (newState) {
                    type.onEnabled(activationCtx);
                } else {
                    type.onDisabled(activationCtx);
                }

                instance.setEnabled(newState);
            }

            // Sync back to client
            SomniumNetwork.syncToClient(player);
        });
        context.setPacketHandled(true);
    }
}