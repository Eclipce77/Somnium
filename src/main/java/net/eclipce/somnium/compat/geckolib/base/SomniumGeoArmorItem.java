package net.eclipce.somnium.compat.geckolib.base;

import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * Base class for GeckoLib-animated armor items in Somnium addons.
 *
 * <p>Provides GeckoLib boilerplate for animated armor. Addon devs
 * override {@link #registerControllers} and create a renderer + model.</p>
 *
 * <h3>Usage</h3>
 * <pre>{@code
 * public class MyAnimatedHelmet extends SomniumGeoArmorItem {
 *     public MyAnimatedHelmet(ArmorMaterial material, Type type, Properties properties) {
 *         super(material, type, properties);
 *     }
 *
 *     @Override
 *     public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
 *         controllers.add(new AnimationController<>(this, "armor", 5, state -> {
 *             state.getController().setAnimation(RawAnimation.begin().thenLoop("idle"));
 *             return PlayState.CONTINUE;
 *         }));
 *     }
 * }
 * }</pre>
 *
 * @see software.bernie.geckolib.animatable.GeoItem
 */
public abstract class SomniumGeoArmorItem extends ArmorItem implements GeoItem {

    private final AnimatableInstanceCache geoCache =
            GeckoLibUtil.createInstanceCache(this);

    public SomniumGeoArmorItem(ArmorMaterial material, Type type, Properties properties) {
        super(material, type, properties);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return geoCache;
    }
}