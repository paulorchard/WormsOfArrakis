package com.paulorchard.islandcraft.wormsofarrakis;

import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractWorldCommand;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.ArrayList;
import java.util.List;

/**
 * Like {@link Positional} but for commands that also work from the console: trailing arguments are optional and
 * positional, one usage variant per argument count, and the runner gets only the strings that were typed.
 */
final class WorldPositional extends AbstractWorldCommand {

    interface Runner {
        void run(CommandContext context, World world, Store<EntityStore> store, String[] args);
    }

    private final Runner runner;
    private final List<RequiredArg<String>> args = new ArrayList<>();

    private WorldPositional(String name, String description, Runner runner) {
        super(name, description);
        this.runner = runner;
    }

    private WorldPositional(String description, Runner runner, String[] argNames) {
        super(description);
        this.runner = runner;
        for (String argName : argNames) {
            args.add(withRequiredArg(argName, description, ArgTypes.STRING));
        }
    }

    /** The command itself takes no arguments; each prefix of {@code argNames} is a variant. */
    static WorldPositional build(String name, String description, Runner runner, String... argNames) {
        WorldPositional main = new WorldPositional(name, description, runner);
        for (int count = 1; count <= argNames.length; count++) {
            String[] first = new String[count];
            System.arraycopy(argNames, 0, first, 0, count);
            main.addUsageVariant(new WorldPositional(description, runner, first));
        }
        return main;
    }

    @Override
    protected void execute(CommandContext context, World world, Store<EntityStore> store) {
        String[] values = new String[args.size()];
        for (int i = 0; i < values.length; i++) {
            values[i] = args.get(i).get(context);
        }
        runner.run(context, world, store, values);
    }
}
