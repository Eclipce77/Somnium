package net.eclipce.somnium.compat.geckolib.player.cast;

import net.minecraft.client.Minecraft;
import software.bernie.geckolib.core.animatable.GeoAnimatable;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.*;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Client-side GeckoLib animatable that manages cast animation state per player UUID.
 *
 * <p>One instance exists per player UUID. When a {@link net.eclipce.somnium.network.PlayPlayerAnimationPacket}
 * arrives, {@link #setAnimation} queues the animation. On the next render frame, the
 * {@link SomniumCastBoneApplicator} reads back the queued data, runs the animation controller,
 * and applies the resulting bone transforms onto the vanilla {@code PlayerModel}.</p>
 *
 * <h3>Animation controller: {@code "somnium_cast"}</h3>
 * <p>The single controller named {@code "somnium_cast"} plays the queued animation once and then
 * stops, allowing vanilla animations to continue unaffected on all other frames.</p>
 */
public class SomniumCastAnimatable implements GeoAnimatable {

    // ─── Static state ─────────────────────────────────────────────────────────

    private static final Map<UUID, SomniumCastAnimatable> INSTANCES = new ConcurrentHashMap<>();

    /** Queued animation name keyed by player UUID, written by packet handler. */
    private static final Map<UUID, String> QUEUED_ANIM = new ConcurrentHashMap<>();
    /** Queued model registry key keyed by player UUID. */
    private static final Map<UUID, String> QUEUED_MODEL = new ConcurrentHashMap<>();

    private static final AtomicLong NEXT_INSTANCE_ID = new AtomicLong(0);

    // ─── Instance fields ──────────────────────────────────────────────────────

    private final UUID playerUuid;
    private final long instanceId;
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    @Nullable
    private String activeAnimation = null;
    @Nullable
    private String activeModelId = null;

    private SomniumCastAnimatable(UUID playerUuid) {
        this.playerUuid = playerUuid;
        this.instanceId = NEXT_INSTANCE_ID.getAndIncrement();
    }

    // ─── Static API ───────────────────────────────────────────────────────────

    /** Returns the animatable for this UUID, creating it if not yet cached. */
    public static SomniumCastAnimatable getOrCreate(UUID uuid) {
        return INSTANCES.computeIfAbsent(uuid, SomniumCastAnimatable::new);
    }

    /**
     * Queues an animation to play for {@code uuid} on the next render frame.
     * Called by {@link net.eclipce.somnium.network.PlayPlayerAnimationPacket} on the client main thread.
     *
     * @param uuid          the player to animate
     * @param animationName the animation name from the animation.json
     * @param modelId       the registry key of the {@link SomniumCastModel} to use
     */
    public static void setAnimation(UUID uuid, String animationName, String modelId) {
        getOrCreate(uuid); // ensure instance exists
        QUEUED_ANIM.put(uuid, animationName);
        QUEUED_MODEL.put(uuid, modelId);
    }

    /**
     * Returns true if this player currently has a pending or active cast animation.
     * Checked each frame by the Mixin before processing.
     */
    public static boolean isActive(UUID uuid) {
        if (QUEUED_ANIM.containsKey(uuid)) return true;
        SomniumCastAnimatable inst = INSTANCES.get(uuid);
        return inst != null && inst.activeAnimation != null;
    }

    /** Cleans up state when a player disconnects or the world unloads. */
    public static void clearPlayer(UUID uuid) {
        INSTANCES.remove(uuid);
        QUEUED_ANIM.remove(uuid);
        QUEUED_MODEL.remove(uuid);
    }

    /** Clears all cached state (e.g. on client level unload). */
    public static void clearAll() {
        INSTANCES.clear();
        QUEUED_ANIM.clear();
        QUEUED_MODEL.clear();
    }

    // ─── Frame lifecycle — called by SomniumCastBoneApplicator ───────────────

    /**
     * Consumes any queued animation, making it the active one for this frame.
     * Called once per render frame by the applicator before processing.
     */
    public void consumeQueue() {
        String queued = QUEUED_ANIM.remove(playerUuid);
        if (queued != null) {
            activeAnimation = queued;
            activeModelId = QUEUED_MODEL.remove(playerUuid);
            // Force the controller to restart on this new animation
            cache.getManagerForId(instanceId)
                    .getAnimationControllers()
                    .values()
                    .forEach(AnimationController::forceAnimationReset);
        }
    }

    /** Called by the applicator when the animation controller reports it has finished. */
    public void onAnimationFinished() {
        activeAnimation = null;
        activeModelId = null;
    }

    // ─── Accessors ────────────────────────────────────────────────────────────

    @Nullable public String getActiveAnimation() { return activeAnimation; }
    @Nullable public String getActiveModelId()   { return activeModelId; }
    public long getInstanceId()                  { return instanceId; }

    // ─── GeoAnimatable ────────────────────────────────────────────────────────

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar registrar) {
        registrar.add(new AnimationController<>(this, "somnium_cast", 0, state -> {
            if (activeAnimation == null) return PlayState.STOP;

            // The controller plays the animation once; GeckoLib will set
            // hasAnimationFinished() when it is done.
            state.getController().setAnimation(
                    RawAnimation.begin().thenPlay(activeAnimation));

            if (state.getController().hasAnimationFinished()) {
                onAnimationFinished();
                return PlayState.STOP;
            }
            return PlayState.CONTINUE;
        }));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }

    @Override
    public double getTick(Object object) {
        Minecraft mc = Minecraft.getInstance();
        return mc.level != null ? mc.level.getGameTime() : 0;
    }
}