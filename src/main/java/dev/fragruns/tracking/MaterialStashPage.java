package dev.fragruns.tracking;

import dev.fragruns.core.*;
import dev.fragruns.data.ItemCatalog;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.Items;
import java.util.*;
import java.util.regex.*;

/** The container's storage area only. Client inventory slots and navigation icons are never material quantities. */
final class MaterialStashPage {
    private static final Pattern TITLE=Pattern.compile("(?i)^(?:\\((\\d+)/(\\d+)\\) )?(?:Your )?Material Stash(?: \\((?:Page )?(\\d+)(?:/| of )(\\d+)\\))?$");
    private static final String AMOUNT_LABEL="(?:(?:(?:item|stored|stashed|total) )?(?:amount|quantity)(?: (?:stored|stashed|in (?:your )?(?:material )?stash))?|(?:total )?(?:stored|stashed)|in (?:your )?(?:material )?stash|total)";
    private static final Pattern AMOUNT=Pattern.compile("(?i)^(?:"+AMOUNT_LABEL+":\\s*[x×]?([\\d,]+)(?:[x×]| materials?| items?)?|([\\d,]+)[x×]? (?:stored|stashed)(?: here)?)$");
    private static final Pattern UNKNOWN_AMOUNT=Pattern.compile("(?i)^"+AMOUNT_LABEL+":.*");
    private static final Pattern NAV=Pattern.compile("(?i)^(?:(?:go )?to )?page[: ]+([0-9]+)(?:\\s*(?:/|of)\\s*([0-9]+))?$");
    record Page(Map<Giant,Integer> counts,int number,int total,int nextSlot,String signature) {}
    static boolean title(String text) { return TITLE.matcher(Signals.clean(text)).matches(); }
    static boolean stashResponse(String text) {
        return Signals.clean(text).matches("(?i)^(?:\\([^)]*\\) )?(?:Your )?(?:(?:Material|Item) )?Stash(?:[ (:#-].*)?$");
    }
    static Page read(ChestMenu menu,String title,ItemCatalog catalog) {
        var heading=TITLE.matcher(Signals.clean(title));if (!heading.matches()) return null;
        int number=number(heading.group(1)!=null?heading.group(1):heading.group(3),0);
        int total=number(heading.group(2)!=null?heading.group(2):heading.group(4),0);
        int size=menu.getContainer().getContainerSize(), next=-1, navTo=0;
        boolean ready=false;
        var counts=new EnumMap<Giant,Integer>(Giant.class);
        var signature=new StringBuilder();
        for(int i=0;i<size;i++) {
            var stack=menu.getSlot(i).getItem();if(stack.isEmpty()) continue;
            String name=Signals.clean(stack.getHoverName().getString());String lower=name.toLowerCase(Locale.ROOT);
            if (lower.startsWith("loading") || lower.equals("please wait...")) return null;
            var lore=stack.get(DataComponents.LORE);
            if (i>=size-9 && stack.is(Items.ARROW) && lower.equals("next page")) {
                if (next>=0) return null;
                next=i;
                if (lore!=null) for (var line:lore.lines()) {
                    var nav=NAV.matcher(Signals.clean(line.getString()));
                    if (nav.matches()) {navTo=number(nav.group(1),0);if(nav.group(2)!=null) total=number(nav.group(2),0);}
                }
                continue;
            }
            if (lower.equals("empty stash") || lower.equals("no materials") || lower.equals("empty material stash")
                    || StashMessages.emptyView(name)) {ready=true;continue;}
            if (BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath().endsWith("glass_pane")) continue;
            if (i>=size-9 && stack.is(Items.ARROW) && lower.equals("previous page") && number==0 && lore!=null) {
                for (var line:lore.lines()) {
                    var nav=NAV.matcher(Signals.clean(line.getString()));
                    if(nav.matches()) number=number(nav.group(1),0)+1;
                }
            }
            if (i>=size-9 && (lower.equals("close") || lower.equals("go back") || lower.equals("previous page")
                    || lower.contains("to sacks") || lower.contains("to your sacks") || lower.contains("insta-sell") || lower.startsWith("pick up all"))) {
                ready|=lower.contains("sacks") || lower.contains("insta-sell") || lower.startsWith("pick up all");continue;
            }
            var item=SkyblockItems.read(stack,catalog);
            var label=StashMessages.item(name);
            Giant giant=item==null?(label==null?null:label.giant()):Giant.fromItem(item.id());
            if (giant==null && Arrays.stream(Giant.values()).anyMatch(g->lower.replace('’','\'').contains(g.fragmentName.toLowerCase(Locale.ROOT)))) return null;
            if (item!=null || giant!=null || i<size-9 && stack.has(DataComponents.CUSTOM_NAME) && !name.isBlank() && !stack.is(Items.BARRIER)) ready=true;
            signature.append(i).append(':').append(name).append('|');
            if (giant==null) continue;
            int count=label!=null && label.count()>1?label.count():stack.getCount();
            if (lore!=null) for (var line:lore.lines()) {
                String text=Signals.clean(line.getString());
                var m=AMOUNT.matcher(text);
                if (m.matches()) {count=number(m.group(1)!=null?m.group(1):m.group(2),-1);break;}
                if (UNKNOWN_AMOUNT.matcher(text).matches()) return null;
            }
            if (count<=0 || counts.putIfAbsent(giant,count)!=null) return null;
        }
        if (navTo>0 && number==0) number=navTo-1;
        if (!ready || total>128 || number>128 || total>0 && (number<1 || number>total)) return null;
        // Only navigate a server-labelled footer arrow with corroborating page metadata.
        if (next>=0 && !(total>number && number>0 || navTo>=2 && navTo==number+1)) return null;
        if (number>0 && total>number && next<0) return null;
        return new Page(Map.copyOf(counts),number,total,next,signature.toString());
    }
    private static int number(String s,int fallback) {
        if (s==null) return fallback;
        try { return Integer.parseInt(s.replace(",","")); } catch(NumberFormatException e) {return fallback;}
    }
}
