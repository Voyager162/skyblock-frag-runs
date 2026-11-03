package dev.fragruns.mixin;

import dev.fragruns.tracking.RunMapRecorder;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets="de.hysky.skyblocker.skyblock.dungeon.DungeonMapTexture",remap=false)
abstract class SkyblockerMapMixin {
    @Inject(method="blitMap",at=@At("TAIL"),require=0)
    private static void fragruns$map(GuiGraphicsExtractor graphics,CallbackInfo ci) {
        RunMapRecorder.mapRendered(graphics,0,128);
    }
}
