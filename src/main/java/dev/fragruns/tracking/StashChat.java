package dev.fragruns.tracking;

import dev.fragruns.core.Giant;
import dev.fragruns.core.StashMessages;
import dev.fragruns.data.ItemCatalog;
import net.minecraft.network.chat.*;
import java.util.*;

final class StashChat {
    private StashChat() {}
    /** Retain itemized hover evidence on genuine stash notifications before other mods compact the chat line. */
    static Map<Giant,Integer> items(Component message,StashMessages.Drop visible,ItemCatalog catalog) {
        var result=new EnumMap<Giant,Integer>(Giant.class);
        if (visible!=null) result.put(visible.giant(),visible.count());
        Set<HoverEvent> seen=new HashSet<>();
        message.visit((style,part)->{
            var event=style.getHoverEvent();
            if (event==null || !seen.add(event)) return Optional.empty();
            if (event instanceof HoverEvent.ShowItem item) {
                var stack=item.item().create();
                var drop=SkyblockItems.read(stack,catalog);
                Giant giant=drop==null?null:Giant.fromItem(drop.id());
                if (giant!=null) result.merge(giant,stack.getCount(),Math::max);
            } else if (event instanceof HoverEvent.ShowText text) {
                for (String line:text.value().getString().split("\\R")) {
                    var drop=StashMessages.item(line);
                    if (drop!=null) result.merge(drop.giant(),drop.count(),Math::max);
                }
            }
            return Optional.empty();
        },Style.EMPTY);
        return result;
    }
}
