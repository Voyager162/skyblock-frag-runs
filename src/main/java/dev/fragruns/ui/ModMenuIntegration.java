package dev.fragruns.ui;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import dev.fragruns.FragRunsClient;

public final class ModMenuIntegration implements ModMenuApi {
    @Override public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return parent->new DashboardScreen(FragRunsClient.INSTANCE,true,parent);
    }
}
