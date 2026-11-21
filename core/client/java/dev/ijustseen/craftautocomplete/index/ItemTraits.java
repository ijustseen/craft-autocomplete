package dev.ijustseen.craftautocomplete.index;

import dev.ijustseen.craftautocomplete.rank.GearKind;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/**
 * Reads what the ranker needs to know about an item from its tags and default components.
 */
public final class ItemTraits {
    private ItemTraits() {}

    public static GearKind gearKind(Item item) {
        Holder<Item> holder = BuiltInRegistries.ITEM.wrapAsHolder(item);
        if (holder.is(ItemTags.PICKAXES)) {
            return GearKind.PICKAXE;
        }
        if (holder.is(ItemTags.SWORDS)) {
            return GearKind.SWORD;
        }
        if (holder.is(ItemTags.AXES)) {
            return GearKind.AXE;
        }
        if (holder.is(ItemTags.CHEST_ARMOR)) {
            return GearKind.CHESTPLATE;
        }
        if (holder.is(ItemTags.LEG_ARMOR)) {
            return GearKind.LEGGINGS;
        }
        if (holder.is(ItemTags.HEAD_ARMOR)) {
            return GearKind.HELMET;
        }
        if (holder.is(ItemTags.FOOT_ARMOR)) {
            return GearKind.BOOTS;
        }
        if (item == Items.SHIELD) {
            return GearKind.SHIELD;
        }
        if (holder.is(ItemTags.SHOVELS)) {
            return GearKind.SHOVEL;
        }
        if (holder.is(ItemTags.HOES)) {
            return GearKind.HOE;
        }
        return null;
    }

    /** Durability stands in for tier: wood, stone, iron, diamond, netherite rise in that order. */
    public static int quality(Item item) {
        return item.getDefaultInstance().getMaxDamage();
    }

    public static boolean smelter(Item item) {
        return item == Items.FURNACE;
    }

    public static boolean essential(Item item) {
        return item == Items.FURNACE
                || item == Items.CHEST
                || item == Items.TORCH
                || BuiltInRegistries.ITEM.wrapAsHolder(item).is(ItemTags.BEDS);
    }
}
