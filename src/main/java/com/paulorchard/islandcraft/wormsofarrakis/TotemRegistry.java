package com.paulorchard.islandcraft.wormsofarrakis;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The deployable ids that count as totems: decoys that start with a lot of aggro and draw the worm. The engine has no
 * totem tag, so a totem is defined here, by exact id. Filled from the config at start-up and open to other mods through
 * {@link WormAggro#registerTotem}. All registered ids behave the same way.
 */
final class TotemRegistry {

    private final Set<String> ids = ConcurrentHashMap.newKeySet();

    boolean register(String id) {
        return id != null && !id.isBlank() && ids.add(id);
    }

    boolean unregister(String id) {
        return id != null && ids.remove(id);
    }

    boolean contains(String id) {
        return id != null && ids.contains(id);
    }

    void addAll(Collection<String> more) {
        if (more != null) {
            more.forEach(this::register);
        }
    }

    /** Sorted, for display. */
    List<String> ids() {
        return new ArrayList<>(new TreeSet<>(ids));
    }
}
