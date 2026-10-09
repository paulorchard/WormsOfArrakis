// Generates the fake blocks of the sink-and-rebound ripple. Usage: node tools/assets/sinkblocks.js
//  Arrakis_Worm_Ripple_Layer_5 to _16: thin layers (Material Empty, no collision) put on top of the surface for a raised block.
//  Arrakis_Worm_Sunk_1 to _16: a full-collision sand block drawn n px lower (a box 32 x (32 - n) x 32, bottom aligned).
// Layers 1 to 4 are hand-made and left alone. The models copy the node and texture layout of Arrakis_Worm_Ripple_Layer_1.
const fs = require('fs'), path = require('path');
const root = path.resolve(__dirname, '../../src/main/resources');
const modelDir = path.join(root, 'Common/Blocks/Worms_of_Arrakis');
const itemDir = path.join(root, 'Server/Item/Items/Worms_of_Arrakis');
const langFile = path.join(root, 'Server/Languages/en-US/server.lang');

const template = JSON.parse(fs.readFileSync(path.join(modelDir, 'Arrakis_Worm_Ripple_Layer_1.blockymodel'), 'utf8'));

function model(height, sideOffsetY) {
  const m = JSON.parse(JSON.stringify(template));
  const node = m.nodes[0];
  node.position.y = height / 2;
  node.shape.settings.size.y = height;
  for (const side of ['front', 'back', 'left', 'right']) node.shape.textureLayout[side].offset.y = sideOffsetY;
  return m;
}

function item(id, blockType) {
  return {
    TranslationProperties: { Name: `server.items.${id}.name` },
    PlayerAnimationsId: 'Block',
    BlockType: Object.assign({
      Material: 'Empty',
      DrawType: 'Model',
      CustomModel: `Blocks/Worms_of_Arrakis/${id}.blockymodel`,
      CustomModelTexture: [{ Texture: 'BlockTextures/Soil_Sand_White.png', Weight: 1 }],
      Group: 'Sand',
      Flags: {},
      BlockParticleSetId: 'Sand',
      BlockSoundSetId: 'Sand',
      PhysicalMaterialId: 'Dirt',
      ParticleColor: '#fcdc7c',
      TextureComputedColor: '#E0C469',
    }, blockType),
  };
}

const write = (file, obj) => fs.writeFileSync(file, JSON.stringify(obj, null, 2) + '\n');
const lang = [];

for (let n = 5; n <= 16; n++) {
  const id = `Arrakis_Worm_Ripple_Layer_${n}`;
  // The side faces start at texture row 16, so a layer can be 16 px tall at most.
  write(path.join(modelDir, `${id}.blockymodel`), model(n, 16));
  write(path.join(itemDir, `${id}.json`), item(id, {}));
  lang.push(`items.${id}.name = Ripple layer ${n} px (test)`);
}
for (let n = 1; n <= 16; n++) {
  const id = `Arrakis_Worm_Sunk_${n}`;
  // The sides are taller than half the texture, so they start at row 0.
  write(path.join(modelDir, `${id}.blockymodel`), model(32 - n, 0));
  write(path.join(itemDir, `${id}.json`), item(id, { Material: 'Solid', HitboxType: 'Full', InteractionHitboxType: 'Full' }));
  lang.push(`items.${id}.name = Sunk sand ${n} px (test)`);
}

let text = fs.readFileSync(langFile, 'utf8');
const eol = text.includes('\r\n') ? '\r\n' : '\n';
for (const line of lang) {
  const key = line.split(' = ')[0];
  if (!text.includes(key + ' =')) text = text.replace(/\s*$/, eol) + line + eol;
}
fs.writeFileSync(langFile, text);
console.log(`wrote ${lang.length} items`);
