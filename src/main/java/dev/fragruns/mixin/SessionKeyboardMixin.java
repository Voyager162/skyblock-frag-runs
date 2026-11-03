package dev.fragruns.mixin;

import dev.fragruns.FragRunsClient;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardHandler.class)
public abstract class SessionKeyboardMixin {
    // A complete press/release/press can happen between client ticks. Observe the physical edges.
    @Inject(method="keyPress", at=@At("HEAD"), cancellable=true)
    private void fragruns$sessionInput(long window, int action, KeyEvent event, CallbackInfo ci) {
        var mod = FragRunsClient.INSTANCE;
        if (mod != null && window == Minecraft.getInstance().getWindow().handle() && mod.keyboardInput(event, action)) ci.cancel();
    }
}
