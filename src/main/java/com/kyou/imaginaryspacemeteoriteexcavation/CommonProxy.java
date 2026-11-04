package com.kyou.imaginaryspacemeteoriteexcavation;

import java.io.File;

import com.kyou.imaginaryspacemeteoriteexcavation.meteor.runtime.MeteorCatalogueService;

import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLLoadCompleteEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;

public class CommonProxy {

    private File configDirectory;

    // preInit "Run before anything else. Read your config, create blocks, items, etc, and register them with the
    // GameRegistry." (Remove if not needed)
    public void preInit(FMLPreInitializationEvent event) {
        configDirectory = event.getModConfigurationDirectory();
        Config.synchronizeConfiguration(event.getSuggestedConfigurationFile());
        Registration.registerItems();
        ImaginarySpaceMeteoriteExcavation.LOG
            .info("I am " + ImaginarySpaceMeteoriteExcavation.MODNAME + " at version " + Tags.VERSION);
    }

    // load "Do your mod setup. Build whatever data structures you care about. Register recipes." (Remove if not needed)
    public void init(FMLInitializationEvent event) {
        Registration.registerController();
    }

    // postInit "Handle interaction with other mods, complete your setup based on this." (Remove if not needed)
    public void postInit(FMLPostInitializationEvent event) {
        Registration.registerControllerRecipe();
        Registration.registerBookRecipes();
    }

    // register server commands in this event handler (Remove if not needed)
    public void loadComplete(FMLLoadCompleteEvent event) {
        MeteorCatalogueService.refresh(configDirectory);
    }

    public void serverStarting(FMLServerStartingEvent event) {
        MeteorCatalogueService.refresh(configDirectory);
    }
}
