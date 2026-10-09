package com.paulorchard.islandcraft.wormsofarrakis;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;

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
                    .documentation("Group total that starts a worm event.")
                    .add()
                    .append(new KeyedCodec<>("WalkAggroPerBlock", Codec.DOUBLE, false),
                            (config, value) -> config.walkAggroPerBlock = value,
                            config -> config.walkAggroPerBlock)
                    .documentation("Aggro per block travelled on sand at walking pace.")
                    .add()
                    .append(new KeyedCodec<>("RunAggroPerBlock", Codec.DOUBLE, false),
                            (config, value) -> config.runAggroPerBlock = value,
                            config -> config.runAggroPerBlock)
                    .documentation("Aggro per block travelled on sand while running or sprinting.")
                    .add()
                    .append(new KeyedCodec<>("JumpAggroPerBlock", Codec.DOUBLE, false),
                            (config, value) -> config.jumpAggroPerBlock = value,
                            config -> config.jumpAggroPerBlock)
                    .documentation("Aggro per block travelled over sand while airborne after a jump.")
                    .add()
                    .append(new KeyedCodec<>("StillDecayPerSecond", Codec.DOUBLE, false),
                            (config, value) -> config.stillDecayPerSecond = value,
                            config -> config.stillDecayPerSecond)
                    .documentation("Taken off a player's own aggro each second they gain nothing.")
                    .add()
                    .append(new KeyedCodec<>("GroupRadius", Codec.DOUBLE, false),
                            (config, value) -> config.groupRadius = value,
                            config -> config.groupRadius)
                    .documentation("Blocks. Players this close count as one group and hear the same worm.")
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
                    .append(new KeyedCodec<>("ResetFractionOnTrigger", Codec.DOUBLE, false),
                            (config, value) -> config.resetFractionOnTrigger = value,
                            config -> config.resetFractionOnTrigger)
                    .documentation("Share of each group member's aggro removed when an event starts (1 removes all).")
                    .add()
                    .append(new KeyedCodec<>("FizzleScoreFraction", Codec.DOUBLE, false),
                            (config, value) -> config.fizzleScoreFraction = value,
                            config -> config.fizzleScoreFraction)
                    .documentation("When an event fizzles the group's total aggro is set to this fraction of the threshold.")
                    .add()
                    .append(new KeyedCodec<>("StalkSeconds", Codec.DOUBLE, false),
                            (config, value) -> config.stalkSeconds = value,
                            config -> config.stalkSeconds)
                    .documentation("STALKING phase length. The target can still escape to rock.")
                    .add()
                    .append(new KeyedCodec<>("LockSeconds", Codec.DOUBLE, false),
                            (config, value) -> config.lockSeconds = value,
                            config -> config.lockSeconds)
                    .documentation("LOCKED phase length. No escape and no retarget.")
                    .add()
                    .append(new KeyedCodec<>("BreachSequenceSeconds", Codec.DOUBLE, false),
                            (config, value) -> config.breachSeconds = value,
                            config -> config.breachSeconds)
                    .documentation("BREACH phase length: the whole breach sequence and settling.")
                    .add()
                    .append(new KeyedCodec<>("CooldownSeconds", Codec.DOUBLE, false),
                            (config, value) -> config.cooldownSeconds = value,
                            config -> config.cooldownSeconds)
                    .documentation("After a worm has devoured, no new event for that group for this long.")
                    .add()
                    .append(new KeyedCodec<>("RetargetKeepsClock", Codec.BOOLEAN, false),
                            (config, value) -> config.retargetKeepsClock = value,
                            config -> config.retargetKeepsClock)
                    .documentation("False restarts the stalk clock when the worm picks a new target.")
                    .add()
                    .append(new KeyedCodec<>("WormStartMinDistance", Codec.DOUBLE, false),
                            (config, value) -> config.wormStartMinDistance = value,
                            config -> config.wormStartMinDistance)
                    .documentation("The stand-in worm starts at least this many blocks from its target.")
                    .add()
                    .append(new KeyedCodec<>("WormStartMaxDistance", Codec.DOUBLE, false),
                            (config, value) -> config.wormStartMaxDistance = value,
                            config -> config.wormStartMaxDistance)
                    .documentation("The stand-in worm starts at most this many blocks from its target.")
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
                    .append(new KeyedCodec<>("PathEaseExponent", Codec.DOUBLE, false),
                            (config, value) -> config.pathEaseExponent = value,
                            config -> config.pathEaseExponent)
                    .documentation("The worm speeds up as it nears: progress = (time / stalk time) raised to this. 1 is constant speed.")
                    .add()
                    .append(new KeyedCodec<>("WormsignViewDistance", Codec.DOUBLE, false),
                            (config, value) -> config.wormsignViewDistance = value,
                            config -> config.wormsignViewDistance)
                    .documentation("Blocks. Players this close to the worm see its trail.")
                    .add()
                    .append(new KeyedCodec<>("WormsignTrailInterval", Codec.DOUBLE, false),
                            (config, value) -> config.wormsignTrailInterval = value,
                            config -> config.wormsignTrailInterval)
                    .documentation("Seconds between trail dust along the path.")
                    .add()
                    .append(new KeyedCodec<>("WormsignPuffInterval", Codec.DOUBLE, false),
                            (config, value) -> config.wormsignPuffInterval = value,
                            config -> config.wormsignPuffInterval)
                    .documentation("Seconds between the puffs of sand thrown up from the trail.")
                    .add()
                    .append(new KeyedCodec<>("WormsignScale", Codec.DOUBLE, false),
                            (config, value) -> config.wormsignScale = value,
                            config -> config.wormsignScale)
                    .documentation("Particle scale of the trail.")
                    .add()
                    .append(new KeyedCodec<>("RumbleBystanderVolume", Codec.DOUBLE, false),
                            (config, value) -> config.rumbleBystanderVolume = value,
                            config -> config.rumbleBystanderVolume)
                    .documentation("Volume (1 = full) for players near the target who are not the target. Flat.")
                    .add()
                    .append(new KeyedCodec<>("RumbleTargetStartVolume", Codec.DOUBLE, false),
                            (config, value) -> config.rumbleTargetStartVolume = value,
                            config -> config.rumbleTargetStartVolume)
                    .documentation("The target hears this at the start of STALKING, rising to RumbleTargetVolume.")
                    .add()
                    .append(new KeyedCodec<>("RumbleTargetVolume", Codec.DOUBLE, false),
                            (config, value) -> config.rumbleTargetVolume = value,
                            config -> config.rumbleTargetVolume)
                    .documentation("Volume the target hears at the end of STALKING and in LOCKED.")
                    .add()
                    .append(new KeyedCodec<>("RumbleTargetEndPitch", Codec.DOUBLE, false),
                            (config, value) -> config.rumbleTargetEndPitch = value,
                            config -> config.rumbleTargetEndPitch)
                    .documentation("Pitch multiplier the target hears at the end of STALKING (1 at the start).")
                    .add()
                    .append(new KeyedCodec<>("RumbleIntervalSeconds", Codec.DOUBLE, false),
                            (config, value) -> config.rumbleIntervalSeconds = value,
                            config -> config.rumbleIntervalSeconds)
                    .documentation("The rumble sounds are played again this often (shorter than the sounds, so they overlap).")
                    .add()
                    .append(new KeyedCodec<>("RippleSeconds", Codec.DOUBLE, false),
                            (config, value) -> config.rippleSeconds = value,
                            config -> config.rippleSeconds)
                    .documentation("The target ripple starts this long before LOCKED.")
                    .add()
                    .append(new KeyedCodec<>("RippleStartInterval", Codec.DOUBLE, false),
                            (config, value) -> config.rippleStartInterval = value,
                            config -> config.rippleStartInterval)
                    .documentation("Seconds between ripples when they start.")
                    .add()
                    .append(new KeyedCodec<>("RippleEndInterval", Codec.DOUBLE, false),
                            (config, value) -> config.rippleEndInterval = value,
                            config -> config.rippleEndInterval)
                    .documentation("Seconds between ripples at the start of LOCKED.")
                    .add()
                    .append(new KeyedCodec<>("RippleViewDistance", Codec.DOUBLE, false),
                            (config, value) -> config.rippleViewDistance = value,
                            config -> config.rippleViewDistance)
                    .documentation("Blocks. Players this close to the target also see the ripple.")
                    .add()
                    .append(new KeyedCodec<>("LockedRippleInterval", Codec.DOUBLE, false),
                            (config, value) -> config.lockedRippleInterval = value,
                            config -> config.lockedRippleInterval)
                    .documentation("Seconds between the tight, quick ripples round the target during LOCKED.")
                    .add()
                    .append(new KeyedCodec<>("VignetteSeconds", Codec.DOUBLE, false),
                            (config, value) -> config.vignetteSeconds = value,
                            config -> config.vignetteSeconds)
                    .documentation("The vignette fades in over this long before LOCKED.")
                    .add()
                    .append(new KeyedCodec<>("VignetteStalkLevel", Codec.DOUBLE, false),
                            (config, value) -> config.vignetteStalkLevel = value,
                            config -> config.vignetteStalkLevel)
                    .documentation("Vignette strength (0 to 1) reached at the end of STALKING; LOCKED goes on to 1.")
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
                    .append(new KeyedCodec<>("ShakeSeconds", Codec.DOUBLE, false),
                            (config, value) -> config.shakeSeconds = value,
                            config -> config.shakeSeconds)
                    .documentation("The tremble starts this long before LOCKED.")
                    .add()
                    .append(new KeyedCodec<>("ShakeStrength", Codec.DOUBLE, false),
                            (config, value) -> config.shakeStrength = value,
                            config -> config.shakeStrength)
                    .documentation("Overall multiplier on the camera shake intensity.")
                    .add()
                    .append(new KeyedCodec<>("BystanderShakeRange", Codec.DOUBLE, false),
                            (config, value) -> config.bystanderShakeRange = value,
                            config -> config.bystanderShakeRange)
                    .documentation("Blocks. A bystander this close to the worm gets one shake pulse.")
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
                    .documentation("Everyone in the swallow zone is held, hidden at BreachHideAt and killed at BreachKillAt. False turns the hold and the hide off: the swallow then kills the target at BreachSwallowAt and nobody else dies.")
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
                    .append(new KeyedCodec<>("BreachHideAt", Codec.DOUBLE, false),
                            (config, value) -> config.breachHideAt = value,
                            config -> config.breachHideAt)
                    .documentation("Seconds into BREACH when held players turn invisible. -1 means BreachSwallowAt.")
                    .add()
                    .append(new KeyedCodec<>("BreachKillAt", Codec.DOUBLE, false),
                            (config, value) -> config.breachKillAt = value,
                            config -> config.breachKillAt)
                    .documentation("Seconds into BREACH when held players are killed, once the worm is under the sand. -1 means BreachDiveEnd.")
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
                    .append(new KeyedCodec<>("WormStartCandidates", Codec.DOUBLE, false),
                            (config, value) -> config.wormStartCandidates = value,
                            config -> config.wormStartCandidates)
                    .documentation("How many candidate start points are sampled in the WormStartMinDistance to WormStartMaxDistance ring.")
                    .add()
                    .append(new KeyedCodec<>("WormStartOpenRadius", Codec.DOUBLE, false),
                            (config, value) -> config.wormStartOpenRadius = value,
                            config -> config.wormStartOpenRadius)
                    .documentation("Blocks. A start point counts only if every surface block this close is sand.")
                    .add()
                    .append(new KeyedCodec<>("WormStartHeightTolerance", Codec.DOUBLE, false),
                            (config, value) -> config.wormStartHeightTolerance = value,
                            config -> config.wormStartHeightTolerance)
                    .documentation("Blocks. The start is a random one among the qualifying points this close to the highest.")
                    .add()
                    .append(new KeyedCodec<>("StartBoomDustScale", Codec.DOUBLE, false),
                            (config, value) -> config.startBoomDustScale = value,
                            config -> config.startBoomDustScale)
                    .documentation("Particle scale of the dust burst at the worm start point when an event begins.")
                    .add()
                    .append(new KeyedCodec<>("StartRippleDelay", Codec.DOUBLE, false),
                            (config, value) -> config.startRippleDelay = value,
                            config -> config.startRippleDelay)
                    .documentation("Seconds after the start boom that everyone near, standing on sand, feels the warning ripple.")
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
                    // END GENERATED CODEC
                    .build();

    private String[] sandBlocks = {};
    private double aggroThreshold = 100;
    private double walkAggroPerBlock = 0.5;
    private double runAggroPerBlock = 1;
    private double jumpAggroPerBlock = 1;
    private double stillDecayPerSecond = 1;
    private double groupRadius = 200;
    private double minGainSpeed = 0.5;
    private double sandTolerance = 1.0;
    private double resetFractionOnTrigger = 1.0;
    private double fizzleScoreFraction = 0.5;
    private double stalkSeconds = 40;
    private double lockSeconds = 4;
    private double breachSeconds = 8;
    private double cooldownSeconds = 120;
    private boolean retargetKeepsClock = false;
    private double wormStartMinDistance = 120;
    private double wormStartMaxDistance = 180;
    private double offSandDecayMultiplier = 10;
    private boolean debugChat = false;
    // GENERATED FIELDS
    private double pathEaseExponent = 2;
    private double wormsignViewDistance = 220;
    private double wormsignTrailInterval = 0.25;
    private double wormsignPuffInterval = 1.5;
    private double wormsignScale = 3;
    private double rumbleBystanderVolume = 0.7;
    private double rumbleTargetStartVolume = 0.3;
    private double rumbleTargetVolume = 1;
    private double rumbleTargetEndPitch = 0.85;
    private double rumbleIntervalSeconds = 3;
    private double rippleSeconds = 15;
    private double rippleStartInterval = 4;
    private double rippleEndInterval = 1;
    private double rippleViewDistance = 30;
    private double lockedRippleInterval = 0.2;
    private double vignetteSeconds = 15;
    private double vignetteStalkLevel = 0.5;
    private double vignetteStrength = 1;
    private boolean cameraShake = true;
    private double shakeSeconds = 15;
    private double shakeStrength = 1;
    private double bystanderShakeRange = 30;
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
    private double breachHideAt = -1;
    private double breachKillAt = -1;
    private double breachCameraReturnAt = -1;
    private double zoomWormHeightFactor = 0.6667;
    private double zoomMaxDistance = 60;
    private String breachCameraMode = "distance";
    private double wormStartCandidates = 48;
    private double wormStartOpenRadius = 4;
    private double wormStartHeightTolerance = 2;
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
    // END GENERATED FIELDS

    public String[] getSandBlocks() {
        return sandBlocks;
    }

    public double getAggroThreshold() {
        return aggroThreshold;
    }

    public double getWalkAggroPerBlock() {
        return walkAggroPerBlock;
    }

    public double getRunAggroPerBlock() {
        return runAggroPerBlock;
    }

    public double getJumpAggroPerBlock() {
        return jumpAggroPerBlock;
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

    public double getResetFractionOnTrigger() {
        return resetFractionOnTrigger;
    }

    public double getFizzleScoreFraction() {
        return fizzleScoreFraction;
    }

    public double getStalkSeconds() {
        return stalkSeconds;
    }

    public double getLockSeconds() {
        return lockSeconds;
    }

    public double getBreachSeconds() {
        return breachSeconds;
    }

    public double getCooldownSeconds() {
        return cooldownSeconds;
    }

    public boolean isRetargetKeepsClock() {
        return retargetKeepsClock;
    }

    public double getWormStartMinDistance() {
        return wormStartMinDistance;
    }

    public double getWormStartMaxDistance() {
        return wormStartMaxDistance;
    }

    public double getOffSandDecayMultiplier() {
        return offSandDecayMultiplier;
    }

    public boolean isDebugChat() {
        return debugChat;
    }
















    // GENERATED GETTERS
    public double getPathEaseExponent() {
        return pathEaseExponent;
    }

    public double getWormsignViewDistance() {
        return wormsignViewDistance;
    }

    public double getWormsignTrailInterval() {
        return wormsignTrailInterval;
    }

    public double getWormsignPuffInterval() {
        return wormsignPuffInterval;
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

    public double getRippleSeconds() {
        return rippleSeconds;
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

    public double getVignetteSeconds() {
        return vignetteSeconds;
    }

    public double getVignetteStalkLevel() {
        return vignetteStalkLevel;
    }

    public double getVignetteStrength() {
        return vignetteStrength;
    }

    public boolean isCameraShake() {
        return cameraShake;
    }

    public double getShakeSeconds() {
        return shakeSeconds;
    }

    public double getShakeStrength() {
        return shakeStrength;
    }

    public double getBystanderShakeRange() {
        return bystanderShakeRange;
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

    public double getBreachHideAt() {
        return breachHideAt;
    }

    public double getBreachKillAt() {
        return breachKillAt;
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

    public double getWormStartCandidates() {
        return wormStartCandidates;
    }

    public double getWormStartOpenRadius() {
        return wormStartOpenRadius;
    }

    public double getWormStartHeightTolerance() {
        return wormStartHeightTolerance;
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
    // END GENERATED GETTERS
}
