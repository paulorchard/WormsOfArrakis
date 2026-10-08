package com.paulorchard.islandcraft.wormsofarrakis;

import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageCause;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

/** The worm as a source of damage, so the death message can read "was devoured by a sandworm". */
public final class WormDevourDamage implements Damage.Source {

    /** Damage cause asset: no armour or resistance reduction, no vanilla durability loss. */
    public static final String CAUSE_ID = "Arrakis_Worm_Devour";

    public static final WormDevourDamage SOURCE = new WormDevourDamage();

    private WormDevourDamage() {
    }

    /** The cause asset, or null if the asset pack did not load. */
    public static DamageCause cause() {
        return DamageCause.getAssetMap().getAsset(CAUSE_ID);
    }

    @Override
    public Message getDeathMessage(Damage damage, Ref<EntityStore> victim, ComponentAccessor<EntityStore> accessor) {
        PlayerRef player = accessor.getComponent(victim, PlayerRef.getComponentType());
        return Message.translation("server.wormsOfArrakis.death").param("player", player != null ? player.getUsername() : "?");
    }
}
