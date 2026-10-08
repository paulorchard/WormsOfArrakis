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

## Prompt 18: aggro and the worm event brain

### What was built

- `AggroManager` (ticked by `WormBrainSystem` once per world tick): reads every player's position and `MovementStatesComponent`, adds or decays aggro, looks for groups every 0.25 s, and runs the events of that world.
- `WormEvent` (STALKING, LOCKED, BREACH, COOLDOWN, ENDED) knows nothing of the engine; it asks a small `Env` (valid, onSand, position). `WormEvents` holds the live events and the listeners. `GroupFinder` does the grouping. These three are covered by 14 unit tests (`gradlew test`).
- `/worm status | aggro [player] | add <player> <amount> | trigger [player|self] [stalk] [lock] | stop [player] | ignore <player>`, all working from the console as well (a player name; `self` or no name needs a player).
- Chat text is in `server.lang` under `wormsOfArrakis.` and `commands.worm.`. Every phase change, retarget and end is written to the server log as `[Worm #n] ...`, and to operators in chat when `DebugChat` is true (an operator is anyone with the permission of the `/worm` command).
- Config `Worms_of_Arrakis.json` (all keys, with these defaults): `AggroThreshold` 100, `WalkAggroPerBlock` 1, `RunAggroPerBlock` 2, `JumpAggroPerBlock` 2, `StillDecayPerSecond` 1, `GroupRadius` 200, `MinGainSpeed` 0.5, `SandTolerance` 1, `ResetFractionOnTrigger` 1, `FizzleScoreFraction` 0.5, `StalkSeconds` 40, `LockSeconds` 4, `BreachSeconds` 3, `CooldownSeconds` 120, `RetargetKeepsClock` false, `WormStartMinDistance` 120, `WormStartMaxDistance` 180, `DebugChat` false, `SandBlocksOverride` empty. `MinGainSpeed` 0.5 blocks per second was my choice: well under the slowest deliberate movement (walking is about 1.65), above the drift of a standing player.

### The rules as built

- **Aggro**: horizontal blocks since the last tick, times the rate of the first matching source. Gains only if the player is eligible, on sand and moving at `MinGainSpeed` or more. Otherwise `StillDecayPerSecond * dt` comes off, down to 0. Players are never dropped from tracking while above 0. A jump of over 40 blocks per second between two ticks is treated as a teleport and gains nothing.
- **Built-in sources**, registered through the public registry in this order (first match wins): `jump` (airborne after a jump, until landing), `run` (running or sprinting flag), `walk` (walking flag). **Crouching gains nothing** by design: the brief lists only these three, and sneaking is how to cross sand quietly. If the game's ordinary movement is not the `running` flag this needs a look (see the in-game checks).
- **On sand**: the first non-empty block at or below the one under the feet, searching down `SandTolerance` extra blocks (three times that while airborne after a jump), is in the sand set. Rock, buildings and an empty drop under the feet are not sand; a block one step down still is. Fluids are not blocks to the engine, so sand under shallow water counts.
- **Sand set**: `SandBlocksOverride` if not empty; otherwise `Arrakis_Sand` when Dunes of Arrakis is installed, else `Soil_Sand`, `Soil_Sand_Ashen`, `Soil_Sand_Red`, `Soil_Sand_White`. The ripple test uses the same set. Checked headless: with Dunes the log says `Sand block ids in this game: [5775]` (one id); in a world with no Dunes it lists four ids. **The old `SandBlocks` key was renamed**, because the earlier test version had written five names into saved configs, which would have defeated the automatic choice.
- **Groups**: players taken from the highest score down; each one not yet grouped starts a group of every unassigned player within `GroupRadius` (horizontal) of them. Players already in an event, and ineligible ones (creative, dead, ignored, or not seen this tick), are left out. The trigger is a group total of at least `AggroThreshold`.
- **Trigger**: members sorted by score, ties to the most recent gainer; the target is the first one on sand; scores are multiplied by `1 - ResetFractionOnTrigger` (0 by default). `/worm trigger` does not reset scores. If nobody is on sand an event is still created, ending FIZZLED at once so the log and hooks see it, and the group total is scaled to `FizzleScoreFraction * threshold`, keeping each player's share.
- **STALKING**: if the target is not on sand, or is gone (disconnected, dead, left the world, went creative), the first player in the ranking now on sand takes over, the clock restarts (unless `RetargetKeepsClock`), and `onRetarget` fires. Nobody on sand: FIZZLED.
- **LOCKED**: nothing changes whatever the target stands on. If the target is gone entirely (disconnected or left the world) there is nobody to hunt, so the event ends FIZZLED. A target who simply dies keeps the event going to BREACH, which ends FIZZLED unless the target is valid again.
- **BREACH**: a 3 s stub; no kill is done here (prompt 20). At its end the event goes to COOLDOWN and `onEnd(DEVOURED)` fires if the target is still valid.
- **COOLDOWN**: the event stays in the list in this phase for `CooldownSeconds`; its members cannot be in a new event. A FIZZLED or STOPPED event has no cooldown (a fizzled group is meant to stay agitated). `/worm stop` also cuts a cooldown short; `/worm trigger` ends the player's cooldown and starts.
- **The stand-in worm position**: starts 120 to 180 blocks from the target in a random direction at the target's height, and moves in a straight line so that it arrives exactly as the stalk clock runs out (re-aimed every tick, and after a retarget it heads for the new target). In LOCKED and BREACH it sits under the target. Prompt 19 replaces the path.

### API for other mods (`WormAggro`, `AggroSource`, `AggroContext`)

```java
WormAggro.registerSource(new AggroSource("mymod_drum", 3.0 /* or a DoubleSupplier */, ctx -> ctx.getStates().crouching));
WormAggro.unregisterSource("mymod_drum");
WormAggro.addAggro(playerRef, 25, "mymod_explosion");   // one-off; negative removes; floor 0; shown in /worm aggro
WormAggro.getAggro(playerUuid);                          // current score
WormAggro.sources();                                     // registered sources, in order
```

A source is an id, a rate in aggro per block and a predicate over an `AggroContext` (the player, their `MovementStates`, the distance and speed this tick, and whether they are airborne after a jump). Sources are tried in registration order and the first match is used for that tick; registering an id again replaces the old source. The built-in `jump`, `run` and `walk` are registered the same way, with rates read from the config on every use.

Hooks and queries for prompts 19 and 20:

```java
WormEvents.get().addListener(new WormEventListener() {   // all default methods, all on the world thread
    void onPhaseChange(WormEvent e, WormPhase old, WormPhase now)  // old is null for the first phase
    void onTick(WormEvent e)                                       // every tick of every phase but ENDED
    void onRetarget(WormEvent e, UUID oldTarget, UUID newTarget)   // STALKING only
    void onEnd(WormEvent e, WormEndReason reason)                  // DEVOURED, FIZZLED or STOPPED
});
event.getPhase(); event.getSecondsLeft(); event.getTarget() /* UUID */; event.getGroup() /* List<UUID> */;
event.getWormPosition(); event.getId(); event.getWorldName(); event.isForced();
WormEvents.get().all(); WormEvents.get().eventOf(playerUuid);
```

Targets and groups are UUIDs, not `PlayerRef`s, because the state machine is engine-free; a `PlayerRef` is `Universe.get().getPlayer(uuid)`.

### Calibration (speeds derived, not measured: confirm with `/wormtest speed 30`)

From `MovementConfig/Default.json`: base speed 5.5; walking flag x0.3 = 1.65 blocks per second; running x1.0 = 5.5; sprinting x1.273 = 7.0; crouching x0.55 = 3.0.

| Lone player, steady | Rate (default) | Aggro per second | Time to 100 |
| --- | --- | --- | --- |
| Walking (1.65 b/s) | 1 per block | 1.65 | **61 s** |
| Running (5.5 b/s) | 2 per block | 11 | **9 s** |
| Sprinting (7.0 b/s) | 2 per block | 14 | **7 s** |
| Jumping (about running speed, airborne) | 2 per block | about 11 | about 9 s |

A lone walker takes about a minute, which is fine, but a lone runner or sprinter triggers in under ten seconds: "you took a few steps", not "you have been loud for a while". **Recommended defaults:** `RunAggroPerBlock` 0.5 and `JumpAggroPerBlock` 0.5, walk left at 1. That gives running 2.75 per second (36 s), sprinting 3.5 (29 s), walking 1.65 (61 s), and keeps faster movement louder per second. Standing still removes 1 per second, so a runner who pauses for 30 s loses 30. If the game's ordinary forward movement turns out to carry the `walking` flag rather than `running`, a lone walker would trigger in about 18 s at the default; then lower `WalkAggroPerBlock` to 0.3. The defaults in the code are the brief's; change them in `Worms_of_Arrakis.json` once the speeds are measured.

### Cost of the group calculation

`GroupFinder.find` (the whole grouping, sorting included), measured in a unit test (`GroupFinderTest.cost`, JIT warmed): **20 players: 2.0 microseconds per call; 200 players: 14.6 microseconds**. It runs four times a second per world, so it is negligible. It is O(n squared) distance checks; a spatial hash would only matter beyond a few hundred players in one world. The per-tick player update (a position, a movement-flag read, a few block lookups) costs more than the grouping and is linear in players; it has not been measured with real players.

### Checks done here (no client)

- Clean build and deploy; the server boots with no warning or unknown-key message from this mod; `/worm status`, `stop`, `add`, `trigger` and `help worm` answer from the console (`No worm events.`, `Stopped 0 event(s).`, `No such player: Nobody`, `Name a player; the console has no position.`).
- 14 unit tests pass: all four phases with the right timings, the worm arriving as the clock ends, target on rock picks the next sand player and restarts the clock (and keeps it when configured), everyone on rock fizzles, a locked target cannot escape, a disconnecting target moves the event on (stalking) or fizzles it (locked), no target fizzles at once, stop cuts events and cooldowns short, grouping by distance, ranking with the tie-break by most recent contributor.
- Sand set with Dunes absent: checked in a separate headless world (`NO_DUNES=1 HEADLESS_DIR=... tools/headless/run.sh ...`): four vanilla sand ids resolved. With Dunes: one id (`Arrakis_Sand`).

### Checks that need players in game

1. `/worm aggro` after walking 20 s, running 20 s and bunny-hopping on sand: the numbers should match the table (about 33 walking, about 220 running, about the same bunny-hopping). Check which flag ordinary movement has.
2. Standing still loses 1 per second; walking on rock gains nothing.
3. Two players: both add to the `/worm status` group total; the higher scorer is the target; a tie goes to the more recent contributor.
4. Target steps onto rock during STALKING: next sand player chosen, clock restarts; all on rock: FIZZLED and the group is at 50.
5. Target on rock during LOCKED: nothing changes.
6. `/worm trigger self 10 4` with `DebugChat` true: STALKING 10 s, LOCKED 4 s, BREACH 3 s, then COOLDOWN, in chat.
7. Disconnect the target mid-event; cooldown respected for a second threshold trigger, ignored by `/worm trigger`.

### Changes after the first in-game review

- Defaults now: `WalkAggroPerBlock` 0.5, `RunAggroPerBlock` 1 (running and sprinting), `JumpAggroPerBlock` 1. With the derived speeds a lone player needs about 121 s walking (0.83 per second), 18 s running (5.5 per second) and 14 s sprinting (7 per second) to reach 100.
- Off sand, aggro falls `OffSandDecayMultiplier` (10) times faster than the still-on-sand decay: 10 per second on rock, water or anything not in the sand set, 1 per second standing still on sand.
- Step ripples (a small ripple round walking players) were tried and removed again for tick smoothness; the config keys and the detection code are gone.

### Anything the game would not allow

- `GameMode` has only `Adventure` and `Creative`; there is no spectator game mode, so only creative (and dead, and ignored) are excluded. A spectator-like state, if there is one, would need a separate component check.
- Events and targets use UUIDs rather than `PlayerRef`, to keep the rules testable without an engine; `Universe.get().getPlayer(uuid)` gets the ref.
- I could not simulate players inside the server, so the engine side of aggro (movement flags, the sand check, real speeds) is checked only by the build and the in-game list above; the rules around it are unit tested.

## Prompt 19: what the players see and hear before the worm arrives

**Status: built, loads clean, not yet seen or heard.** No client was available, so none of the feeling has been tuned and there are no screenshots. Every effect has its own `/worm preview` so each can be tuned alone, and every number is in `Worms_of_Arrakis.json`.

### How it is wired

`WormEffects` is a second listener on the prompt 18 hooks (registered after the log). `onTick` runs every world tick for STALKING, LOCKED and BREACH; `onRetarget` and `onEnd` handle fades and clean-up. Per-player state for the target (`TargetFx`) is faded and restored from the brain system once per tick. Nothing is announced in chat; `DebugChat` still prints the phase lines for operators.

### Techniques used, and why

The summary at the top of this log says the weather route replaces the sky, fog and sound tags (a clash with the Coriolis storm, which forces a weather), that the movement-settings slowdown did not slow the player in the test, and that one-shot sounds cannot be stopped. So:

- **Vignette: entity effects**, one per level, swapped as the strength changes: `Arrakis_Worm_Vignette_Level_1` to `_6` (the soft vignette texture at 15% to 90% edge opacity, generated by `tools/assets/build.js`). There is no fade control on one effect, so the fade is the levels stepping up. Not the weather route, so nothing collides with Coriolis.
- **Slow: entity effects**, `Arrakis_Worm_Slow_95` to `_10` (horizontal speed 95% down to 10% in 5% steps, the same field the vanilla Slow effect uses), swapped to the nearest step to the wanted share as it ramps. `SlowMethod` can be set to `settings` to use the smooth MovementSettings route instead (it also scales walk, run and sprint). **Jump force is always lowered through the settings route** (it is the only way); if the settings route does not work in your game the jump height will not change.
- **Rumble: one-shot sounds played again every `RumbleIntervalSeconds` (3 s), per player**, with the volume and pitch arguments of `playSoundEvent2dToPlayer`, so there is no distance falloff and no weather tag. The three low layers from the prompt 17 spike are played together: `Arrakis_SFX_Worm_Rumble_Statue_Low`, `_Ice_Low`, `_Storm_Low`.
- **Ripple: the prompt 17 thin-layer ripple** (`RippleJob`, layers of 1 to 4 px, restored from the world at the end, on disconnect and on shutdown).
- **Camera shake**: new assets `Arrakis_Worm_Tremble` (faint), `Arrakis_Worm_Lock` (strong) and `Arrakis_Worm_Pass` (bystander pulse), sent with `CameraShakeEffect(index, intensity, AccumulationMode.Set)`.
- **Wormsign**: `ParticleUtil.spawnParticleEffect(..., scale, color, null, listOfViewerRefs, store, visibleDistance)` with only the players within `WormsignViewDistance` of the worm, so cost scales with viewers, and only over loaded sand.

### What each player gets (all values in the config)

| Who | What |
| --- | --- |
| Anyone within `WormsignViewDistance` (220) of the worm | A dust trail along the path every `WormsignTrailInterval` (0.25 s), made of `Block_Break_Dust` and `Block_Land_Hard_Dust` along the stretch the worm covered, and a puff (`Block_Break_Sand` and a higher `Block_Break_Dust`) every `WormsignPuffInterval` (1.5 s). Not shown while the worm is over rock or buildings. |
| Everyone within `GroupRadius` (200) of the target | The rumble at `RumbleBystanderVolume` (0.7), flat, pitch 1. |
| The target | The rumble from `RumbleTargetStartVolume` (0.3) to `RumbleTargetVolume` (1.0) over STALKING, pitch down to `RumbleTargetEndPitch` (0.85), full in LOCKED. |
| The target and anyone within `RippleViewDistance` (30) | In the last `RippleSeconds` (15) of STALKING a ring-by-ring ripple of radius `RippleRadius` (5), every 4 s speeding to every 1 s; in LOCKED a tight radius-2 ripple every `LockedRippleInterval` (0.2 s). |
| The target | Vignette over the last `VignetteSeconds` (15) up to `VignetteStalkLevel` (0.5), to full by the end of LOCKED, held through the 3 s stub breach, then fading out. `VignetteStrength` scales it (0 turns it off). |
| The target | Camera shake: tremble from `ShakeSeconds` (15) out, every 2 s growing to every 0.5 s and from 0.2 to 0.6 intensity; strong `Lock` pulses every 0.4 s in LOCKED. `CameraShake` false turns it off; `ShakeStrength` scales it. |
| A bystander the worm passes within `BystanderShakeRange` (30) of | One `Pass` shake pulse (at most once every 10 s). |
| The target | From the start of LOCKED, slowed from 100% to `SlowFloor` (15%) of normal speed by the end of the lock, and jump force to `SlowJumpFloor` (50%). |

### The worm path

`WormEvent` now follows the ground: the stand-in position goes along a straight leg from the start to the target, with progress = (time / stalk time) raised to `PathEaseExponent` (2): slow at first, faster as it nears, arriving exactly as the clock runs out. Its height is the surface height of the column it is over (when that chunk is loaded). The start is a random point `WormStartMinDistance` to `WormStartMaxDistance` (120 to 180) away, trying up to 16 directions for one whose top block is sand (if the column is not loaded it cannot be checked and is accepted). **Rock on the way: the worm passes under it.** Routing around would need a path over blocks that are mostly not loaded 100 blocks out; the straight line is simple, always arrives on time, and the trail is simply not drawn over rock or buildings, so a worm crossing a rock outcrop disappears and comes out the other side. Whether that reads better than a detour has not been seen.

On a retarget the leg restarts from where the worm is, towards the new target, with the clock restarted (or kept, per `RetargetKeepsClock`), so the path bends.

### Always restored

- **Fake blocks**: each ripple restores the real blocks as it ends; the job is also ended and restored when its owner disconnects and when the plugin shuts down.
- **Vignette and slow**: `TargetFx.restore` removes the effects and resets the movement settings. It runs when an event ends (after a fade of `FadeOutSeconds`, 1 s), at once for `/worm stop`, when the plugin shuts down, and when the target dies. On disconnect or world change the entity is gone and the game drops the effects and resets movement on entry to the next world.
- **Sounds**: there is no stop-sound packet, so a sound already playing cannot be cut. Stopping means no more are played; the last one fades by itself within the length of the longest file (about 6.6 s at normal pitch, longer at a lower pitch). `/worm stop` silences nothing earlier than that.

### Retargeting

`onRetarget` fades the old target's vignette and slowdown out over `FadeOutSeconds`, resets the rumble timer so the new target hears it at once, and the new target's effects are computed from the event clock, so they start at the stage that matches it (the clock restarts, so they start at the beginning of STALKING's curve, unless `RetargetKeepsClock` is true).

### `/worm preview <wormsign|rumble|ripple|vignette|slow|shake|off> [seconds]`

Runs one effect for the caller only, ramping from nothing to full over the seconds (default 10), using the same code: wormsign draws a worm approaching from 120 blocks ahead (over any ground); rumble plays the target's volume and pitch ramp; ripple speeds up as it would; vignette and slow ramp 0 to 1; shake is the tremble then the lock pulses. `off` stops it and fades the effect.

### Checked here

- Builds and loads with no warning; every new asset validates (`/wormtest selftest`: 18 slow effects, 6 vignette effects, 3 shakes, the textures; `assets missing: none`).
- 14 unit tests pass, including the changed worm path code (the worm still arrives as the clock ends).
- Not checked, because they need players: everything in the table above and the clean-up cases.

### In-game checks (from the brief)

1. `/worm preview` each effect and tune the numbers in `Worms_of_Arrakis.json`.
2. `/worm trigger self 40 4` from open sand, then the same with a second player 80 blocks away (wormsign and the 70% rumble only), then a target who steps onto rock (ripples and vignette stop within about a second, speed normal; another sand player becomes the target), then disconnect and death mid-event, then two far-apart groups.
3. Screenshots at 30 s, 10 s, 4 s and 1 s before the end, target and bystander views, into `Screenshots/`.
4. The numbers to report: how far the wormsign is visible, the particle cost with several viewers (it spawns about three to eight particle systems per 0.25 s per event, sent to the viewers in range), and which sounds carry the feeling.

### Things the game would not allow

- A single entity effect has no strength control, so the vignette fade is built from six levels and the slowdown from eighteen steps rather than one smooth ramp.
- No stop-sound packet: sounds cannot be silenced, only not repeated (above).
- Jump height can only be changed through the movement-settings route, which did not slow the player in the earlier test, so the jump reduction may not work even if the speed reduction does.

## Cleanup after prompt 20

Remove or hide `WormTestCommand`, `Positional`, `WormSelfTest`, `WormTestSystem`, the job classes and the `Arrakis_Worm_*` test weathers, effects and sound events that the real worm does not use. Keep `Worms_of_Arrakis.json` (`SandBlocks`) and the placeholder model until the real one exists.
