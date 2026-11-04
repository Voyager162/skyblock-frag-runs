package dev.fragruns.core;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/** Display preferences and the measured content shared by gameplay and the layout editor. */
public final class HudSettings {
    public static final int WIDTH = 240, ROW = 14, GIANT_ROW = 27, DROP_OFFSET = 12, FIRST_PROFIT_ROW = 17;
    private static final int FONT = 9, PAD = 8, PROMPT_SPACE = 23;
    private static final int GIANTS = Giant.values().length;

    private enum Group { SESSION, RUNS, GIANTS, BREAKDOWN, TOTALS }
    public enum Section {
        HEADER("Title & status", "Tracker title and live, paused or saved status.", Group.SESSION),
        SESSION_TIMER("Session timer", "Elapsed session time.", Group.SESSION),
        RUN_TIMER("Current run timer", "Time spent in the current Floor VII run.", Group.SESSION),
        RUN_RECORDS("Run time records", "Fastest, slowest, last and average run times.", Group.RUNS),
        RUN_COUNTS("Run counts", "Total, finished and early runs.", Group.RUNS),
        SESSION_LUCK("Session luck", "0-100 estimate. 50 is expected; unverified stash drops are excluded.", Group.RUNS),
        WATCHER_GIANTS("Watcher giants", "Encounters, giant shares, fragment quantities and drop rates.", Group.GIANTS),
        PROFIT_BREAKDOWN("Profit breakdown", "Fragment income, essence, kill coins and other items.", Group.BREAKDOWN),
        TOTAL_PROFIT("Total profit", "The value of all collected loot and coins.", Group.TOTALS),
        HOURLY_PROFIT("Profit per hour", "The hourly estimate in your selected profit mode.", Group.TOTALS);

        public final String label, description;
        private final Group group;
        Section(String label, String description, Group group) {
            this.label = label;
            this.description = description;
            this.group = group;
        }
        private int height(boolean combined) {
            return switch (this) {
                case HEADER -> 16;
                case SESSION_LUCK, SESSION_TIMER, RUN_TIMER, TOTAL_PROFIT, HOURLY_PROFIT -> FONT;
                case RUN_RECORDS -> 3 * ROW + FONT;
                case RUN_COUNTS -> ROW + FONT;
                case WATCHER_GIANTS -> (GIANTS - 1) * GIANT_ROW + DROP_OFFSET + FONT;
                case PROFIT_BREAKDOWN -> FIRST_PROFIT_ROW + ((combined ? 1 : GIANTS) + 2) * ROW + FONT;
            };
        }
    }

    public record Block(Section section, int y, boolean divider) {}
    public record Content(List<Block> blocks, int bodyHeight) {
        public int height(boolean prompt) {
            return bodyHeight + (prompt && bodyHeight > 0 ? PROMPT_SPACE : 0);
        }
    }

    private static final int FULL_BODY_HEIGHT = measure(Set.of(), false).bodyHeight();
    private Set<Section> hidden = EnumSet.noneOf(Section.class);
    private boolean combinedFragments;
    private int scaleReferenceHeight;
    private transient Content measured;

    public boolean visible(Section section) { return !hidden.contains(section); }
    public boolean combinedFragments() { return combinedFragments; }
    public void toggle(Section section) {
        rememberScale();
        if (!hidden.remove(section)) hidden.add(section);
    }
    public void toggleFragmentGrouping() {
        rememberScale();
        combinedFragments = !combinedFragments;
    }
    private void rememberScale() {
        // On a small screen, hiding a section should shrink the panel, not enlarge the remaining text.
        scaleReferenceHeight = Math.max(scaleReferenceHeight, content().bodyHeight());
        measured = null;
    }
    /** An explicit resize may use all the space available to the current selection. */
    public void allowResize() { scaleReferenceHeight = 0; }
    public Layout.Bounds bounds(Layout placement, int viewportWidth, int viewportHeight, boolean prompt) {
        Content content = content();
        int scaleHeight = Math.max(content.bodyHeight(), scaleReferenceHeight) + PROMPT_SPACE;
        return placement.fit(viewportWidth, viewportHeight, WIDTH, content.height(prompt), scaleHeight);
    }
    public Content content() {
        if (measured == null) measured = measure(hidden, combinedFragments);
        return measured;
    }
    private static Content measure(Set<Section> hidden, boolean combined) {
        List<Block> blocks = new ArrayList<>();
        Group previous = null;
        int y = PAD;
        for (Section section : Section.values()) {
            if (hidden.contains(section)) continue;
            boolean divider = previous != null && previous != section.group;
            if (previous != null) y += divider ? 12 : 5;
            blocks.add(new Block(section, y, divider));
            y += section.height(combined);
            previous = section.group;
        }
        return new Content(List.copyOf(blocks), blocks.isEmpty() ? 0 : y + PAD);
    }
    public void sanitize() {
        if (hidden == null) hidden = EnumSet.noneOf(Section.class);
        else hidden.remove(null);
        scaleReferenceHeight = Math.clamp(scaleReferenceHeight, 0, FULL_BODY_HEIGHT);
        measured = null;
    }
}
