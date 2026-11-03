package dev.fragruns.core;

import java.util.Locale;
import java.util.regex.*;

/** Parse server receipts, not totals/reminders, player chat or mentions of a fragment in prose. */
public final class StashMessages {
    public record Drop(Giant giant,int count) {}
    private static final Pattern OVERFLOW=Pattern.compile("One or more (?:items?|materials?) didn't fit in your inventory and were added to your (?:material|item) stash!(?: Click here to pick them up!)?",Pattern.CASE_INSENSITIVE);
    private static final Pattern DEPOSIT=Pattern.compile("(?:Your inventory (?:is|was) full! )?(.+?) (?:was|were) (?:added|sent) to your (?:material|item) stash[.!]?",Pattern.CASE_INSENSITIVE);
    private static final Pattern QUANTITY=Pattern.compile("(?:(?<before>[0-9][0-9,]*)[x×]\\s+)?(?<name>.+?)(?:\\s+(?:[x×](?<after>[0-9][0-9,]*)|(?<suffix>[0-9][0-9,]*)[x×]))?",Pattern.CASE_INSENSITIVE);
    private StashMessages() {}
    public static boolean overflow(String s) { return OVERFLOW.matcher(s).matches(); }
    public static Drop deposited(String s) {
        var match=DEPOSIT.matcher(s);
        return match.matches()?item(match.group(1)):null;
    }
    public static boolean withdrawal(String s) { return s.startsWith("From stash: "); }
    public static Drop item(String s) {
        String label=Signals.clean(s).replace('’','\'').replaceFirst("^(?:[-•] |\\+\\s*)","");
        var m=QUANTITY.matcher(label);
        if (!m.matches()) return null;
        for (Giant g:Giant.values()) if (m.group("name").equalsIgnoreCase(g.fragmentName)) {
            String amount=m.group("before")!=null?m.group("before"):m.group("after")!=null?m.group("after"):m.group("suffix");
            try {
                int n=amount==null?1:Integer.parseInt(amount.replace(",",""));
                return n>0 && n<=1_000_000?new Drop(g,n):null;
            } catch (NumberFormatException e) { return null; }
        }
        return null;
    }
    public static boolean materialEmptied(String s) {
        return s.equals("You picked up all items from your material stash!") || s.equals("Your material stash is empty!");
    }
    public static boolean pickupCommand(String command) {
        String s=command.strip().toLowerCase(Locale.ROOT);
        return s.equals("pickupstash") || s.startsWith("pickupstash ");
    }
}
