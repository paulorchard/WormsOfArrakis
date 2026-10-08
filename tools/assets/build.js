// Generates the test weathers and the soft vignette texture. Usage: node tools/assets/build.js <dir holding Zone2_Sunny.json and Zone2_Desert_Haze.json> <Rock_Sandstone_Side.png>
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

// Stand-in worm: one box 6 blocks wide and 20 tall (10 show above the sand, 10 stay buried; 32 model units per block), sandstone texture.
const npcDir = path.join(root, 'Common/NPC/Worms_of_Arrakis');
fs.mkdirSync(npcDir, { recursive: true });
const face = { offset: { x: 0, y: 0 }, mirror: { x: false, y: false }, angle: 0 };
// A box seems to be limited to 320 model units (10 blocks) tall, so the 20-block body is two boxes stacked, centred on the model origin so the position is the middle of the body whatever the engine does with the pivot.
const box = (id, name, y) => ({
  id, name, children: [],
  position: { x: 0, y: 0, z: 0 }, orientation: { x: 0, y: 0, z: 0, w: 1 },
  shape: {
    type: 'box', offset: { x: 0, y, z: 0 }, stretch: { x: 1, y: 1, z: 1 },
    settings: { size: { x: 192, y: 320, z: 192 } },
    visible: true, doubleSided: false, shadingMode: 'flat', unwrapMode: 'custom',
    textureLayout: { front: face, back: face, left: face, right: face, top: face, bottom: face },
  },
});
const model = { nodes: [box('1', 'Lower', -160), box('2', 'Upper', 160)], lod: 'auto' };
fs.writeFileSync(path.join(npcDir, 'Arrakis_Worm_Placeholder.blockymodel'), JSON.stringify(model, null, 2) + '\n');
// Sandstone texture for the worm: the vanilla Rock_Sandstone_Side tile repeated over a 256x704 sheet.
const sandstone = process.argv[3];
if (!sandstone) { console.error('usage: node build.js <Zone2 weather dir> <Rock_Sandstone_Side.png>'); process.exit(1); }
const src8 = fs.readFileSync(sandstone);
let tw = 0, th = 0, ctype = 0, plte = null, trns = null; const idat = [];
for (let p = 8; p < src8.length;) {
  const len = src8.readUInt32BE(p), type = src8.toString('latin1', p + 4, p + 8), data = src8.subarray(p + 8, p + 8 + len);
  if (type === 'IHDR') { tw = data.readUInt32BE(0); th = data.readUInt32BE(4); ctype = data[9]; }
  else if (type === 'PLTE') plte = data; else if (type === 'tRNS') trns = data; else if (type === 'IDAT') idat.push(data);
  p += 12 + len;
}
const bpp = ctype === 6 ? 4 : ctype === 2 ? 3 : 1;
const inflated = zlib.inflateSync(Buffer.concat(idat)), stride = tw * bpp;
const pix = Buffer.alloc(stride * th);
for (let y = 0; y < th; y++) {
  const f = inflated[y * (stride + 1)];
  for (let x = 0; x < stride; x++) {
    const raw = inflated[y * (stride + 1) + 1 + x];
    const a = x >= bpp ? pix[y * stride + x - bpp] : 0, b = y ? pix[(y - 1) * stride + x] : 0, c = x >= bpp && y ? pix[(y - 1) * stride + x - bpp] : 0;
    const pa = Math.abs(b - c), pb = Math.abs(a - c), pc = Math.abs(a + b - 2 * c);
    const pred = f === 0 ? 0 : f === 1 ? a : f === 2 ? b : f === 3 ? (a + b) >> 1 : (pa <= pb && pa <= pc ? a : pb <= pc ? b : c);
    pix[y * stride + x] = (raw + pred) & 255;
  }
}
const rgba = (x, y) => {
  const o = y * stride + x * bpp;
  if (ctype === 3) { const i = pix[o]; return [plte[i * 3], plte[i * 3 + 1], plte[i * 3 + 2], trns && i < trns.length ? trns[i] : 255]; }
  return [pix[o], pix[o + 1], pix[o + 2], ctype === 6 ? pix[o + 3] : 255];
};
const SW = 256, SH = 704, sheet = Buffer.alloc((SW * 4 + 1) * SH);
for (let y = 0; y < SH; y++) {
  sheet[y * (SW * 4 + 1)] = 0;
  for (let x = 0; x < SW; x++) {
    const c = rgba(x % tw, y % th), o = y * (SW * 4 + 1) + 1 + x * 4;
    sheet[o] = c[0]; sheet[o + 1] = c[1]; sheet[o + 2] = c[2]; sheet[o + 3] = 255;
  }
}
const ih2 = Buffer.alloc(13); ih2.writeUInt32BE(SW, 0); ih2.writeUInt32BE(SH, 4); ih2[8] = 8; ih2[9] = 6;
fs.writeFileSync(path.join(npcDir, 'Arrakis_Worm_Placeholder.png'),
  Buffer.concat([Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]), chunk('IHDR', ih2), chunk('IDAT', zlib.deflateSync(sheet)), chunk('IEND', Buffer.alloc(0))]));


const modelDir = path.join(root, 'Server/Models/Worms_of_Arrakis');
fs.mkdirSync(modelDir, { recursive: true });
fs.writeFileSync(path.join(modelDir, 'Arrakis_Worm_Placeholder.json'), JSON.stringify({
  EyeHeight: 8,
  HitBox: { Min: { X: -3, Y: 0, Z: -3 }, Max: { X: 3, Y: 20, Z: 3 } },
  Model: 'NPC/Worms_of_Arrakis/Arrakis_Worm_Placeholder.blockymodel',
  Texture: 'NPC/Worms_of_Arrakis/Arrakis_Worm_Placeholder.png',
}, null, 2) + '\n');
console.log('worm placeholder written');
