package dev.fragruns.tracking;

import dev.fragruns.core.Signals;
import dev.fragruns.core.HuntingShards;
import dev.fragruns.data.ItemCatalog;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import java.util.regex.Pattern;

public final class SkyblockItems {
    private static final Pattern RARITY = Pattern.compile("(?:VERY SPECIAL|SPECIAL|MYTHIC|LEGENDARY|EPIC|RARE|UNCOMMON|COMMON)(?= (?:DUNGEON )?[A-Z ]+$|$)");
    public record Drop(String id, String name, String uuid, String tier, int quality, int stars, int count) {}
    private SkyblockItems() {}
    public static Drop read(ItemStack stack, ItemCatalog catalog) {
        if (stack.isEmpty()) return null;
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) return null;
        CompoundTag root = data.copyTag();
        CompoundTag extra = root.getCompound("ExtraAttributes").orElse(root);
        String id = extra.getStringOr("id","");
        if (id.isEmpty()) return null;
        if (id.startsWith("ATTRIBUTE_SHARD") || id.startsWith("SHARD_")) {
            var shard = HuntingShards.byId(id);
            if (shard == null && id.startsWith("ATTRIBUTE_SHARD")) shard = HuntingShards.byLabel(stack.getHoverName().getString());
            if (shard != null) return new Drop(shard.productId(), shard.name()+" Shard", "", "", 0, 0, stack.getCount());
        }
        ItemCatalog.Item item = catalog.get(id);
        String tier = item == null ? "" : item.tier();
        ItemLore lore = stack.get(DataComponents.LORE);
        if (lore != null) for (var line : lore.lines()) {
            var matcher = RARITY.matcher(Signals.clean(line.getString()));
            if (matcher.find()) tier = matcher.group();
        }
        return new Drop(id, item == null ? Signals.clean(stack.getHoverName().getString()) : item.name(),
                extra.getStringOr("uuid",""), tier, extra.getIntOr("baseStatBoostPercentage",0),
                extra.getIntOr("upgrade_level",extra.getIntOr("dungeon_item_level",0)),stack.getCount());
    }
}
