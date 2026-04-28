package net.eclipce.somnium.compat.curios;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;
import top.theillusivec4.curios.api.type.inventory.IDynamicStackHandler;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Utility methods for querying Curios slots from Somnium ability code.
 *
 * <p>All methods are safe to call — they return empty/null results if
 * Curios is not loaded. Addon devs should check
 * {@code CuriosCompat.isLoaded()} before calling these methods.</p>
 *
 * <h3>Usage from ability code</h3>
 * <pre>{@code
 * if (CuriosCompat.isLoaded()) {
 *     // Check if the player has a specific item in any curios slot
 *     boolean hasAmulet = CuriosHelper.hasItem(player, MyItems.FIRE_AMULET.get());
 *
 *     // Find all items in a specific slot type
 *     List<ItemStack> rings = CuriosHelper.getEquippedInSlot(player, "ring");
 *
 *     // Get the first matching item
 *     ItemStack charm = CuriosHelper.findFirstItem(player, MyItems.POWER_CHARM.get());
 * }
 * }</pre>
 */
public final class CuriosHelper {

    /**
     * Checks if the entity has a specific item equipped in any Curios slot.
     *
     * @param entity the living entity
     * @param item   the item to search for
     * @return true if found in any curio slot
     */
    public static boolean hasItem(LivingEntity entity, Item item) {
        return CuriosApi.getCuriosInventory(entity)
                .map(handler -> {
                    for (var entry : handler.getCurios().entrySet()) {
                        ICurioStacksHandler stacksHandler = entry.getValue();
                        IDynamicStackHandler stacks = stacksHandler.getStacks();
                        for (int i = 0; i < stacks.getSlots(); i++) {
                            if (stacks.getStackInSlot(i).getItem() == item) {
                                return true;
                            }
                        }
                    }
                    return false;
                })
                .orElse(false);
    }

    /**
     * Finds the first occurrence of an item in any Curios slot.
     *
     * @param entity the living entity
     * @param item   the item to find
     * @return the ItemStack, or {@link ItemStack#EMPTY} if not found
     */
    public static ItemStack findFirstItem(LivingEntity entity, Item item) {
        return CuriosApi.getCuriosInventory(entity)
                .map(handler -> {
                    for (var entry : handler.getCurios().entrySet()) {
                        ICurioStacksHandler stacksHandler = entry.getValue();
                        IDynamicStackHandler stacks = stacksHandler.getStacks();
                        for (int i = 0; i < stacks.getSlots(); i++) {
                            ItemStack stack = stacks.getStackInSlot(i);
                            if (stack.getItem() == item) {
                                return stack;
                            }
                        }
                    }
                    return ItemStack.EMPTY;
                })
                .orElse(ItemStack.EMPTY);
    }

    /**
     * Gets all items equipped in a specific Curios slot type.
     *
     * @param entity the living entity
     * @param slotId the slot type identifier (e.g., "ring", "necklace", "charm")
     * @return a list of non-empty ItemStacks in that slot type
     */
    public static List<ItemStack> getEquippedInSlot(LivingEntity entity, String slotId) {
        List<ItemStack> result = new ArrayList<>();
        CuriosApi.getCuriosInventory(entity).ifPresent(handler -> {
            Optional.ofNullable(handler.getCurios().get(slotId)).ifPresent(stacksHandler -> {
                IDynamicStackHandler stacks = stacksHandler.getStacks();
                for (int i = 0; i < stacks.getSlots(); i++) {
                    ItemStack stack = stacks.getStackInSlot(i);
                    if (!stack.isEmpty()) {
                        result.add(stack);
                    }
                }
            });
        });
        return result;
    }

    /**
     * Counts how many of a specific item are equipped across all Curios slots.
     *
     * @param entity the living entity
     * @param item   the item to count
     * @return the count of matching items
     */
    public static int countItem(LivingEntity entity, Item item) {
        return CuriosApi.getCuriosInventory(entity)
                .map(handler -> {
                    int count = 0;
                    for (var entry : handler.getCurios().entrySet()) {
                        ICurioStacksHandler stacksHandler = entry.getValue();
                        IDynamicStackHandler stacks = stacksHandler.getStacks();
                        for (int i = 0; i < stacks.getSlots(); i++) {
                            if (stacks.getStackInSlot(i).getItem() == item) {
                                count++;
                            }
                        }
                    }
                    return count;
                })
                .orElse(0);
    }

    private CuriosHelper() {}
}
