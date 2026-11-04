package com.kyou.imaginaryspacemeteoriteexcavation;

import java.io.File;

import net.minecraftforge.common.config.Configuration;

public class Config {

    public static int controllerId = 31980;

    public static void synchronizeConfiguration(File configFile) {
        Configuration configuration = new Configuration(configFile);

        controllerId = configuration.getInt(
            "controllerId",
            Configuration.CATEGORY_GENERAL,
            controllerId,
            1,
            32767,
            "GregTech MetaTileEntity ID. Must be unused in this pack; keep identical on client and server.");

        if (configuration.hasChanged()) {
            configuration.save();
        }
    }
}
