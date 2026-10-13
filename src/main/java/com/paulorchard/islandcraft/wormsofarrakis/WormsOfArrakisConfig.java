package com.paulorchard.islandcraft.wormsofarrakis;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.codecs.map.MapCodec;

import java.util.HashMap;
import java.util.Map;

/** Settings read from Worms_of_Arrakis.json in the plugin's data folder. */
public class WormsOfArrakisConfig {

    public static final String FILE_NAME = "Worms_of_Arrakis";

    public static final BuilderCodec<WormsOfArrakisConfig> CODEC =
            BuilderCodec.builder(WormsOfArrakisConfig.class, WormsOfArrakisConfig::new)
                    .documentation("Settings for the sandworms.")
                    .append(new KeyedCodec<>("SandBlocksOverride", Codec.STRING_ARRAY, false),
                            (config, value) -> config.sandBlocks = value,
                            config -> config.sandBlocks)
                    .documentation("Block ids that count as sand. Empty means automatic: Arrakis_Sand when Dunes of "
                            + "Arrakis is installed, otherwise the vanilla sand blocks. Ids that do not exist are ignored.")
                    .add()
                    .append(new KeyedCodec<>("AggroThreshold", Codec.DOUBLE, false),
                            (config, value) -> config.aggroThreshold = value,
                            config -> config.aggroThreshold)
                    .documentation("A being whose own aggro reaches this starts a worm event.")
                    .add()
                    .append(new KeyedCodec<>("StillDecayPerSecond", Codec.DOUBLE, false),
                            (config, value) -> config.stillDecayPerSecond = value,
                            config -> config.stillDecayPerSecond)
                    .documentation("Taken off a being's aggro each second it gains nothing (not inside a worm event).")
                    .add()
                    .append(new KeyedCodec<>("GroupRadius", Codec.DOUBLE, false),
                            (config, value) -> config.groupRadius = value,
                            config -> config.groupRadius)
                    .documentation("Blocks. When an event starts, every being on sand this close (horizontally) to the one that triggered it is in its list.")
                    .add()
                    .append(new KeyedCodec<>("MinGainSpeed", Codec.DOUBLE, false),
                            (config, value) -> config.minGainSpeed = value,
                            config -> config.minGainSpeed)
                    .documentation("Blocks per second. Slower movement (shuffling in place) gains nothing.")
                    .add()
                    .append(new KeyedCodec<>("SandTolerance", Codec.DOUBLE, false),
                            (config, value) -> config.sandTolerance = value,
                            config -> config.sandTolerance)
                    .documentation("Blocks below the feet searched for the block that counts as the one underfoot, "
                            + "so a player mid-step is still on sand. Airborne after a jump it is three times this.")
                    .add()
                    .append(new KeyedCodec<>("BreachSequenceSeconds", Codec.DOUBLE, false),
                            (config, value) -> config.breachSeconds = value,
                            config -> config.breachSeconds)
                    .documentation("BREACH phase length: the whole breach sequence and settling.")
                    .add()
                    .append(new KeyedCodec<>("OffSandDecayMultiplier", Codec.DOUBLE, false),
                            (config, value) -> config.offSandDecayMultiplier = value,
                            config -> config.offSandDecayMultiplier)
                    .documentation("Aggro falls this many times faster while the player is not on sand.")
                    .add()
                    .append(new KeyedCodec<>("DebugChat", Codec.BOOLEAN, false),
                            (config, value) -> config.debugChat = value,
                            config -> config.debugChat)
                    .documentation("Send every phase change and retarget to operators in chat.")
                    .add()
                    // GENERATED CODEC
                    .append(new KeyedCodec<>("WormsignViewDistance", Codec.DOUBLE, false),
                            (config, value) -> config.wormsignViewDistance = value,
                            config -> config.wormsignViewDistance)
                    .documentation("Blocks. Players this close see the dust trail the breach worm leaves as it dives away.")
                    .add()
                    .append(new KeyedCodec<>("WormsignTrailInterval", Codec.DOUBLE, false),
                            (config, value) -> config.wormsignTrailInterval = value,
                            config -> config.wormsignTrailInterval)
                    .documentation("Seconds between trail dust as the breach worm dives away.")
                    .add()
                    .append(new KeyedCodec<>("WormsignScale", Codec.DOUBLE, false),
                            (config, value) -> config.wormsignScale = value,
                            config -> config.wormsignScale)
                    .documentation("Particle scale of that trail.")
                    .add()
                    .append(new KeyedCodec<>("RumbleBystanderVolume", Codec.DOUBLE, false),
                            (config, value) -> config.rumbleBystanderVolume = value,
                            config -> config.rumbleBystanderVolume)
                    .documentation("Volume (1 = full) for players near the trigger who are not in the list. Flat.")
                    .add()
                    .append(new KeyedCodec<>("RumbleTargetStartVolume", Codec.DOUBLE, false),
                            (config, value) -> config.rumbleTargetStartVolume = value,
                            config -> config.rumbleTargetStartVolume)
                    .documentation("Everyone in the list hears this at the start of STALKING, rising to RumbleTargetVolume.")
                    .add()
                    .append(new KeyedCodec<>("RumbleTargetVolume", Codec.DOUBLE, false),
                            (config, value) -> config.rumbleTargetVolume = value,
                            config -> config.rumbleTargetVolume)
                    .documentation("Volume the list hears at the end of STALKING and in LOCKED.")
                    .add()
                    .append(new KeyedCodec<>("RumbleTargetEndPitch", Codec.DOUBLE, false),
                            (config, value) -> config.rumbleTargetEndPitch = value,
                            config -> config.rumbleTargetEndPitch)
                    .documentation("Pitch multiplier the list hears at the end of STALKING (1 at the start).")
                    .add()
                    .append(new KeyedCodec<>("RumbleIntervalSeconds", Codec.DOUBLE, false),
                            (config, value) -> config.rumbleIntervalSeconds = value,
                            config -> config.rumbleIntervalSeconds)
                    .documentation("The rumble sounds are played again this often (shorter than the sounds, so they overlap).")
                    .add()
                    .append(new KeyedCodec<>("RippleStartInterval", Codec.DOUBLE, false),
                            (config, value) -> config.rippleStartInterval = value,
                            config -> config.rippleStartInterval)
                    .documentation("Only /worm preview ripple uses this now: seconds between ripples at its start.")
                    .add()
                    .append(new KeyedCodec<>("RippleEndInterval", Codec.DOUBLE, false),
                            (config, value) -> config.rippleEndInterval = value,
                            config -> config.rippleEndInterval)
                    .documentation("Only /worm preview ripple uses this now: seconds between ripples at its end.")
                    .add()
                    .append(new KeyedCodec<>("RippleViewDistance", Codec.DOUBLE, false),
                            (config, value) -> config.rippleViewDistance = value,
                            config -> config.rippleViewDistance)
                    .documentation("Blocks. Players this close to a rippled being also see the ripple.")
                    .add()
                    .append(new KeyedCodec<>("LockedRippleInterval", Codec.DOUBLE, false),
                            (config, value) -> config.lockedRippleInterval = value,
                            config -> config.lockedRippleInterval)
                    .documentation("Seconds between the tight, quick ripples round the target during LOCKED.")
                    .add()
                    .append(new KeyedCodec<>("VignetteStrength", Codec.DOUBLE, false),
                            (config, value) -> config.vignetteStrength = value,
                            config -> config.vignetteStrength)
                    .documentation("Overall multiplier on the vignette (0 turns it off).")
                    .add()
                    .append(new KeyedCodec<>("CameraShake", Codec.BOOLEAN, false),
                            (config, value) -> config.cameraShake = value,
                            config -> config.cameraShake)
                    .documentation("Turn the target camera shake off by setting false.")
                    .add()
                    .append(new KeyedCodec<>("ShakeStrength", Codec.DOUBLE, false),
                            (config, value) -> config.shakeStrength = value,
                            config -> config.shakeStrength)
                    .documentation("Overall multiplier on the camera shake intensity.")
                    .add()
                    .append(new KeyedCodec<>("SlowFloor", Codec.DOUBLE, false),
                            (config, value) -> config.slowFloor = value,
                            config -> config.slowFloor)
                    .documentation("Share of normal speed the target is slowed to by the end of LOCKED.")
                    .add()
                    .append(new KeyedCodec<>("SlowJumpFloor", Codec.DOUBLE, false),
                            (config, value) -> config.slowJumpFloor = value,
                            config -> config.slowJumpFloor)
                    .documentation("Share of normal jump force the target is down to by the end of LOCKED (needs SlowMethod effect or settings both working).")
                    .add()
                    .append(new KeyedCodec<>("SlowMethod", Codec.STRING, false),
                            (config, value) -> config.slowMethod = value,
                            config -> config.slowMethod)
                    .documentation("effect: entity effects with a horizontal speed multiplier in steps. settings: MovementManager settings, smooth. Jump force always uses settings.")
                    .add()
                    .append(new KeyedCodec<>("FadeOutSeconds", Codec.DOUBLE, false),
                            (config, value) -> config.fadeOutSeconds = value,
                            config -> config.fadeOutSeconds)
                    .documentation("Effects of an old target fade out over this long on retarget.")
                    .add()
                    .append(new KeyedCodec<>("BreachBoomAt", Codec.DOUBLE, false),
                            (config, value) -> config.breachBoomAt = value,
                            config -> config.breachBoomAt)
                    .documentation("Seconds from the start of BREACH: the dust ring, the heave and the boom.")
                    .add()
                    .append(new KeyedCodec<>("BreachWormAt", Codec.DOUBLE, false),
                            (config, value) -> config.breachWormAt = value,
                            config -> config.breachWormAt)
                    .documentation("The stand-in worm shoots up through the surface.")
                    .add()
                    .append(new KeyedCodec<>("BreachLiftAt", Codec.DOUBLE, false),
                            (config, value) -> config.breachLiftAt = value,
                            config -> config.breachLiftAt)
                    .documentation("The target is lifted with the worm from here.")
                    .add()
                    .append(new KeyedCodec<>("BreachSwallowAt", Codec.DOUBLE, false),
                            (config, value) -> config.breachSwallowAt = value,
                            config -> config.breachSwallowAt)
                    .documentation("The worm is at full height; the target is swallowed (killed, or thrown with DevourKills off).")
                    .add()
                    .append(new KeyedCodec<>("BreachDiveAt", Codec.DOUBLE, false),
                            (config, value) -> config.breachDiveAt = value,
                            config -> config.breachDiveAt)
                    .documentation("The worm starts to arc over and dive after hanging from the swallow.")
                    .add()
                    .append(new KeyedCodec<>("BreachDiveEnd", Codec.DOUBLE, false),
                            (config, value) -> config.breachDiveEnd = value,
                            config -> config.breachDiveEnd)
                    .documentation("The worm is under again; a second, lower burst and a falling rumble. It is removed just after.")
                    .add()
                    .append(new KeyedCodec<>("BreachWormSize", Codec.DOUBLE, false),
                            (config, value) -> config.breachWormSize = value,
                            config -> config.breachWormSize)
                    .documentation("Scale of the breach worm. 3 is the same worm as /wormtest worm: about 18 blocks wide and 30 above the surface at the top.")
                    .add()
                    .append(new KeyedCodec<>("BreachDustScale", Codec.DOUBLE, false),
                            (config, value) -> config.breachDustScale = value,
                            config -> config.breachDustScale)
                    .documentation("Particle scale of the breach dust.")
                    .add()
                    .append(new KeyedCodec<>("BreachDebrisScale", Codec.DOUBLE, false),
                            (config, value) -> config.breachDebrisScale = value,
                            config -> config.breachDebrisScale)
                    .documentation("Particle scale of the thrown chunks of sand.")
                    .add()
                    .append(new KeyedCodec<>("BreachShakeRange", Codec.DOUBLE, false),
                            (config, value) -> config.breachShakeRange = value,
                            config -> config.breachShakeRange)
                    .documentation("Blocks. Players this close to the breach get camera shake and see the worm.")
                    .add()
                    .append(new KeyedCodec<>("BreachShakeStrength", Codec.DOUBLE, false),
                            (config, value) -> config.breachShakeStrength = value,
                            config -> config.breachShakeStrength)
                    .documentation("Intensity of the breach camera shake (times ShakeStrength).")
                    .add()
                    .append(new KeyedCodec<>("BreachBoomVolume", Codec.DOUBLE, false),
                            (config, value) -> config.breachBoomVolume = value,
                            config -> config.breachBoomVolume)
                    .documentation("Volume of the breach sounds within BreachShakeRange.")
                    .add()
                    .append(new KeyedCodec<>("BreachFarVolume", Codec.DOUBLE, false),
                            (config, value) -> config.breachFarVolume = value,
                            config -> config.breachFarVolume)
                    .documentation("Volume of the breach sounds for players further away, up to GroupRadius. Flat.")
                    .add()
                    .append(new KeyedCodec<>("DevourKills", Codec.BOOLEAN, false),
                            (config, value) -> config.devourKills = value,
                            config -> config.devourKills)
                    .documentation("False: the breach only throws the target and hurts them badly (they survive), for testing or a gentler worm.")
                    .add()
                    .append(new KeyedCodec<>("DevourDropsItems", Codec.BOOLEAN, false),
                            (config, value) -> config.devourDropsItems = value,
                            config -> config.devourDropsItems)
                    .documentation("False: the target dies with an empty inventory and nothing is dropped.")
                    .add()
                    .append(new KeyedCodec<>("DevourHurtFraction", Codec.DOUBLE, false),
                            (config, value) -> config.devourHurtFraction = value,
                            config -> config.devourHurtFraction)
                    .documentation("With DevourKills off, the share of the target current health taken (never lethal).")
                    .add()
                    .append(new KeyedCodec<>("CameraShakeScale", Codec.DOUBLE, false),
                            (config, value) -> config.cameraShakeScale = value,
                            config -> config.cameraShakeScale)
                    .documentation("Overall multiplier on every camera shake this mod sends (1 = the original strength).")
                    .add()
                    .append(new KeyedCodec<>("CameraZoom", Codec.BOOLEAN, false),
                            (config, value) -> config.cameraZoom = value,
                            config -> config.cameraZoom)
                    .documentation("Pull the target camera out into third person in the last ZoomSeconds of LOCKED so they can see the worm attack.")
                    .add()
                    .append(new KeyedCodec<>("ZoomSeconds", Codec.DOUBLE, false),
                            (config, value) -> config.zoomSeconds = value,
                            config -> config.zoomSeconds)
                    .documentation("The camera zoom out takes this long, ending when LOCKED ends.")
                    .add()
                    .append(new KeyedCodec<>("ZoomFromDistance", Codec.DOUBLE, false),
                            (config, value) -> config.zoomFromDistance = value,
                            config -> config.zoomFromDistance)
                    .documentation("Camera distance in blocks the zoom starts from (the usual third person distance).")
                    .add()
                    .append(new KeyedCodec<>("BreachHold", Codec.BOOLEAN, false),
                            (config, value) -> config.breachHold = value,
                            config -> config.breachHold)
                    .documentation("Everyone in the swallow zone is held, pulled down into the sand and killed at the bottom. False turns the hold and the sink off: the swallow then kills the target at BreachSwallowAt and nobody else dies.")
                    .add()
                    .append(new KeyedCodec<>("BreachSwallowZoneFactor", Codec.DOUBLE, false),
                            (config, value) -> config.breachSwallowZoneFactor = value,
                            config -> config.breachSwallowZoneFactor)
                    .documentation("The swallow zone is this times the worm radius, measured from the breach centre, decided when the worm appears.")
                    .add()
                    .append(new KeyedCodec<>("BreachCameraRadius", Codec.DOUBLE, false),
                            (config, value) -> config.breachCameraRadius = value,
                            config -> config.breachCameraRadius)
                    .documentation("Blocks. Everyone this close to the breach gets the pulled-out camera. 0 means worm radius times 4 plus 20.")
                    .add()
                    .append(new KeyedCodec<>("BreachKillAt", Codec.DOUBLE, false),
                            (config, value) -> config.breachKillAt = value,
                            config -> config.breachKillAt)
                    .documentation("Seconds into BREACH when held players are killed. -1 means BreachDiveEnd plus BreachKillDelay (never before the sink has ended); any other value overrides both.")
                    .add()
                    .append(new KeyedCodec<>("BreachSinkAt", Codec.DOUBLE, false),
                            (config, value) -> config.breachSinkAt = value,
                            config -> config.breachSinkAt)
                    .documentation("Seconds into BREACH when held players start to be pulled down into the sand.")
                    .add()
                    .append(new KeyedCodec<>("BreachSinkSeconds", Codec.DOUBLE, false),
                            (config, value) -> config.breachSinkSeconds = value,
                            config -> config.breachSinkSeconds)
                    .documentation("Seconds the pull down takes; it ends at the bottom, where they are killed.")
                    .add()
                    .append(new KeyedCodec<>("BreachSinkDepth", Codec.DOUBLE, false),
                            (config, value) -> config.breachSinkDepth = value,
                            config -> config.breachSinkDepth)
                    .documentation("Blocks the held players are pulled down (a fixed number for now, it does not scale with the worm).")
                    .add()
                    .append(new KeyedCodec<>("BreachKillDelay", Codec.DOUBLE, false),
                            (config, value) -> config.breachKillDelay = value,
                            config -> config.breachKillDelay)
                    .documentation("Seconds after the worm has gone back under the sand (BreachDiveEnd) that held players are killed, or released with DevourKills off. Ignored if BreachKillAt is set.")
                    .add()
                    .append(new KeyedCodec<>("BreachCameraReturnAt", Codec.DOUBLE, false),
                            (config, value) -> config.breachCameraReturnAt = value,
                            config -> config.breachCameraReturnAt)
                    .documentation("Seconds into BREACH when the pulled-out camera eases back. -1 means BreachDiveEnd plus 1.")
                    .add()
                    .append(new KeyedCodec<>("ZoomWormHeightFactor", Codec.DOUBLE, false),
                            (config, value) -> config.zoomWormHeightFactor = value,
                            config -> config.zoomWormHeightFactor)
                    .documentation("The pulled-out camera distance is this times the worm height (10 blocks times BreachWormSize), up to ZoomMaxDistance.")
                    .add()
                    .append(new KeyedCodec<>("ZoomMaxDistance", Codec.DOUBLE, false),
                            (config, value) -> config.zoomMaxDistance = value,
                            config -> config.zoomMaxDistance)
                    .documentation("Blocks. The longest the pulled-out camera distance can be.")
                    .add()
                    .append(new KeyedCodec<>("BreachCameraMode", Codec.STRING, false),
                            (config, value) -> config.breachCameraMode = value,
                            config -> config.breachCameraMode)
                    .documentation("distance: pull the player camera back. fixed: a camera placed on the player side of the worm, aimed at it.")
                    .add()
                    .append(new KeyedCodec<>("StartBoomDustScale", Codec.DOUBLE, false),
                            (config, value) -> config.startBoomDustScale = value,
                            config -> config.startBoomDustScale)
                    .documentation("Particle scale of the dust burst at the trigger being's feet when an event begins.")
                    .add()
                    .append(new KeyedCodec<>("StartRippleDelay", Codec.DOUBLE, false),
                            (config, value) -> config.startRippleDelay = value,
                            config -> config.startRippleDelay)
                    .documentation("Seconds after the start boom that everyone in the list who stands on sand feels the warning ripple.")
                    .add()
                    .append(new KeyedCodec<>("BreachRippleMaxRings", Codec.DOUBLE, false),
                            (config, value) -> config.breachRippleMaxRings = value,
                            config -> config.breachRippleMaxRings)
                    .documentation("Most rings the breach heave is allowed (it uses ceil of the worm radius, so 8 is a 17 by 17 patch).")
                    .add()
                    .append(new KeyedCodec<>("BreachLiftsVictim", Codec.BOOLEAN, false),
                            (config, value) -> config.breachLiftsVictim = value,
                            config -> config.breachLiftsVictim)
                    .documentation("Lift the victim up with the worm before the swallow (the old behaviour). Off by default.")
                    .add()
                    .append(new KeyedCodec<>("RippleShake", Codec.BOOLEAN, false),
                            (config, value) -> config.rippleShake = value,
                            config -> config.rippleShake)
                    .documentation("Sink-and-rebound ripple: a small camera shake on the first drop and on the first two rebound peaks.")
                    .add()
                    .append(new KeyedCodec<>("RippleShakeStrength", Codec.DOUBLE, false),
                            (config, value) -> config.rippleShakeStrength = value,
                            config -> config.rippleShakeStrength)
                    .documentation("Intensity of that shake (times CameraShakeScale), before the fall-off with distance.")
                    .add()
                    .append(new KeyedCodec<>("SinkIncrement", Codec.DOUBLE, false),
                            (config, value) -> config.sinkIncrement = value,
                            config -> config.sinkIncrement)
                    .documentation("Pixels the centre drops on each descent step (1 to 4); everything else scales with it.")
                    .add()
                    .append(new KeyedCodec<>("SinkRings", Codec.DOUBLE, false),
                            (config, value) -> config.sinkRings = value,
                            config -> config.sinkRings)
                    .documentation("Rings round the centre block (3 is a 7 by 7 patch).")
                    .add()
                    .append(new KeyedCodec<>("SinkStepSeconds", Codec.DOUBLE, false),
                            (config, value) -> config.sinkStepSeconds = value,
                            config -> config.sinkStepSeconds)
                    .documentation("Seconds between descent steps.")
                    .add()
                    .append(new KeyedCodec<>("SinkSwingSeconds", Codec.DOUBLE, false),
                            (config, value) -> config.sinkSwingSeconds = value,
                            config -> config.sinkSwingSeconds)
                    .documentation("Seconds for one swing of the rebound, before the per-block random factor.")
                    .add()
                    .append(new KeyedCodec<>("SinkSwingJitter", Codec.DOUBLE, false),
                            (config, value) -> config.sinkSwingJitter = value,
                            config -> config.sinkSwingJitter)
                    .documentation("Each block swings this much faster or slower (a fraction, 0.15 is 0.85 to 1.15).")
                    .add()
                    .append(new KeyedCodec<>("SinkDropMin", Codec.DOUBLE, false),
                            (config, value) -> config.sinkDropMin = value,
                            config -> config.sinkDropMin)
                    .documentation("Smallest drop of a ring block per step, times the increment (at least 1 px).")
                    .add()
                    .append(new KeyedCodec<>("SinkDropMax", Codec.DOUBLE, false),
                            (config, value) -> config.sinkDropMax = value,
                            config -> config.sinkDropMax)
                    .documentation("Largest drop of a ring block per step, times the increment.")
                    .add()
                    .append(new KeyedCodec<>("SinkReboundMin", Codec.DOUBLE, false),
                            (config, value) -> config.sinkReboundMin = value,
                            config -> config.sinkReboundMin)
                    .documentation("The first rebound peak is at least this fraction of the depth.")
                    .add()
                    .append(new KeyedCodec<>("SinkReboundMax", Codec.DOUBLE, false),
                            (config, value) -> config.sinkReboundMax = value,
                            config -> config.sinkReboundMax)
                    .documentation("The first rebound peak is at most this fraction of the depth.")
                    .add()
                    .append(new KeyedCodec<>("SinkDecayMin", Codec.DOUBLE, false),
                            (config, value) -> config.sinkDecayMin = value,
                            config -> config.sinkDecayMin)
                    .documentation("Each later swing is at least this fraction of the one before.")
                    .add()
                    .append(new KeyedCodec<>("SinkDecayMax", Codec.DOUBLE, false),
                            (config, value) -> config.sinkDecayMax = value,
                            config -> config.sinkDecayMax)
                    .documentation("Each later swing is at most this fraction of the one before.")
                    .add()
                    .append(new KeyedCodec<>("DisturbanceEnabled", Codec.BOOLEAN, false),
                            (config, value) -> config.disturbanceEnabled = value,
                            config -> config.disturbanceEnabled)
                    .documentation("Placing a block on Arrakis_Sand starts a rumble in that chunk, and the worm takes what was placed. False turns it all off.")
                    .add()
                    .append(new KeyedCodec<>("DisturbanceSeconds", Codec.DOUBLE, false),
                            (config, value) -> config.disturbanceSeconds = value,
                            config -> config.disturbanceSeconds)
                    .documentation("Seconds from the first placement to the end of the disturbance, when the held players are killed.")
                    .add()
                    .append(new KeyedCodec<>("DisturbanceSwallowLead", Codec.DOUBLE, false),
                            (config, value) -> config.disturbanceSwallowLead = value,
                            config -> config.disturbanceSwallowLead)
                    .documentation("Seconds before the end that everyone standing on Arrakis_Sand or a placed block in the chunk is held (decided once, then).")
                    .add()
                    .append(new KeyedCodec<>("DisturbanceBoomLead", Codec.DOUBLE, false),
                            (config, value) -> config.disturbanceBoomLead = value,
                            config -> config.disturbanceBoomLead)
                    .documentation("Seconds after the capture that the dust and the boom come, every placed block is removed and the held players start to sink.")
                    .add()
                    .append(new KeyedCodec<>("DisturbancePulseSeconds", Codec.DOUBLE, false),
                            (config, value) -> config.disturbancePulseSeconds = value,
                            config -> config.disturbancePulseSeconds)
                    .documentation("Each sand column starts one pulse at a random moment within every period of this length.")
                    .add()
                    .append(new KeyedCodec<>("DisturbanceIncrementStart", Codec.DOUBLE, false),
                            (config, value) -> config.disturbanceIncrementStart = value,
                            config -> config.disturbanceIncrementStart)
                    .documentation("Pixels of the first drop of a pulse at the centre of the placed blocks, at the start of the disturbance.")
                    .add()
                    .append(new KeyedCodec<>("DisturbanceIncrementEnd", Codec.DOUBLE, false),
                            (config, value) -> config.disturbanceIncrementEnd = value,
                            config -> config.disturbanceIncrementEnd)
                    .documentation("The same at the end of the disturbance; it rises in between.")
                    .add()
                    .append(new KeyedCodec<>("DisturbanceMaxEditsPerTick", Codec.DOUBLE, false),
                            (config, value) -> config.disturbanceMaxEditsPerTick = value,
                            config -> config.disturbanceMaxEditsPerTick)
                    .documentation("Most fake block edits sent in one tick (a moving column is two edits); the rest wait for the next ticks.")
                    .add()
                    .append(new KeyedCodec<>("DisturbanceViewDistance", Codec.DOUBLE, false),
                            (config, value) -> config.disturbanceViewDistance = value,
                            config -> config.disturbanceViewDistance)
                    .documentation("Blocks from the chunk centre. Players this close see the sand rumble.")
                    .add()
                    .append(new KeyedCodec<>("DisturbanceDustScale", Codec.DOUBLE, false),
                            (config, value) -> config.disturbanceDustScale = value,
                            config -> config.disturbanceDustScale)
                    .documentation("Particle scale of the dust when the worm takes the blocks (the same default as BreachDustScale).")
                    .add()
                    .append(new KeyedCodec<>("DisturbanceMaxBursts", Codec.DOUBLE, false),
                            (config, value) -> config.disturbanceMaxBursts = value,
                            config -> config.disturbanceMaxBursts)
                    .documentation("Most dust bursts at placed block positions when they vanish; more positions are sampled.")
                    .add()
                    .append(new KeyedCodec<>("DisturbanceCreativeTriggers", Codec.BOOLEAN, false),
                            (config, value) -> config.disturbanceCreativeTriggers = value,
                            config -> config.disturbanceCreativeTriggers)
                    .documentation("Blocks placed by Creative players start a disturbance and are tracked (a Creative player is never killed).")
                    .add()
                    .append(new KeyedCodec<>("DisturbanceLagFallback", Codec.BOOLEAN, false),
                            (config, value) -> config.disturbanceLagFallback = value,
                            config -> config.disturbanceLagFallback)
                    .documentation("Drop the rumble visuals and shorten the event when the server is lagging. False turns the check off.")
                    .add()
                    .append(new KeyedCodec<>("DisturbanceLagMillis", Codec.DOUBLE, false),
                            (config, value) -> config.disturbanceLagMillis = value,
                            config -> config.disturbanceLagMillis)
                    .documentation("Milliseconds. The fallback is used if the average time between ticks over the last two seconds is above this. 0 means 1.5 times the normal tick length.")
                    .add()
                    .append(new KeyedCodec<>("DisturbanceMaxCost", Codec.DOUBLE, false),
                            (config, value) -> config.disturbanceMaxCost = value,
                            config -> config.disturbanceMaxCost)
                    .documentation("The fallback is also used if columns times viewers is above this (a full chunk is 1024 columns).")
                    .add()
                    .append(new KeyedCodec<>("DisturbanceFallbackSeconds", Codec.DOUBLE, false),
                            (config, value) -> config.disturbanceFallbackSeconds = value,
                            config -> config.disturbanceFallbackSeconds)
                    .documentation("In the fallback the event ends this many seconds after it started (or at once if that is past).")
                    .add()
                    .append(new KeyedCodec<>("MovementAggroPerBlock", Codec.DOUBLE, false),
                            (config, value) -> config.movementAggroPerBlock = value,
                            config -> config.movementAggroPerBlock)
                    .documentation("Aggro per horizontal block moved on sand, flat for walking, running, sprinting and jumping.")
                    .add()
                    .append(new KeyedCodec<>("StaminaAggroFactor", Codec.DOUBLE, false),
                            (config, value) -> config.staminaAggroFactor = value,
                            config -> config.staminaAggroFactor)
                    .documentation("Aggro added per point of stamina spent on sand (only decreases count; regeneration is ignored). 0 turns it off.")
                    .add()
                    .append(new KeyedCodec<>("AggroEffectMultipliers", new MapCodec<>(Codec.DOUBLE, HashMap::new), false),
                            (config, value) -> config.aggroEffectMultipliers = value,
                            config -> config.aggroEffectMultipliers)
                    .documentation("Entity effect id to a factor: a being that has the effect gains that many times the aggro from movement, stamina spent and other mods' sources. Ids that do not exist in this game are skipped. The default is the Holtzman field of Equipment of Arrakis.")
                    .add()
                    .append(new KeyedCodec<>("AggroMultiplierMax", Codec.DOUBLE, false),
                            (config, value) -> config.aggroMultiplierMax = value,
                            config -> config.aggroMultiplierMax)
                    .documentation("The product of a being's effect factors is limited to this, so stacking cannot run away.")
                    .add()
                    .append(new KeyedCodec<>("StalkBaseSeconds", Codec.DOUBLE, false),
                            (config, value) -> config.stalkBaseSeconds = value,
                            config -> config.stalkBaseSeconds)
                    .documentation("Stalk length is max(StalkMinSeconds, StalkBaseSeconds - StalkStepSeconds * players in the list).")
                    .add()
                    .append(new KeyedCodec<>("StalkStepSeconds", Codec.DOUBLE, false),
                            (config, value) -> config.stalkStepSeconds = value,
                            config -> config.stalkStepSeconds)
                    .documentation("Seconds taken off the stalk for each player in the list.")
                    .add()
                    .append(new KeyedCodec<>("StalkMinSeconds", Codec.DOUBLE, false),
                            (config, value) -> config.stalkMinSeconds = value,
                            config -> config.stalkMinSeconds)
                    .documentation("The shortest stalk.")
                    .add()
                    .append(new KeyedCodec<>("EntityStalkSeconds", Codec.DOUBLE, false),
                            (config, value) -> config.entityStalkSeconds = value,
                            config -> config.entityStalkSeconds)
                    .documentation("Stalk length when the being that triggered the event is not a player (the target is then that being).")
                    .add()
                    .append(new KeyedCodec<>("StalkPulseSeconds", Codec.DOUBLE, false),
                            (config, value) -> config.stalkPulseSeconds = value,
                            config -> config.stalkPulseSeconds)
                    .documentation("Every listed being on sand gets a ripple, a stumble and a small shake this often during STALKING.")
                    .add()
                    .append(new KeyedCodec<>("StumbleSeconds", Codec.DOUBLE, false),
                            (config, value) -> config.stumbleSeconds = value,
                            config -> config.stumbleSeconds)
                    .documentation("How long the stumble (a brief near-stop) lasts.")
                    .add()
                    .append(new KeyedCodec<>("StumbleSlowPercent", Codec.DOUBLE, false),
                            (config, value) -> config.stumbleSlowPercent = value,
                            config -> config.stumbleSlowPercent)
                    .documentation("Speed left during the stumble, in percent (the nearest Arrakis_Worm_Slow_<percent> asset, 10 to 95 in steps of 5).")
                    .add()
                    .append(new KeyedCodec<>("MobTrackRadius", Codec.DOUBLE, false),
                            (config, value) -> config.mobTrackRadius = value,
                            config -> config.mobTrackRadius)
                    .documentation("Blocks. A hostile mob is tracked (and gains aggro) only while it is this close to at least one player, or while it is in an event. 0 means GroupRadius.")
                    .add()
                    .append(new KeyedCodec<>("MobAggroMultiplier", Codec.DOUBLE, false),
                            (config, value) -> config.mobAggroMultiplier = value,
                            config -> config.mobAggroMultiplier)
                    .documentation("A mob gains this share of what a player gains for the same distance on sand (0.25: about 400 blocks to reach 100).")
                    .add()
                    .append(new KeyedCodec<>("MobRoleDeny", Codec.STRING_ARRAY, false),
                            (config, value) -> config.mobRoleDeny = value,
                            config -> config.mobRoleDeny)
                    .documentation("NPC role names that are never counted as mobs, whatever their attitude.")
                    .add()
                    .append(new KeyedCodec<>("MobRoleAllow", Codec.STRING_ARRAY, false),
                            (config, value) -> config.mobRoleAllow = value,
                            config -> config.mobRoleAllow)
                    .documentation("NPC role names that are always counted as mobs, whatever their attitude.")
                    .add()
                    .append(new KeyedCodec<>("TotemDeployableIds", Codec.STRING_ARRAY, false),
                            (config, value) -> config.totemDeployableIds = value,
                            config -> config.totemDeployableIds)
                    .documentation("Deployable config ids that count as totems (decoys). Other mods add theirs with WormAggro.registerTotem; /worm totem add edits it for the session.")
                    .add()
                    .append(new KeyedCodec<>("TotemStartAggro", Codec.DOUBLE, false),
                            (config, value) -> config.totemStartAggro = value,
                            config -> config.totemStartAggro)
                    .documentation("Aggro a totem has the moment it lands on sand.")
                    .add()
                    .append(new KeyedCodec<>("TotemAggroPerSecond", Codec.DOUBLE, false),
                            (config, value) -> config.totemAggroPerSecond = value,
                            config -> config.totemAggroPerSecond)
                    .documentation("Aggro a totem gains per second for as long as it exists.")
                    .add()
                    .append(new KeyedCodec<>("DevourMobDrops", Codec.BOOLEAN, false),
                            (config, value) -> config.devourMobDrops = value,
                            config -> config.devourMobDrops)
                    .documentation("False: a mob the worm swallows is removed so that it drops nothing. True: it is killed and drops its loot as usual.")
                    .add()
                    .append(new KeyedCodec<>("LockedSeconds", Codec.DOUBLE, false),
                            (config, value) -> config.lockedSeconds = value,
                            config -> config.lockedSeconds)
                    .documentation("LOCKED phase length: the target is slowed, the vignette rises and the camera pulls out, then the breach. Replaces LockSeconds (default 4), which saved configs still hold.")
                    .add()
                    // END GENERATED CODEC
                    .build();

    private String[] sandBlocks = {};
    private double aggroThreshold = 100;
    private double stillDecayPerSecond = 1;
    private double groupRadius = 200;
    private double minGainSpeed = 0.5;
    private double sandTolerance = 1.0;
    private double breachSeconds = 8;
    private double offSandDecayMultiplier = 10;
    private boolean debugChat = false;
    // GENERATED FIELDS
    private double wormsignViewDistance = 220;
    private double wormsignTrailInterval = 0.25;
    private double wormsignScale = 3;
    private double rumbleBystanderVolume = 0.7;
    private double rumbleTargetStartVolume = 0.3;
    private double rumbleTargetVolume = 1;
    private double rumbleTargetEndPitch = 0.85;
    private double rumbleIntervalSeconds = 3;
    private double rippleStartInterval = 4;
    private double rippleEndInterval = 1;
    private double rippleViewDistance = 30;
    private double lockedRippleInterval = 0.2;
    private double vignetteStrength = 1;
    private boolean cameraShake = true;
    private double shakeStrength = 1;
    private double slowFloor = 0.15;
    private double slowJumpFloor = 0.5;
    private String slowMethod = "effect";
    private double fadeOutSeconds = 1;
    private double breachBoomAt = 0;
    private double breachWormAt = 0.2;
    private double breachLiftAt = 0.6;
    private double breachSwallowAt = 1.2;
    private double breachDiveAt = 1.8;
    private double breachDiveEnd = 3;
    private double breachWormSize = 3;
    private double breachDustScale = 6;
    private double breachDebrisScale = 3;
    private double breachShakeRange = 40;
    private double breachShakeStrength = 1.5;
    private double breachBoomVolume = 1;
    private double breachFarVolume = 0.8;
    private boolean devourKills = true;
    private boolean devourDropsItems = false;
    private double devourHurtFraction = 0.8;
    private double cameraShakeScale = 0.5;
    private boolean cameraZoom = true;
    private double zoomSeconds = 2;
    private double zoomFromDistance = 5;
    private boolean breachHold = true;
    private double breachSwallowZoneFactor = 1.15;
    private double breachCameraRadius = 0;
    private double breachKillAt = -1;
    private double breachSinkAt = 1.9;
    private double breachSinkSeconds = 0.5;
    private double breachSinkDepth = 3;
    private double breachKillDelay = 0.5;
    private double breachCameraReturnAt = -1;
    private double zoomWormHeightFactor = 0.6667;
    private double zoomMaxDistance = 60;
    private String breachCameraMode = "distance";
    private double startBoomDustScale = 3;
    private double startRippleDelay = 1;
    private double breachRippleMaxRings = 8;
    private boolean breachLiftsVictim = false;
    private boolean rippleShake = true;
    private double rippleShakeStrength = 0.5;
    private double sinkIncrement = 2;
    private double sinkRings = 4;
    private double sinkStepSeconds = 0.1;
    private double sinkSwingSeconds = 0.16;
    private double sinkSwingJitter = 0.15;
    private double sinkDropMin = 0.5;
    private double sinkDropMax = 1.5;
    private double sinkReboundMin = 0.75;
    private double sinkReboundMax = 0.88;
    private double sinkDecayMin = 0.55;
    private double sinkDecayMax = 0.85;
    private boolean disturbanceEnabled = true;
    private double disturbanceSeconds = 10;
    private double disturbanceSwallowLead = 1;
    private double disturbanceBoomLead = 0.5;
    private double disturbancePulseSeconds = 1.2;
    private double disturbanceIncrementStart = 1;
    private double disturbanceIncrementEnd = 3;
    private double disturbanceMaxEditsPerTick = 600;
    private double disturbanceViewDistance = 128;
    private double disturbanceDustScale = 6;
    private double disturbanceMaxBursts = 64;
    private boolean disturbanceCreativeTriggers = true;
    private boolean disturbanceLagFallback = true;
    private double disturbanceLagMillis = 0;
    private double disturbanceMaxCost = 6000;
    private double disturbanceFallbackSeconds = 2;
    private double movementAggroPerBlock = 1;
    private double staminaAggroFactor = 1;
    private Map<String, Double> aggroEffectMultipliers = new HashMap<>(Map.of("Arrakis_Holtzman_Field", 10.0));
    private double aggroMultiplierMax = 20;
    private double stalkBaseSeconds = 45;
    private double stalkStepSeconds = 5;
    private double stalkMinSeconds = 15;
    private double entityStalkSeconds = 10;
    private double stalkPulseSeconds = 5;
    private double stumbleSeconds = 0.6;
    private double stumbleSlowPercent = 10;
    private double mobTrackRadius = 200;
    private double mobAggroMultiplier = 0.25;
    private String[] mobRoleDeny = {};
    private String[] mobRoleAllow = {};
    private String[] totemDeployableIds = {"Slowness_Totem", "Healing_Totem"};
    private double totemStartAggro = 100;
    private double totemAggroPerSecond = 5;
    private boolean devourMobDrops = false;
    private double lockedSeconds = 5;
    // END GENERATED FIELDS

    public String[] getSandBlocks() {
        return sandBlocks;
    }

    public double getAggroThreshold() {
        return aggroThreshold;
    }

    public double getStillDecayPerSecond() {
        return stillDecayPerSecond;
    }

    public double getGroupRadius() {
        return groupRadius;
    }

    public double getMinGainSpeed() {
        return minGainSpeed;
    }

    public double getSandTolerance() {
        return sandTolerance;
    }

    public double getBreachSeconds() {
        return breachSeconds;
    }

    public double getOffSandDecayMultiplier() {
        return offSandDecayMultiplier;
    }

    public boolean isDebugChat() {
        return debugChat;
    }



























    // GENERATED GETTERS
    public double getWormsignViewDistance() {
        return wormsignViewDistance;
    }

    public double getWormsignTrailInterval() {
        return wormsignTrailInterval;
    }

    public double getWormsignScale() {
        return wormsignScale;
    }

    public double getRumbleBystanderVolume() {
        return rumbleBystanderVolume;
    }

    public double getRumbleTargetStartVolume() {
        return rumbleTargetStartVolume;
    }

    public double getRumbleTargetVolume() {
        return rumbleTargetVolume;
    }

    public double getRumbleTargetEndPitch() {
        return rumbleTargetEndPitch;
    }

    public double getRumbleIntervalSeconds() {
        return rumbleIntervalSeconds;
    }

    public double getRippleStartInterval() {
        return rippleStartInterval;
    }

    public double getRippleEndInterval() {
        return rippleEndInterval;
    }

    public double getRippleViewDistance() {
        return rippleViewDistance;
    }

    public double getLockedRippleInterval() {
        return lockedRippleInterval;
    }

    public double getVignetteStrength() {
        return vignetteStrength;
    }

    public boolean isCameraShake() {
        return cameraShake;
    }

    public double getShakeStrength() {
        return shakeStrength;
    }

    public double getSlowFloor() {
        return slowFloor;
    }

    public double getSlowJumpFloor() {
        return slowJumpFloor;
    }

    public String getSlowMethod() {
        return slowMethod;
    }

    public double getFadeOutSeconds() {
        return fadeOutSeconds;
    }

    public double getBreachBoomAt() {
        return breachBoomAt;
    }

    public double getBreachWormAt() {
        return breachWormAt;
    }

    public double getBreachLiftAt() {
        return breachLiftAt;
    }

    public double getBreachSwallowAt() {
        return breachSwallowAt;
    }

    public double getBreachDiveAt() {
        return breachDiveAt;
    }

    public double getBreachDiveEnd() {
        return breachDiveEnd;
    }

    public double getBreachWormSize() {
        return breachWormSize;
    }

    public double getBreachDustScale() {
        return breachDustScale;
    }

    public double getBreachDebrisScale() {
        return breachDebrisScale;
    }

    public double getBreachShakeRange() {
        return breachShakeRange;
    }

    public double getBreachShakeStrength() {
        return breachShakeStrength;
    }

    public double getBreachBoomVolume() {
        return breachBoomVolume;
    }

    public double getBreachFarVolume() {
        return breachFarVolume;
    }

    public boolean isDevourKills() {
        return devourKills;
    }

    public boolean isDevourDropsItems() {
        return devourDropsItems;
    }

    public double getDevourHurtFraction() {
        return devourHurtFraction;
    }

    public double getCameraShakeScale() {
        return cameraShakeScale;
    }

    public boolean isCameraZoom() {
        return cameraZoom;
    }

    public double getZoomSeconds() {
        return zoomSeconds;
    }

    public double getZoomFromDistance() {
        return zoomFromDistance;
    }

    public boolean isBreachHold() {
        return breachHold;
    }

    public double getBreachSwallowZoneFactor() {
        return breachSwallowZoneFactor;
    }

    public double getBreachCameraRadius() {
        return breachCameraRadius;
    }

    public double getBreachKillAt() {
        return breachKillAt;
    }

    public double getBreachSinkAt() {
        return breachSinkAt;
    }

    public double getBreachSinkSeconds() {
        return breachSinkSeconds;
    }

    public double getBreachSinkDepth() {
        return breachSinkDepth;
    }

    public double getBreachKillDelay() {
        return breachKillDelay;
    }

    public double getBreachCameraReturnAt() {
        return breachCameraReturnAt;
    }

    public double getZoomWormHeightFactor() {
        return zoomWormHeightFactor;
    }

    public double getZoomMaxDistance() {
        return zoomMaxDistance;
    }

    public String getBreachCameraMode() {
        return breachCameraMode;
    }

    public double getStartBoomDustScale() {
        return startBoomDustScale;
    }

    public double getStartRippleDelay() {
        return startRippleDelay;
    }

    public double getBreachRippleMaxRings() {
        return breachRippleMaxRings;
    }

    public boolean isBreachLiftsVictim() {
        return breachLiftsVictim;
    }

    public boolean isRippleShake() {
        return rippleShake;
    }

    public double getRippleShakeStrength() {
        return rippleShakeStrength;
    }

    public double getSinkIncrement() {
        return sinkIncrement;
    }

    public double getSinkRings() {
        return sinkRings;
    }

    public double getSinkStepSeconds() {
        return sinkStepSeconds;
    }

    public double getSinkSwingSeconds() {
        return sinkSwingSeconds;
    }

    public double getSinkSwingJitter() {
        return sinkSwingJitter;
    }

    public double getSinkDropMin() {
        return sinkDropMin;
    }

    public double getSinkDropMax() {
        return sinkDropMax;
    }

    public double getSinkReboundMin() {
        return sinkReboundMin;
    }

    public double getSinkReboundMax() {
        return sinkReboundMax;
    }

    public double getSinkDecayMin() {
        return sinkDecayMin;
    }

    public double getSinkDecayMax() {
        return sinkDecayMax;
    }

    public boolean isDisturbanceEnabled() {
        return disturbanceEnabled;
    }

    public double getDisturbanceSeconds() {
        return disturbanceSeconds;
    }

    public double getDisturbanceSwallowLead() {
        return disturbanceSwallowLead;
    }

    public double getDisturbanceBoomLead() {
        return disturbanceBoomLead;
    }

    public double getDisturbancePulseSeconds() {
        return disturbancePulseSeconds;
    }

    public double getDisturbanceIncrementStart() {
        return disturbanceIncrementStart;
    }

    public double getDisturbanceIncrementEnd() {
        return disturbanceIncrementEnd;
    }

    public double getDisturbanceMaxEditsPerTick() {
        return disturbanceMaxEditsPerTick;
    }

    public double getDisturbanceViewDistance() {
        return disturbanceViewDistance;
    }

    public double getDisturbanceDustScale() {
        return disturbanceDustScale;
    }

    public double getDisturbanceMaxBursts() {
        return disturbanceMaxBursts;
    }

    public boolean isDisturbanceCreativeTriggers() {
        return disturbanceCreativeTriggers;
    }

    public boolean isDisturbanceLagFallback() {
        return disturbanceLagFallback;
    }

    public double getDisturbanceLagMillis() {
        return disturbanceLagMillis;
    }

    public double getDisturbanceMaxCost() {
        return disturbanceMaxCost;
    }

    public double getDisturbanceFallbackSeconds() {
        return disturbanceFallbackSeconds;
    }

    public double getMovementAggroPerBlock() {
        return movementAggroPerBlock;
    }

    public Map<String, Double> getAggroEffectMultipliers() {
        return aggroEffectMultipliers;
    }

    public double getAggroMultiplierMax() {
        return aggroMultiplierMax;
    }

    public double getStaminaAggroFactor() {
        return staminaAggroFactor;
    }

    public double getStalkBaseSeconds() {
        return stalkBaseSeconds;
    }

    public double getStalkStepSeconds() {
        return stalkStepSeconds;
    }

    public double getStalkMinSeconds() {
        return stalkMinSeconds;
    }

    public double getEntityStalkSeconds() {
        return entityStalkSeconds;
    }

    public double getStalkPulseSeconds() {
        return stalkPulseSeconds;
    }

    public double getStumbleSeconds() {
        return stumbleSeconds;
    }

    public double getStumbleSlowPercent() {
        return stumbleSlowPercent;
    }

    public double getMobTrackRadius() {
        return mobTrackRadius;
    }

    public double getMobAggroMultiplier() {
        return mobAggroMultiplier;
    }

    public String[] getMobRoleDeny() {
        return mobRoleDeny;
    }

    public String[] getMobRoleAllow() {
        return mobRoleAllow;
    }

    public String[] getTotemDeployableIds() {
        return totemDeployableIds;
    }

    public double getTotemStartAggro() {
        return totemStartAggro;
    }

    public double getTotemAggroPerSecond() {
        return totemAggroPerSecond;
    }

    public boolean isDevourMobDrops() {
        return devourMobDrops;
    }

    public double getLockedSeconds() {
        return lockedSeconds;
    }
    // END GENERATED GETTERS
}
