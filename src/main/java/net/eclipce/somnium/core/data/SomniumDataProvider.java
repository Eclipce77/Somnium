package net.eclipce.somnium.core.data;

import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.common.util.LazyOptional;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Forge capability provider that wraps {@link SomniumPlayerData}.
 *
 * <p>This class bridges Somnium's data model with Forge's capability system.
 * It implements {@link ICapabilitySerializable} to provide:</p>
 * <ul>
 *     <li>Capability exposure via {@link LazyOptional} (so other code can
 *         query players for Somnium data)</li>
 *     <li>NBT serialization/deserialization (so data persists across
 *         saves, dimension changes, etc.)</li>
 * </ul>
 *
 * <p>Addon developers don't interact with this class directly — it's
 * created and attached automatically by {@link SomniumCapabilityHandler}.
 * Use {@link SomniumCapability#get(net.minecraft.world.entity.player.Player)}
 * to access the data.</p>
 *
 * @see SomniumPlayerData
 * @see SomniumCapability
 * @see SomniumCapabilityHandler
 */
public class SomniumDataProvider implements ICapabilitySerializable<CompoundTag> {

    private final SomniumPlayerData data = new SomniumPlayerData();
    private final LazyOptional<SomniumPlayerData> lazyOptional = LazyOptional.of(() -> data);

    /**
     * Returns the capability if it matches the Somnium capability token.
     * Direction is ignored for entity capabilities.
     */
    @Nonnull
    @Override
    public <T> LazyOptional<T> getCapability(@Nonnull Capability<T> cap, @Nullable Direction side) {
        if (cap == SomniumCapability.CAPABILITY) {
            return lazyOptional.cast();
        }
        return LazyOptional.empty();
    }

    /**
     * Serializes the player data to NBT for saving.
     */
    @Override
    public CompoundTag serializeNBT() {
        return data.serializeNBT();
    }

    /**
     * Deserializes the player data from NBT on load.
     */
    @Override
    public void deserializeNBT(CompoundTag nbt) {
        data.deserializeNBT(nbt);
    }

    /**
     * Invalidates the LazyOptional. Called when the player entity
     * is removed/invalidated.
     */
    public void invalidate() {
        lazyOptional.invalidate();
    }
}