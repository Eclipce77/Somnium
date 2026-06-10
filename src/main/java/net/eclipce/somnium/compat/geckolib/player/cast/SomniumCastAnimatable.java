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
    /** Queued behaviour options keyed by player UUID. */
    private static final Map<UUID, CastAnimationOptions> QUEUED_OPTIONS = new ConcurrentHashMap<>();

    private static final AtomicLong NEXT_INSTANCE_ID = new AtomicLong(0);

    // ─── Instance fields ──────────────────────────────────────────────────────

    private final UUID playerUuid;
    private final long instanceId;
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    @Nullable
    private String activeAnimation = null;
    @Nullable
    private String activeModelId = null;

    /** Options for the currently-active animation. Resets to {@link CastAnimationOptions#DEFAULT} when no animation is active. */
    private CastAnimationOptions currentOptions = CastAnimationOptions.DEFAULT;

    /**
     * Base body parts currently hidden by us via
     * {@link CastAnimationOptions#hideBodyPart()}. Tracked so the applicator can restore
     * them when the option set changes, the animation ends, or another animation is queued.
     */
    private final java.util.Set<CastBodyPart> hiddenBodyParts =
            java.util.concurrent.ConcurrentHashMap.newKeySet();

    /**
     * Skin overlay layers currently hidden by us via {@link CastAnimationOptions#hideLayer()}.
     * Stored separately from {@link #hiddenBodyParts} because the API distinguishes the
     * two and the restoration loop iterates them separately too.
     */
    private final java.util.Set<CastLayer> hiddenLayers =
            java.util.concurrent.ConcurrentHashMap.newKeySet();

    // Cached RawAnimation to avoid creating a new instance every render frame.
    // GeckoLib compares by reference when deciding whether to restart — a new
    // object each frame means the animation restarts from frame 0 every tick.
    @Nullable private String cachedAnimName = null;
    @Nullable private RawAnimation cachedRawAnimation = null;

    /**
     * Set true by {@link #onAnimationFinished()} so the applicator runs ONE more full
     * pass to restore suppressed/hidden/first-person state, then clears it via
     * {@link #clearCleanupPass()}.
     *
     * <p>Without this, the {@code PlayerModelSetupMixin} guard stops calling
     * {@code apply()} the instant {@code activeAnimation} goes null — leaving suppressed
     * limbs frozen on their last pose and hidden layers / first-person arms stuck until
     * some unrelated later animation re-enters the loop. That late, lazy teardown was the
     * shared root cause behind the "only fixes itself when another ability runs" cycle on
     * suppressVanillaAnimOn, hideLayer, and showInFirstPerson.</p>
     */
    private volatile boolean needsCleanupPass = false;

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
     * Returns the animatable for this UUID without creating it.
     * Used by the per-frame applicator to avoid allocating an instance for every
     * rendered player just to discover they have no animation.
     */
    @Nullable
    public static SomniumCastAnimatable getOrNull(UUID uuid) {
        return INSTANCES.get(uuid);
    }

    /**
     * Queues an animation to play for {@code uuid} on the next render frame, with the
     * default behaviour options ({@link CastAnimationOptions#DEFAULT}).
     * Called by {@link net.eclipce.somnium.network.PlayPlayerAnimationPacket} on the client main thread.
     */
    public static void setAnimation(UUID uuid, String animationName, String modelId) {
        setAnimation(uuid, animationName, modelId, CastAnimationOptions.DEFAULT);
    }

    /**
     * Queues an animation to play for {@code uuid} on the next render frame, with the
     * given behaviour options.
     */
    public static void setAnimation(UUID uuid, String animationName, String modelId, CastAnimationOptions options) {
        System.out.println("[Somnium-DIAG] SomniumCastAnimatable.setAnimation: uuid=" + uuid
                + " anim=" + animationName + " model=" + modelId);
        getOrCreate(uuid); // ensure instance exists
        QUEUED_ANIM.put(uuid, animationName);
        QUEUED_MODEL.put(uuid, modelId);
        QUEUED_OPTIONS.put(uuid, options != null ? options : CastAnimationOptions.DEFAULT);
        System.out.println("[Somnium-DIAG] SomniumCastAnimatable.setAnimation: QUEUED_ANIM now contains "
                + QUEUED_ANIM.size() + " entries; isActive(uuid)=" + isActive(uuid));
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
        QUEUED_OPTIONS.remove(uuid);
    }

    /** Clears all cached state (e.g. on client level unload). */
    public static void clearAll() {
        INSTANCES.clear();
        QUEUED_ANIM.clear();
        QUEUED_MODEL.clear();
        QUEUED_OPTIONS.clear();
    }

    // ─── Frame lifecycle — called by SomniumCastBoneApplicator ───────────────

    /**
     * Consumes any queued animation, making it the active one for this frame.
     * Called once per render frame by the applicator before processing.
     */
    public void consumeQueue() {
        String queued = QUEUED_ANIM.remove(playerUuid);
        if (queued != null) {
            System.out.println("[Somnium-DIAG] consumeQueue: transitioning queued '" + queued
                    + "' to active for uuid=" + playerUuid);
            activeAnimation = queued;
            activeModelId = QUEUED_MODEL.remove(playerUuid);
            CastAnimationOptions opt = QUEUED_OPTIONS.remove(playerUuid);
            currentOptions = opt != null ? opt : CastAnimationOptions.DEFAULT;
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
        currentOptions = CastAnimationOptions.DEFAULT;
        // Force exactly one more apply() pass so the applicator can restore everything
        // this animation changed (suppress/hide/FP) on the very next frame, instead of
        // deferring teardown to whenever the next animation happens to arrive.
        needsCleanupPass = true;
        // hiddenBodyParts / hiddenLayers are intentionally NOT cleared here — the
        // applicator restores those parts on its next call by reading them and
        // setting visible=true. Clearing here would leave the parts stuck invisible
        // because setModelProperties would set them visible=true at the start of the
        // next render, but no setter would ever set them back if the player skin part
        // toggle isn't enabled for them.
    }

    // ─── Accessors ────────────────────────────────────────────────────────────

    @Nullable public String getActiveAnimation()         { return activeAnimation; }
    @Nullable public String getActiveModelId()           { return activeModelId; }
    public long             getInstanceId()              { return instanceId; }
    public CastAnimationOptions getCurrentOptions()      { return currentOptions; }
    public java.util.Set<CastBodyPart> getHiddenBodyParts() { return hiddenBodyParts; }
    public java.util.Set<CastLayer>    getHiddenLayers()    { return hiddenLayers; }

    /** True when a finished animation still owes the applicator one restore pass. */
    public boolean needsCleanupPass() { return needsCleanupPass; }

    /** Cleared by the applicator once it has run the post-animation restore pass. */
    public void clearCleanupPass()    { needsCleanupPass = false; }

    // ─── GeoAnimatable ────────────────────────────────────────────────────────

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar registrar) {
        registrar.add(new AnimationController<>(this, "somnium_cast", 0, state -> {
            if (activeAnimation == null) return PlayState.STOP;

            // Build/reuse the RawAnimation. Reference identity matters — a fresh
            // RawAnimation every frame would force the controller to restart from frame 0.
            if (!activeAnimation.equals(cachedAnimName) || cachedRawAnimation == null) {
                cachedRawAnimation = RawAnimation.begin().thenPlay(activeAnimation);
                cachedAnimName = activeAnimation;
                System.out.println("[Somnium-DIAG] predicate: built new cachedRawAnimation for '" + activeAnimation + "'");
            }

            // Idiomatic GeckoLib: setAndContinue handles setAnimation + return CONTINUE.
            // Do NOT call hasAnimationFinished() in the same tick as setAnimation —
            // on a fresh controller, currentAnimation is still null until process() runs,
            // which can cause spurious "finished" results.
            return state.setAndContinue(cachedRawAnimation);
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