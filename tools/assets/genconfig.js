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
let s = fs.readFileSync(file, 'utf8');
const cut = (name) => { s = s.replace(new RegExp(`\n?[ ]*// GENERATED ${name}[\s\S]*?// END GENERATED ${name}\n`), ''); };
['FIELDS', 'GETTERS', 'CODEC'].forEach(cut);
s = s.replace('                    .build();', `                    // GENERATED CODEC\n${codecs}                    // END GENERATED CODEC\n                    .build();`);
s = s.replace('    private boolean debugChat = false;', `    private boolean debugChat = false;\n    // GENERATED FIELDS\n${fields}    // END GENERATED FIELDS`);
s = s.replace(/\}\s*$/, `\n    // GENERATED GETTERS\n${getters.trimEnd()}\n    // END GENERATED GETTERS\n}\n`);
fs.writeFileSync(file, s);
