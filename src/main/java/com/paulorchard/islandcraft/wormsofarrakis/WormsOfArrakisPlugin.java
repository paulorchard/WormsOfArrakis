package com.paulorchard.islandcraft.wormsofarrakis;

import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.util.Config;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;

public class WormsOfArrakisPlugin extends JavaPlugin {

    /** The vanilla sand blocks (Server/Item/Items/Soil/Sand), used when Dunes of Arrakis is not installed. */
    static final String[] VANILLA_SAND = {"Soil_Sand", "Soil_Sand_Ashen", "Soil_Sand_Red", "Soil_Sand_White"};
    static final String DUNES_SAND = "Arrakis_Sand";

    private static WormsOfArrakisPlugin instance;

    // Must be created before setup(); the server loads it from disk in between.
    private final Config<WormsOfArrakisConfig> config =
            withConfig(WormsOfArrakisConfig.FILE_NAME, WormsOfArrakisConfig.CODEC);

    private final WormTestSystem testSystem = new WormTestSystem();
    private final AggroManager aggro = new AggroManager(config::get, this::sandBlockIds);
    private final WormEffects effects = new WormEffects(() -> config.get(), this::sandBlockIds, testSystem);
    private WormCommand wormCommand;

    public WormsOfArrakisPlugin(JavaPluginInit init) {
        super(init);
        instance = this;
    }

    public static WormsOfArrakisPlugin get() {
        return instance;
    }

    WormEffects effects() {
        return effects;
    }

    WormsOfArrakisConfig config() {
        return config.get();
    }

    AggroManager aggro() {
        return aggro;
    }

    @Override
    protected void setup() {
        writeConfig();
        getEntityStoreRegistry().registerSystem(testSystem);
        getEntityStoreRegistry().registerSystem(new WormBrainSystem(aggro, effects));
        getCommandRegistry().registerCommand(new WormTestCommand(testSystem, this::sandBlockIds));
        wormCommand = new WormCommand(aggro, config::get, effects, testSystem);
        getCommandRegistry().registerCommand(wormCommand);
        WormEvents.get().addListener(new WormEventLog(aggro, config::get, () -> wormCommand.getPermission()));
        WormEvents.get().addListener(effects);
        getEventRegistry().registerGlobal(PlayerDisconnectEvent.class, event -> {
            // A client that is gone needs no restoring, but a test job must not keep running for it.
            testSystem.dropFor(event.getPlayerRef().getUuid());
            aggro.forget(event.getPlayerRef().getUuid());
            effects.forget(event.getPlayerRef().getUuid());
        });
    }

    /** Assets are loaded by now, so say which sand blocks this game has. */
    @Override
    protected void start() {
        getLogger().at(Level.INFO).log("Sand block ids in this game: %s", Arrays.toString(sandBlockIds().toArray()));
    }

    /** Restores every test effect and ends every worm event before the plugin goes. */
    @Override
    protected void shutdown() {
        effects.restoreAll();
        testSystem.abortAll();
        WormEvents.get().stop(null);
    }

    /**
     * Numeric ids of the blocks that count as sand. A non-empty SandBlocks config is used as written; otherwise
     * Arrakis_Sand when Dunes of Arrakis is installed, else the vanilla sand blocks. Ids that do not exist are
     * ignored. Read on use, so assets are loaded.
     */
    Set<Integer> sandBlockIds() {
        String[] names = config.get().getSandBlocks();
        if (names == null || names.length == 0) {
            boolean dunes = BlockType.getAssetMap().getIndex(DUNES_SAND) != Integer.MIN_VALUE;
            names = dunes ? new String[] {DUNES_SAND} : VANILLA_SAND;
        }
        Set<Integer> ids = new HashSet<>();
        for (String name : names) {
            int index = BlockType.getAssetMap().getIndex(name);
            if (index != Integer.MIN_VALUE) {
                ids.add(index);
            }
        }
        return ids;
    }

    /** The server reads the config file but never creates it, so write it back on every start. */
    private void writeConfig() {
        Path file = getDataDirectory().resolve(WormsOfArrakisConfig.FILE_NAME + ".json");
        try {
            Files.createDirectories(getDataDirectory());
            config.save().join();
        } catch (Exception e) {
            getLogger().at(Level.WARNING).withCause(e).log("Could not write config to %s", file);
        }
    }
}
