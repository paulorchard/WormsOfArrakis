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
  ['RippleRadius', 'double', 5, 'Blocks. Radius of the target ripple.'],
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
  ['BreachRippleRadius','double',8,'Blocks. Radius of the heave of fake blocks at the start of the breach.'],
  ['BreachShakeRange','double',40,'Blocks. Players this close to the breach get camera shake and see the worm.'],
  ['BreachShakeStrength','double',1.5,'Intensity of the breach camera shake (times ShakeStrength).'],
  ['BreachBoomVolume','double',1,'Volume of the breach sounds within BreachShakeRange.'],
  ['BreachFarVolume','double',0.8,'Volume of the breach sounds for players further away, up to GroupRadius. Flat.'],
  ['DevourKills','boolean',true,'False: the breach only throws the target and hurts them badly (they survive), for testing or a gentler worm.'],
  ['DevourDropsItems','boolean',false,'False: the target dies with an empty inventory and nothing is dropped.'],
  ['DevourHurtFraction','double',0.8,'With DevourKills off, the share of the target current health taken (never lethal).'],
  ['KnockbackRadius','double',4,'Blocks. Bystanders this close to the target when the worm erupts are thrown back, unharmed.'],
  ['KnockbackForce','double',16,'Horizontal speed in blocks per second given to a thrown bystander (or to a surviving target).'],
  ['CameraShakeScale','double',0.5,'Overall multiplier on every camera shake this mod sends (1 = the original strength).'],
  ['CameraZoom','boolean',true,'Pull the target camera out into third person in the last ZoomSeconds of LOCKED so they can see the worm attack.'],
  ['ZoomSeconds','double',2,'The camera zoom out takes this long, ending when LOCKED ends.'],
  ['ZoomFromDistance','double',5,'Camera distance in blocks the zoom starts from (the usual third person distance).'],
  ['ZoomDistance','double',15,'Camera distance in blocks at the end of the zoom: about three times the usual third person distance.'],
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
