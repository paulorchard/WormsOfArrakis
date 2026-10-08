package com.paulorchard.islandcraft.wormsofarrakis;

import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

/** Ticks the aggro manager, the worm events and then the per-player effect fades once per world tick. */
public final class WormBrainSystem extends TickingSystem<EntityStore> {

    private final AggroManager manager;
    private final WormEffects effects;

    WormBrainSystem(AggroManager manager, WormEffects effects) {
        this.manager = manager;
        this.effects = effects;
    }

    @Override
    public void tick(float dt, int index, Store<EntityStore> store) {
        World world = store.getExternalData().getWorld();
        effects.setDt(dt);
        manager.tick(world, store, dt);
        effects.tick(world, store, dt);
    }
}
