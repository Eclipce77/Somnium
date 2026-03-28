package net.eclipce.somnium.core.ability.transformation;

import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;

/**
 * Visual specification for a transformation. Describes what the player looks
 * like and sounds like while transformed, without coupling to any specific
 * rendering system (vanilla or GeckoLib).
 *
 * <p>All visual references use {@link ResourceLocation} identifiers rather than
 * renderer-specific types. The client-side rendering system (built separately)
 * resolves these identifiers at render time. This means:</p>
 * <ul>
 *     <li>TransformationData works without GeckoLib installed</li>
 *     <li>GeckoLib integration is added by a rendering layer that reads these
 *         resource locations and maps them to GeckoLib models/animations</li>
 *     <li>Switching rendering backends (e.g., GeckoLib 4 → 5) requires no
 *         changes to any TransformationAbilityType code</li>
 * </ul>
 *
 * <h3>Usage</h3>
 * <pre>{@code
 * TransformationData.builder()
 *     .transformedModel(new ResourceLocation("mymod", "geo/werewolf.geo.json"))
 *     .transformedTexture(new ResourceLocation("mymod", "textures/entity/werewolf.png"))
 *     .transformInAnimation(new ResourceLocation("mymod", "animations/werewolf_transform_in.json"))
 *     .transformOutAnimation(new ResourceLocation("mymod", "animations/werewolf_transform_out.json"))
 *     .idleAnimation(new ResourceLocation("mymod", "animations/werewolf_idle.json"))
 *     .moveAnimation(new ResourceLocation("mymod", "animations/werewolf_walk.json"))
 *     .transformSound(new ResourceLocation("mymod", "werewolf_transform"))
 *     .deTransformSound(new ResourceLocation("mymod", "werewolf_revert"))
 *     .modelScale(1.3f)
 *     .build()
 * }</pre>
 *
 * @see TransformationAbilityType
 */
public class TransformationData {

    // ═══════════════════════════════════════════════════════════════════
    //  Model references
    // ═══════════════════════════════════════════════════════════════════

    /** The model to render while transformed (third person). */
    private final ResourceLocation transformedModel;

    /** The texture to apply to the transformed model. */
    private final ResourceLocation transformedTexture;

    /** Optional first-person hand/arm model override. */
    private final ResourceLocation firstPersonModel;

    // ═══════════════════════════════════════════════════════════════════
    //  Animation references
    // ═══════════════════════════════════════════════════════════════════

    /** Animation to play during the TRANSFORMING_IN phase. */
    private final ResourceLocation transformInAnimation;

    /** Animation to play during the TRANSFORMING_OUT phase. */
    private final ResourceLocation transformOutAnimation;

    /** Idle animation while in the TRANSFORMED state. */
    private final ResourceLocation idleAnimation;

    /** Movement animation while in the TRANSFORMED state. */
    private final ResourceLocation moveAnimation;

    // ═══════════════════════════════════════════════════════════════════
    //  Sound references
    // ═══════════════════════════════════════════════════════════════════

    /** Sound to play when transformation starts (TRANSFORMING_IN begins). */
    private final ResourceLocation transformSound;

    /** Sound to play when de-transformation starts (TRANSFORMING_OUT begins). */
    private final ResourceLocation deTransformSound;

    /** Optional ambient/looping sound while fully transformed. */
    private final ResourceLocation ambientSound;

    // ═══════════════════════════════════════════════════════════════════
    //  Scale
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Visual and hitbox scale multiplier while transformed.
     * 1.0 = normal size, 1.5 = 50% larger, 0.5 = half size.
     * <p>Note: Hitbox scaling requires additional implementation in
     * the player data/tick system. For now, this value is stored as
     * data for future use by the rendering and hitbox systems.</p>
     */
    private final float modelScale;

    // ═══════════════════════════════════════════════════════════════════
    //  Construction (use Builder)
    // ═══════════════════════════════════════════════════════════════════

    private TransformationData(Builder builder) {
        this.transformedModel = builder.transformedModel;
        this.transformedTexture = builder.transformedTexture;
        this.firstPersonModel = builder.firstPersonModel;
        this.transformInAnimation = builder.transformInAnimation;
        this.transformOutAnimation = builder.transformOutAnimation;
        this.idleAnimation = builder.idleAnimation;
        this.moveAnimation = builder.moveAnimation;
        this.transformSound = builder.transformSound;
        this.deTransformSound = builder.deTransformSound;
        this.ambientSound = builder.ambientSound;
        this.modelScale = builder.modelScale;
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Getters
    // ═══════════════════════════════════════════════════════════════════

    /** @return the third-person model resource, or {@code null} if not set */
    @Nullable
    public ResourceLocation getTransformedModel() {
        return transformedModel;
    }

    /** @return the texture for the transformed model, or {@code null} if not set */
    @Nullable
    public ResourceLocation getTransformedTexture() {
        return transformedTexture;
    }

    /** @return the first-person model override, or {@code null} for no override */
    @Nullable
    public ResourceLocation getFirstPersonModel() {
        return firstPersonModel;
    }

    /** @return the transform-in animation resource, or {@code null} if not set */
    @Nullable
    public ResourceLocation getTransformInAnimation() {
        return transformInAnimation;
    }

    /** @return the transform-out animation resource, or {@code null} if not set */
    @Nullable
    public ResourceLocation getTransformOutAnimation() {
        return transformOutAnimation;
    }

    /** @return the idle animation while transformed, or {@code null} if not set */
    @Nullable
    public ResourceLocation getIdleAnimation() {
        return idleAnimation;
    }

    /** @return the movement animation while transformed, or {@code null} if not set */
    @Nullable
    public ResourceLocation getMoveAnimation() {
        return moveAnimation;
    }

    /** @return the transformation sound resource, or {@code null} for no sound */
    @Nullable
    public ResourceLocation getTransformSound() {
        return transformSound;
    }

    /** @return the de-transformation sound resource, or {@code null} for no sound */
    @Nullable
    public ResourceLocation getDeTransformSound() {
        return deTransformSound;
    }

    /** @return the ambient sound while transformed, or {@code null} for no sound */
    @Nullable
    public ResourceLocation getAmbientSound() {
        return ambientSound;
    }

    /**
     * @return the scale multiplier for the transformed model and hitbox.
     *         Default is 1.0 (no scaling).
     */
    public float getModelScale() {
        return modelScale;
    }

    /**
     * @return {@code true} if a transformed model has been specified
     */
    public boolean hasModel() {
        return transformedModel != null;
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Builder
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Creates a new builder for constructing TransformationData.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for {@link TransformationData}. All fields are optional —
     * set only what your transformation needs.
     */
    public static class Builder {

        private ResourceLocation transformedModel = null;
        private ResourceLocation transformedTexture = null;
        private ResourceLocation firstPersonModel = null;
        private ResourceLocation transformInAnimation = null;
        private ResourceLocation transformOutAnimation = null;
        private ResourceLocation idleAnimation = null;
        private ResourceLocation moveAnimation = null;
        private ResourceLocation transformSound = null;
        private ResourceLocation deTransformSound = null;
        private ResourceLocation ambientSound = null;
        private float modelScale = 1.0f;

        private Builder() {}

        /** Sets the third-person model resource for the transformed state. */
        public Builder transformedModel(ResourceLocation model) {
            this.transformedModel = model;
            return this;
        }

        /** Sets the texture applied to the transformed model. */
        public Builder transformedTexture(ResourceLocation texture) {
            this.transformedTexture = texture;
            return this;
        }

        /** Sets the first-person hand/arm model override. */
        public Builder firstPersonModel(ResourceLocation model) {
            this.firstPersonModel = model;
            return this;
        }

        /** Sets the animation played during the TRANSFORMING_IN phase. */
        public Builder transformInAnimation(ResourceLocation animation) {
            this.transformInAnimation = animation;
            return this;
        }

        /** Sets the animation played during the TRANSFORMING_OUT phase. */
        public Builder transformOutAnimation(ResourceLocation animation) {
            this.transformOutAnimation = animation;
            return this;
        }

        /** Sets the idle animation while fully transformed. */
        public Builder idleAnimation(ResourceLocation animation) {
            this.idleAnimation = animation;
            return this;
        }

        /** Sets the movement animation while fully transformed. */
        public Builder moveAnimation(ResourceLocation animation) {
            this.moveAnimation = animation;
            return this;
        }

        /** Sets the sound played when transformation starts. */
        public Builder transformSound(ResourceLocation sound) {
            this.transformSound = sound;
            return this;
        }

        /** Sets the sound played when de-transformation starts. */
        public Builder deTransformSound(ResourceLocation sound) {
            this.deTransformSound = sound;
            return this;
        }

        /** Sets the ambient sound looped while fully transformed. */
        public Builder ambientSound(ResourceLocation sound) {
            this.ambientSound = sound;
            return this;
        }

        /**
         * Sets the visual and hitbox scale multiplier.
         * Default is 1.0 (no scaling).
         *
         * @param scale the scale multiplier (must be positive)
         */
        public Builder modelScale(float scale) {
            this.modelScale = Math.max(0.1f, scale);
            return this;
        }

        /** Builds the TransformationData instance. */
        public TransformationData build() {
            return new TransformationData(this);
        }
    }
}