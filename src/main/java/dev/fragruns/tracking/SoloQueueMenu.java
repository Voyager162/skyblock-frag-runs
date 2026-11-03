package dev.fragruns.tracking;

import dev.fragruns.core.RunActions;
import dev.fragruns.core.SoloEntry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import java.util.ArrayList;
import java.util.stream.Collectors;

final class SoloQueueMenu {
    private SoloQueueMenu() {}
    static void confirm(Minecraft mc,RunActions actions) {
        if (mc.player==null || mc.gameMode==null || mc.gui.overlay()!=null || mc.player.isDeadOrDying()) return;
        if (mc.gui.screen()==null) return;
        if (!(mc.gui.screen() instanceof AbstractContainerScreen<?> screen) || !(screen.getMenu() instanceof ChestMenu menu)) {
            actions.cancelQueue();return;
        }
        if (menu!=mc.player.containerMenu || !menu.getCarried().isEmpty()) return;
        var options=new ArrayList<SoloEntry.Option>();
        for (int i=0;i<menu.getContainer().getContainerSize();i++) {
            var stack=menu.getContainer().getItem(i);
            if (stack.isEmpty() || !stack.has(DataComponents.CUSTOM_NAME) && !stack.has(DataComponents.ITEM_NAME)) continue;
            var lore=stack.get(DataComponents.LORE);
            String text=lore==null?"":lore.lines().stream().map(c->c.getString()).collect(Collectors.joining(" "));
            options.add(new SoloEntry.Option(i,stack.getHoverName().getString(),text));
        }
        String title=screen.getTitle().getString();
        int slot=SoloEntry.slot(title,options);
        if (slot>=0 && actions.claimButton(menu.containerId+"/"+title+"/"+slot,System.nanoTime()/1_000_000))
            mc.gameMode.handleContainerInput(menu.containerId,slot,0,ContainerInput.PICKUP,mc.player);
    }
}
