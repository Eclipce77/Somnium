package net.eclipce.somnium.compat.geckolib.base;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.ThrowableProjectile;
import net.minecraft.world.level.Level;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * Base class for GeckoLib-animated projectile entities.
 *
 * <p>Use this for abilities that spawn visually complex projectiles
 * with custom models and animations (e.g., energy blasts, magic missiles).</p>
 *
 * <h3>Usage</h3>
 * <pre>{@code
 * public class EnergyBolt extends SomniumGeoProjectile {
 *     private static final RawAnimation FLY =
 *         RawAnimation.begin().thenLoop("animation.energybolt.fly");
 *
 *     public EnergyBolt(EntityType<? extends EnergyBolt> type, Level level) {
 *         super(type, level);
 *     }
 *
 *     @Override
 *     public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
 *         controllers.add(new AnimationController<>(this, "main", 0, state -> {
 *             state.getController().setAnimation(FLY);
 *             return PlayState.CONTINUE;
 *         }));
 *     }
 *
 *     @Override
 *     protected void onHit(HitResult result) {
 *         // Handle impact
 *     }
 * }
 * }</pre>
 */
public abstract class SomniumGeoProjectile extends ThrowableProjectile implements GeoEntity {

    private final AnimatableInstanceCache geoCache =
            GeckoLibUtil.createInstanceCache(this);

    protected SomniumGeoProjectile(EntityType<? extends SomniumGeoProjectile> type,
                                    Level level) {
        super(type, level);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return geoCache;
    }

    @Override
    protected void defineSynchedData() {
        // Override in subclass if synced data is needed
    }
}
