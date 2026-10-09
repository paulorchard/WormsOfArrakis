// Generates the round worm stand-in and its mouth hole. Usage: node tools/assets/wormmodel.js
//
// Hytale models are boxes, so a cylinder is six long strips rotated about the vertical axis in steps of 30 degrees.
// Each strip is w wide and 2 * sqrt(R^2 - (w/2)^2) long, with w = 2 * R * sin(15 degrees): the ends of neighbouring
// strips then meet exactly at the circle of radius R, which makes a regular 12-sided prism of circumradius R
// (R = 96 units = 3 blocks) with nothing sticking out. Only the ends of the strips are ever seen from outside, so
// no two visible faces overlap; the top is covered by the lip and the hole.
//
// Nodes: Lower and Upper (the body, each 320 units tall, as before) and MouthHole (Lip, then Hole on top of it).
// MouthHole is the part a real mouth model replaces. All pivots are at the model origin, the middle of the body.
//
// The texture sheet is the existing one (sandstone, 256 x 704) with a black square and a sand-coloured block added
// in the unused lower part, where the hole and the lip look.
const fs = require('fs'), path = require('path'), zlib = require('zlib');
const root = path.resolve(__dirname, '../../src/main/resources/Common/NPC/Worms_of_Arrakis');
const pngFile = path.join(root, 'Arrakis_Worm_Placeholder.png');
const modelFile = path.join(root, 'Arrakis_Worm_Placeholder.blockymodel');

const R = 96;            // circumradius of the body: 3 blocks
const HOLE_R = 64;       // 2 blocks
const HEIGHT = 320;      // one body box
const SIDES = 6;         // strips per disc; 12 sides
const round1 = (v) => Math.round(v * 10) / 10;
const strip = (r) => {
  const w = 2 * r * Math.sin(Math.PI / (2 * SIDES));
  return { w: round1(w), l: round1(2 * Math.sqrt(r * r - (w / 2) * (w / 2))) };
};

// ---- texture
function readPng(file) {
  const buf = fs.readFileSync(file);
  let w = 0, h = 0, ctype = 0; const idat = [];
  for (let p = 8; p < buf.length;) {
    const len = buf.readUInt32BE(p), type = buf.toString('latin1', p + 4, p + 8), data = buf.subarray(p + 8, p + 8 + len);
    if (type === 'IHDR') { w = data.readUInt32BE(0); h = data.readUInt32BE(4); ctype = data[9]; }
    else if (type === 'IDAT') idat.push(data);
    p += 12 + len;
  }
  if (ctype !== 6) throw new Error('expected an RGBA png');
  const raw = zlib.inflateSync(Buffer.concat(idat)), stride = w * 4, pix = Buffer.alloc(stride * h);
  for (let y = 0; y < h; y++) {
    const f = raw[y * (stride + 1)];
    for (let x = 0; x < stride; x++) {
      const v = raw[y * (stride + 1) + 1 + x];
      const a = x >= 4 ? pix[y * stride + x - 4] : 0, b = y ? pix[(y - 1) * stride + x] : 0, c = x >= 4 && y ? pix[(y - 1) * stride + x - 4] : 0;
      const pa = Math.abs(b - c), pb = Math.abs(a - c), pc = Math.abs(a + b - 2 * c);
      const pred = f === 0 ? 0 : f === 1 ? a : f === 2 ? b : f === 3 ? (a + b) >> 1 : (pa <= pb && pa <= pc ? a : pb <= pc ? b : c);
      pix[y * stride + x] = (v + pred) & 255;
    }
  }
  return { w, h, pix };
}
const crcTable = Array.from({ length: 256 }, (_, n) => { let c = n; for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1; return c >>> 0; });
const crc = (b) => { let c = 0xffffffff; for (const x of b) c = crcTable[(c ^ x) & 255] ^ (c >>> 8); return (c ^ 0xffffffff) >>> 0; };
const chunk = (type, data) => {
  const t = Buffer.from(type, 'latin1'), len = Buffer.alloc(4), c = Buffer.alloc(4);
  len.writeUInt32BE(data.length); c.writeUInt32BE(crc(Buffer.concat([t, data])));
  return Buffer.concat([len, t, data, c]);
};
function writePng(file, w, h, pix) {
  const raw = Buffer.alloc((w * 4 + 1) * h);
  for (let y = 0; y < h; y++) pix.copy(raw, y * (w * 4 + 1) + 1, y * w * 4, (y + 1) * w * 4);
  const ihdr = Buffer.alloc(13); ihdr.writeUInt32BE(w, 0); ihdr.writeUInt32BE(h, 4); ihdr[8] = 8; ihdr[9] = 6;
  fs.writeFileSync(file, Buffer.concat([Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]), chunk('IHDR', ihdr), chunk('IDAT', zlib.deflateSync(raw)), chunk('IEND', Buffer.alloc(0))]));
}

const BLACK = { x: 0, y: 500, w: 130, h: 130 };
const LIP = { x: 140, y: 500, w: 116, h: 204 };
const tex = readPng(pngFile);
// The lip is the average colour of the sandstone, a little lighter.
let r = 0, g = 0, b = 0;
for (let y = 0; y < 320; y++) for (let x = 0; x < 192; x++) { const o = (y * tex.w + x) * 4; r += tex.pix[o]; g += tex.pix[o + 1]; b += tex.pix[o + 2]; }
const n = 320 * 192;
const lip = [r, g, b].map((v) => Math.min(255, Math.round(v / n * 1.12)));
const fill = (rect, c) => {
  for (let y = rect.y; y < rect.y + rect.h; y++) for (let x = rect.x; x < rect.x + rect.w; x++) {
    const o = (y * tex.w + x) * 4; tex.pix[o] = c[0]; tex.pix[o + 1] = c[1]; tex.pix[o + 2] = c[2]; tex.pix[o + 3] = 255;
  }
};
fill(BLACK, [0, 0, 0]);
fill(LIP, lip);
writePng(pngFile, tex.w, tex.h, tex.pix);

// ---- model
let nextId = 1;
const faceAt = (rect) => ({ offset: { x: rect.x, y: rect.y }, mirror: { x: false, y: false }, angle: 0 });
const layout = (rect) => { const f = faceAt(rect); return { front: f, back: f, left: f, right: f, top: f, bottom: f }; };
const box = (name, size, y, deg, rect) => ({
  id: String(nextId++), name, children: [],
  position: { x: 0, y: 0, z: 0 },
  orientation: { x: 0, y: round1(Math.sin(deg * Math.PI / 360) * 1e5) / 1e5, z: 0, w: round1(Math.cos(deg * Math.PI / 360) * 1e5) / 1e5 },
  shape: {
    type: 'box', offset: { x: 0, y, z: 0 }, stretch: { x: 1, y: 1, z: 1 },
    settings: { size }, visible: true, doubleSided: false, shadingMode: 'flat', unwrapMode: 'custom', textureLayout: layout(rect),
  },
});
const group = (name, children) => ({
  id: String(nextId++), name, children,
  position: { x: 0, y: 0, z: 0 }, orientation: { x: 0, y: 0, z: 0, w: 1 },
  shape: {
    type: 'none', offset: { x: 0, y: 0, z: 0 }, stretch: { x: 1, y: 1, z: 1 }, settings: { isPiece: true },
    visible: true, doubleSided: false, shadingMode: 'flat', unwrapMode: 'custom', textureLayout: {},
  },
});
const disc = (name, r, height, y, rect) => {
  const s = strip(r);
  const boxes = [];
  for (let k = 0; k < SIDES; k++) boxes.push(box(`${name}_${k}`, { x: s.w, y: height, z: s.l }, y, k * 180 / SIDES, rect));
  return boxes;
};
const BODY = { x: 0, y: 0, w: 0, h: 0 };
const sandRect = BODY; // offset (0, 0): the sandstone, as before
const model = {
  nodes: [
    group('Lower', disc('Lower', R, HEIGHT, -HEIGHT / 2, sandRect)),
    group('Upper', disc('Upper', R, HEIGHT, HEIGHT / 2, sandRect)),
    // 1 unit proud of the top of the body (y = 320), the hole 1 unit above the lip.
    group('MouthHole', [
      group('Lip', disc('Lip', R, 1, HEIGHT + 0.5, LIP)),
      group('Hole', disc('Hole', HOLE_R, 1, HEIGHT + 1.5, BLACK)),
    ]),
  ],
  lod: 'auto',
};
fs.writeFileSync(modelFile, JSON.stringify(model, null, 2) + '\n');
const s = strip(R), h = strip(HOLE_R);
console.log(`body strips ${s.w} x ${s.l} (x6), hole strips ${h.w} x ${h.l}, lip colour ${lip}`);
