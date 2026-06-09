package net.eclipce.somnium.compat.geckolib.mixin;

import net.eclipce.somnium.compat.geckolib.GeckoLibCompat;
import net.eclipce.somnium.compat.geckolib.player.cast.CastAnimationOptions;
import net.eclipce.somnium.compat.geckolib.player.cast.SomniumCastAnimatable;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Honours {@link CastAnimationOptions#heldItemsShown()} = {@code false} by skipping
 * vanilla's hand-attached item render for the duration of the cast animation.
 *
 * <h3>What this hides</h3>
 * <p>{@link ItemInHandLayer} is the render layer responsible for drawing whatever the
 * entity is holding attached to its hand bone — swords, blocks, food, etc. — in the
 * <em>third-person world render</em>. The vanilla first-person path uses a different
 * code path ({@code ItemInHandRenderer.renderHandsWithItems}), which is suppressed
 * separately by {@code SomniumFirstPersonRenderer} via {@code RenderHandEvent}.</p>
 *
 * <h3>Why mixin and not a render layer subscriber</h3>
 * <p>Forge does not expose a per-layer cancel event for {@code ItemInHandLayer}, and
 * removing the layer at runtime would affect all players globally on the client (the
 * layer instance is shared by the renderer). Cancelling in a mixin lets us scope the
 * cancellation to exactly the player whose cast options ask for it, leaving every
 * other player's held-item rendering completely undisturbed.</p>
 *
 * <h3>Interaction with the first-person re-render</h3>
 * <p>{@code SomniumFirstPersonRenderer.onRenderLevelStage} calls
 * {@code PlayerRenderer.render(...)} when rendering the local player from inside the
 * FP camera. That render path invokes every layer including this one, so the same
 * {@code heldItemsShown=false} check fires there too — meaning the item is hidden
 * in the first-person re-render as well, exactly as expected.</p>
 */
@Mixin(ItemInHandLayer.class)
public abstract class ItemInHandLayerMixin {

    @Inject(
            method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/entity/LivingEntity;FFFFFF)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void somnium$skipItemWhenHidden(
            com.mojang.blaze3d.vertex.PoseStack poseStack,
            net.minecraft.client.renderer.MultiBufferSource buffer,
            int packedLight,
            LivingEntity entity,
            float limbSwing,
            float limbSwingAmount,
            float partialTicks,
            float ageInTicks,
            float netHeadYaw,
            float headPitch,
            CallbackInfo ci) {

        if (!GeckoLibCompat.isLoaded()) return;
        if (!(entity instanceof Player player)) return;

        SomniumCastAnimatable animatable = SomniumCastAnimatable.getOrNull(player.getUUID());
        if (animatable == null) return;
        if (animatable.getActiveAnimation() == null) return;

        if (!animatable.getCurrentOptions().heldItemsShown()) {
            ci.cancel();
        }
    }
}