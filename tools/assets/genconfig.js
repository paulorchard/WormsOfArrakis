// Generates the prompt 19 config entries (fields, getters, codec) into WormsOfArrakisConfig.java between the GENERATED markers.
const fs = require('fs');
const file = 'src/main/java/com/paulorchard/islandcraft/wormsofarrakis/WormsOfArrakisConfig.java';
const rows = [
  ['PathEaseExponent', 'double', 2.0, 'The worm speeds up as it nears: progress = (time / stalk time) raised to this. 1 is constant speed.'],
  ['WormsignViewDistance', 'double', 220, 'Blocks. Players this close to the worm see its trail.'],
  ['WormsignTrailInterval', 'double', 0.25, 'Seconds between trail dust along the path.'],
  ['WormsignPuffInterval', 'double', 1.5, 'Seconds between the puffs of sand thrown up from the trail.'],
  ['WormsignScale', 'double', 3.0, 'Particle scale of the trail.'],
  ['RumbleBystanderVolume', 'double', 0.7, 'Volume (1 = full) for players near the target who are not the target. Flat.'],
  ['RumbleTargetStartVolume', 'double', 0.3, 'The target hears this at the start of STALKING, rising to RumbleTargetVolume.'],
  ['RumbleTargetVolume', 'double', 1.0, 'Volume the target hears at the end of STALKING and in LOCKED.'],
  ['RumbleTargetEndPitch', 'double', 0.85, 'Pitch multiplier the target hears at the end of STALKING (1 at the start).'],
  ['RumbleIntervalSeconds', 'double', 3.0, 'The rumble sounds are played again this often (shorter than the sounds, so they overlap).'],
  ['RippleSeconds', 'double', 15, 'The target ripple starts this long before LOCKED.'],
  ['RippleStartInterval', 'double', 4, 'Seconds between ripples when they start.'],
  ['RippleEndInterval', 'double', 1, 'Seconds between ripples at the start of LOCKED.'],
  ['RippleViewDistance', 'double', 30, 'Blocks. Players this close to the target also see the ripple.'],
  ['LockedRippleInterval', 'double', 0.2, 'Seconds between the tight, quick ripples round the target during LOCKED.'],
  ['VignetteSeconds', 'double', 15, 'The vignette fades in over this long before LOCKED.'],
  ['VignetteStalkLevel', 'double', 0.5, 'Vignette strength (0 to 1) reached at the end of STALKING; LOCKED goes on to 1.'],
  ['VignetteStrength', 'double', 1.0, 'Overall multiplier on the vignette (0 turns it off).'],
  ['CameraShake', 'boolean', true, 'Turn the target camera shake off by setting false.'],
  ['ShakeSeconds', 'double', 15, 'The tremble starts this long before LOCKED.'],
  ['ShakeStrength', 'double', 1.0, 'Overall multiplier on the camera shake intensity.'],
  ['BystanderShakeRange', 'double', 30, 'Blocks. A bystander this close to the worm gets one shake pulse.'],
  ['SlowFloor', 'double', 0.15, 'Share of normal speed the target is slowed to by the end of LOCKED.'],
  ['SlowJumpFloor', 'double', 0.5, 'Share of normal jump force the target is down to by the end of LOCKED (needs SlowMethod effect or settings both working).'],
  ['SlowMethod', 'string', 'effect', 'effect: entity effects with a horizontal speed multiplier in steps. settings: MovementManager settings, smooth. Jump force always uses settings.'],
  ['FadeOutSeconds', 'double', 1.0, 'Effects of an old target fade out over this long on retarget.'],
  ['BreachBoomAt','double',0,'Seconds from the start of BREACH: the dust ring, the heave and the boom.'],
  ['BreachWormAt','double',0.2,'The stand-in worm shoots up through the surface.'],
  ['BreachLiftAt','double',0.6,'The target is lifted with the worm from here.'],
  ['BreachSwallowAt','double',1.2,'The worm is at full height; the target is swallowed (killed, or thrown with DevourKills off).'],
  ['BreachDiveAt','double',1.8,'The worm starts to arc over and dive after hanging from the swallow.'],
  ['BreachDiveEnd','double',3,'The worm is under again; a second, lower burst and a falling rumble. It is removed just after.'],
  ['BreachWormSize', 'double', 3.0, 'Scale of the breach worm. 3 is the same worm as /wormtest worm: about 18 blocks wide and 30 above the surface at the top.'],
  ['BreachDustScale','double',6,'Particle scale of the breach dust.'],
  ['BreachDebrisScale','double',3,'Particle scale of the thrown chunks of sand.'],
  ['BreachShakeRange','double',40,'Blocks. Players this close to the breach get camera shake and see the worm.'],
  ['BreachShakeStrength','double',1.5,'Intensity of the breach camera shake (times ShakeStrength).'],
  ['BreachBoomVolume','double',1,'Volume of the breach sounds within BreachShakeRange.'],
  ['BreachFarVolume','double',0.8,'Volume of the breach sounds for players further away, up to GroupRadius. Flat.'],
  ['DevourKills','boolean',true,'False: the breach only throws the target and hurts them badly (they survive), for testing or a gentler worm.'],
  ['DevourDropsItems','boolean',false,'False: the target dies with an empty inventory and nothing is dropped.'],
  ['DevourHurtFraction','double',0.8,'With DevourKills off, the share of the target current health taken (never lethal).'],
  ['CameraShakeScale','double',0.5,'Overall multiplier on every camera shake this mod sends (1 = the original strength).'],
  ['CameraZoom','boolean',true,'Pull the target camera out into third person in the last ZoomSeconds of LOCKED so they can see the worm attack.'],
  ['ZoomSeconds','double',2,'The camera zoom out takes this long, ending when LOCKED ends.'],
  ['ZoomFromDistance','double',5,'Camera distance in blocks the zoom starts from (the usual third person distance).'],
  ["BreachHold","boolean",true,"Everyone in the swallow zone is held, hidden at BreachHideAt and killed at BreachKillAt. False turns the hold and the hide off: the swallow then kills the target at BreachSwallowAt and nobody else dies."],
  ["BreachSwallowZoneFactor","double",1.15,"The swallow zone is this times the worm radius, measured from the breach centre, decided when the worm appears."],
  ["BreachCameraRadius","double",0,"Blocks. Everyone this close to the breach gets the pulled-out camera. 0 means worm radius times 4 plus 20."],
  ["BreachHideAt","double",-1,"Seconds into BREACH when held players turn invisible. -1 means BreachSwallowAt."],
  ["BreachKillAt","double",-1,"Seconds into BREACH when held players are killed, once the worm is under the sand. -1 means BreachDiveEnd."],
  ["BreachCameraReturnAt","double",-1,"Seconds into BREACH when the pulled-out camera eases back. -1 means BreachDiveEnd plus 1."],
  ["ZoomWormHeightFactor","double",0.6667,"The pulled-out camera distance is this times the worm height (10 blocks times BreachWormSize), up to ZoomMaxDistance."],
  ["ZoomMaxDistance","double",60,"Blocks. The longest the pulled-out camera distance can be."],
  ["BreachCameraMode","string","distance","distance: pull the player camera back. fixed: a camera placed on the player side of the worm, aimed at it."],
  ["WormStartCandidates","double",48,"How many candidate start points are sampled in the WormStartMinDistance to WormStartMaxDistance ring."],
  ["WormStartOpenRadius","double",4,"Blocks. A start point counts only if every surface block this close is sand."],
  ["WormStartHeightTolerance","double",2,"Blocks. The start is a random one among the qualifying points this close to the highest."],
  ["StartBoomDustScale","double",3,"Particle scale of the dust burst at the worm start point when an event begins."],
  ["StartRippleDelay","double",1,"Seconds after the start boom that everyone near, standing on sand, feels the warning ripple."],
  ["BreachRippleMaxRings","double",8,"Most rings the breach heave is allowed (it uses ceil of the worm radius, so 8 is a 17 by 17 patch)."],
  ["BreachLiftsVictim","boolean",false,"Lift the victim up with the worm before the swallow (the old behaviour). Off by default."],
  ['RippleShake','boolean',true,'Sink-and-rebound ripple: a small camera shake on the first drop and on the first two rebound peaks.'],
  ['RippleShakeStrength','double',0.5,'Intensity of that shake (times CameraShakeScale), before the fall-off with distance.'],
  ['SinkIncrement','double',2,'Pixels the centre drops on each descent step (1 to 4); everything else scales with it.'],
  ['SinkRings','double',4,'Rings round the centre block (3 is a 7 by 7 patch).'],
  ['SinkStepSeconds','double',0.10,'Seconds between descent steps.'],
  ['SinkSwingSeconds','double',0.16,'Seconds for one swing of the rebound, before the per-block random factor.'],
  ['SinkSwingJitter','double',0.15,'Each block swings this much faster or slower (a fraction, 0.15 is 0.85 to 1.15).'],
  ['SinkDropMin','double',0.5,'Smallest drop of a ring block per step, times the increment (at least 1 px).'],
  ['SinkDropMax','double',1.5,'Largest drop of a ring block per step, times the increment.'],
  ['SinkReboundMin','double',0.75,'The first rebound peak is at least this fraction of the depth.'],
  ['SinkReboundMax','double',0.88,'The first rebound peak is at most this fraction of the depth.'],
  ['SinkDecayMin','double',0.55,'Each later swing is at least this fraction of the one before.'],
  ['SinkDecayMax','double',0.85,'Each later swing is at most this fraction of the one before.'],
];
const camel = (n) => n[0].toLowerCase() + n.slice(1);
const jt = { double: 'double', boolean: 'boolean', string: 'String' };
const codec = { double: 'Codec.DOUBLE', boolean: 'Codec.BOOLEAN', string: 'Codec.STRING' };
const lit = (r) => r[1] === 'string' ? `"${r[2]}"` : String(r[2]);
let fields = '', getters = '', codecs = '';
for (const r of rows) {
  const f = camel(r[0]);
  fields += `    private ${jt[r[1]]} ${f} = ${lit(r)};\n`;
  getters += `    public ${jt[r[1]]} ${r[1] === 'boolean' ? 'is' : 'get'}${r[0]}() {\n        return ${f};\n    }\n\n`;
  codecs += `                    .append(new KeyedCodec<>("${r[0]}", ${codec[r[1]]}, false),\n                            (config, value) -> config.${f} = value,\n                            config -> config.${f})\n                    .documentation(${JSON.stringify(r[3])})\n                    .add()\n`;
}
const NL = String.fromCharCode(10);
const strip = (text) => {
  const out = [];
  let skip = false;
  for (const l of text.split(NL)) {
    if (l.includes('// END GENERATED')) skip = false;
    else if (l.includes('// GENERATED')) skip = true;
    else if (!skip) out.push(l);
  }
  return out.join(NL);
};
let s = strip(fs.readFileSync(file, 'utf8'));
s = s.replace('                    .build();', `                    // GENERATED CODEC\n${codecs}                    // END GENERATED CODEC\n                    .build();`);
s = s.replace('    private boolean debugChat = false;', `    private boolean debugChat = false;\n    // GENERATED FIELDS\n${fields}    // END GENERATED FIELDS`);
s = s.replace(/\}\s*$/, `\n    // GENERATED GETTERS\n${getters.trimEnd()}\n    // END GENERATED GETTERS\n}\n`);
fs.writeFileSync(file, s);
