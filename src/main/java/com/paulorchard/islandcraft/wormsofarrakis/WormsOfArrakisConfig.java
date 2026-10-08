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
                    .append(new KeyedCodec<>("BreachSeconds", Codec.DOUBLE, false),
                            (config, value) -> config.breachSeconds = value,
                            config -> config.breachSeconds)
                    .documentation("BREACH phase length until the breach effect exists (stub).")
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
                    .append(new KeyedCodec<>("RippleRadius", Codec.DOUBLE, false),
                            (config, value) -> config.rippleRadius = value,
                            config -> config.rippleRadius)
                    .documentation("Blocks. Radius of the target ripple.")
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
    private double breachSeconds = 3;
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
    private double rippleRadius = 5;
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

    public double getRippleRadius() {
        return rippleRadius;
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
    // END GENERATED GETTERS
}
