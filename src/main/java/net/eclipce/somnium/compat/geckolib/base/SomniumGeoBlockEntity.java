package net.eclipce.somnium.compat.geckolib.base;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * Base class for GeckoLib-animated block entities in Somnium addons.
 *
 * <h3>Usage</h3>
 * <pre>{@code
 * public class PowerAltarBlockEntity extends SomniumGeoBlockEntity {
 *     public PowerAltarBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
 *         super(type, pos, state);
 *     }
 *
 *     @Override
 *     public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
 *         controllers.add(new AnimationController<>(this, "main", 0, state -> {
 *             state.getController().setAnimation(RawAnimation.begin().thenLoop("idle"));
 *             return PlayState.CONTINUE;
 *         }));
 *     }
 * }
 * }</pre>
 */
public abstract class SomniumGeoBlockEntity extends BlockEntity implements GeoBlockEntity {

    private final AnimatableInstanceCache geoCache =
            GeckoLibUtil.createInstanceCache(this);

    protected SomniumGeoBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return geoCache;
    }
}
