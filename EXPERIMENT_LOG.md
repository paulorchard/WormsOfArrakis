# IslandCraft - Worms of Arrakis

Server version: 0.6.8

## One-page summary (prompt 17)

**Read this first: no game client was driven in this session.** Everything below marked *verified* was checked against the
0.6.8 server jar and by booting the real server headless (`/wormtest selftest`). Everything the client has to show (flicker,
look, sound, smoothness) is marked *in game: not yet seen* and has an exact checklist in the section for that question. The
screenshots folder is empty for that reason. Nothing here is guessed and presented as seen.

| # | Question | Built and loads | Verified without a client | Still needs your eyes in game | Fallback |
| - | -------- | --------------- | ------------------------- | ----------------------------- | -------- |
| 1 | Sand ripple on one client | `/wormtest ripple [radius] [raise\|trough\|both] [self\|nearby]` | Packet layout, restore guarantee, cost (9 bytes per block, peak 172 updates per tick at radius 40 = 1.6 KB) | Flicker, light, collision/push, stale blocks after chunk resend | Particles only (question 5) |
| 2 | Vignette | `/wormtest vignette on\|off [weather-soft\|weather-sand\|effect-soft\|effect-sand]` | Both routes exist in the engine (weather `ScreenEffect`, entity effect `ApplicationEffects.ScreenEffect`); assets load | Which route looks good, whether it fades, side effects on sky and fog | Camera shake only |
| 3 | Slow a player | `/wormtest slow <percent\|off> [seconds] [fields] [hz]`, `/wormtest speed [seconds]` | Which settings fields exist and their defaults; derived speeds; what else touches `MovementManager` | Smoothness of a ramp at a given update rate; jump and air feel; measured speeds from `/wormtest speed` | Entity effect `HorizontalSpeedMultiplier` (fixed multiplier, no ramp) |
| 4 | Stand-in worm | `/wormtest worm [scale\|remove] [persist]` | Spawns, moves 750 ticks and is removed with no error, both saved and unsaved; model and texture load | Looks, smoothness, tilt, chunk-unload survival | Particles and ripple only |
| 5 | Burst and rumble | `/wormtest burst [scale] [variant]`, `/wormtest rumble [seconds] [combo] [pitch]` | Every particle system and sound used exists; lengths and channel counts of the source sounds; engine limits on pitch and looping | How the plume looks; which rumble sounds right | Commission a deep stereo rumble (see question 5) |

Engine limits found that change the design (all *verified*):

1. A sound asset cannot be pitched down. `RandomSettings.MaxPitch` must be at least 0 (the asset validator rejects negative), so the only way to pitch down is the `pitchModifier` float at play time.
2. An `AmbienceFX` bed must be **stereo**. The two best rumble sources (`Memories_Statue_Rumble`, `Z3_Emit_Ice_Rumble_*`) are mono, so they cannot be loop beds. Only `Sandstorm_Stereo_LOOP` (wind) qualifies.
3. There is **no stop-sound packet** in the protocol (only `PlaySoundEvent2D`, `PlaySoundEvent3D`, `PlaySoundEventEntity`, `PlaySoundEventLocalPlayer`, `SetAudioState`). A plugin cannot cut a sound short, so a `Looping` sound event started by a plugin could never be ended. Loops must be AmbienceFX beds that stop when the weather tag goes away.
4. An asset that fails validation stops the whole server from starting ("Mod ... failed to load"), not just that asset. Found by hitting it twice.
5. There is no player movement event. Movement has to be read each tick from components.

## Naming convention

- Family name `IslandCraft - <mod name>`; manifest group `IslandCraft`. Here: `IslandCraft - Worms of Arrakis`.
- Identifier form `Worms_of_Arrakis` for asset folders and the config file name (`Worms_of_Arrakis.json`).
- Code form `WormsOfArrakis`; package `com.paulorchard.islandcraft.wormsofarrakis`; jar `IslandCraft-WormsOfArrakis-<version>.jar`.
- In-world content is prefixed `Arrakis_`; asset IDs are global.
- Optional dependency: `IslandCraft:IslandCraft - Dunes of Arrakis` (block `Arrakis_Sand`). Config `SandBlocks` lists the block ids that count as sand: `Arrakis_Sand`, `Soil_Sand`, `Soil_Sand_Ashen`, `Soil_Sand_Red`, `Soil_Sand_White`. Ids that do not exist are ignored, so without Dunes the vanilla ones remain. At boot the log prints the resolved numeric ids (with Dunes: 5 ids).

## Iteration loop

- `gradlew deployMod` builds the jar and replaces this mod's jar in `%APPDATA%\Hytale\UserData\Mods`. The game locks the jar while a world is open.
- `node tools/assets/build.js <dir with vanilla Zone2_Sunny.json and Zone2_Desert_Haze.json>` regenerates the test weathers, the soft vignette texture and the worm placeholder model, texture and model asset. Generated files are committed. `node tools/assets/ogginfo.js <files>` prints channels, rate and length of `.ogg` files.
- Headless: `tools/headless/run.sh "wait 28" "wormtest selftest" "wait 15"` boots the real server in `build/headless` with this mod and Dunes, runs the console self-test and prints the replies. The player commands need a player, so only `selftest` runs from the console.
- Asset problems show in the log as `Failed to validate asset` and stop the server; read the first lines of a headless run.

## What the headless self-test checks (and its last result)

`/wormtest selftest` loads four chunks round the origin of the Dunes world, then:

- checks every sound event, entity effect, weather, particle system and the worm model this mod uses is loaded: **none missing**;
- finds a sand column and builds and runs a ripple with no viewers at radius 5, 20 and 40;
- spawns the stand-in worm (saved and unsaved variants), ticks its script for 750 ticks (25 s, a full cycle and more) and removes it.

Last run, in a fresh world:

```
assets missing: none
using sand at (-6.5 87 7.5)
ripple radius 5:  49 columns (98 block updates), built in 0.1 ms, 38 ticks, tick loop 0.2 ms, peak 16 updates in one tick = 157 bytes (uncompressed)
ripple radius 20: 623 columns (1246 block updates), built in 0.6 ms, 43 ticks, tick loop 1.2 ms, peak 96 updates in one tick = 877 bytes
ripple radius 40: 1219 columns (2438 block updates), built in 1.2 ms, 43 ticks, tick loop 1.0 ms, peak 172 updates in one tick = 1562 bytes
worm (not saved): spawned, moved for 750 ticks, removed
worm (persistent): spawned, moved for 750 ticks, removed
```

(Radius 40 found only 1219 columns because only four chunks were loaded. The server log has no warning from this mod.)

## Question 1: sand ripple on one client

### Exact calls that work (verified they compile, run and send)

```java
// Block read from the world. Never invented.
WorldChunk chunk = world.getChunkIfLoaded(ChunkUtil.indexChunkFromBlock(x, z));
int top = chunk.getHeight(x & ChunkUtil.SIZE_MASK, z & ChunkUtil.SIZE_MASK);   // y of the top block
int id  = chunk.getBlock(x, top, z);                                            // compare with the sand ids
BlockSection s = chunk.getBlockChunk().getSectionAtBlockY(y);
int local = ChunkUtil.indexBlock(x & 31, y & 31, z & 31);
s.get(local); s.getFiller(local); s.getRotationIndex(local);                    // the true block, for the restore

// One packet per section. x, y, z are SECTION coordinates (ChunkUtil.chunkCoordinate), not block coordinates.
new ServerSetBlocks(sx, sy, sz, new SetBlockCmd[] { new SetBlockCmd((short) local, blockId, (short) filler, (byte) rotation) });
if (playerRef.getChunkTracker().isLoaded(sx, sy, sz))
    playerRef.getPacketHandler().writeNoCache(packet);
```

How the layout was established: the game's own `ChunkSystems$ReplicateChanges` builds exactly this, with `ChunkSection.getX/Y/Z()` as the packet's x, y, z and `BlockSection` section-local indices as `SetBlockCmd.index`; for a single changed block it sends `ServerSetBlock(worldX, worldY, worldZ, id, filler, rotation)` with world coordinates. It only sends to players whose `ChunkTracker.isLoaded(section)` is true; this mod does the same.

`SetBlockCmd(short index, int blockId, short filler, byte rotation)` is 9 bytes; `ServerSetBlocks` is not compressed (`IS_COMPRESSED = false`).

### Design as built (`RippleJob`)

- Columns within the radius whose top block is a configured sand block. Raise shows that sand id one block higher (only if the block above is empty); trough shows air in the top block; `both` raises on even rings and troughs on odd.
- Ring r starts at `r / (radius + 1) * 1.2 s` and stays up 0.3 s, so the wave finishes by 1.5 s (verified: ends in 38-43 ticks).
- A column is skipped if the fake block would be inside a viewer (raise) or take the floor from under a viewer's feet (trough), judged by their position when the ripple starts.
- `scope nearby` sends the same packets to every player within `radius + 48` blocks; `self` (default) sends only to the caller.

### Findings

| Asked | Answer | Status |
| ----- | ------ | ------ |
| Clean on the client (no flicker, lighting artefacts, collision mismatch)? Pushed or stuck? | The server never knows about the fake block, so a player who walks into one is not moved by the server. What the client does is not known. | *in game: not yet seen* |
| Blocks per second before lag; players that can see it | Bytes: 9 per update, so 1.6 KB per tick at the radius-40 peak, about 47 KB/s for the busiest second per viewer. Server time to build and run a radius-40 ripple: about 2 ms in total. Both grow linearly with viewers. The limit is the client, not seen yet. | server side *verified*; client *not yet seen* |
| Chunk resend leaves a stale fake block? | A resend goes through the same `ChunkTracker` path as the first send and carries the server's blocks, so it should replace the fake; if the ripple is still running it may re-show the fake until its own restore. Not seen. | *in game: not yet seen* |
| What guarantees the real block is restored? | Four layers, all in code: (1) the restore reads the block from the world at restore time, so it is never a stale value; (2) a job that outlives 6 s restores everything and ends; (3) `WormTestSystem.finish` calls `abort`, which restores whatever is still up, when a job ends, when the owner disconnects, and from the plugin's `shutdown()`; (4) the game's own `ReplicateChanges` sends the real block to everyone whenever a block really changes. A disconnecting client loses the fakes by itself. Server stop disconnects every client. | *verified in code*, restore on chunk-gone case: skipped because the client gets the real chunk on load |
| Visible to bystanders? Wanted? | Yes: `ripple 5 raise nearby` sends the same packets to everyone near. It is wanted: a worm ripple under you that others cannot see would look like a bug. | built; *in game: not yet seen* |

### In-game checklist

1. `/wormtest ripple` (radius 5, raise). Stand still; stand on the edge. Look for flicker and for sand blocks with wrong lighting.
2. `/wormtest ripple 5 trough`, then `/wormtest ripple 8 both`.
3. Walk into a raised block while it is up: pushed, stuck, or ghosted through?
4. `/wormtest ripple 25` and `/wormtest ripple 40`: any frame drop?
5. Start `/wormtest ripple 20`, then run 150 blocks away and back within two seconds: any block left raised?
6. Second player: `/wormtest ripple 8 raise nearby`.
7. Screenshots to `Screenshots/` named `q1-*.png`.

## Question 2: vignette and screen effect

### What exists (verified)

- **a. Weather route.** `Weather` has a `ScreenEffect` texture key (vanilla `Zone2_Sand_Storm` uses `ScreenEffects/Sand.png`). `WeatherTracker.setOverrideWeatherIndex(int)` is a per-player override that `updateWeather` checks before the forced and environment weather, and `sendWeatherIndex(PlayerRef, index, transitionSeconds)` sends it with a chosen blend time. `VignetteJob` does: save the current index, set the override, send with a 1.5 s blend; on abort clear the override and blend back.
  - The new weathers are copies of the two vanilla weathers the Dunes forecast mostly uses (`Zone2_Sunny`, `Zone2_Desert_Haze`) plus a `ScreenEffect`, in `Server/Weathers/Worms_of_Arrakis/` as `Arrakis_Worm_Vignette_{Soft,Sand}_{Sunny,Haze}`. The command picks Sunny or Haze from the player's current weather. The Dunes forecast also rolls `Zone2_Sand_Storm` (20%); in that case it falls back to Sunny, so the sky would change for the duration. The side effects on sky and fog (a hard cut to a copy) *have not been seen*.
- **b. Entity effect route.** `EntityEffect.ApplicationEffects.ScreenEffect` is used by vanilla `Burn`, `Freeze`, `Immune`, `Poison_T1`, `Root`, `Slow`, `Stun`. `Arrakis_Worm_Vignette_{Sand,Soft}` are such effects (6 s base; `VignetteJob` adds them with `EffectControllerComponent.addEffect(ref, effect, 60f, OverlapBehavior.OVERWRITE, store)` and removes them with `removeEffect(ref, index, store)`). This route changes nothing else about the world.
- **c. Textures.** `Common/ScreenEffects/`: `Sand.png` (viewed: white middle with a ragged dark edge, so a framing vignette), `Sniper.png` (a scope), `Daggers`, `Fire`, `Immune`, `Poison`, `Poison_T1`, `Snow`, `Water`. This mod adds a generated soft radial vignette (`ScreenEffects/Worms_of_Arrakis/Arrakis_Worm_Vignette.png`, transparent centre, dark sand brown edge). Whether the client uses the PNG's alpha or its brightness is unknown, which is why both a vanilla and a custom texture are provided.

### Status: not yet seen. Order to try

`/wormtest vignette on effect-sand`, `... effect-soft`, `... weather-sand`, `... weather-soft`, each followed by `/wormtest vignette off`. Look at: does it appear at all; does it fade (weather route: blend 1.5 s; effect route: no fade control found); did the sky or fog change; does `off` restore the exact earlier weather. Fallback if nothing is clean: camera shake only (see Weather of Arrakis `CoriolisEffects.shake`).

## Question 3: slowing a player

### Settings (verified from `Server/Entity/MovementConfig/Default.json`)

Ground speed on the server's side of the model is `baseSpeed` times a state multiplier.

| Value | Default |
| ----- | ------- |
| `baseSpeed` | 5.5 |
| forward walk / run / sprint multiplier | 0.3 / 1.0 / 1.273 |
| backward / strafe run | 0.65 / 0.8 |
| forward crouch | 0.55 |
| `jumpForce` | 11.8 |
| `acceleration` | 0.1 |
| `airSpeedMultiplier`, `airControlMaxMultiplier` | 1, 3.13 |
| `maxSpeedMultiplier`, `minSpeedMultiplier` | 15, 0.01 |

**Derived speeds (not measured):** walk 1.65, run 5.5, sprint 7.0 blocks per second if `baseSpeed` is in blocks per second. `/wormtest speed [seconds]` measures them from the server's view of the player's position each tick, per state (`MovementStates.walking/running/sprinting`), on ground only, and prints the mean and the peak next to the settings. **Run it once and put the numbers here before prompt 18 calibrates aggro:**

```
walk: ___  run: ___  sprint: ___  crouch: ___   (blocks/s, from /wormtest speed 30)
```

### Calls that work

```java
MovementManager m = store.getComponent(ref, MovementManager.getComponentType());
MovementSettings s = m.getSettings();          // what the client currently has
MovementSettings d = m.getDefaultSettings();   // never modified by us; always compute from this
s.baseSpeed = d.baseSpeed * factor;            // also: *SpeedMultiplier fields, jumpForce, airSpeedMultiplier, ...
m.update(playerRef.getPacketHandler());        // sends the settings
m.resetDefaultsAndUpdate(ref, store);          // restore
```

`SlowJob` ramps linearly from 100% to the target over `seconds`, holds 5 s, ramps back over 1 s and then calls `resetDefaultsAndUpdate`. `fields` is one or more of `base`, `mult`, `jump`, `air`, `accel` joined with `+`, or `all` (= base+jump+air); `hz` is the update rate (default 30). `slow <p> <s> effect` uses the entity-effect route instead.

### Findings

| Asked | Answer | Status |
| ----- | ------ | ------ |
| Which fields change ground speed for walk, run, sprint, jump | Speed: `baseSpeed` scales all three at once; the three `forward*SpeedMultiplier` scale one each. Jump height: `jumpForce`. Air: `airSpeedMultiplier`, `airControlMaxMultiplier`. Which the client really uses, and whether jump or air control must also be lowered, has to be tried: `slow 40 3 base`, then `base+jump`, then `all`. | fields *verified*; feel *in game: not yet seen* |
| Does the client accept many updates a second | Not seen. Try `hz` 5, 10, 30. | *in game: not yet seen* |
| Does anything else write these settings | Classes that reference `MovementManager` (a scan of the jar): `PlayerMovementManagerSystems` (assign and post-assign), `ModelSystems.PlayerUpdateMovementManager` (model change), `GameModeTypeState`, `SetGameModeEffect` and `TriggerVolumePlayerStateSystem` (game mode), `MountPlugin` and `ActionMount` (mounts), `KnockbackPredictionSystems`, `PlayerSystems.PlayerAddedSystem`, `Player`, `EntityModule`, `PlayerRef`, `MotionControllerBase`, `DumpUtil`. **No item, armour, stat or entity-effect class references it**, so armour does not write these settings and neither does the `Slow` effect (its `HorizontalSpeedMultiplier` is applied another way). Reads versus writes in each of the others were not traced except the first row below. | scan *verified* |
| Restore on death, disconnect, world change, stop | `PlayerMovementManagerSystems$PostAssignmentSystem` calls `resetDefaultsAndUpdate` when the `MovementManager` component is assigned, so entering a world (join, world change; likely respawn) resets it by itself. A model change or game-mode change will also reset it. Disconnect: the component goes with the player. Stop: our `shutdown()` calls `abort` on every job, which calls `resetDefaultsAndUpdate`. Nothing is saved to disk. | *verified in code*; death/world change *in game: not yet seen* |

### Fallback

Entity effect `Arrakis_Worm_Slow_Effect` (`ApplicationEffects.HorizontalSpeedMultiplier` 0.5, exactly how vanilla `Slow` works). No ramp, one fixed multiplier, but restores by itself when it expires or is removed.

## Question 4: stand-in worm

### Built

- `Server/Models/Worms_of_Arrakis/Arrakis_Worm_Placeholder.json`, `Common/NPC/Worms_of_Arrakis/Arrakis_Worm_Placeholder.blockymodel` (one box 192 x 320 x 192 model units, i.e. 6 x 10 blocks at 32 units per block) and a flat sand-coloured 64 x 64 texture. All generated by `tools/assets/build.js`. No animation sets. `Arrakis_Worm_Placeholder` is loaded with no warnings. (Blockbench can open the `.blockymodel`.)
- **A prop, not an NPC**: the same recipe the game's own entity-spawn page uses: a `Holder` with `NetworkId(takeNextNetworkId())`, `TransformComponent`, `ModelComponent(Model.createStaticScaledModel(asset, scale))`, `HeadRotation`, `PropComponent`, `UUIDComponent`, added with `store.addEntity(holder, AddReason.SPAWN)`. Plus `Intangible.INSTANCE` (no collision) and `Invulnerable.INSTANCE`. With no NPC role there is no behaviour at all, nothing to run and nothing to despawn it.
- Saving: by default the entity gets `NonSerialized` (the registry's `getNonSerializedComponentType()`), so it is not written with its chunk. `worm <scale> persist` adds `PersistentModel(new Model.ModelReference(id, scale, null, true))` instead, which is what the game uses for props that must survive.
- Movement: `TransformComponent.setPosition` and `getRotation().setPitch/setYaw/setRoll` every tick from `WormJob.tick`, through a 20 s loop: buried, rise over 3 s, hold with a sway, sink, travel 8 blocks sideways buried with a small bob, return while turning. Spawned buried: the base is at ground height minus the model's height, so the top is level with the ground.
- Removal: `store.removeEntity(ref, RemoveReason.REMOVE)` from `abort`, which runs on `worm remove`, on the owner leaving, on a 600 s limit and from `shutdown()`.

### Findings

| Asked | Answer | Status |
| ----- | ------ | ------ |
| How to move it every tick; how smooth | Setting the transform each world tick works without error (750 ticks). How smooth the client shows it is not known; if choppy, the next things to try are a `Velocity` component or teleport-style updates. | works server side; *in game: not yet seen* |
| Non-colliding, invulnerable, no despawn, no NPC behaviour | All four by construction (`Intangible`, `Invulnerable`, no `DespawnComponent`, no role). | *verified in code*; collision *in game: not yet seen* |
| Rotated and tilted | Pitch, yaw and roll are set each tick. Whether the client applies pitch and roll to a prop's model is not known. | *in game: not yet seen* |
| Survives a chunk unload? | Unsaved: no, by design. `persist`: expected yes, not yet tried. Walk 300 blocks away and back, then look for it; then restart the server. | *in game: not yet seen* |
| Vanishes cleanly on `remove` and on server stop | `remove` removes the entity (verified headless: spawn, 750 ticks, remove, no exception). Stop: `shutdown()` removes it, and an unsaved entity is not written. A `persist` worm would remain after a hard crash. | *verified headless* |

## Question 5: dust, debris and sound

### Burst

`TimedEffectsJob.particle` calls this overload (the floats are the three rotation angles then the scale; the last is the visible distance):

```java
ParticleUtil.spawnParticleEffect(systemId, x, y, z, 0f, 0f, 0f, scale, color /* protocol.Color */, null,
        listOfPlayerRefsInWorld, store, 160f);
```

Verified loaded: `Block_Break_Dust`, `Block_Land_Hard_Dust`, `Block_Break_Sand`, `Block_Land_Sand_Hard`, `Block_Break_Dirt`, `Sand_Storm`. `burst [scale] [variant]` (default scale 3) spawns ten blocks ahead on the ground:

| Variant | What |
| ------- | ---- |
| 1 | `Block_Break_Dust` |
| 2 | `Block_Land_Hard_Dust` |
| 3 | `Block_Break_Sand` + `Block_Land_Sand_Hard` |
| 4 (default) | A column: one dust and one dust-or-sand system per 1.5 blocks of height (`scale * 2` layers), in tan (#c8a46e), eight waves 0.25 s apart, each 10% smaller, so the dust trails off |
| 5 | Variant 4 plus `Block_Break_Dirt` and `Sand_Storm` |

Not seen: which looks best, and the cost. Try `burst 3 4`, `burst 6 4`, `burst 10 5`, then compare the frame rate. There is no debris model in this: chunks flying outward would need `Rubble_Sandstone`-style projectile models (they exist under `Server/Models/Projectiles/Items/Rubble/`) and is a later step.

### Rumble

Sound facts (*verified*, from the `.ogg` files):

| File | Channels | Length |
| ---- | -------- | ------ |
| `Memories_Statue_Rumble` | mono | 5.72 s |
| `Z3_Emit_Ice_Rumble_01 / 02 / 03` | mono | 6.58 / 4.01 / 5.32 s |
| `Sandstorm_Stereo_LOOP` | stereo | 5.02 s (a seamless loop for beds) |
| `Sand_Break_01` | mono | 0.95 s |

- `Memories_Statue_Rumble` and `Sandstorm_Stereo_LOOP` have no sound event of their own in vanilla, so `Server/Audio/SoundEvents/Worms_of_Arrakis/` wraps them: `Arrakis_SFX_Worm_Rumble_{Statue,Ice,Storm}` and a `_Low` version of each (`MinPitch -12, MaxPitch 0`: random between an octave down and normal, because the validator refuses a negative `MaxPitch`).
- `playSoundEvent2dToPlayer(PlayerRef, int index, SoundCategory, float volume, float pitch)` is the call; the packet fields are `volumeModifier` and `pitchModifier`, and the one-argument overload passes `1, 1`, so both are plain multipliers with 1.0 unchanged. The accepted range on the client was not found in the server; sweep it with the `pitch` argument (try 0.5, 0.35, 0.25).
- `rumble [seconds] [combo] [pitch]`: combos `statue`, `ice`, `storm`, each with `-low`; `layered` (the three `_Low`); `ambience`; `stop`. A combo plays at 70% for the first half of `seconds` and at 100% for the second, re-triggering every 3 s (shorter than every file, so the repeats overlap).
- **One-shot length** is the file length above (a sound event plays its longest layer; the `_Low` pitch-down lengthens it).
- **Looping by plugin**: only by re-triggering. A `Looping: true` layer in a sound event is possible, but with no stop packet it could never be ended, so none was built. The clean loop is an `AmbienceFX` bed driven by a weather tag. `rumble 1 ambience` does that: it sends a per-player weather (`Arrakis_Worm_Rumble_{Sunny,Haze}`, tag `{"Arrakis": ["Worm_Rumble"]}`) which matches `Arrakis_AmbFX_Worm_Rumble_Storm` (`WeatherTagPattern` `Worm_Rumble`, `Shelter` `Open`/`Partial`). Its bed is only `Sandstorm_Stereo_LOOP`, because the bed validator demands stereo.
- Which combination sounds like something huge under sand: not heard.

### What is missing if vanilla is not enough

A **stereo** deep rumble (30-60 Hz, 6-10 s, loopable, no wind) for the ambience bed, so a worm can be heard moving through a weather-tag loop; and optionally a **breach** one-shot (a dull thud with a rising sand hiss). If `layered` at a low `pitch` is not convincing, those two files are the commission list; the format is `.ogg` Vorbis, referenced from `Common/Sounds/...` and wrapped by one `SoundEvent` and one `AmbienceFX` json like the existing ones.

## Things the game would not allow, and what was done

| Wanted | Game says | Done instead |
| ------ | --------- | ------------ |
| Pitch a sound down in an asset | `MaxPitch` must be >= 0 | `_Low` assets pitch randomly 0 to -12 semitones; deterministic pitch via the play-time `pitchModifier` |
| Loop the statue rumble as an ambience bed | Bed must be stereo; it is mono | Bed is the stereo sandstorm; a custom stereo file is on the commission list |
| Stop or loop a sound from a plugin | No stop packet | Re-trigger one-shots; loops only via weather-tag AmbienceFX |
| Copy "the player's current look" at runtime for the vignette weather | Weathers are static assets | Copies of `Zone2_Sunny` and `Zone2_Desert_Haze`, chosen by the player's current weather id; the entity-effect route avoids the problem |
| Test the player commands headless | They need a player | `/wormtest selftest` for the console, the rest in game |

## Recommendation for prompt 18: how to read player movement

**Read it per tick from components; there is no movement event.** The server has no player-move event (the only events under `events/player` are connect, disconnect, ready, world add/remove, chat, craft, interact and mouse buttons and motion). Use a `TickingSystem` or `EntityTickingSystem` over players that reads, each tick:

- `MovementStatesComponent.getMovementStates()` for the flags (`walking`, `running`, `sprinting`, `crouching`, `jumping`, `onGround`, `horizontalIdle`, `sliding`, `swimming`, `inFluid`). This is the cheapest reliable "how loud is this player" signal and needs no position maths: sprinting is loud, walking is quiet, crouching is silent, `horizontalIdle` is silent.
- `TransformComponent.getPosition()` against the previous tick for real speed (that is what `SpeedProbeJob` does) when aggro should depend on speed rather than state, for example for slowed players.
- Keep per-player state (a noise score that rises and decays) in a plain map or a small component, dropped on `PlayerDisconnectEvent`. `WormTestSystem` is a working pattern for the tick and the cleanup.

Calibrate with the numbers from `/wormtest speed` (see question 3); the derived values are walk 1.65, run 5.5, sprint 7.0 blocks per second.

## Checks

- Clean build and deploy: `gradlew jar` and `gradlew deployMod` succeed.
- Mod list: the server log says `Loaded pack: IslandCraft:IslandCraft - Worms of Arrakis from IslandCraft-WormsOfArrakis-0.1.0.jar` and the plugin logs the sand ids at start. The in-game mod list was not looked at.
- No missing-asset warnings: none from this mod in the headless server log; `selftest` reports `assets missing: none`.
- Each of the five commands runs: registered and listed by `help wormtest` (verified); `selftest` exercises the ripple and worm code (verified); the player-only ones have not been run by a player. **Screenshots: none, no client was available.**

## Cleanup after prompt 20

Remove or hide `WormTestCommand`, `Positional`, `WormSelfTest`, `WormTestSystem`, the job classes and the `Arrakis_Worm_*` test weathers, effects and sound events that the real worm does not use. Keep `Worms_of_Arrakis.json` (`SandBlocks`) and the placeholder model until the real one exists.
