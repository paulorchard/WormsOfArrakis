package com.paulorchard.islandcraft.wormsofarrakis;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;

/** Settings read from Worms_of_Arrakis.json in the plugin's data folder. */
public class WormsOfArrakisConfig {

    public static final String FILE_NAME = "Worms_of_Arrakis";

    public static final BuilderCodec<WormsOfArrakisConfig> CODEC =
            BuilderCodec.builder(WormsOfArrakisConfig.class, WormsOfArrakisConfig::new)
                    .documentation("Settings for the sandworms.")
                    .append(new KeyedCodec<>("SandBlocks", Codec.STRING_ARRAY, false),
                            (config, value) -> config.sandBlocks = value,
                            config -> config.sandBlocks)
                    .documentation("Block ids that count as sand. Ids that do not exist (for example Arrakis_Sand "
                            + "when Dunes of Arrakis is not installed) are ignored.")
                    .add()
                    .build();

    private String[] sandBlocks = {
            "Arrakis_Sand", "Soil_Sand", "Soil_Sand_Ashen", "Soil_Sand_Red", "Soil_Sand_White"
    };

    public String[] getSandBlocks() {
        return sandBlocks;
    }
}
