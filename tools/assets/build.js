// Generates the test weathers and the soft vignette texture. Usage: node tools/assets/build.js <dir holding Zone2_Sunny.json and Zone2_Desert_Haze.json>
// The weathers are copies of the two vanilla weathers the Dunes of Arrakis forecast uses, with a ScreenEffect and/or a sound tag added.
const fs = require('fs'), path = require('path'), zlib = require('zlib');
const src = process.argv[2];
if (!src) { console.error('usage: node build.js <vanilla Zone2 weather dir>'); process.exit(1); }
const root = path.resolve(__dirname, '../../src/main/resources');
const weatherDir = path.join(root, 'Server/Weathers/Worms_of_Arrakis');
fs.mkdirSync(weatherDir, { recursive: true });

const bases = { Sunny: 'Zone2_Sunny.json', Haze: 'Zone2_Desert_Haze.json' };
const variants = {
  Vignette_Sand: { ScreenEffect: 'ScreenEffects/Sand.png', tag: 'Vignette_Sand' },
  Vignette_Soft: { ScreenEffect: 'ScreenEffects/Worms_of_Arrakis/Arrakis_Worm_Vignette.png', tag: 'Vignette_Soft' },
  Rumble: { tag: 'Rumble' },
};
for (const [baseName, file] of Object.entries(bases)) {
  for (const [name, v] of Object.entries(variants)) {
    const w = JSON.parse(fs.readFileSync(path.join(src, file), 'utf8'));
    w.Tags = { Arrakis: [v.tag === 'Rumble' ? 'Worm_Rumble' : 'Worm_' + v.tag] };
    if (v.ScreenEffect) w.ScreenEffect = v.ScreenEffect;
    fs.writeFileSync(path.join(weatherDir, `Arrakis_Worm_${name}_${baseName}.json`), JSON.stringify(w, null, 2) + '\n');
  }
}

// Soft vignette: transparent in the middle, dark sand brown at the edges. 480x270 RGBA.
const W = 480, H = 270;
const raw = Buffer.alloc((W * 4 + 1) * H);
const smooth = (a, b, x) => { const t = Math.min(1, Math.max(0, (x - a) / (b - a))); return t * t * (3 - 2 * t); };
for (let y = 0; y < H; y++) {
  raw[y * (W * 4 + 1)] = 0;
  for (let x = 0; x < W; x++) {
    const dx = (x + 0.5) / W * 2 - 1, dy = (y + 0.5) / H * 2 - 1;
    const d = Math.sqrt(dx * dx + dy * dy) / Math.SQRT2;
    const a = Math.round(255 * 0.85 * smooth(0.35, 0.95, d));
    const o = y * (W * 4 + 1) + 1 + x * 4;
    raw[o] = 0x3a; raw[o + 1] = 0x24; raw[o + 2] = 0x10; raw[o + 3] = a;
  }
}
const crcTable = Array.from({ length: 256 }, (_, n) => { let c = n; for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1; return c >>> 0; });
const crc = (buf) => { let c = 0xffffffff; for (const b of buf) c = crcTable[(c ^ b) & 255] ^ (c >>> 8); return (c ^ 0xffffffff) >>> 0; };
const chunk = (type, data) => { const len = Buffer.alloc(4); len.writeUInt32BE(data.length); const td = Buffer.concat([Buffer.from(type), data]); const c = Buffer.alloc(4); c.writeUInt32BE(crc(td)); return Buffer.concat([len, td, c]); };
const ihdr = Buffer.alloc(13); ihdr.writeUInt32BE(W, 0); ihdr.writeUInt32BE(H, 4); ihdr[8] = 8; ihdr[9] = 6;
const png = Buffer.concat([Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]), chunk('IHDR', ihdr), chunk('IDAT', zlib.deflateSync(raw)), chunk('IEND', Buffer.alloc(0))]);
const texDir = path.join(root, 'Common/ScreenEffects/Worms_of_Arrakis');
fs.mkdirSync(texDir, { recursive: true });
fs.writeFileSync(path.join(texDir, 'Arrakis_Worm_Vignette.png'), png);
console.log('weathers and texture written');

// Stand-in worm: one box 6 blocks wide and 10 tall (32 model units per block), flat sand-coloured texture.
const npcDir = path.join(root, 'Common/NPC/Worms_of_Arrakis');
fs.mkdirSync(npcDir, { recursive: true });
const face = { offset: { x: 0, y: 0 }, mirror: { x: false, y: false }, angle: 0 };
const model = {
  nodes: [{
    id: '1', name: 'Origin', children: [],
    position: { x: 0, y: 0, z: 0 }, orientation: { x: 0, y: 0, z: 0, w: 1 },
    shape: {
      type: 'box', offset: { x: 0, y: 160, z: 0 }, stretch: { x: 1, y: 1, z: 1 },
      settings: { size: { x: 192, y: 320, z: 192 } },
      visible: true, doubleSided: false, shadingMode: 'flat', unwrapMode: 'custom',
      textureLayout: { front: face, back: face, left: face, right: face, top: face, bottom: face },
    },
  }],
  lod: 'auto',
};
fs.writeFileSync(path.join(npcDir, 'Arrakis_Worm_Placeholder.blockymodel'), JSON.stringify(model, null, 2) + '\n');
const T = 64, tex = Buffer.alloc((T * 4 + 1) * T);
let seed = 7; const rnd = () => (seed = (seed * 1103515245 + 12345) & 0x7fffffff) / 0x7fffffff;
for (let y = 0; y < T; y++) {
  tex[y * (T * 4 + 1)] = 0;
  for (let x = 0; x < T; x++) {
    const n = Math.round((rnd() - 0.5) * 16), o = y * (T * 4 + 1) + 1 + x * 4;
    tex[o] = 0xc9 + n; tex[o + 1] = 0xa6 + n; tex[o + 2] = 0x6b + n; tex[o + 3] = 255;
  }
}
const ih2 = Buffer.alloc(13); ih2.writeUInt32BE(T, 0); ih2.writeUInt32BE(T, 4); ih2[8] = 8; ih2[9] = 6;
fs.writeFileSync(path.join(npcDir, 'Arrakis_Worm_Placeholder.png'),
  Buffer.concat([Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]), chunk('IHDR', ih2), chunk('IDAT', zlib.deflateSync(tex)), chunk('IEND', Buffer.alloc(0))]));

const modelDir = path.join(root, 'Server/Models/Worms_of_Arrakis');
fs.mkdirSync(modelDir, { recursive: true });
fs.writeFileSync(path.join(modelDir, 'Arrakis_Worm_Placeholder.json'), JSON.stringify({
  EyeHeight: 8,
  HitBox: { Min: { X: -3, Y: 0, Z: -3 }, Max: { X: 3, Y: 10, Z: 3 } },
  Model: 'NPC/Worms_of_Arrakis/Arrakis_Worm_Placeholder.blockymodel',
  Texture: 'NPC/Worms_of_Arrakis/Arrakis_Worm_Placeholder.png',
}, null, 2) + '\n');
console.log('worm placeholder written');
