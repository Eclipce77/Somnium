package net.eclipce.somnium.compat.geckolib.player;

import net.eclipce.somnium.core.ability.transformation.TransformationData;
import net.eclipce.somnium.core.data.SomniumCapability;
import net.eclipce.somnium.core.data.SomniumPlayerData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.GeoAnimatable;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.*;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * GeckoLib animatable wrapper for players during transformations.
 *
 * <p>Each player gets one instance (cached by UUID) that manages their
 * transformation animation state. When a transformation with a GeckoLib
 * model is active, this object drives the animation controllers.</p>
 *
 * <p>Not a real entity — uses GeckoLib's {@link GeoAnimatable} interface
 * directly. The {@link TransformedPlayerRenderer} renders this alongside
 * (or instead of) the vanilla player model.</p>
 */
public class TransformedPlayerAnimatable implements GeoAnimatable {

    private static final Map<UUID, TransformedPlayerAnimatable> CACHE =
            new ConcurrentHashMap<>();

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final UUID playerUUID;

    @Nullable
    private TransformationData currentData;

    private TransformedPlayerAnimatable(UUID playerUUID) {
        this.playerUUID = playerUUID;
    }

    /**
     * Gets or creates the animatable for a player.
     */
    public static TransformedPlayerAnimatable getOrCreate(Player player) {
        return CACHE.computeIfAbsent(player.getUUID(),
                uuid -> new TransformedPlayerAnimatable(uuid));
    }

    /**
     * Removes a player's animatable (on disconnect, etc.).
     */
    public static void remove(UUID playerUUID) {
        CACHE.remove(playerUUID);
    }

    /**
     * Clears all cached animatables.
     */
    public static void clearAll() {
        CACHE.clear();
    }

    // ═══════════════════════════════════════════════════════════════════
    //  State
    // ═══════════════════════════════════════════════════════════════════

    public UUID getPlayerUUID() { return playerUUID; }

    @Nullable
    public TransformationData getCurrentData() { return currentData; }

    public void setCurrentData(@Nullable TransformationData data) {
        this.currentData = data;
    }

    /**
     * Checks if this player currently has an active transformation with
     * a GeckoLib model.
     */
    public boolean hasActiveModel() {
        return currentData != null && currentData.hasModel();
    }

    // ═══════════════════════════════════════════════════════════════════
    //  GeoAnimatable implementation
    // ═══════════════════════════════════════════════════════════════════

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "transformation", 5, state -> {
            if (currentData == null) return PlayState.STOP;

            // Determine which animation to play based on player state
            // The entity is not directly accessible here, so we use
            // the animation file references from TransformationData

            ResourceLocation idle = currentData.getIdleAnimation();
            ResourceLocation move = currentData.getMoveAnimation();

            if (idle != null) {
                state.getController().setAnimation(
                        RawAnimation.begin().thenLoop("animation.idle"));
            }

            return PlayState.CONTINUE;
        }));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    @Override
    public double getTick(Object object) {
        return 0; // Let GeckoLib handle tick calculation
    }
}
