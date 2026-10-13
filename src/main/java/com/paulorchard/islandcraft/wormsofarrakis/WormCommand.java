package com.paulorchard.islandcraft.wormsofarrakis;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.NameMatching;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * /worm status | aggro [player] | add player amount | trigger [player|self] [stalkSeconds] [lockSeconds] |
 * stop [player] | ignore player | preview | breach | sink | disturb. No permission group is set, so only operators can run it.
 */
public class WormCommand extends AbstractCommandCollection {

    private static final String LANG = "server.commands.worm.";

    private final AggroManager manager;
    private final Supplier<WormsOfArrakisConfig> config;
    private final WormEffects effects;
    private final WormTestSystem jobs;
    private final DisturbanceManager disturbances;
    private final WormEvents events = WormEvents.get();

    public WormCommand(AggroManager manager, Supplier<WormsOfArrakisConfig> config, WormEffects effects,
                       WormTestSystem jobs, DisturbanceManager disturbances) {
        super("worm", LANG + "desc");
        this.manager = manager;
        this.config = config;
        this.effects = effects;
        this.jobs = jobs;
        this.disturbances = disturbances;
        addSubCommand(WorldPositional.build("status", LANG + "status.desc", this::status));
        addSubCommand(WorldPositional.build("aggro", LANG + "aggro.desc", this::aggro, "player"));
        addSubCommand(WorldPositional.build("add", LANG + "add.desc", this::add, "player", "amount"));
        addSubCommand(WorldPositional.build("trigger", LANG + "trigger.desc", this::trigger, "player", "stalkSeconds",
                "lockSeconds"));
        addSubCommand(WorldPositional.build("preview", LANG + "preview.desc", this::preview, "effect", "seconds"));
        addSubCommand(WorldPositional.build("breach", LANG + "breach.desc", this::breach, "target"));
        addSubCommand(WorldPositional.build("sink", LANG + "sink.desc", this::sink, "wait", "depth", "delay"));
        addSubCommand(WorldPositional.build("disturb", LANG + "disturb.desc", this::disturb, "action", "arg"));
        addSubCommand(WorldPositional.build("totem", LANG + "totem.desc", this::totem, "action", "id"));
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
            Vector3d o = e.getOrigin();
            BeingAggro t = manager.state(e.getTrigger());
            int players = 0;
            for (UUID id : e.getList()) {
                BeingAggro m = manager.state(id);
                if (m != null && m.kind == BeingKind.PLAYER) {
                    players++;
                }
            }
            String why = e.isGuaranteed() ? "entity trigger, " + num(config.get().getEntityStalkSeconds()) + "s whatever the list"
                    : "player trigger, " + players + " player(s) in the list";
            say(context, "status.event", "id", String.valueOf(e.getId()), "phase", e.getPhase().name(),
                    "seconds", num(e.getSecondsLeft()), "stalk", num(e.getStalkSeconds()), "why", why,
                    "trigger", WormEventLog.name(manager, e.getTrigger()) + (t == null ? "" : " [" + t.kind + "]"),
                    "target", e.getTarget() == null ? "not chosen yet" : WormEventLog.name(manager, e.getTarget()),
                    "guaranteed", e.isGuaranteed() ? " (guaranteed)" : "",
                    "world", e.getWorldName(), "origin", String.format(Locale.ROOT, "%.0f %.0f %.0f", o.x, o.y, o.z));
            for (UUID id : e.getList()) {
                BeingAggro a = manager.state(id);
                say(context, "status.member", "name", WormEventLog.name(manager, id), "kind", a == null ? "gone" : a.kind.name(),
                        "score", num(a == null ? 0 : a.score()), "sand", a != null && a.onSand ? "on sand" : "off sand",
                        "factor", (a == null ? EffectMultipliers.Result.NONE : a.effectFactor).text());
            }
        }
        MobTracker mobs = manager.mobs();
        say(context, "status.tracking", "mobs", String.valueOf(mobs.tracked), "hostile", String.valueOf(mobs.hostile),
                "scanned", String.valueOf(mobs.scanned), "ms", String.format(Locale.ROOT, "%.2f", mobs.millis),
                "max", String.format(Locale.ROOT, "%.2f", mobs.maxMillis), "totems", String.valueOf(manager.totems().active()),
                "landed", String.valueOf(manager.totems().landed()));
        PlayerRef self = caller(context, store);
        if (self != null) {
            describe(context, self, true);
        }
    }

    /** The score, the event it is in or the list it would start (display only), and when not {@code brief} what feeds it. */
    private void describe(CommandContext context, PlayerRef player, boolean brief) {
        BeingAggro a = manager.state(player.getUuid());
        double score = a == null ? 0 : a.score();
        double threshold = config.get().getAggroThreshold();
        WormEvent inEvent = a == null ? null : a.event;
        String listText;
        if (inEvent != null) {
            listText = "in event #" + inEvent.getId() + " (" + inEvent.getPhase() + ")";
        } else {
            List<ListScan.Candidate> list = manager.listOf(player.getUuid());
            if (list == null || !a.eligible) {
                listText = "not eligible";
            } else {
                List<UUID> ids = new ArrayList<>();
                for (ListScan.Candidate c : list) {
                    ids.add(c.id());
                }
                listText = "of " + num(threshold) + "; would list " + ids.size() + " [" + WormEventLog.names(manager, ids) + "]";
            }
        }
        say(context, "aggro.line", "player", player.getUsername(), "score", num(score), "group", listText,
                "onSand", String.valueOf(a != null && a.onSand), "speed", num(a == null ? 0 : a.speed),
                "ignored", manager.isIgnored(player.getUuid()) ? " (ignored)" : "");
        if (!brief && a != null) {
            Map<String, Double> feeding = a.feeding();
            List<String> parts = new ArrayList<>();
            feeding.forEach((k, v) -> parts.add(k + " " + num(v)));
            say(context, "aggro.feeding", "sources", parts.isEmpty() ? "nothing" : String.join(", ", parts));
            say(context, "aggro.factor", "factor", a.effectFactor.text());
        }
    }

    // ------------------------------------------------------------------ aggro, add, ignore

    private void aggro(CommandContext context, World world, Store<EntityStore> store, String[] args) {
        if (args.length > 0 && (args[0].equalsIgnoreCase("mobs") || args[0].equalsIgnoreCase("totems"))) {
            beings(context, world, store, args[0].equalsIgnoreCase("mobs") ? BeingKind.MOB : BeingKind.TOTEM);
            return;
        }
        PlayerRef player = resolve(context, store, args, 0);
        if (player != null) {
            describe(context, player, false);
        }
    }

    /** The mobs or totems tracked near the caller (or in the world, from the console), nearest first, with their scores. */
    private void beings(CommandContext context, World world, Store<EntityStore> store, BeingKind kind) {
        PlayerRef self = caller(context, store);
        Vector3d from = self == null ? null : WormEffects.position(store, self);
        List<BeingAggro> list = new ArrayList<>();
        for (BeingAggro b : manager.core().all()) {
            if (b.kind == kind && world.getName().equals(b.world)) {
                list.add(b);
            }
        }
        list.sort((x, y) -> Double.compare(from == null ? 0 : x.position.distance(from), from == null ? 0 : y.position.distance(from)));
        say(context, "aggro.beings", "count", String.valueOf(list.size()), "kind", kind == BeingKind.MOB ? "hostile mobs" : "totems");
        int shown = 0;
        for (BeingAggro b : list) {
            if (shown++ >= 25) {
                break;
            }
            say(context, "aggro.being", "name", WormEventLog.name(manager, b.id), "score", num(b.score()),
                    "distance", from == null ? "?" : num(b.position.distance(from)), "sand", b.onSand ? "on sand" : "off sand",
                    "event", b.event == null ? "free" : "in event #" + b.event.getId());
        }
    }

    /** The nearest tracked being of this kind (and role or deployable id, if given) to the caller; null from the console. */
    private BeingAggro nearest(CommandContext context, World world, Store<EntityStore> store, BeingKind kind, String label) {
        PlayerRef self = caller(context, store);
        Vector3d from = self == null ? null : WormEffects.position(store, self);
        if (from == null) {
            say(context, "needPlayer");
            return null;
        }
        BeingAggro best = null;
        for (BeingAggro b : manager.core().all()) {
            if (b.kind != kind || !b.eligible || !world.getName().equals(b.world) || b.event != null
                    || (label != null && !label.equalsIgnoreCase(b.label))) {
                continue;
            }
            if (best == null || b.position.distance(from) < best.position.distance(from)) {
                best = b;
            }
        }
        if (best == null) {
            say(context, "nearest.none", "kind", kind.name().toLowerCase(Locale.ROOT));
        }
        return best;
    }

    private void add(CommandContext context, World world, Store<EntityStore> store, String[] args) {
        if (args.length < 2) {
            say(context, "add.usage");
            return;
        }
        if (args[0].equalsIgnoreCase("mob") || args[0].equalsIgnoreCase("totem")) {
            BeingAggro b = nearest(context, world, store, args[0].equalsIgnoreCase("mob") ? BeingKind.MOB : BeingKind.TOTEM, null);
            if (b != null) {
                double amount = parse(args[1], 0);
                manager.core().add(b, amount, "command");
                say(context, "add.done", "player", WormEventLog.name(manager, b.id), "amount", num(amount), "score", num(b.score()));
            }
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
        if (args.length > 0 && (args[0].equalsIgnoreCase("mob") || args[0].equalsIgnoreCase("totem"))) {
            boolean mob = args[0].equalsIgnoreCase("mob");
            BeingAggro b = nearest(context, world, store, mob ? BeingKind.MOB : BeingKind.TOTEM, args.length > 1 ? args[1] : null);
            if (b != null) {
                WormEvent event = manager.trigger(b, false, Double.NaN, Double.NaN);
                say(context, "trigger.done", "id", String.valueOf(event.getId()), "target", WormEventLog.name(manager, b.id),
                        "stalk", num(event.getStalkSeconds()), "lock", num(event.getLockSeconds()),
                        "count", String.valueOf(event.getList().size()));
            }
            return;
        }
        trigger(context, store, args, false);
    }

    /** /worm totem add|remove|list [deployableId]: the registry of totem ids, for this session only. */
    private void totem(CommandContext context, World world, Store<EntityStore> store, String[] args) {
        String action = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : "list";
        TotemRegistry registry = manager.totemRegistry();
        switch (action) {
            case "add" -> {
                if (args.length < 2) {
                    say(context, "totem.usage");
                    return;
                }
                say(context, registry.register(args[1]) ? "totem.added" : "totem.already", "id", args[1]);
            }
            case "remove" -> {
                if (args.length < 2) {
                    say(context, "totem.usage");
                    return;
                }
                say(context, registry.unregister(args[1]) ? "totem.removed" : "totem.unknown", "id", args[1]);
            }
            case "list" -> say(context, "totem.list", "ids", String.join(", ", registry.ids()));
            default -> say(context, "totem.usage");
        }
    }

    /** {@code solo} lists only that player (for /worm breach); otherwise the list is scanned as for a real trigger. */
    private void trigger(CommandContext context, Store<EntityStore> store, String[] args, boolean solo) {
        PlayerRef player = resolve(context, store, args, 0);
        if (player == null) {
            return;
        }
        UUID id = player.getUuid();
        WormEvent existing = events.eventOf(id);
        if (existing != null) {
            say(context, "trigger.busy", "player", player.getUsername(), "phase", existing.getPhase().name());
            return;
        }
        BeingAggro a = manager.state(id);
        if (a == null || !a.eligible || a.world == null) {
            say(context, "trigger.ineligible", "player", player.getUsername());
            return;
        }
        double stalk = args.length > 1 ? parse(args[1], Double.NaN) : Double.NaN;
        double lock = args.length > 2 ? parse(args[2], Double.NaN) : Double.NaN;
        WormEvent event = manager.trigger(a, solo, stalk, lock);
        say(context, "trigger.done", "id", String.valueOf(event.getId()), "target", player.getUsername(),
                "stalk", num(event.getStalkSeconds()), "lock", num(event.getLockSeconds()),
                "count", String.valueOf(event.getList().size()));
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
        trigger(context, store, new String[] {who, "0.05", "0.05"}, true);
    }

    /** /worm sink [wait] [depth] [delay]: the pull under the sand, on you alone, to watch. */
    private void sink(CommandContext context, World world, Store<EntityStore> store, String[] args) {
        PlayerRef self = caller(context, store);
        if (self == null) {
            say(context, "needPlayer");
            return;
        }
        WormsOfArrakisConfig cfg = config.get();
        double wait = args.length > 0 ? parse(args[0], 0) : cfg.getZoomSeconds() + 1.0;
        double depth = args.length > 1 ? parse(args[1], cfg.getBreachSinkDepth()) : cfg.getBreachSinkDepth();
        // By default the same wait at the bottom as in the breach: from the end of the sink to BreachDiveEnd + BreachKillDelay.
        double breachDelay = Math.max(0, cfg.getBreachDiveEnd() + cfg.getBreachKillDelay() - (cfg.getBreachSinkAt() + cfg.getBreachSinkSeconds()));
        double delay = args.length > 2 ? parse(args[2], breachDelay) : breachDelay;
        jobs.stop(self.getUuid(), "sinktest");
        jobs.add(new SinkTestJob(world, self, config, depth, Math.max(0, wait), Math.max(0, delay)));
        say(context, "sink.start", "wait", num(wait), "depth", num(depth), "delay", num(delay));
    }

    /** /worm disturb [here] | status | stop [here|all] | fallback on|off: the sand disturbance (see SandDisturbance). */
    private void disturb(CommandContext context, World world, Store<EntityStore> store, String[] args) {
        String action = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : "here";
        String arg = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "";
        switch (action) {
            case "here" -> {
                PlayerRef self = caller(context, store);
                if (self == null) {
                    say(context, "needPlayer");
                    return;
                }
                if (WormsOfArrakisPlugin.get().arrakisSandId() == WormsOfArrakisPlugin.NOT_INSTALLED) {
                    say(context, "disturb.noDunes");
                    return;
                }
                Vector3d at = WormEffects.position(store, self);
                int cx = ChunkUtil.chunkCoordinate((int) Math.floor(at.x));
                int cz = ChunkUtil.chunkCoordinate((int) Math.floor(at.z));
                SandDisturbance d = disturbances.startIn(world, store, cx, cz);
                say(context, d == null ? "disturb.busy" : "disturb.started", "chunk", cx + "," + cz);
            }
            case "status" -> {
                List<String> lines = disturbances.status();
                if (lines.isEmpty()) {
                    say(context, "disturb.none");
                }
                for (String line : lines) {
                    say(context, "disturb.line", "line", line);
                }
                say(context, "disturb.forced", "state", disturbances.isForced() ? "on" : "off");
            }
            case "stop" -> {
                int n;
                if (arg.equals("all")) {
                    n = disturbances.stop(null, null, null, "/worm disturb stop all");
                } else {
                    PlayerRef self = caller(context, store);
                    Vector3d at = self == null ? null : WormEffects.position(store, self);
                    if (at == null) {
                        say(context, "needPlayer");
                        return;
                    }
                    n = disturbances.stop(world, ChunkUtil.chunkCoordinate((int) Math.floor(at.x)),
                            ChunkUtil.chunkCoordinate((int) Math.floor(at.z)), "/worm disturb stop");
                }
                say(context, "disturb.stopped", "count", String.valueOf(n));
            }
            case "fallback" -> {
                if (!arg.equals("on") && !arg.equals("off")) {
                    say(context, "disturb.usage");
                    return;
                }
                disturbances.setForced(arg.equals("on"));
                say(context, "disturb.forced", "state", arg);
            }
            default -> say(context, "disturb.usage");
        }
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
