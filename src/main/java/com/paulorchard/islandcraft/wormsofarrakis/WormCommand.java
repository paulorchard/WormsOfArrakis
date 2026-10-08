package com.paulorchard.islandcraft.wormsofarrakis;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.NameMatching;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * /worm status | aggro [player] | add player amount | trigger [player|self] [stalkSeconds] [lockSeconds] |
 * stop [player] | ignore player. No permission group is set, so only operators can run it.
 */
public class WormCommand extends AbstractCommandCollection {

    private static final String LANG = "server.commands.worm.";

    private final AggroManager manager;
    private final Supplier<WormsOfArrakisConfig> config;
    private final WormEffects effects;
    private final WormTestSystem jobs;
    private final WormEvents events = WormEvents.get();

    public WormCommand(AggroManager manager, Supplier<WormsOfArrakisConfig> config, WormEffects effects,
                       WormTestSystem jobs) {
        super("worm", LANG + "desc");
        this.manager = manager;
        this.config = config;
        this.effects = effects;
        this.jobs = jobs;
        addSubCommand(WorldPositional.build("status", LANG + "status.desc", this::status));
        addSubCommand(WorldPositional.build("aggro", LANG + "aggro.desc", this::aggro, "player"));
        addSubCommand(WorldPositional.build("add", LANG + "add.desc", this::add, "player", "amount"));
        addSubCommand(WorldPositional.build("trigger", LANG + "trigger.desc", this::trigger, "player", "stalkSeconds",
                "lockSeconds"));
        addSubCommand(WorldPositional.build("preview", LANG + "preview.desc", this::preview, "effect", "seconds"));
        addSubCommand(WorldPositional.build("breach", LANG + "breach.desc", this::breach, "target"));
        addSubCommand(WorldPositional.build("stop", LANG + "stop.desc", this::stop, "player"));
        addSubCommand(WorldPositional.build("ignore", LANG + "ignore.desc", this::ignore, "player"));
    }

    private static String num(double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }

    private static void say(CommandContext context, String key, String... params) {
        Message message = Message.translation(LANG + key);
        for (int i = 0; i + 1 < params.length; i += 2) {
            message = message.param(params[i], params[i + 1]);
        }
        context.sendMessage(message);
    }

    /** The caller as a player, or null from the console. */
    private static PlayerRef caller(CommandContext context, Store<EntityStore> store) {
        if (!context.isPlayer()) {
            return null;
        }
        Ref<EntityStore> ref = context.senderAsPlayerRef();
        return ref == null ? null : store.getComponent(ref, PlayerRef.getComponentType());
    }

    /** "self", nothing, or a name. Reports an unknown name and returns null. */
    private PlayerRef resolve(CommandContext context, Store<EntityStore> store, String[] args, int index) {
        if (index >= args.length || args[index].equalsIgnoreCase("self")) {
            PlayerRef self = caller(context, store);
            if (self == null) {
                say(context, "needPlayer");
            }
            return self;
        }
        PlayerRef found = Universe.get().getPlayer(args[index], NameMatching.DEFAULT);
        if (found == null) {
            say(context, "noPlayer", "name", args[index]);
        }
        return found;
    }

    // ------------------------------------------------------------------ status

    private void status(CommandContext context, World world, Store<EntityStore> store, String[] args) {
        List<WormEvent> all = events.all();
        if (all.isEmpty()) {
            say(context, "status.none");
        }
        for (WormEvent e : all) {
            say(context, "status.event", "id", String.valueOf(e.getId()), "phase", e.getPhase().name(),
                    "seconds", num(e.getSecondsLeft()), "target", WormEventLog.name(manager, e.getTarget()),
                    "world", e.getWorldName(), "group", WormEventLog.names(manager, e.getGroup()),
                    "worm", position(e));
        }
        PlayerRef self = caller(context, store);
        if (self != null) {
            describe(context, self, true);
        }
    }

    private static String position(WormEvent e) {
        var p = e.getWormPosition();
        return String.format(Locale.ROOT, "%.0f %.0f %.0f", p.x, p.y, p.z);
    }

    /** The score, the group total and (when {@code brief} is false) what feeds it. */
    private void describe(CommandContext context, PlayerRef player, boolean brief) {
        PlayerAggro a = manager.state(player.getUuid());
        double score = a == null ? 0 : a.score();
        double threshold = config.get().getAggroThreshold();
        List<GroupFinder.Member> group = manager.groupOf(player.getUuid());
        WormEvent inEvent = events.eventOf(player.getUuid());
        String groupText = inEvent != null
                ? "in event #" + inEvent.getId() + " (" + inEvent.getPhase() + ")"
                : group == null ? "no group (not eligible)"
                : num(GroupFinder.total(group)) + " of " + num(threshold) + " with " + group.size() + " in group";
        say(context, "aggro.line", "player", player.getUsername(), "score", num(score), "group", groupText,
                "onSand", String.valueOf(a != null && a.onSand), "speed", num(a == null ? 0 : a.speed),
                "ignored", manager.isIgnored(player.getUuid()) ? " (ignored)" : "");
        if (!brief && a != null) {
            Map<String, Double> feeding = a.feeding();
            List<String> parts = new ArrayList<>();
            feeding.forEach((k, v) -> parts.add(k + " " + num(v)));
            say(context, "aggro.feeding", "sources", parts.isEmpty() ? "nothing" : String.join(", ", parts));
        }
    }

    // ------------------------------------------------------------------ aggro, add, ignore

    private void aggro(CommandContext context, World world, Store<EntityStore> store, String[] args) {
        PlayerRef player = resolve(context, store, args, 0);
        if (player != null) {
            describe(context, player, false);
        }
    }

    private void add(CommandContext context, World world, Store<EntityStore> store, String[] args) {
        if (args.length < 2) {
            say(context, "add.usage");
            return;
        }
        PlayerRef player = resolve(context, store, args, 0);
        if (player == null) {
            return;
        }
        double amount;
        try {
            amount = Double.parseDouble(args[1]);
        } catch (NumberFormatException e) {
            say(context, "add.usage");
            return;
        }
        manager.addAggro(player, amount, "command");
        say(context, "add.done", "player", player.getUsername(), "amount", num(amount),
                "score", num(manager.state(player.getUuid()).score()));
    }

    private void ignore(CommandContext context, World world, Store<EntityStore> store, String[] args) {
        if (args.length < 1) {
            say(context, "ignore.usage");
            return;
        }
        PlayerRef player = resolve(context, store, args, 0);
        if (player != null) {
            boolean now = manager.toggleIgnore(player.getUuid());
            say(context, now ? "ignore.on" : "ignore.off", "player", player.getUsername());
        }
    }

    // ------------------------------------------------------------------ preview

    /** /worm preview <effect|off> [seconds]: one effect on the caller alone, ramping up, to tune it. */
    private void preview(CommandContext context, World world, Store<EntityStore> store, String[] args) {
        PlayerRef player = caller(context, store);
        if (player == null) {
            say(context, "needPlayer");
            return;
        }
        String effect = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : "";
        jobs.stop(player.getUuid(), "preview");
        if (effect.equals("off")) {
            say(context, "preview.off");
            return;
        }
        if (!PreviewJob.EFFECTS.contains(effect)) {
            say(context, "preview.usage", "effects", String.join(", ", PreviewJob.EFFECTS));
            return;
        }
        double seconds = args.length > 1 ? parse(args[1], 10) : 10;
        jobs.add(new PreviewJob(effects, world, player, context.senderAsPlayerRef(), effect, seconds, config));
        say(context, "preview.start", "effect", effect, "seconds", num(seconds));
    }

    // ------------------------------------------------------------------ trigger, stop

    private void trigger(CommandContext context, World world, Store<EntityStore> store, String[] args) {
        PlayerRef player = resolve(context, store, args, 0);
        if (player == null) {
            return;
        }
        UUID id = player.getUuid();
        WormEvent existing = events.eventOf(id);
        if (existing != null && existing.isActive()) {
            say(context, "trigger.busy", "player", player.getUsername(), "phase", existing.getPhase().name());
            return;
        }
        if (existing != null) {
            events.stop(id); // /worm trigger ignores the cooldown
        }
        List<GroupFinder.Member> group = manager.groupOf(id);
        PlayerAggro a = manager.state(id);
        if (group == null || a == null) {
            say(context, "trigger.ineligible", "player", player.getUsername());
            return;
        }
        WormsOfArrakisConfig cfg = config.get();
        WormEvent.Timings base = manager.defaultTimings(cfg);
        double stalk = args.length > 1 ? parse(args[1], base.stalk()) : base.stalk();
        double lock = args.length > 2 ? parse(args[2], base.lock()) : base.lock();
        WormEvent.Timings timings = new WormEvent.Timings(stalk, lock, base.breach(), base.cooldown(),
                base.retargetKeepsClock(), base.ease());
        WormEvent event = manager.trigger(a.world, group, id, timings, true);
        say(context, "trigger.done", "id", String.valueOf(event.getId()), "target", player.getUsername(),
                "stalk", num(stalk), "lock", num(lock));
    }

    /** /worm breach [player|self|here]: straight to BREACH under a player, or the effect alone at the caller. */
    private void breach(CommandContext context, World world, Store<EntityStore> store, String[] args) {
        String who = args.length > 0 ? args[0] : "self";
        if (who.equalsIgnoreCase("here")) {
            PlayerRef self = caller(context, store);
            if (self == null) {
                say(context, "needPlayer");
                return;
            }
            effects.startBreach(world, store, null, self, config.get().getBreachSeconds());
            say(context, "breach.here");
            return;
        }
        trigger(context, world, store, new String[] {who, "0.05", "0.05"});
    }

    private static double parse(String text, double fallback) {
        try {
            return Double.parseDouble(text);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private void stop(CommandContext context, World world, Store<EntityStore> store, String[] args) {
        UUID who = null;
        if (args.length > 0) {
            PlayerRef player = resolve(context, store, args, 0);
            if (player == null) {
                return;
            }
            who = player.getUuid();
        }
        say(context, "stop.done", "count", String.valueOf(events.stop(who)));
    }
}
