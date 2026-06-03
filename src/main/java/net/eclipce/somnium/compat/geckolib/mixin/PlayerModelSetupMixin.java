package net.eclipce.somnium.compat.geckolib.mixin;

import net.eclipce.somnium.compat.geckolib.GeckoLibCompat;
import net.eclipce.somnium.compat.geckolib.player.cast.SomniumCastAnimatable;
import net.eclipce.somnium.compat.geckolib.player.cast.SomniumCastBoneApplicator;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Injects into {@link HumanoidModel#setupAnim} at RETURN so that Somnium's
 * GeckoLib cast animation bone transforms are applied immediately after the
 * vanilla model pose has been established.
 *
 * <h3>Why HumanoidModel and not PlayerModel?</h3>
 * <p>{@code setupAnim} is declared in {@code HumanoidModel<T extends LivingEntity>}
 * with erased signature {@code setupAnim(LivingEntity, float, float, float, float, float)}.
 * Targeting the declaring class directly avoids any bridge-method ambiguity that
 * arises from subclass overrides. The injection fires for all HumanoidModel
 * instances; the {@code instanceof Player} guard limits execution to players only.</p>
 *
 * <h3>Why additive?</h3>
 * <p>Vanilla sets each {@code ModelPart}'s rotation fields to match the current
 * locomotion state (arm swing, head look, swimming lean, etc.). By adding our
 * GeckoLib rotation deltas on top with {@code +=}, both animations coexist:
 * a punch animation on {@code right_arm} adds to whatever arm swing vanilla computed,
 * while every other bone remains exactly as vanilla set it.</p>
 */
@Mixin(HumanoidModel.class)
public abstract class PlayerModelSetupMixin {

    @Inject(method = "setupAnim", at = @At("RETURN"))
    private void somnium$onSetupAnimReturn(
            LivingEntity entity,   // declared erased type in HumanoidModel — matches bytecode exactly
            float limbSwing,
            float limbSwingAmount,
            float ageInTicks,
            float netHeadYaw,
            float headPitch,
            CallbackInfo ci) {

        // Only apply to player entities
        if (!(entity instanceof Player player)) return;
        if (!GeckoLibCompat.isLoaded()) return;
        if (!SomniumCastAnimatable.isActive(player.getUUID())) return;

        // Only apply when this HumanoidModel is actually a PlayerModel
        if (!((Object) this instanceof PlayerModel)) return;

        @SuppressWarnings("unchecked")
        PlayerModel<Player> self = (PlayerModel<Player>) (Object) this;

        float partialTick = net.minecraft.client.Minecraft.getInstance().getFrameTime();
        SomniumCastBoneApplicator.apply(player, self, partialTick);
    }
}