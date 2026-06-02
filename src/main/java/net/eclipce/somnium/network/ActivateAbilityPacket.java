package net.eclipce.somnium.network;

import net.eclipce.somnium.core.ability.AbilityActivationContext;
import net.eclipce.somnium.core.ability.AbilityInstance;
import net.eclipce.somnium.core.ability.AbilityType;
import net.eclipce.somnium.core.ability.ActivationType;
import net.eclipce.somnium.core.data.SomniumCapability;
import net.eclipce.somnium.core.data.SomniumPlayerData;
import net.eclipce.somnium.core.registry.SomniumRegistries;
import net.eclipce.somnium.core.progression.ProgressionHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Client → Server packet sent when the player presses or releases an
 * ability bar keybind.
 *
 * <p>The packet carries two pieces of information:</p>
 * <ul>
 *     <li><strong>slot</strong> — which bar slot was pressed (0 to 5)</li>
 *     <li><strong>action</strong> — whether this is a key press or release</li>
 * </ul>
 *
 * <p>The server validates the request (is the slot valid? is there an ability
 * there? can it activate?) and then executes the appropriate behavior method
 * on the AbilityType. The client never executes ability logic directly —
 * it only sends this "intent" packet.</p>
 *
 * <h3>How different activation types respond</h3>
 * <ul>
 *     <li>{@code INSTANT} — PRESS triggers {@code onActivate()}. RELEASE is ignored.</li>
 *     <li>{@code TOGGLE} — PRESS toggles between {@code onActivate()} and
 *         {@code onDeactivate()}. RELEASE is ignored.</li>
 *     <li>{@code HOLD} — PRESS triggers {@code onKeyDown()}, RELEASE triggers
 *         {@code onKeyUp()}.</li>
 *     <li>{@code CHARGED} — PRESS triggers {@code onChargeStart()}, RELEASE
 *         triggers {@code onChargeRelease()}.</li>
 *     <li>{@code PASSIVE} — never sent (passives aren't on the bar).</li>
 * </ul>
 */
public class ActivateAbilityPacket {

    /**
     * The type of key action.
     */
    public enum Action {
        /** The key was pressed down. */
        PRESS,
        /** The key was released. */
        RELEASE;

        public static Action fromOrdinal(int ordinal) {
            return ordinal == 1 ? RELEASE : PRESS;
        }
    }

    private final int slot;
    private final Action action;
    private final int barType; // 0 = ability bar, 1 = transformation bar, 2 = category bar
    private final String categoryId; // only used when barType == 2

    /**
     * Creates an activation packet for the ability bar.
     */
    public ActivateAbilityPacket(int slot, Action action) {
        this(slot, action, 0, "");
    }

    /**
     * Creates an activation packet for a specific bar (ability or transformation).
     */
    public ActivateAbilityPacket(int slot, Action action, int barType) {
        this(slot, action, barType, "");
    }

    /**
     * Creates an activation packet for a custom category bar.
     */
    public ActivateAbilityPacket(int slot, Action action, ResourceLocation categoryId) {
        this(slot, action, 2, categoryId.toString());
    }

    private ActivateAbilityPacket(int slot, Action action, int barType, String categoryId) {
        this.slot = slot;
        this.action = action;
        this.barType = barType;
        this.categoryId = categoryId;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(slot);
        buf.writeVarInt(action.ordinal());
        buf.writeVarInt(barType);
        buf.writeUtf(categoryId);
    }

    public static ActivateAbilityPacket decode(FriendlyByteBuf buf) {
        int slot = buf.readVarInt();
        Action action = Action.fromOrdinal(buf.readVarInt());
        int barType = buf.readVarInt();
        String categoryId = buf.readUtf(256);
        return new ActivateAbilityPacket(slot, action, barType, categoryId);
    }

    /**
     * Handles this packet on the server side. Validates the request and
     * routes to the appropriate AbilityType behavior method.
     */
    public void handle(Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) return;

            SomniumPlayerData data = SomniumCapability.get(player);
            if (data == null) return;

            // Route to correct bar
            AbilityInstance instance;
            if (barType == 2 && !categoryId.isEmpty()) {
                // Custom category bar
                ResourceLocation catKey = new ResourceLocation(categoryId);
                if (slot < 0 || slot >= SomniumPlayerData.BAR_SIZE) return;
                instance = data.getCategoryBarSlotInstance(catKey, slot);
            } else if (barType == 1) {
                if (slot < 0 || slot >= SomniumPlayerData.TRANS_BAR_SIZE) return;
                instance = data.getTransBarSlotInstance(slot);
            } else {
                if (slot < 0 || slot >= SomniumPlayerData.BAR_SIZE) return;
                instance = data.getBarSlotInstance(slot);
            }
            if (instance == null) return;

            AbilityType abilityType = instance.getAbilityType();
            ActivationType activationType = abilityType.getActivationType();
            AbilityActivationContext activationCtx = new AbilityActivationContext(
                    player, player.level(), instance, slot);

            switch (action) {
                case PRESS -> handlePress(abilityType, activationType, instance, activationCtx, data);
                case RELEASE -> handleRelease(abilityType, activationType, instance, activationCtx);
            }

            // Track most recent for category quick-bind
            if (barType == 2 && !categoryId.isEmpty() && action == Action.PRESS) {
                ResourceLocation catKey = new ResourceLocation(categoryId);
                ResourceLocation abilityKey = SomniumRegistries.getAbilityKey(abilityType);
                if (abilityKey != null) {
                    data.setMostRecentCategoryAbility(catKey, abilityKey);
                }
            }

            data.markDirty();
            SomniumNetwork.syncToClient(player);
        });
        context.setPacketHandled(true);
    }

    /**
     * Handles a key press for the given ability.
     */
    private static void handlePress(AbilityType type, ActivationType activation,
                                    AbilityInstance instance,
                                    AbilityActivationContext ctx,
                                    SomniumPlayerData data) {
        switch (activation) {
            case INSTANT -> {
                if (type.canActivate(ctx) && type.canAffordMeterCosts(data)) {
                    type.onActivate(ctx);
                    type.applyCosts(ctx);
                    type.applyMeterCosts(data);
                    ProgressionHandler.onAbilityActivated(ctx.getPlayer(), type);
                    fireCastAnimation(type, ctx.getPlayer());
                }
            }
            case TOGGLE -> {
                if (instance.isActive()) {
                    type.onDeactivate(ctx);
                    instance.setActive(false);
                } else {
                    if (type.canActivate(ctx) && type.canAffordMeterCosts(data)) {
                        type.onActivate(ctx);
                        type.applyCosts(ctx);
                        type.applyMeterCosts(data);
                        instance.setActive(true);
                        ProgressionHandler.onAbilityActivated(ctx.getPlayer(), type);
                        fireCastAnimation(type, ctx.getPlayer());
                        if (type instanceof net.eclipce.somnium.core.ability.transformation.TransformationAbilityType) {
                            ResourceLocation transKey = SomniumRegistries.getAbilityKey(type);
                            data.setMostRecentTransformation(transKey);
                        }
                    }
                }
            }
            case HOLD -> {
                if (type.canActivate(ctx) && type.canAffordMeterCosts(data)) {
                    type.onKeyDown(ctx);
                    type.applyMeterCosts(data);
                    instance.setActive(true);
                    instance.resetChargeTicks();
                    ProgressionHandler.onAbilityActivated(ctx.getPlayer(), type);
                }
            }
            case CHARGED -> {
                if (type.canActivate(ctx) && type.canAffordMeterCosts(data)) {
                    type.onChargeStart(ctx);
                    type.applyMeterCosts(data);
                    instance.setActive(true);
                    instance.resetChargeTicks();
                    ProgressionHandler.onAbilityActivated(ctx.getPlayer(), type);
                }
            }
            case PASSIVE -> {
                // Passives are never on the bar — ignore
            }
        }
    }

    /**
     * Handles a key release for the given ability.
     */
    private static void handleRelease(AbilityType type, ActivationType activation,
                                      AbilityInstance instance,
                                      AbilityActivationContext ctx) {
        switch (activation) {
            case HOLD -> {
                if (instance.isActive()) {
                    type.onKeyUp(ctx);
                    instance.setActive(false);
                    instance.resetChargeTicks();
                    type.applyCosts(ctx);
                }
            }
            case CHARGED -> {
                if (instance.isActive()) {
                    type.onChargeRelease(ctx, instance.getChargeTicks());
                    instance.setActive(false);
                    type.applyCosts(ctx);
                    instance.resetChargeTicks();
                }
            }
            // INSTANT, TOGGLE, PASSIVE — release is irrelevant
            default -> {}
        }
    }

    /**
     * Fires the GeckoLib cast animation for an ability, if one is configured
     * and GeckoLib is present. Sends to the activating player and all observers.
     */
    private static void fireCastAnimation(AbilityType type, ServerPlayer player) {
        if (!net.eclipce.somnium.compat.geckolib.GeckoLibCompat.isLoaded()) return;
        String anim = type.getCastAnimation();
        net.minecraft.resources.ResourceLocation model = type.getCastAnimationModel();
        if (anim == null || model == null) return;
        net.eclipce.somnium.compat.geckolib.animation.SomniumAnimHelper
                .triggerCastAnimation(player, anim, model);
    }
}