package com.paulorchard.islandcraft.wormsofarrakis;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageCause;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageSystems;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatValue;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.logging.Level;

/** The one place that kills (or badly hurts) someone the worm has caught, for the breach and for /worm sink. */
final class WormDevour {

    private WormDevour() {
    }

    /** What the worm does to someone it has caught: killed with nothing dropped, or (DevourKills off) badly hurt. */
    static void apply(Store<EntityStore> store, WormsOfArrakisConfig cfg, Ref<EntityStore> ref) {
        try {
            DamageCause cause = WormDevourDamage.cause();
            if (cause == null) {
                WormsOfArrakisPlugin.get().getLogger().at(Level.SEVERE).log("%s", "Damage cause "
                        + WormDevourDamage.CAUSE_ID + " is not loaded, so the worm cannot hurt anyone");
                return;
            }
            if (cfg.isDevourKills()) {
                if (!cfg.isDevourDropsItems()) {
                    clearInventory(store, ref);
                }
                DamageSystems.executeDamage(ref, store, new Damage(WormDevourDamage.SOURCE, cause, 1.0e6f));
            } else {
                EntityStatMap stats = store.getComponent(ref, EntityStatMap.getComponentType());
                EntityStatValue health = stats == null ? null : stats.get(DefaultEntityStatTypes.getHealth());
                if (health != null) {
                    float hp = health.get();
                    float amount = (float) Math.max(0, Math.min(hp * cfg.getDevourHurtFraction(), hp - 1));
                    DamageSystems.executeDamage(ref, store, new Damage(WormDevourDamage.SOURCE, cause, amount));
                }
            }
        } catch (Throwable t) {
            WormsOfArrakisPlugin.get().getLogger().at(Level.WARNING).withCause(t).log("The worm could not hurt someone it caught");
        }
    }

    /** Empties every inventory section so that nothing is dropped and the respawn is bare. */
    private static void clearInventory(Store<EntityStore> store, Ref<EntityStore> ref) {
        clear(store, ref, InventoryComponent.Hotbar.getComponentType());
        clear(store, ref, InventoryComponent.Storage.getComponentType());
        clear(store, ref, InventoryComponent.Armor.getComponentType());
        clear(store, ref, InventoryComponent.Utility.getComponentType());
        clear(store, ref, InventoryComponent.Tool.getComponentType());
        clear(store, ref, InventoryComponent.Backpack.getComponentType());
    }

    private static void clear(Store<EntityStore> store, Ref<EntityStore> ref,
                       com.hypixel.hytale.component.ComponentType<EntityStore, ? extends InventoryComponent> type) {
        InventoryComponent section = store.getComponent(ref, type);
        if (section != null) {
            section.getInventory().clear();
        }
    }
}
