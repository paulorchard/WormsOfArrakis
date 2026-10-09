// Generates the assets of the swallow: the hold effect, the hide effect and the invisible model it swaps in.
// Usage: node tools/assets/holdfx.js
//  Arrakis_Worm_Hold: no walking, sprinting, jumping, crouching, or use of items and abilities, no knockback, and
//   nothing visible of its own (no tint, no screen effect, no particles). Based on the vanilla Status/Stun.json.
//  Arrakis_Worm_Hide: a model override (as Status/Root.json uses) that swaps the player's model for
//   Arrakis_Worm_Invisible, one box a fraction of a unit big that is not visible, with a one-pixel clear texture.
const fs = require('fs'), path = require('path'), zlib = require('zlib');
const root = path.resolve(__dirname, '../../src/main/resources');
const effectDir = path.join(root, 'Server/Entity/Effects/Worms_of_Arrakis');
const modelDir = path.join(root, 'Server/Models/Worms_of_Arrakis');
const npcDir = path.join(root, 'Common/NPC/Worms_of_Arrakis');
for (const d of [effectDir, modelDir, npcDir]) fs.mkdirSync(d, { recursive: true });
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
write(path.join(effectDir, 'Arrakis_Worm_Hide.json'), {
  Duration: SECONDS,
  ModelOverride: {
    Model: 'NPC/Worms_of_Arrakis/Arrakis_Worm_Invisible.blockymodel',
    Texture: 'NPC/Worms_of_Arrakis/Arrakis_Worm_Invisible.png',
  },
  ApplicationEffects: { KnockbackMultiplier: 0 },
});
write(path.join(modelDir, 'Arrakis_Worm_Invisible.json'), {
  EyeHeight: 1.6,
  HitBox: { Min: { X: -0.3, Y: 0, Z: -0.3 }, Max: { X: 0.3, Y: 1.8, Z: 0.3 } },
  Model: 'NPC/Worms_of_Arrakis/Arrakis_Worm_Invisible.blockymodel',
  Texture: 'NPC/Worms_of_Arrakis/Arrakis_Worm_Invisible.png',
});
const face = { offset: { x: 0, y: 0 }, mirror: { x: false, y: false }, angle: 0 };
write(path.join(npcDir, 'Arrakis_Worm_Invisible.blockymodel'), {
  nodes: [{
    id: '1', name: 'Nothing', children: [],
    position: { x: 0, y: 0, z: 0 }, orientation: { x: 0, y: 0, z: 0, w: 1 },
    shape: {
      type: 'box', offset: { x: 0, y: 0, z: 0 }, stretch: { x: 1, y: 1, z: 1 },
      settings: { size: { x: 0.1, y: 0.1, z: 0.1 } },
      visible: false, doubleSided: false, shadingMode: 'flat', unwrapMode: 'custom',
      textureLayout: { front: face, back: face, left: face, right: face, top: face, bottom: face },
    },
  }],
  lod: 'auto',
});
// One fully transparent pixel.
const crcTable = Array.from({ length: 256 }, (_, n) => { let c = n; for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1; return c >>> 0; });
const crc = (b) => { let c = 0xffffffff; for (const x of b) c = crcTable[(c ^ x) & 255] ^ (c >>> 8); return (c ^ 0xffffffff) >>> 0; };
const chunk = (type, data) => {
  const t = Buffer.from(type, 'latin1'), len = Buffer.alloc(4), c = Buffer.alloc(4);
  len.writeUInt32BE(data.length); c.writeUInt32BE(crc(Buffer.concat([t, data])));
  return Buffer.concat([len, t, data, c]);
};
const ihdr = Buffer.alloc(13); ihdr.writeUInt32BE(1, 0); ihdr.writeUInt32BE(1, 4); ihdr[8] = 8; ihdr[9] = 6;
fs.writeFileSync(path.join(npcDir, 'Arrakis_Worm_Invisible.png'), Buffer.concat([
  Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]), chunk('IHDR', ihdr),
  chunk('IDAT', zlib.deflateSync(Buffer.from([0, 0, 0, 0, 0]))), chunk('IEND', Buffer.alloc(0))]));
console.log('hold and hide assets written');
