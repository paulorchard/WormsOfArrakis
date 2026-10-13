package com.paulorchard.islandcraft.wormsofarrakis;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.logging.Level;

/**
 * The built-in listener: writes every phase change and end to the server log, and to operators in chat when
 * DebugChat is on.
 */
final class WormEventLog implements WormEventListener {

    private static final String LANG = "server.wormsOfArrakis.event.";

    private final AggroManager manager;
    private final Supplier<WormsOfArrakisConfig> config;
    private final Supplier<String> operatorPermission;

    WormEventLog(AggroManager manager, Supplier<WormsOfArrakisConfig> config, Supplier<String> operatorPermission) {
        this.manager = manager;
        this.config = config;
        this.operatorPermission = operatorPermission;
    }

    static String name(AggroManager manager, UUID id) {
        if (id == null) {
            return "nobody";
        }
        BeingAggro a = manager.state(id);
        if (a != null && a.ref != null) {
            return a.ref.getUsername();
        }
        String label = a != null && a.label != null ? a.label + " " : "";
        return (a != null ? a.kind.name().toLowerCase(Locale.ROOT) + " " : "") + label + id.toString().substring(0, 8);
    }

    static String names(AggroManager manager, List<UUID> ids) {
        List<String> list = new ArrayList<>();
        for (UUID id : ids) {
            list.add(name(manager, id));
        }
        return String.join(", ", list);
    }

    @Override
    public void onPhaseChange(WormEvent event, WormPhase old, WormPhase now) {
        String target = name(manager, event.getTarget());
        String seconds = String.format(Locale.ROOT, "%.0f", event.getSecondsLeft());
        if (old == null) {
            log("[Worm #%d] started in %s by %s for list [%s], stalking for %ss", event.getId(),
                    event.getWorldName(), name(manager, event.getTrigger()), names(manager, event.getList()), seconds);
            chat(Message.translation(LANG + "start").param("id", String.valueOf(event.getId()))
                    .param("group", names(manager, event.getList())).param("target", name(manager, event.getTrigger()))
                    .param("seconds", seconds));
            return;
        }
        log("[Worm #%d] %s -> %s, target %s, %ss", event.getId(), old, now, target, seconds);
        chat(Message.translation(LANG + "phase").param("id", String.valueOf(event.getId()))
                .param("old", old.name()).param("new", now.name()).param("target", target).param("seconds", seconds));
    }

    @Override
    public void onMemberAdded(WormEvent event, UUID being) {
        log("[Worm #%d] %s joined the list", event.getId(), name(manager, being));
    }

    @Override
    public void onMemberRemoved(WormEvent event, UUID being, WormEvent.Leave why) {
        log("[Worm #%d] %s left the list (%s)", event.getId(), name(manager, being), why);
    }

    @Override
    public void onEnd(WormEvent event, WormEndReason reason) {
        log("[Worm #%d] ended: %s", event.getId(), reason);
        chat(Message.translation(LANG + "end").param("id", String.valueOf(event.getId())).param("reason", reason.name()));
    }

    private static void log(String format, Object... args) {
        // The game logger formats only a single argument, so format here.
        WormsOfArrakisPlugin.get().getLogger().at(Level.INFO).log("%s", String.format(Locale.ROOT, format, args));
    }

    private void chat(Message message) {
        if (!config.get().isDebugChat()) {
            return;
        }
        String permission = operatorPermission.get();
        for (PlayerRef player : Universe.get().getPlayers()) {
            if (permission == null || player.hasPermission(permission)) {
                player.sendMessage(message);
            }
        }
    }
}
