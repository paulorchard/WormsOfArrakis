package com.paulorchard.islandcraft.wormsofarrakis;

import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.server.core.asset.type.model.config.Model;
import com.hypixel.hytale.server.core.asset.type.model.config.ModelAsset;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.entity.component.HeadRotation;
import com.hypixel.hytale.server.core.modules.entity.component.Intangible;
import com.hypixel.hytale.server.core.modules.entity.component.Invulnerable;
import com.hypixel.hytale.server.core.modules.entity.component.ModelComponent;
import com.hypixel.hytale.server.core.modules.entity.component.PersistentModel;
import com.hypixel.hytale.server.core.modules.entity.component.PropComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.tracker.NetworkId;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.joml.Vector3d;

import java.util.UUID;

/**
 * The stand-in worm: a prop entity (a model with a transform, no NPC role, so no behaviour at all) that a script
 * moves every tick. It is spawned buried with its top at ground level and then loops: rise, sway, sink, travel
 * sideways underground, come back.
 */
final class WormJob implements WormTestSystem.Job {

    static final String MODEL_ID = "Arrakis_Worm_Placeholder";
    static final float CYCLE = 17.0f;
    static final float MAX_SECONDS = 600.0f;
    /** Body length divided by the height that shows above the sand: the rest stays buried. */
    static final double BODY_RATIO = 2.0;
    /** Height that shows above the sand at scale 1, in blocks. */
    static final double HEIGHT = 10.0;

    private final World world;
    private final PlayerRef player;
    private Ref<EntityStore> entity;
    private final Vector3d origin;
    private final double groundY;
    private final double height;
    private final double headingX;
    private final double headingZ;
    private final float baseYaw;
    private float elapsed;

    private WormJob(World world, PlayerRef player, Vector3d origin, double groundY, double height,
                    double headingX, double headingZ, float baseYaw) {
        this.world = world;
        this.player = player;
        this.origin = origin;
        this.groundY = groundY;
        this.height = height;
        this.headingX = headingX;
        this.headingZ = headingZ;
        this.baseYaw = baseYaw;
    }

    /**
     * Spawns the worm at the player's feet. Returns null if the model asset is not loaded.
     * @param persist true to let the game save it with its chunk (needs a PersistentModel), false to mark it NonSerialized
     */
    static WormJob spawn(World world, Store<EntityStore> store, PlayerRef player, Vector3d feet, Vector3d facing,
                         float scale, boolean persist) {
        ModelAsset asset = ModelAsset.getAssetMap().getAsset(MODEL_ID);
        if (asset == null) {
            return null;
        }
        double height = HEIGHT * scale;
        double hx = facing.x, hz = facing.z;
        double length = Math.hypot(hx, hz);
        if (length < 1e-6) {
            hx = 0;
            hz = 1;
        } else {
            hx /= length;
            hz /= length;
        }
        float yaw = (float) Math.atan2(-hx, -hz);
        WormJob job = new WormJob(world, player, new Vector3d(feet.x, feet.y, feet.z), feet.y, height, hx, hz, yaw);

        Vector3d buried = new Vector3d(feet.x, feet.y - height * BODY_RATIO, feet.z); // top level with the ground
        Rotation3f rotation = new Rotation3f(0, yaw, 0);
        Holder<EntityStore> holder = store.getRegistry().newHolder();
        holder.addComponent(NetworkId.getComponentType(), new NetworkId(store.getExternalData().takeNextNetworkId()));
        holder.addComponent(TransformComponent.getComponentType(), new TransformComponent(buried, rotation));
        holder.addComponent(ModelComponent.getComponentType(), new ModelComponent(Model.createStaticScaledModel(asset, scale)));
        if (persist) {
            holder.addComponent(PersistentModel.getComponentType(),
                    new PersistentModel(new Model.ModelReference(MODEL_ID, scale, null, true)));
        } else {
            holder.addComponent(store.getRegistry().getNonSerializedComponentType(),
                    com.hypixel.hytale.component.NonSerialized.get());
        }
        holder.addComponent(HeadRotation.getComponentType(), new HeadRotation(rotation));
        holder.addComponent(PropComponent.getComponentType(), PropComponent.get());
        holder.addComponent(Intangible.getComponentType(), Intangible.INSTANCE);
        holder.addComponent(Invulnerable.getComponentType(), Invulnerable.INSTANCE);
        holder.ensureComponent(UUIDComponent.getComponentType());
        job.entity = store.addEntity(holder, AddReason.SPAWN);
        return job;
    }

    private static double smooth(double t) {
        t = Math.min(1, Math.max(0, t));
        return t * t * (3 - 2 * t);
    }

    @Override
    public World world() {
        return world;
    }

    @Override
    public UUID owner() {
        return player.getUuid();
    }

    @Override
    public String kind() {
        return "worm";
    }

    @Override
    public boolean tick(float dt, Store<EntityStore> store) {
        if (entity == null || !entity.isValid()) {
            return false;
        }
        elapsed += dt;
        double t = elapsed % CYCLE;
        double depth; // 0 = standing on the ground, 1 = buried with its top at ground level
        double travel = 0; // blocks along the heading
        double reach = height * 0.27; // how far it travels sideways, about 8 blocks at scale 3
        float roll = 0, pitch = 0;
        float yaw = baseYaw;
        if (t < 2) {
            depth = 1;
        } else if (t < 3.5) { // rises in 1.5 s
            depth = 1 - smooth((t - 2) / 1.5);
            pitch = (float) (Math.sin((t - 2) * 4.0) * 0.06);
        } else if (t < 5.5) {
            depth = 0;
            roll = (float) (Math.sin((t - 3.5) * 3.0) * 0.12);
            pitch = (float) (Math.sin((t - 3.5) * 2.0) * 0.07);
        } else if (t < 7) {
            depth = smooth((t - 5.5) / 1.5);
            roll = (float) (Math.sin((t - 5.5) * 3.0) * 0.12 * (1 - smooth((t - 5.5) / 1.5)));
        } else if (t < 13) {
            depth = 1 + 0.05 * Math.sin((t - 7) * 2.5); // buried, bobbing a little as it moves
            travel = reach * smooth((t - 7) / 6);
        } else {
            depth = 1 + 0.05 * Math.sin((t - 13) * 2.5);
            travel = reach * (1 - smooth((t - 13) / 4));
            yaw = baseYaw + (float) (Math.PI * smooth((t - 13) / 2) * (t < 15 ? 1 : 0));
        }
        TransformComponent transform = store.getComponent(entity, TransformComponent.getComponentType());
        if (transform == null) {
            return false;
        }
        transform.setPosition(new Vector3d(origin.x + headingX * travel, groundY - height * BODY_RATIO + height * (1 - depth), origin.z + headingZ * travel));
        Rotation3f rotation = transform.getRotation();
        rotation.setPitch(pitch);
        rotation.setYaw(yaw);
        rotation.setRoll(roll);
        transform.setRotation(rotation);
        return elapsed < MAX_SECONDS;
    }

    @Override
    public void abort(Store<EntityStore> store) {
        if (entity != null && entity.isValid()) {
            store.removeEntity(entity, RemoveReason.REMOVE);
        }
    }
}
