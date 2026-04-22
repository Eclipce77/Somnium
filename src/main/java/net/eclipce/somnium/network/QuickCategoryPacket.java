package net.eclipce.somnium.network;

import net.eclipce.somnium.core.ability.AbilityActivationContext;
import net.eclipce.somnium.core.ability.AbilityInstance;
import net.eclipce.somnium.core.ability.AbilityType;
import net.eclipce.somnium.core.ability.ActivationType;
import net.eclipce.somnium.core.data.SomniumCapability;
import net.eclipce.somnium.core.data.SomniumPlayerData;
import net.eclipce.somnium.core.registry.SomniumRegistries;
import net.eclipce.somnium.core.unlock.ProgressionHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Client → Server packet for the custom category crouch quick-bind.
 *
 * <p>Activates the most recently used ability from the specified category.
 * If that ability is currently active (toggled on), deactivates it instead.</p>
 *
 * <h3>Usage by addon developers</h3>
 * <pre>{@code
 * // In your client tick handler:
 * if (MY_KEY.consumeClick() && mc.player.isCrouching()) {
 *     SomniumNetwork.sendToServer(
 *         new QuickCategoryPacket(MyCategories.MAGIC.getId()));
 * }
 * }</pre>
 */
public class QuickCategoryPacket {

    private final String categoryId;

    public QuickCategoryPacket(ResourceLocation categoryId) {
        this.categoryId = categoryId.toString();
    }

    private QuickCategoryPacket(String categoryId) {
        this.categoryId = categoryId;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(categoryId);
    }

    public static QuickCategoryPacket decode(FriendlyByteBuf buf) {
        return new QuickCategoryPacket(buf.readUtf(256));
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) return;

            SomniumPlayerData data = SomniumCapability.get(player);
            if (data == null) return;

            ResourceLocation catKey = new ResourceLocation(categoryId);
            ResourceLocation recentKey = data.getMostRecentCategoryAbility(catKey);
            if (recentKey == null) return;

            AbilityInstance instance = data.getAbilityInstance(recentKey);
            if (instance == null) return;

            AbilityType type = instance.getAbilityType();
            if (!data.isAbilityUnlocked(type)) return;

            AbilityActivationContext actCtx = new AbilityActivationContext(
                    player, player.level(), instance);
            ActivationType activation = type.getActivationType();

            switch (activation) {
                case TOGGLE -> {
                    if (instance.isActive()) {
                        type.onDeactivate(actCtx);
                        instance.setActive(false);
                    } else if (type.canActivate(actCtx)) {
                        type.onActivate(actCtx);
                        type.applyCosts(actCtx);
                        instance.setActive(true);
                        ProgressionHandler.onAbilityActivated(player, type);
                        data.setMostRecentCategoryAbility(catKey, recentKey);
                    }
                }
                case INSTANT -> {
                    if (type.canActivate(actCtx)) {
                        type.onActivate(actCtx);
                        type.applyCosts(actCtx);
                        ProgressionHandler.onAbilityActivated(player, type);
                        data.setMostRecentCategoryAbility(catKey, recentKey);
                    }
                }
                default -> {
                    // HOLD/CHARGED/PASSIVE don't support quick-bind
                }
            }

            data.markDirty();
            SomniumNetwork.syncToClient(player);
        });
        context.setPacketHandled(true);
    }
}