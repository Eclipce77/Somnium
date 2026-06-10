package net.eclipce.somnium.compat.geckolib.player.cast;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.CameraType;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.eclipce.somnium.compat.geckolib.mixin.PlayerModelSetupMixin;

/**
 * Bridges the cast animation system into the first-person view via the
 * {@link CastAnimationOptions#showInFirstPerson()} option.
 *
 * <h3>The problem with the old approach</h3>
 * <p>Vanilla's first-person hand rendering ({@code ItemInHandRenderer.renderHandsWithItems})
 * positions the arm in <em>screen space</em> — pinned to the bottom-right (or bottom-left)
 * of the view, with a small fixed rotation appropriate for displaying a held item.
 * Animations applied to that arm's rotation/position/scale fields compose with the
 * screen-space pose, producing the "arm diagonally across the screen" artifact:
 * a 90° forward extension on a screen-pinned arm sweeps the hand from the bottom of
 * the view to the top-left, which is not what anyone wants from a Gomu-Pistol-style
 * stretch.</p>
 *
 * <h3>What this class does instead</h3>
 * <p>While a cast animation with {@code showInFirstPerson=true} is active:</p>
 * <ol>
 *   <li>{@link #onRenderLevelStage} fires after the world's entities have been rendered
 *       and re-renders the local player at their actual world position, using the
 *       standard {@link PlayerRenderer}. Because the FP camera is positioned at the
 *       player's eye, this places the player's body just below/around the camera —
 *       so any animated part that extends into the player's line of sight (arm
 *       stretching forward, leg kicking up, etc.) is visible to them, looking
 *       exactly as a third-person observer would see it.</li>
 *   <li>{@link #onRenderHand} cancels vanilla's screen-space hand+item rendering for
 *       both hands so we don't double-draw the arm. The held item still appears in
 *       the player's hand because the standard player render path includes
 *       {@code PlayerItemInHandLayer}, which draws the item attached to the hand
 *       at its world position.</li>
 *   <li>{@link #onRenderHand} also cancels rendering when
 *       {@link CastAnimationOptions#heldItemsShown()} is {@code false}, regardless
 *       of {@code showInFirstPerson} — that flag's purpose is to hide the held item
 *       for animations where it would clip into animated geometry.</li>
 * </ol>
 *
 * <p>The head and hat are hidden during the FP re-render so the player doesn't see
 * the inside of their own skull. All other parts (body, arms, legs, jacket, sleeves,
 * pants) render normally — and since our {@link PlayerModelSetupMixin} fires inside
 * the re-render's own {@code setupAnim} call, the cast animation transforms are
 * applied during this render just as they are during the third-person world render.</p>
 *
 * <h3>Registration</h3>
 * <p>Registered to the Forge event bus from
 * {@link net.eclipce.somnium.compat.geckolib.GeckoLibClientSetup#init()}.</p>
 *
 * <h3>Held items</h3>
 * <p>See {@code ItemInHandLayerMixin} for the {@code heldItemsShown=false} path that
 * runs inside our re-render (the mixin intercepts {@code ItemInHandLayer.render} and
 * skips it when the player's cast options demand it). This same mixin also applies
 * in the third-person world render, so {@code heldItemsShown=false} hides the item
 * in both perspectives.</p>
 */
public final class SomniumFirstPersonRenderer {

    /**
     * Set to {@code true} for the duration of the FP re-render call below. Read by
     * {@link net.eclipce.somnium.compat.geckolib.mixin.PlayerModelSetupMixin} so it can
     * force {@code head.visible} and {@code hat.visible} to {@code false} <em>after</em>
     * {@code PlayerRenderer.setModelProperties} has wiped them back to {@code true}.
     *
     * <p>This is the cleanest place to flip those flags: setModelProperties runs at the
     * very start of {@code PlayerRenderer.render} and calls {@code setAllVisible(true)},
     * so any visibility set <em>before</em> render is lost. The next mutable hook in the
     * pipeline is the model's {@code setupAnim} method — which our mixin already targets
     * for the cast-animation bone application. By reading this thread-local from inside
     * that mixin we hide head/hat after vanilla has set them and before the geometry
     * is sent to the buffer.</p>
     */
    public static final ThreadLocal<Boolean> RENDERING_OWN_PLAYER_IN_FIRST_PERSON =
            ThreadLocal.withInitial(() -> Boolean.FALSE);

    /**
     * Client render tick (game time) through which vanilla first-person hands should remain
     * suppressed even if no cast animation is currently active. Armed each frame a
     * {@code showInFirstPerson} animation is live, and read by {@link #onRenderHand}.
     *
     * <p>Why this exists: completion is detected client-side by elapsed time, but the
     * follow-up animation (e.g. Pistol's retract) is triggered server-side and arrives a
     * round-trip later. In the gap, {@code getActiveAnimation()} is briefly null, so without
     * this grace {@code onRenderHand} would stop cancelling and vanilla's screen-space hand
     * would flash across the screen for a frame at the extend→retract seam.</p>
     *
     * <p>Erring slightly long here is safe: suppressing vanilla hands for a few extra frames
     * when nothing is animating shows nothing wrong (there's no FP arm to miss), whereas
     * suppressing a frame too few shows the artifact. {@code 0} means "not armed".</p>
     */
    private static long suppressVanillaHandsUntilTick = 0L;

    /** Frames of grace after an FP cast ends, to bridge the client/server retrigger seam. */
    private static final long FP_HAND_GRACE_TICKS = 3L;

    /**
     * Renders the local player at their world position after the level's entities
     * are drawn. Only runs in first-person camera mode with an active cast animation
     * that has {@code showInFirstPerson=true}.
     *
     * <p>{@code AFTER_ENTITIES} is the right stage because the local player is normally
     * skipped from the entity loop in FP — by the time this fires, every other entity
     * has been drawn and we can inject the player without conflicting with vanilla.</p>
     */
    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.options.getCameraType() != CameraType.FIRST_PERSON) return;

        LocalPlayer player = mc.player;
        if (player == null) return;

        SomniumCastAnimatable animatable = SomniumCastAnimatable.getOrNull(player.getUUID());
        if (animatable == null) return;
        if (animatable.getActiveAnimation() == null) return;

        CastAnimationOptions options = animatable.getCurrentOptions();
        if (!options.showInFirstPerson()) return;

        // Arm the vanilla-hand suppression grace: as long as this FP cast is live, keep
        // vanilla hands suppressed for a few frames PAST the point the animation ends, so
        // the client/server retrigger seam (extend→retract) doesn't flash a vanilla hand.
        if (Minecraft.getInstance().level != null) {
            suppressVanillaHandsUntilTick =
                    Minecraft.getInstance().level.getGameTime() + FP_HAND_GRACE_TICKS;
        }

        // Resolve the player renderer (always a PlayerRenderer for AbstractClientPlayer)
        EntityRenderer<? super AbstractClientPlayer> renderer =
                mc.getEntityRenderDispatcher().getRenderer(player);
        if (!(renderer instanceof PlayerRenderer playerRenderer)) return;

        Camera camera = mc.gameRenderer.getMainCamera();
        Vec3 cameraPos = camera.getPosition();
        float partialTick = event.getPartialTick();

        // Interpolate the player position the same way LevelRenderer does for other
        // entities, so the body doesn't lag behind the camera at high framerates.
        double px = Mth.lerp(partialTick, player.xo, player.getX());
        double py = Mth.lerp(partialTick, player.yo, player.getY());
        double pz = Mth.lerp(partialTick, player.zo, player.getZ());

        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();
        poseStack.translate(px - cameraPos.x, py - cameraPos.y, pz - cameraPos.z);

        // Mark this thread as rendering the local player in FP. The setupAnim mixin
        // reads this and force-hides head/hat after vanilla has set them visible — which
        // is the only stable window in the render to do it. We CANNOT just set
        // model.head.visible=false here: PlayerRenderer.render's very first line is
        // setModelProperties() which calls setAllVisible(true), wiping any pre-set value.
        RENDERING_OWN_PLAYER_IN_FIRST_PERSON.set(Boolean.TRUE);

        try {
            // BufferSource specifically (not the abstract MultiBufferSource) so endBatch()
            // resolves below. mc.renderBuffers().bufferSource() returns BufferSource.
            net.minecraft.client.renderer.MultiBufferSource.BufferSource buffers =
                    mc.renderBuffers().bufferSource();
            int packedLight = mc.getEntityRenderDispatcher().getPackedLightCoords(player, partialTick);
            float yaw = Mth.lerp(partialTick, player.yRotO, player.getYRot());
            playerRenderer.render(player, yaw, partialTick, poseStack, buffers, packedLight);
            // Flush the buffer so the player draws immediately rather than blending into
            // the next batch (which would render after particles/translucents and look
            // off-order against the world).
            buffers.endBatch();
        } finally {
            RENDERING_OWN_PLAYER_IN_FIRST_PERSON.set(Boolean.FALSE);
            poseStack.popPose();
        }
    }

    /**
     * Suppresses vanilla's screen-space FP hand+item rendering when either
     * {@code showInFirstPerson} or {@code heldItemsShown=false} is in effect.
     *
     * <ul>
     *   <li>{@code showInFirstPerson=true}: cancel so we don't double-draw the arm
     *       (the full-body render above already includes it).</li>
     *   <li>{@code heldItemsShown=false} (FP without showInFirstPerson): cancel so
     *       the held item doesn't display. Unfortunately this also hides the FP arm,
     *       since vanilla's {@code renderHand} pairs them — an acceptable tradeoff
     *       for what is a rarely-used flag combination.</li>
     * </ul>
     *
     * <p>Uses {@link EventPriority#HIGH} so we cancel before other mods (e.g. cosmetic
     * mods) get a chance to render into the now-doomed pose.</p>
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onRenderHand(RenderHandEvent event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;

        SomniumCastAnimatable animatable = SomniumCastAnimatable.getOrNull(player.getUUID());

        // Grace bridge: even with no active animation, keep suppressing vanilla hands for a
        // few frames after an FP cast ends, so the brief gap before the next animation
        // (e.g. Pistol's server-triggered retract) arrives doesn't flash the vanilla
        // screen-space hand across the view.
        if (Minecraft.getInstance().level != null
                && Minecraft.getInstance().level.getGameTime() < suppressVanillaHandsUntilTick) {
            event.setCanceled(true);
            return;
        }

        if (animatable == null) return;
        if (animatable.getActiveAnimation() == null) return;

        CastAnimationOptions options = animatable.getCurrentOptions();

        if (options.showInFirstPerson()) {
            event.setCanceled(true);
            return;
        }
        if (!options.heldItemsShown()) {
            event.setCanceled(true);
        }
    }

    private SomniumFirstPersonRenderer() {}
}