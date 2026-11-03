package dev.fragruns.core;

import org.junit.jupiter.api.Test;
import static dev.fragruns.core.HudSettings.Section.*;
import static org.junit.jupiter.api.Assertions.*;

class HudSettingsTest {
    @Test void everySectionCanBeHiddenIndependentlyAndItsSpaceIsReclaimed() {
        var sections = HudSettings.Section.values();
        var anchor = new Layout(1,1,2.5); // height-constrained, anchored to the bottom right
        for (int mask = 0; mask < 1 << sections.length; mask++) {
            var settings = new HudSettings();
            for (var section : sections) if ((mask & (1 << section.ordinal())) != 0) settings.toggle(section);
            for (int mode = 0; mode < 2; mode++) {
                var selected = settings.content().blocks();
                assertEquals(sections.length - Integer.bitCount(mask), selected.size());
                assertEquals(selected.isEmpty(), settings.content().bodyHeight() == 0);
                if (!selected.isEmpty()) assertFalse(selected.getFirst().divider());
                var before = settings.bounds(anchor,320,240,false);
                for (var block : selected) {
                    settings.toggle(block.section());
                    var after = settings.bounds(anchor,320,240,false);
                    assertTrue(after.height() < before.height(), block.section().name());
                    assertEquals(before.scale(), after.scale(), 1e-9);
                    assertEquals(314, after.x()+after.width(), 1e-8);
                    assertEquals(234, after.y()+after.height(), 1e-8);
                    assertFalse(settings.content().blocks().stream().anyMatch(b -> b.section() == block.section()));
                    settings.toggle(block.section());
                    assertEquals(selected, settings.content().blocks());
                }
                settings.toggleFragmentGrouping();
            }
        }
    }
    @Test void groupingCollapsesExactlyThreeRowsAndDoesNotAffectOtherSections() {
        var settings = new HudSettings();
        int full = settings.content().bodyHeight();
        settings.toggleFragmentGrouping();
        assertEquals(full-3*HudSettings.ROW,settings.content().bodyHeight());
        assertTrue(settings.visible(WATCHER_GIANTS));
        settings.toggle(PROFIT_BREAKDOWN);
        int hidden = settings.content().bodyHeight();
        settings.toggleFragmentGrouping();
        assertEquals(hidden,settings.content().bodyHeight());
        assertTrue(settings.visible(TOTAL_PROFIT));
        assertTrue(settings.visible(HOURLY_PROFIT));
    }
    @Test void compactHudCanBeEnlargedAndEmptyHudHasNoPanelOrStopHint() {
        var settings = new HudSettings();
        for (var section : HudSettings.Section.values()) if (section != TOTAL_PROFIT) settings.toggle(section);
        var placement = new Layout(0,0,2);
        var compact = settings.bounds(placement,640,240,false);
        assertTrue(settings.bounds(placement,640,240,true).height() > compact.height());
        settings.allowResize();
        assertTrue(settings.bounds(placement,640,240,false).scale() > compact.scale());
        settings.toggle(TOTAL_PROFIT);
        assertEquals(0,settings.content().height(true));
        assertEquals(0,settings.content().height(false));
    }
}
