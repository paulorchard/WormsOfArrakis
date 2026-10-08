package com.paulorchard.islandcraft.wormsofarrakis;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.ArrayList;
import java.util.List;

/**
 * A player command whose trailing arguments are optional and positional, done the way the game does it:
 * one command per argument count, each a usage variant of the first. Arguments reach the runner as strings,
 * only as many as were typed.
 */
final class Positional extends AbstractPlayerCommand {

    interface Runner {
        void run(CommandContext context, Store<EntityStore> store, Ref<EntityStore> ref, PlayerRef player,
                 World world, String[] args);
    }

    private final Runner runner;
    private final List<RequiredArg<String>> args = new ArrayList<>();

    private Positional(String name, String description, Runner runner) {
        super(name, description);
        this.runner = runner;
    }

    private Positional(String description, Runner runner, String[] argNames) {
        super(description);
        this.runner = runner;
        for (String argName : argNames) {
            args.add(withRequiredArg(argName, description, ArgTypes.STRING));
        }
    }

    static Positional build(String name, String description, Runner runner, String... argNames) {
        Positional main = new Positional(name, description, runner);
        for (int count = 1; count <= argNames.length; count++) {
            String[] first = new String[count];
            System.arraycopy(argNames, 0, first, 0, count);
            main.addUsageVariant(new Positional(description, runner, first));
        }
        return main;
    }

    @Override
    protected void execute(CommandContext context, Store<EntityStore> store, Ref<EntityStore> ref,
                           PlayerRef playerRef, World world) {
        String[] values = new String[args.size()];
        for (int i = 0; i < values.length; i++) {
            values[i] = args.get(i).get(context);
        }
        runner.run(context, store, ref, playerRef, world, values);
    }
}
