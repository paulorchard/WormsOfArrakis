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
 * The built-in listener: writes every phase change, retarget and end to the server log, to operators in chat when
 * DebugChat is on, and puts a fizzled group back to a share of the threshold.
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
        PlayerAggro a = manager.state(id);
        return a != null && a.ref != null ? a.ref.getUsername() : id.toString().substring(0, 8);
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
            log("[Worm #%d] started in %s for group [%s], target %s, stalking for %ss", event.getId(),
                    event.getWorldName(), names(manager, event.getGroup()), target, seconds);
            chat(Message.translation(LANG + "start").param("id", String.valueOf(event.getId()))
                    .param("group", names(manager, event.getGroup())).param("target", target)
                    .param("seconds", seconds));
            return;
        }
        log("[Worm #%d] %s -> %s, target %s, %ss", event.getId(), old, now, target, seconds);
        chat(Message.translation(LANG + "phase").param("id", String.valueOf(event.getId()))
                .param("old", old.name()).param("new", now.name()).param("target", target).param("seconds", seconds));
    }

    @Override
    public void onRetarget(WormEvent event, UUID oldTarget, UUID newTarget) {
        log("[Worm #%d] retarget %s -> %s", event.getId(), name(manager, oldTarget), name(manager, newTarget));
        chat(Message.translation(LANG + "retarget").param("id", String.valueOf(event.getId()))
                .param("old", name(manager, oldTarget)).param("new", name(manager, newTarget)));
    }

    @Override
    public void onEnd(WormEvent event, WormEndReason reason) {
        log("[Worm #%d] ended: %s", event.getId(), reason);
        chat(Message.translation(LANG + "end").param("id", String.valueOf(event.getId())).param("reason", reason.name()));
        if (reason == WormEndReason.FIZZLED && !event.isForced()) {
            manager.agitate(event);
        }
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
