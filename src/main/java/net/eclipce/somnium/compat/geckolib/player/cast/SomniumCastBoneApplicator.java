package net.eclipce.somnium.compat.geckolib.player.cast;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.player.Player;
import org.slf4j.Logger;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.core.animation.AnimationState;

import java.util.Optional;

/**
 * Core of Somnium's per-bone player animation system.
 *
 * <p>Called once per render frame from {@link net.eclipce.somnium.compat.geckolib.mixin.PlayerModelSetupMixin} immediately after
 * {@code PlayerModel.setupAnim()} completes. If the player has an active cast animation,
 * this class:</p>
 * <ol>
 *   <li>Consumes any newly queued animation from {@link SomniumCastAnimatable}.</li>
 *   <li>Resolves the registered {@link SomniumCastModel} for that animation.</li>
 *   <li>Runs the GeckoLib animation processor for the current frame (without rendering).</li>
 *   <li>Walks each bone in the baked model and adds its rotation/position deltas onto
 *       the corresponding vanilla {@link ModelPart} in the {@link PlayerModel}.</li>
 * </ol>
 *
 * <p>Because transforms are applied <em>additively</em> (using {@code +=}), vanilla
 * locomotion animations (walking, swimming, sneaking, etc.) continue running on all
 * bones. Only keyframed bones in the cast animation are affected — a punch animation
 * that keys only {@code right_arm} will not disturb the legs, head, or any other part.</p>
 *
 * <p>Both skin layers are preserved because this modifies the already-set-up vanilla
 * model directly — armor layers and the overlay skin (jacket, sleeves, pants, hat) are
 * all rendered through the normal pipeline after this method returns.</p>
 */
public final class SomniumCastBoneApplicator {

    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * Entry point called by {@link net.eclipce.somnium.compat.geckolib.mixin.PlayerModelSetupMixin}.
     * No-ops gracefully if the player has no active animation or model.
     *
     * @param player      the player being rendered (may be the local player or a remote player)
     * @param playerModel the vanilla model whose bones will be adjusted
     * @param partialTick render partial tick from the event
     */
    public static void apply(Player player, PlayerModel<?> playerModel, float partialTick) {
        // Clear scale data from the previous rendered player before writing new data.
        // This must happen unconditionally — even if there is no active animation —
        // so that scale data never carries over across players in the same frame.
        SomniumBoneScaleMap.clearAll();

        SomniumCastAnimatable animatable = SomniumCastAnimatable.getOrCreate(player.getUUID());

        // Consume any packet-queued animation before checking isActive
        animatable.consumeQueue();

        if (animatable.getActiveAnimation() == null) return;

        // Resolve the model registered for this animation's modelId
        SomniumCastModel model = CastAnimationModelRegistry.get(animatable.getActiveModelId());
        if (model == null) {
            LOGGER.warn("[Somnium] CastAnimation: no model registered for id '{}' — " +
                            "verify CastAnimationModelRegistry.register() was called during FMLClientSetupEvent " +
                            "and that the id matches exactly (including namespace).",
                    animatable.getActiveModelId());
            animatable.onAnimationFinished();
            return;
        }

        // ── Run the GeckoLib animation processor without rendering ──
        // This updates all GeoBone transforms in the BakedGeoModel for the current frame.
        AnimationState<SomniumCastAnimatable> state =
                new AnimationState<>(animatable, 0f, 0f, partialTick, false);

        try {
            model.setCustomAnimations(animatable, animatable.getInstanceId(), state);
        } catch (Exception e) {
            LOGGER.error("[Somnium] CastAnimation: setCustomAnimations threw for animation '{}' with model id '{}'. " +
                            "This usually means the animation JSON could not be loaded — check the resource path is correct " +
                            "and the file exists in assets/<modid>/animations/.",
                    animatable.getActiveAnimation(), animatable.getActiveModelId());
            LOGGER.error("[Somnium] CastAnimation: exception detail", e);
            return;
        }

        // ── Apply bone transforms to the vanilla PlayerModel ──
        BakedGeoModel bakedModel;
        try {
            bakedModel = model.getBakedModel(model.getModelResource(animatable));
        } catch (Exception e) {
            LOGGER.error("[Somnium] CastAnimation: getBakedModel threw for resource '{}'. " +
                            "This usually means the geo.json could not be loaded — check the resource path is correct " +
                            "and the file exists in assets/<modid>/geo/.",
                    model.getModelResource(animatable));
            LOGGER.error("[Somnium] CastAnimation: exception detail", e);
            return;
        }

        applyAllBones(bakedModel, playerModel);
    }

    // ─── Bone application ────────────────────────────────────────────────────

    private static void applyAllBones(BakedGeoModel bakedModel, PlayerModel<?> playerModel) {
        // Rather than walking the bone tree manually, query each known bone by name.
        // Bones that don't exist in the geo.json return Optional.empty() gracefully.
        applyBoneIfPresent(bakedModel, playerModel, "head");
        applyBoneIfPresent(bakedModel, playerModel, "hat");
        applyBoneIfPresent(bakedModel, playerModel, "body");
        applyBoneIfPresent(bakedModel, playerModel, "jacket");
        applyBoneIfPresent(bakedModel, playerModel, "right_arm");
        applyBoneIfPresent(bakedModel, playerModel, "right_sleeve");
        applyBoneIfPresent(bakedModel, playerModel, "left_arm");
        applyBoneIfPresent(bakedModel, playerModel, "left_sleeve");
        applyBoneIfPresent(bakedModel, playerModel, "right_leg");
        applyBoneIfPresent(bakedModel, playerModel, "right_pants");
        applyBoneIfPresent(bakedModel, playerModel, "left_leg");
        applyBoneIfPresent(bakedModel, playerModel, "left_pants");
    }

    private static void applyBoneIfPresent(BakedGeoModel baked,
                                           PlayerModel<?> playerModel,
                                           String boneName) {
        Optional<GeoBone> opt = baked.getBone(boneName);
        if (opt.isEmpty()) return;

        GeoBone bone = opt.get();
        if (bone.isHidden()) return;

        ModelPart part = SomniumPlayerBoneMap.getPart(playerModel, boneName);
        if (part == null) return;

        // ── Additive rotation (radians) ──
        // GeckoLib returns the bone's absolute rotation for this animation frame.
        // Because the default geo.json has all bones at (0, 0, 0) rest rotation,
        // these values equal the delta from vanilla pose — exactly what additive
        // blending requires.
        float rx = bone.getRotX();
        float ry = bone.getRotY();
        float rz = bone.getRotZ();

        if (rx != 0f) part.xRot += rx;
        if (ry != 0f) part.yRot += ry;
        if (rz != 0f) part.zRot += rz;

        // ── Additive position (model units, Y-axis flipped) ──
        // GeckoLib uses Y-up; vanilla PlayerModel uses Y-down.
        float px = bone.getPosX();
        float py = bone.getPosY();
        float pz = bone.getPosZ();

        if (px != 0f) part.x += px;
        if (py != 0f) part.y -= py;   // flip Y to match vanilla coordinate system
        if (pz != 0f) part.z += pz;

        // ── Multiplicative scale (applied via PoseStack in ModelPartRenderMixin) ──
        // Scale cannot be stored as a field on ModelPart — it must be applied via
        // PoseStack.scale() at render time. Values are written to SomniumBoneScaleMap
        // and read by ModelPartRenderMixin after translateAndRotate() positions the
        // PoseStack at this bone's pivot. Identity scale (1, 1, 1) is a no-op.
        float sx = bone.getScaleX();
        float sy = bone.getScaleY();
        float sz = bone.getScaleZ();
        if (sx != 1f || sy != 1f || sz != 1f) {
            SomniumBoneScaleMap.setScale(part, sx, sy, sz);
        }
    }

    private SomniumCastBoneApplicator() {}
}