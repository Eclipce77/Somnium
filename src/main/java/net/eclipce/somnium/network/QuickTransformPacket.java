package net.eclipce.somnium.network;

import net.eclipce.somnium.core.ability.AbilityActivationContext;
import net.eclipce.somnium.core.ability.AbilityInstance;
import net.eclipce.somnium.core.ability.AbilityType;
import net.eclipce.somnium.core.ability.transformation.TransformationAbilityType;
import net.eclipce.somnium.core.data.SomniumCapability;
import net.eclipce.somnium.core.data.SomniumPlayerData;
import net.eclipce.somnium.core.registry.SomniumRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Client → Server packet for the transformation crouch quick-bind.
 *
 * <p>When crouching + pressing the transformation keybind:</p>
 * <ul>
 *     <li>If not transformed → activates the most recent transformation</li>
 *     <li>If transformed → deactivates the current transformation</li>
 * </ul>
 */
public class QuickTransformPacket {

    public QuickTransformPacket() {}

    public void encode(FriendlyByteBuf buf) {
        // No data — server determines action from player state
    }

    public static QuickTransformPacket decode(FriendlyByteBuf buf) {
        return new QuickTransformPacket();
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) return;

            SomniumPlayerData data = SomniumCapability.get(player);
            if (data == null) return;

            if (data.hasActiveTransformation()) {
                // Currently transformed — deactivate
                deactivateCurrentTransformation(player, data);
            } else {
                // Not transformed — activate most recent
                activateMostRecent(player, data);
            }

            data.markDirty();
            SomniumNetwork.syncToClient(player);
        });
        context.setPacketHandled(true);
    }

    private void deactivateCurrentTransformation(ServerPlayer player, SomniumPlayerData data) {
        ResourceLocation activeKey = data.getActiveTransformation();
        if (activeKey == null) return;

        AbilityInstance instance = data.getAbilityInstance(activeKey);
        if (instance == null) return;

        AbilityType type = instance.getAbilityType();
        if (!(type instanceof TransformationAbilityType)) return;

        AbilityActivationContext ctx = new AbilityActivationContext(
                player, player.level(), instance);

        // Trigger deactivation (starts transform-out phase)
        type.onDeactivate(ctx);
        instance.setActive(false);
    }

    private void activateMostRecent(ServerPlayer player, SomniumPlayerData data) {
        ResourceLocation recentKey = data.getMostRecentTransformation();
        if (recentKey == null) return;

        AbilityInstance instance = data.getAbilityInstance(recentKey);
        if (instance == null) return;

        AbilityType type = instance.getAbilityType();
        if (!(type instanceof TransformationAbilityType)) return;

        // Must be unlocked
        if (!data.isAbilityUnlocked(type)) return;

        AbilityActivationContext ctx = new AbilityActivationContext(
                player, player.level(), instance);

        if (type.canActivate(ctx)) {
            type.onActivate(ctx);
            type.applyCosts(ctx);
            instance.setActive(true);
            data.setMostRecentTransformation(recentKey);
        }
    }
}