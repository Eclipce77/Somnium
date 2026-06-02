package net.eclipce.somnium.compat.geckolib.mixin;

import net.eclipce.somnium.compat.geckolib.GeckoLibCompat;
import net.eclipce.somnium.compat.geckolib.player.cast.SomniumCastAnimatable;
import net.eclipce.somnium.compat.geckolib.player.cast.SomniumCastBoneApplicator;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Injects into {@link PlayerModel#setupAnim} at RETURN so that Somnium's
 * GeckoLib cast animation bone transforms are applied immediately after the
 * vanilla model pose has been established.
 *
 * <h3>Why PlayerModel and not HumanoidModel?</h3>
 * <p>{@code PlayerModel.setupAnim} calls {@code super.setupAnim} (HumanoidModel) and then
 * additionally positions the jacket, sleeve, and pants overlay parts. Injecting at
 * {@code RETURN} on {@code PlayerModel} ensures all twelve vanilla model parts —
 * including the overlay layer — are fully set up before our bone application runs.</p>
 *
 * <h3>Why additive?</h3>
 * <p>Vanilla sets each {@code ModelPart}'s rotation fields to match the current
 * locomotion state (arm swing, head look, swimming lean, etc.). By adding our
 * GeckoLib rotation deltas on top with {@code +=}, both animations coexist:
 * a punch animation on {@code right_arm} adds to whatever arm swing vanilla computed,
 * while every other bone remains exactly as vanilla set it.</p>
 *
 * <h3>Safety</h3>
 * <p>The injection is guarded by both a GeckoLib availability check and an
 * {@link SomniumCastAnimatable#isActive} check, making it a no-op for any player
 * without an active cast animation.</p>
 */
@Mixin(PlayerModel.class)
public abstract class PlayerModelSetupMixin {

    @Inject(method = "setupAnim", at = @At("RETURN"))
    private void somnium$onSetupAnimReturn(
            net.minecraft.world.entity.LivingEntity entity,  // erased type — must match bytecode
            float limbSwing,
            float limbSwingAmount,
            float ageInTicks,
            float netHeadYaw,
            float headPitch,
            CallbackInfo ci) {

        // PlayerModel only receives Player instances in practice
        if (!(entity instanceof Player player)) return;

        if (!GeckoLibCompat.isLoaded()) return;
        if (!SomniumCastAnimatable.isActive(player.getUUID())) return;

        @SuppressWarnings("unchecked")
        PlayerModel<Player> self = (PlayerModel<Player>) (Object) this;

        float partialTick = net.minecraft.client.Minecraft.getInstance().getFrameTime();
        SomniumCastBoneApplicator.apply(player, self, partialTick);
    }
}