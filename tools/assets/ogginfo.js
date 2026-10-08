// Prints channels, sample rate and length of .ogg (Vorbis) files. Usage: node tools/assets/ogginfo.js <file>...
const fs = require('fs');
for (const f of process.argv.slice(2)) {
  const b = fs.readFileSync(f);
  const i = b.indexOf(Buffer.from('\x01vorbis', 'latin1'));
  const channels = b[i + 11];
  const rate = b.readUInt32LE(i + 12);
  const last = b.lastIndexOf(Buffer.from('OggS', 'latin1'));
  const granule = Number(b.readBigUInt64LE(last + 6));
  console.log(`${f.split(/[\/]/).pop()}: ${channels === 1 ? 'mono' : channels === 2 ? 'stereo' : channels + ' ch'}, ${rate} Hz, ${(granule / rate).toFixed(2)} s`);
}
