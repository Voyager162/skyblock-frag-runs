package dev.fragruns.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GiantDropsTest {
    private final GiantDrops drops = new GiantDrops();
    private final Giant handle = Giant.DIAMANTE;

    @Test void handleCarriedThroughWarpAndInventoryReloadDoesNotDropAgain() {
        assertEquals(0, drops.inventory(handle,0,false));
        assertEquals(1, drops.inventory(handle,1,true));
        drops.reset();
        assertEquals(0, drops.inventory(handle,1,false)); // quantity before warp
        assertEquals(0, drops.inventory(handle,0,false));
        assertEquals(0, drops.inventory(handle,1,false)); // inventory arrives later
        assertEquals(0, drops.inventory(handle,0,true));
        assertEquals(0, drops.inventory(handle,1,true)); // same stack during the second kill
        assertEquals(1, drops.inventory(handle,2,true)); // one genuinely new Handle
    }
    @Test void preGiantInventoryIsABaselineAndDifferentGiantsDoNotShareEvidence() {
        assertEquals(0,drops.inventory(handle,1,false));
        assertEquals(0,drops.inventory(Giant.LASR,4,false));
        assertEquals(0,drops.inventory(handle,1,true));
        assertEquals(1,drops.inventory(handle,2,true));
    }
    @Test void delayedPickupAndInventoryUpdatesCreditTheSameDropOnceInEitherOrder() {
        assertEquals(1,drops.pickup(handle,1));
        assertEquals(0,drops.inventory(handle,1,true));
        drops.reset();
        assertEquals(2,drops.inventory(handle,2,true));
        assertEquals(0,drops.pickup(handle,1));
        assertEquals(0,drops.pickup(handle,1));
    }
}
