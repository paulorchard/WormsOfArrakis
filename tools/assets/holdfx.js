// Generates the hold effect of the swallow. Usage: node tools/assets/holdfx.js
//  Arrakis_Worm_Hold: no walking, sprinting, jumping, crouching, or use of items and abilities, no knockback, and
//   nothing visible of its own (no tint, no screen effect, no particles). Based on the vanilla Status/Stun.json.
const fs = require('fs'), path = require('path');
const root = path.resolve(__dirname, '../../src/main/resources');
const effectDir = path.join(root, 'Server/Entity/Effects/Worms_of_Arrakis');
fs.mkdirSync(effectDir, { recursive: true });
const write = (file, obj) => fs.writeFileSync(file, JSON.stringify(obj, null, 2) + '\n');

// Longer than the whole breach; the mod removes it explicitly.
const SECONDS = 12;
write(path.join(effectDir, 'Arrakis_Worm_Hold.json'), {
  Duration: SECONDS,
  ApplicationEffects: {
    MovementEffects: { DisableAll: true },
    AbilityEffects: { Disabled: ['Primary', 'Secondary', 'Ability1', 'Ability3'] },
    KnockbackMultiplier: 0,
  },
});
console.log('hold effect written');
