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

    private static WormsOfArrakisPlugin instance;

    // Must be created before setup(); the server loads it from disk in between.
    private final Config<WormsOfArrakisConfig> config =
            withConfig(WormsOfArrakisConfig.FILE_NAME, WormsOfArrakisConfig.CODEC);

    private final WormTestSystem testSystem = new WormTestSystem();

    public WormsOfArrakisPlugin(JavaPluginInit init) {
        super(init);
        instance = this;
    }

    public static WormsOfArrakisPlugin get() {
        return instance;
    }

    @Override
    protected void setup() {
        writeConfig();
        getEntityStoreRegistry().registerSystem(testSystem);
        getCommandRegistry().registerCommand(new WormTestCommand(testSystem, this::sandBlockIds));
        // A client that is gone needs no restoring, but the job must not keep running for it.
        getEventRegistry().registerGlobal(PlayerDisconnectEvent.class,
                event -> testSystem.dropFor(event.getPlayerRef().getUuid()));
    }

    /** Assets are loaded by now, so say which of the configured sand blocks exist. */
    @Override
    protected void start() {
        getLogger().at(Level.INFO).log("Sand block ids in this game: %s", Arrays.toString(sandBlockIds().toArray()));
    }

    /** Restores every fake block, movement setting, weather override and test entity before the plugin goes. */
    @Override
    protected void shutdown() {
        testSystem.abortAll();
    }

    /** Numeric ids of the configured sand blocks that exist in this game. Read on use, so assets are loaded. */
    Set<Integer> sandBlockIds() {
        Set<Integer> ids = new HashSet<>();
        for (String name : config.get().getSandBlocks()) {
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
