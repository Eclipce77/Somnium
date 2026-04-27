package net.eclipce.somnium.compat.geckolib.base;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.level.Level;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * Base class for GeckoLib-animated entities in Somnium addons.
 *
 * <p>Provides the GeckoLib boilerplate so addon devs only need to
 * override {@link #registerControllers(AnimatableManager.ControllerRegistrar)}
 * and create a renderer + model.</p>
 *
 * <h3>Usage for addon developers</h3>
 * <pre>{@code
 * public class MyCreature extends SomniumGeoEntity {
 *     private static final RawAnimation IDLE =
 *         RawAnimation.begin().thenLoop("animation.mycreature.idle");
 *
 *     public MyCreature(EntityType<? extends MyCreature> type, Level level) {
 *         super(type, level);
 *     }
 *
 *     @Override
 *     public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
 *         controllers.add(new AnimationController<>(this, "main", 5, state -> {
 *             state.getController().setAnimation(IDLE);
 *             return PlayState.CONTINUE;
 *         }));
 *     }
 * }
 * }</pre>
 *
 * <p><strong>Note:</strong> This class extends {@link PathfinderMob}. For
 * different entity base classes (LivingEntity, Mob, etc.), implement
 * {@link GeoEntity} directly on your entity class instead.</p>
 *
 * @see GeoEntity
 */
public abstract class SomniumGeoEntity extends PathfinderMob implements GeoEntity {

    private final AnimatableInstanceCache geoCache =
            GeckoLibUtil.createInstanceCache(this);

    protected SomniumGeoEntity(EntityType<? extends SomniumGeoEntity> type, Level level) {
        super(type, level);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return geoCache;
    }
}
