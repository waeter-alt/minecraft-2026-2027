import * as THREE from 'three';

// Lien du zip du launcher. Vide tant qu'aucune release publique n'existe :
// le bouton affiche alors un message au lieu de télécharger.
const DOWNLOAD_URL = '';

const reduceMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
const $ = (id) => document.getElementById(id);

/* =========================================================================
   Textures pixel-art générées en code (16 texels par bloc, comme Minecraft)
   ========================================================================= */

function rng(seed) {
  return () => {
    seed = (seed + 0x6d2b79f5) | 0;
    let t = Math.imul(seed ^ (seed >>> 15), 1 | seed);
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}
const R = rng(20262027);
const pick = (a) => a[Math.floor(R() * a.length)];

function canvas(w, h, paint) {
  const c = document.createElement('canvas');
  c.width = w;
  c.height = h;
  const g = c.getContext('2d');
  paint(g, w, h);
  return c;
}
function px(g, x, y, col) {
  g.fillStyle = col;
  g.fillRect(x, y, 1, 1);
}

// Planches : rangées de lattes avec fil du bois, joint sombre et nœuds.
function planks(size, boardH, pal, seam, vertical = false) {
  return canvas(size, size, (g) => {
    const put = (x, y, c) => (vertical ? px(g, y, x, c) : px(g, x, y, c));
    for (let b = 0; b < size / boardH; b++) {
      const y0 = b * boardH;
      const base = R() < 0.5 ? 0 : 1;
      const joint = Math.floor(R() * size);
      const rows = [];
      for (let r = 0; r < boardH; r++) rows.push(R() < 0.35 ? 1 : R() < 0.15 ? -1 : 0);
      for (let r = 0; r < boardH; r++) {
        for (let x = 0; x < size; x++) {
          let k = base + rows[r] + (R() < 0.18 ? 1 : 0);
          k = Math.max(0, Math.min(pal.length - 1, k));
          let c = pal[k];
          if (r === boardH - 1) c = seam;
          else if (r === 0) c = pal[0];
          if (x === joint && r < boardH - 1) c = seam;
          put(x, y0 + r, c);
        }
      }
      if (R() < 0.7) {
        const kx = Math.floor(R() * (size - 2));
        const ky = y0 + 2 + Math.floor(R() * (boardH - 4));
        put(kx, ky, seam);
        put(kx + 1, ky, pal[pal.length - 1]);
      }
    }
  });
}

function noise(size, pal, extra) {
  return canvas(size, size, (g) => {
    for (let y = 0; y < size; y++) for (let x = 0; x < size; x++) px(g, x, y, pick(pal));
    if (extra) extra(g, size);
  });
}

function pixelArt(rows, colors) {
  return canvas(rows[0].length, rows.length, (g) => {
    rows.forEach((row, y) => [...row].forEach((ch, x) => colors[ch] && px(g, x, y, colors[ch])));
  });
}

const PAL = {
  table: ['#55361f', '#4e311c', '#472c19', '#3f2716'],
  tableSeam: '#1c0f06',
  chest: ['#c48a46', '#b67e3f', '#a67238', '#946431'],
  chestSeam: '#3c2511',
  iron: ['#cfcfcf', '#bdbdbd', '#aeaeae', '#9d9d9d'],
  darkIron: ['#5c5c5c', '#4f4f4f', '#434343'],
  gold: ['#ffe58f', '#f6c84f', '#e6ad3a', '#cf9428'],
  wax: ['#f3e9cc', '#ece0bd', '#e2d3aa'],
  velvet: ['#86211f', '#7a1c1b', '#6d1817', '#932a26'],
  paper: ['#efdcad', '#e9d4a0', '#e2ca93', '#dabf85'],
  foam: ['#fffdf4', '#f7f0dc', '#efe5c9'],
  ink: '#5a3a1c',
};

const C = {
  table: planks(32, 8, PAL.table, PAL.tableSeam),
  chest: planks(16, 4, PAL.chest, PAL.chestSeam),
  staves: planks(16, 4, ['#9a6a38', '#8e6133', '#81572d', '#744e28'], '#3a230f', true),
  iron: noise(16, PAL.iron, (g) => {
    for (let x = 3; x < 16; x += 8) { px(g, x, 7, '#5f5f5f'); px(g, x, 8, '#7a7a7a'); }
  }),
  darkIron: noise(16, PAL.darkIron),
  gold: noise(16, PAL.gold, (g) => {
    for (let i = 0; i < 6; i++) px(g, Math.floor(R() * 16), Math.floor(R() * 16), '#fff6c8');
  }),
  wax: noise(16, PAL.wax),
  velvet: noise(16, PAL.velvet),
  paper: noise(32, PAL.paper, (g) => {
    for (let i = 0; i < 14; i++) px(g, Math.floor(R() * 32), Math.floor(R() * 32), '#c9a96f');
    for (let i = 0; i < 4; i++) {
      const x = Math.floor(R() * 30), y = Math.floor(R() * 30);
      px(g, x, y, '#d4b67b'); px(g, x + 1, y, '#d4b67b'); px(g, x, y + 1, '#d4b67b');
    }
  }),
  foam: noise(16, PAL.foam),
};

// Carte griffonnée posée sur la table.
C.map = canvas(48, 64, (g) => {
  for (let y = 0; y < 64; y++) for (let x = 0; x < 48; x++) px(g, x, y, pick(PAL.paper));
  for (let x = 0; x < 48; x++) { px(g, x, 0, '#c8a76a'); px(g, x, 63, '#c8a76a'); }
  for (let y = 0; y < 64; y++) { px(g, 0, y, '#c8a76a'); px(g, 47, y, '#c8a76a'); }
  // Côte
  let cx = 8;
  for (let y = 3; y < 61; y++) {
    cx += Math.round((R() - 0.5) * 2.2);
    cx = Math.max(4, Math.min(16, cx));
    px(g, cx, y, PAL.ink);
    for (let x = 2; x < cx; x += 3) if ((x + y) % 4 === 0) px(g, x, y, '#8fa3a8');
  }
  // Rivière
  let rx = 34;
  for (let y = 4; y < 44; y++) {
    rx += Math.round((R() - 0.5) * 2);
    rx = Math.max(22, Math.min(42, rx));
    px(g, rx, y, '#4f7389');
  }
  // Chemin en pointillés
  for (let i = 0; i < 26; i += 2) px(g, 18 + i, 50 - Math.round(i * 0.7), '#8a3a24');
  // Châteaux (petits créneaux)
  const castle = (x, y) => {
    for (let i = 0; i < 5; i++) for (let j = 0; j < 3; j++) px(g, x + i, y + j, PAL.ink);
    px(g, x, y - 1, PAL.ink); px(g, x + 2, y - 1, PAL.ink); px(g, x + 4, y - 1, PAL.ink);
  };
  castle(20, 49); castle(38, 30); castle(26, 14);
  // Rose des vents
  const rx0 = 40, ry0 = 54;
  for (let i = -3; i <= 3; i++) { px(g, rx0 + i, ry0, '#8a3a24'); px(g, rx0, ry0 + i, '#8a3a24'); }
  px(g, rx0, ry0 - 4, PAL.ink);
});

// Faces de dé (ordre BoxGeometry : +x, -x, +y, -y, +z, -z ; faces opposées = 7).
const PIPS = {
  1: [[3, 3]],
  2: [[1, 1], [5, 5]],
  3: [[1, 1], [3, 3], [5, 5]],
  4: [[1, 1], [5, 1], [1, 5], [5, 5]],
  5: [[1, 1], [5, 1], [3, 3], [1, 5], [5, 5]],
  6: [[1, 1], [5, 1], [1, 3], [5, 3], [1, 5], [5, 5]],
};
const diceFace = (n) => canvas(7, 7, (g) => {
  for (let y = 0; y < 7; y++) for (let x = 0; x < 7; x++) {
    const edge = x === 0 || y === 0 || x === 6 || y === 6;
    px(g, x, y, edge ? '#d8cdb4' : pick(['#f4eedf', '#efe7d4']));
  }
  PIPS[n].forEach(([x, y]) => px(g, x, y, n === 1 ? '#a3241d' : '#2a1d14'));
});

// Flamme de bougie, trois images pour l'animation.
const flameFrames = [0, 1, 2].map((f) => pixelArt(
  [
    ['....y...', '...yy...', '...yyy..', '..yyy...'][f % 4] || '...yy...',
    f === 1 ? '...yyo..' : '..oyy...',
    '..oyyo..',
    '.oyyyyo.',
    '.oywwyo.',
    '.oywwyo.',
    'ooywwyoo',
    'oyywwyyo',
    '.oywwyo.',
    '.ooyyoo.',
    '..oooo..',
    '...bb...',
  ],
  { y: '#ffd34d', o: '#ff8a1f', w: '#fff8d8', b: '#5a86ff' },
));

const keyIcon = pixelArt(
  ['.###........', '#ggg#.......', '#g.g#######.', '#g.gggggggg#', '#ggg###gg#g#', '.###...##.#.'],
  { '#': '#3b2304', g: '#ffe27a' },
);
const dlIcon = pixelArt(
  ['...####...', '...#ww#...', '...#ww#...', '...#ww#...', '####ww####', '.#wwwwww#.', '..#wwww#..',
    '...#ww#...', '....##....', 'w........w', 'wwwwwwwwww'],
  { '#': '#1d3a10', w: '#ffffff' },
);

/* =========================================================================
   Interface HTML : textures du parchemin, icônes, téléchargement
   ========================================================================= */

const root = document.documentElement;
const big = (c, s) => {
  // Agrandit sans lissage pour que le CSS n'ait pas à deviner.
  const out = document.createElement('canvas');
  out.width = c.width * s;
  out.height = c.height * s;
  const g = out.getContext('2d');
  g.imageSmoothingEnabled = false;
  g.drawImage(c, 0, 0, out.width, out.height);
  return out.toDataURL();
};
root.style.setProperty('--paper-img', `url(${big(C.paper, 4)})`);
root.style.setProperty('--rod-img', `url(${big(planks(32, 8, ['#6b4021', '#5f381c', '#533019', '#472915'], '#24140a'), 4)})`);
document.querySelectorAll('.key-icon').forEach((el) => {
  el.style.backgroundImage = `url(${big(keyIcon, 3)})`;
  el.style.width = '36px';
  el.style.height = '18px';
});
document.querySelectorAll('.dl-icon').forEach((el) => {
  el.style.backgroundImage = `url(${big(dlIcon, 3)})`;
  el.style.width = '30px';
  el.style.height = '33px';
});

const dlBtn = $('download-btn');
if (DOWNLOAD_URL) {
  dlBtn.href = DOWNLOAD_URL;
} else {
  dlBtn.addEventListener('click', (e) => {
    e.preventDefault();
    $('dl-pending').hidden = false;
  });
}

/* =========================================================================
   Moteur 3D
   ========================================================================= */

const canvasEl = $('scene');
let renderer;
try {
  renderer = new THREE.WebGLRenderer({ canvas: canvasEl, antialias: true });
} catch (err) {
  document.body.classList.add('no-webgl');
  throw err;
}
renderer.setPixelRatio(Math.min(window.devicePixelRatio, 2));
renderer.outputColorSpace = THREE.SRGBColorSpace;
renderer.toneMapping = THREE.ACESFilmicToneMapping;
renderer.toneMappingExposure = 1.05;
renderer.shadowMap.enabled = true;
renderer.shadowMap.type = THREE.PCFSoftShadowMap;

const maxAniso = renderer.capabilities.getMaxAnisotropy();
const texCache = new Map();
function tex(c) {
  if (texCache.has(c)) return texCache.get(c);
  const t = new THREE.CanvasTexture(c);
  t.colorSpace = THREE.SRGBColorSpace;
  t.magFilter = THREE.NearestFilter;
  t.minFilter = THREE.NearestMipmapLinearFilter;
  t.wrapS = t.wrapT = THREE.RepeatWrapping;
  t.anisotropy = maxAniso;
  texCache.set(c, t);
  return t;
}
function mat(c, opts = {}) {
  return new THREE.MeshStandardMaterial({ map: tex(c), roughness: 0.85, metalness: 0, ...opts });
}

const M = {
  table: mat(C.table, { roughness: 0.9 }),
  chest: mat(C.chest),
  staves: mat(C.staves),
  iron: mat(C.iron, { roughness: 0.45, metalness: 0.35 }),
  darkIron: mat(C.darkIron, { roughness: 0.5, metalness: 0.3 }),
  gold: mat(C.gold, { roughness: 0.35, metalness: 0.25, emissive: 0x3a2400, emissiveIntensity: 0.4 }),
  goldFlat: mat(C.gold, { roughness: 0.35, metalness: 0.25, emissive: 0x3a2400, emissiveIntensity: 0.4, flatShading: true }),
  wax: mat(C.wax, { roughness: 0.6 }),
  velvet: mat(C.velvet, { roughness: 1 }),
  paper: mat(C.paper, { roughness: 0.95 }),
  foam: mat(C.foam, { roughness: 0.9 }),
  map: mat(C.map, { roughness: 0.95 }),
  sealWax: new THREE.MeshStandardMaterial({ color: 0x9e2a22, roughness: 0.5, flatShading: true }),
  wick: new THREE.MeshStandardMaterial({ color: 0x1a1410, roughness: 1 }),
  gem: new THREE.MeshStandardMaterial({ color: 0xb46cf2, emissive: 0x8b3be0, emissiveIntensity: 0.8, roughness: 0.2, flatShading: true }),
};
const diceMats = [3, 4, 1, 6, 2, 5].map((n) => mat(diceFace(n), { roughness: 0.6 }));

// Pavé dont les UV suivent la taille réelle : la densité de pixels reste la même partout.
function vbox(w, h, d, material, ppu = 1) {
  const g = new THREE.BoxGeometry(w, h, d);
  const uv = g.attributes.uv;
  const dims = [[d, h], [d, h], [w, d], [w, d], [w, h], [w, h]];
  for (let f = 0; f < 6; f++) {
    for (let i = 0; i < 4; i++) {
      const k = f * 4 + i;
      uv.setXY(k, uv.getX(k) * dims[f][0] * ppu, uv.getY(k) * dims[f][1] * ppu);
    }
  }
  const m = new THREE.Mesh(g, material);
  m.castShadow = true;
  m.receiveShadow = true;
  return m;
}
function at(obj, x, y, z, parent) {
  obj.position.set(x, y, z);
  if (parent) parent.add(obj);
  return obj;
}
function cyl(r, h, material, seg = 8) {
  const m = new THREE.Mesh(new THREE.CylinderGeometry(r, r, h, seg), material);
  m.castShadow = true;
  m.receiveShadow = true;
  return m;
}

const scene = new THREE.Scene();
scene.background = new THREE.Color(0x120a05);
scene.fog = new THREE.Fog(0x120a05, 26, 52);

const camera = new THREE.PerspectiveCamera(35, 1, 0.1, 120);

/* ---------- Table ---------- */
const table = vbox(44, 1.2, 30, M.table, 0.5);
table.position.y = -0.6;
table.castShadow = false;
scene.add(table);

/* ---------- Coffre ---------- */
const CH = { w: 5, h: 2.2, d: 3.4, t: 0.25, lidH: 0.9 };
const chest = new THREE.Group();
scene.add(chest);

(function buildBase() {
  const { w, h, d, t } = CH;
  at(vbox(w, t, d, M.chest), 0, t / 2, 0, chest);
  at(vbox(w, h, t, M.chest), 0, h / 2, d / 2 - t / 2, chest);
  at(vbox(w, h, t, M.chest), 0, h / 2, -d / 2 + t / 2, chest);
  at(vbox(t, h, d - 2 * t, M.chest), -w / 2 + t / 2, h / 2, 0, chest);
  at(vbox(t, h, d - 2 * t, M.chest), w / 2 - t / 2, h / 2, 0, chest);
  // Fond en velours
  const velvet = at(vbox(w - 2 * t, 0.08, d - 2 * t, M.velvet), 0, t + 0.04, 0, chest);
  velvet.castShadow = false;
  // Cerclage de fer en haut de la caisse
  const o = 0.05;
  at(vbox(w + o * 2, 0.22, 0.08, M.iron), 0, h - 0.2, d / 2 + 0.02, chest);
  at(vbox(w + o * 2, 0.22, 0.08, M.iron), 0, h - 0.2, -d / 2 - 0.02, chest);
  at(vbox(0.08, 0.22, d + o * 2, M.iron), -w / 2 - 0.02, h - 0.2, 0, chest);
  at(vbox(0.08, 0.22, d + o * 2, M.iron), w / 2 + 0.02, h - 0.2, 0, chest);
  // Bandes verticales
  for (const x of [-1.85, 1.85]) {
    at(vbox(0.32, h, 0.07, M.iron), x, h / 2, d / 2 + 0.03, chest);
    at(vbox(0.32, h, 0.07, M.iron), x, h / 2, -d / 2 - 0.03, chest);
  }
  // Coins dorés
  for (const sx of [-1, 1]) for (const sz of [-1, 1]) {
    at(vbox(0.4, 0.4, 0.4, M.gold), sx * (w / 2 - 0.12), 0.2, sz * (d / 2 - 0.12), chest);
    at(vbox(0.4, 0.3, 0.4, M.gold), sx * (w / 2 - 0.12), h - 0.15, sz * (d / 2 - 0.12), chest);
  }
  // Pieds
  for (const sx of [-1, 1]) for (const sz of [-1, 1]) {
    at(vbox(0.5, 0.12, 0.5, M.darkIron), sx * (w / 2 - 0.25), 0.02, sz * (d / 2 - 0.25), chest);
  }
  // Serrure avant
  const plate = at(vbox(0.72, 0.95, 0.1, M.gold), 0, 1.3, d / 2 + 0.06, chest);
  plate.castShadow = false;
  at(vbox(0.14, 0.14, 0.04, M.wick), 0, 1.4, d / 2 + 0.12, chest);
  at(vbox(0.07, 0.24, 0.04, M.wick), 0, 1.24, d / 2 + 0.12, chest);
})();

// Engrenages en blocs : un octogone et des dents.
function gear(r, teeth, thick, material) {
  const g = new THREE.Group();
  const body = cyl(r, thick, material);
  body.rotation.x = Math.PI / 2;
  g.add(body);
  for (let i = 0; i < teeth; i++) {
    const a = (i / teeth) * Math.PI * 2;
    const tooth = vbox(0.16, 0.2, thick, material, 4);
    tooth.position.set(Math.cos(a) * (r + 0.07), Math.sin(a) * (r + 0.07), 0);
    tooth.rotation.z = a;
    g.add(tooth);
  }
  const hub = cyl(r * 0.35, thick + 0.08, M.darkIron);
  hub.rotation.x = Math.PI / 2;
  g.add(hub);
  return g;
}
const gears = [];
function addGear(x, y, r, teeth, dir, rot = 0) {
  const g = gear(r, teeth, 0.12, M.goldFlat);
  g.position.set(x, y, CH.d / 2 + 0.1);
  g.rotation.z = rot;
  chest.add(g);
  gears.push({ g, speed: dir / r });
}
addGear(-1.0, 1.05, 0.48, 10, 1);
addGear(1.0, 1.05, 0.48, 10, -1, 0.3);
addGear(-1.05, 1.75, 0.22, 6, -1);
addGear(1.05, 1.75, 0.22, 6, 1);

/* ---------- Couvercle en deux battants ---------- */
function buildLid(side) {
  const { w, d, t, lidH } = CH;
  const hw = w / 2;
  const pivot = new THREE.Group();
  pivot.position.set(side * hw, CH.h, 0);
  chest.add(pivot);
  const lid = new THREE.Group();
  pivot.add(lid);
  const cx = -side * hw / 2; // centre du battant, vers l'intérieur
  const inner = -side * (hw - t / 2);
  at(vbox(hw, t, d, M.chest), cx, lidH - t / 2, 0, lid);
  at(vbox(hw, lidH - t, t, M.chest), cx, (lidH - t) / 2, d / 2 - t / 2, lid);
  at(vbox(hw, lidH - t, t, M.chest), cx, (lidH - t) / 2, -d / 2 + t / 2, lid);
  at(vbox(t, lidH - t, d - 2 * t, M.chest), -side * t / 2, (lidH - t) / 2, 0, lid);
  at(vbox(t, lidH - t, d - 2 * t, M.chest), inner, (lidH - t) / 2, 0, lid);
  // Velours sous le battant
  const v = at(vbox(hw - 2 * t, 0.05, d - 2 * t, M.velvet), cx, lidH - t - 0.03, 0, lid);
  v.castShadow = false;
  // Fer sur le dessus
  for (const z of [-1.1, 1.1]) at(vbox(hw + 0.04, 0.07, 0.32, M.iron), cx, lidH + 0.03, z, lid);
  at(vbox(hw + 0.04, 0.2, 0.07, M.iron), cx, 0.12, d / 2 + 0.03, lid);
  at(vbox(hw + 0.04, 0.2, 0.07, M.iron), cx, 0.12, -d / 2 - 0.03, lid);
  // Liseré doré le long de la jointure
  at(vbox(0.14, 0.08, d + 0.04, M.gold), -side * (hw - 0.07), lidH + 0.03, 0, lid);
  // Coins dorés
  for (const sz of [-1, 1]) at(vbox(0.4, 0.4, 0.4, M.gold), -side * 0.12, lidH - 0.15, sz * (d / 2 - 0.12), lid);
  // Charnières
  for (const z of [-1.1, 1.1]) {
    const hinge = cyl(0.12, 0.5, M.darkIron);
    hinge.rotation.x = Math.PI / 2;
    at(hinge, side * 0.03, 0.05, z, lid);
  }
  return pivot;
}
const lidL = buildLid(-1);
const lidR = buildLid(1);

/* ---------- Sceau central : verrous qui rentrent, disque qui tourne ---------- */
const SEAL_Y = CH.h + CH.lidH + 0.06;
const seal = new THREE.Group();
seal.position.set(0, SEAL_Y, 0);
chest.add(seal);
const sealSpin = new THREE.Group();
seal.add(sealSpin);
const sealDisc = cyl(0.82, 0.14, M.goldFlat);
sealSpin.add(sealDisc);
const ring = cyl(0.58, 0.18, M.darkIron);
sealSpin.add(ring);
for (let i = 0; i < 4; i++) {
  const a = (i / 4) * Math.PI * 2 + Math.PI / 4;
  const stud = vbox(0.16, 0.2, 0.16, M.gold, 4);
  stud.position.set(Math.cos(a) * 0.7, 0.05, Math.sin(a) * 0.7);
  sealSpin.add(stud);
}
const gem = new THREE.Mesh(new THREE.OctahedronGeometry(0.3, 0), M.gem);
gem.position.y = 0.2;
gem.scale.y = 0.7;
sealSpin.add(gem);

const latches = [];
for (const side of [-1, 1]) for (const z of [-0.42, 0.42]) {
  const pivot = new THREE.Group();
  pivot.position.set(side * 0.6, -0.02, z);
  const bar = vbox(1.3, 0.1, 0.22, M.iron, 4);
  bar.position.x = side * 0.65;
  pivot.add(bar);
  const tip = vbox(0.2, 0.16, 0.3, M.darkIron, 4);
  tip.position.x = side * 1.3;
  pivot.add(tip);
  seal.add(pivot);
  latches.push(pivot);
}

/* ---------- Intérieur : le parchemin roulé ---------- */
const scrollRoll = new THREE.Group();
scrollRoll.position.set(0, CH.t + 0.42, 0.1);
scrollRoll.rotation.y = 0.12;
chest.add(scrollRoll);
{
  const roll = cyl(0.32, 3.0, M.paper, 10);
  roll.rotation.z = Math.PI / 2;
  scrollRoll.add(roll);
  for (const s of [-1, 1]) {
    const cap = cyl(0.4, 0.16, M.goldFlat);
    cap.rotation.z = Math.PI / 2;
    cap.position.x = s * 1.56;
    scrollRoll.add(cap);
    const knob = cyl(0.14, 0.2, M.goldFlat, 6);
    knob.rotation.z = Math.PI / 2;
    knob.position.x = s * 1.72;
    scrollRoll.add(knob);
  }
  const ribbon = cyl(0.335, 0.22, M.sealWax, 10);
  ribbon.rotation.z = Math.PI / 2;
  scrollRoll.add(ribbon);
  const wax = cyl(0.2, 0.08, M.sealWax, 8);
  wax.rotation.x = Math.PI / 2;
  wax.position.set(0, 0, 0.35);
  scrollRoll.add(wax);
}
// Quelques pièces au fond du coffre
const coinGeo = new THREE.CylinderGeometry(0.26, 0.26, 0.07, 8);
function coin(x, y, z, parent, tilt = 0) {
  const c = new THREE.Mesh(coinGeo, M.goldFlat);
  c.castShadow = c.receiveShadow = true;
  c.position.set(x, y, z);
  c.rotation.set(tilt, R() * Math.PI, tilt * 0.5);
  parent.add(c);
  return c;
}
[[-1.7, -0.9], [1.6, 0.9], [1.9, -0.7], [-1.5, 1.0], [-1.9, 0.6]].forEach(([x, z]) => coin(x, CH.t + 0.12, z, chest));

const innerLight = new THREE.PointLight(0xffc46b, 0, 9, 2);
innerLight.position.set(0, 1.4, 0);
chest.add(innerLight);

/* ---------- Bougie ---------- */
const candle = new THREE.Group();
candle.position.set(-6.4, 0, -2.4);
scene.add(candle);
at(vbox(1.5, 0.14, 1.5, M.darkIron, 2), 0, 0.07, 0, candle);
at(vbox(0.9, 0.12, 0.9, M.darkIron, 2), 0, 0.2, 0, candle);
const handle = new THREE.Group();
at(vbox(0.14, 0.12, 0.6, M.darkIron, 4), 0.85, 0.12, 0, handle);
at(vbox(0.14, 0.4, 0.14, M.darkIron, 4), 0.95, 0.3, 0.22, handle);
candle.add(handle);
const CANDLE_H = 1.7;
at(vbox(0.56, CANDLE_H, 0.56, M.wax), 0, 0.26 + CANDLE_H / 2, 0, candle);
// Coulures de cire
[[0.3, 1.5, 0.1, 0.5], [-0.3, 1.6, -0.12, 0.35], [0.05, 1.55, 0.3, 0.6], [-0.12, 1.2, -0.3, 0.3]].forEach(([x, y, z, h]) => {
  at(vbox(Math.abs(x) > 0.2 ? 0.08 : 0.16, h, Math.abs(z) > 0.2 ? 0.08 : 0.16, M.wax, 4), x, y - h / 2 + 0.25, z, candle);
});
at(vbox(0.7, 0.1, 0.7, M.wax), 0, 0.31, 0, candle);
const WICK_Y = 0.26 + CANDLE_H;
at(vbox(0.06, 0.16, 0.06, M.wick), 0, WICK_Y + 0.08, 0, candle);

const flameTex = flameFrames.map((c) => {
  const t = tex(c);
  t.wrapS = t.wrapT = THREE.ClampToEdgeWrapping;
  return t;
});
const flame = new THREE.Sprite(new THREE.SpriteMaterial({ map: flameTex[0], transparent: true, depthWrite: false }));
flame.center.set(0.5, 0.05);
flame.position.set(0, WICK_Y + 0.02, 0);
flame.scale.set(0.36, 0.56, 1);
candle.add(flame);

const glowTex = new THREE.CanvasTexture(canvas(64, 64, (g) => {
  const grd = g.createRadialGradient(32, 32, 0, 32, 32, 32);
  grd.addColorStop(0, 'rgba(255,200,110,0.9)');
  grd.addColorStop(0.25, 'rgba(255,150,60,0.35)');
  grd.addColorStop(1, 'rgba(255,120,40,0)');
  g.fillStyle = grd;
  g.fillRect(0, 0, 64, 64);
}));
const glow = new THREE.Sprite(new THREE.SpriteMaterial({ map: glowTex, transparent: true, depthWrite: false, blending: THREE.AdditiveBlending }));
glow.position.set(0, WICK_Y + 0.25, 0);
glow.scale.set(2.6, 2.6, 1);
candle.add(glow);

const candleLight = new THREE.PointLight(0xffa54f, 70, 0, 2);
candleLight.position.set(0, WICK_Y + 0.5, 0);
candleLight.castShadow = true;
candleLight.shadow.mapSize.set(1024, 1024);
candleLight.shadow.bias = -0.004;
candleLight.shadow.radius = 4;
candle.add(candleLight);

/* ---------- Chope ---------- */
const mug = new THREE.Group();
mug.position.set(6.3, 0, -2.0);
mug.rotation.y = -0.45;
scene.add(mug);
at(vbox(1.3, 1.7, 1.3, M.staves), 0, 0.85, 0, mug);
for (const y of [0.3, 1.38]) at(vbox(1.38, 0.16, 1.38, M.iron), 0, y, 0, mug);
at(vbox(0.44, 0.18, 0.24, M.staves), 0.86, 1.3, 0, mug);
at(vbox(0.2, 0.98, 0.24, M.staves), 1.0, 0.86, 0, mug);
at(vbox(0.44, 0.18, 0.24, M.staves), 0.86, 0.45, 0, mug);
at(vbox(1.4, 0.3, 1.4, M.foam), 0, 1.82, 0, mug);
at(vbox(1.1, 0.14, 1.1, M.foam), 0.05, 2.0, -0.05, mug);
[[-0.66, 1.55, 0.2, 0.36], [0.2, 1.6, 0.66, 0.26], [-0.3, 1.62, -0.66, 0.2]].forEach(([x, y, z, h]) => {
  at(vbox(Math.abs(x) > 0.6 ? 0.1 : 0.22, h, Math.abs(z) > 0.6 ? 0.1 : 0.22, M.foam, 4), x, y, z, mug);
});

/* ---------- Pièces, dés, carte ---------- */
const coins = new THREE.Group();
coins.position.set(-4.6, 0, 2.7);
scene.add(coins);
for (let i = 0; i < 7; i++) coin((R() - 0.5) * 0.08, 0.035 + i * 0.075, (R() - 0.5) * 0.08, coins);
for (let i = 0; i < 4; i++) coin(0.65 + (R() - 0.5) * 0.06, 0.035 + i * 0.075, 0.25, coins);
[[-0.7, 0.5], [1.4, -0.3], [0.2, 1.0], [-1.2, -0.4]].forEach(([x, z]) => coin(x, 0.035, z, coins));

function die(x, z, ry, rx = 0) {
  const d = vbox(0.5, 0.5, 0.5, diceMats, 2);
  d.position.set(x, 0.25, z);
  d.rotation.set(rx, ry, 0);
  scene.add(d);
}
die(4.4, 2.9, 0.5);
die(5.3, 3.5, -0.3, Math.PI / 2);

const mapSheet = new THREE.Mesh(new THREE.PlaneGeometry(3.6, 4.8), M.map);
mapSheet.rotation.set(-Math.PI / 2, 0, 0.28);
mapSheet.position.set(-9.2, 0.01, 0.2);
mapSheet.receiveShadow = true;
scene.add(mapSheet);

/* ---------- Lumières d'ambiance ---------- */
scene.add(new THREE.HemisphereLight(0x8a6142, 0x140904, 0.9));
const key = new THREE.DirectionalLight(0xffd2a0, 1.6);
key.position.set(3, 14, 7);
key.castShadow = true;
key.shadow.mapSize.set(2048, 2048);
Object.assign(key.shadow.camera, { left: -14, right: 14, top: 12, bottom: -12, near: 1, far: 40 });
key.shadow.bias = -0.0008;
scene.add(key);
const moon = new THREE.DirectionalLight(0x6f7fb8, 0.35);
moon.position.set(-10, 10, -10);
scene.add(moon);
const hearth = new THREE.PointLight(0xff7a2e, 120, 0, 2);
hearth.position.set(15, 5, 3);
scene.add(hearth);

/* ---------- Particules : poussière et étincelles ---------- */
const DUST = 110;
const dustGeo = new THREE.BufferGeometry();
const dustPos = new Float32Array(DUST * 3);
const dustVel = [];
for (let i = 0; i < DUST; i++) {
  dustPos.set([(R() - 0.5) * 24, 0.4 + R() * 7, (R() - 0.5) * 14], i * 3);
  dustVel.push([(R() - 0.5) * 0.05, (R() - 0.3) * 0.04, (R() - 0.5) * 0.05]);
}
dustGeo.setAttribute('position', new THREE.BufferAttribute(dustPos, 3));
const dust = new THREE.Points(dustGeo, new THREE.PointsMaterial({
  color: 0xffd9a0, size: 0.05, transparent: true, opacity: 0.55, depthWrite: false, blending: THREE.AdditiveBlending,
}));
scene.add(dust);

const SPARKS = 220;
const sparkGeo = new THREE.BufferGeometry();
const sparkPos = new Float32Array(SPARKS * 3).fill(-100);
const sparkCol = new Float32Array(SPARKS * 3);
const sparks = [];
for (let i = 0; i < SPARKS; i++) sparks.push({ life: 0, max: 1, v: new THREE.Vector3(), c: new THREE.Color() });
sparkGeo.setAttribute('position', new THREE.BufferAttribute(sparkPos, 3));
sparkGeo.setAttribute('color', new THREE.BufferAttribute(sparkCol, 3));
const sparkPoints = new THREE.Points(sparkGeo, new THREE.PointsMaterial({
  size: 0.09, vertexColors: true, transparent: true, depthWrite: false, blending: THREE.AdditiveBlending,
}));
scene.add(sparkPoints);
const SPARK_COLORS = [0xffd34d, 0xffe9a0, 0xc77dff, 0x9b5cff, 0xffb347];
let sparkCursor = 0;
function emitSpark(x, y, z, vx, vy, vz, life) {
  const s = sparks[sparkCursor];
  sparkCursor = (sparkCursor + 1) % SPARKS;
  s.life = s.max = life;
  s.v.set(vx, vy, vz);
  s.c.setHex(pick(SPARK_COLORS));
  sparkPos.set([x, y, z], sparks.indexOf(s) * 3);
}

/* =========================================================================
   Caméra : cadrage selon l'écran, léger parallaxe, plongée dans le coffre
   ========================================================================= */

const LOOK = new THREE.Vector3(0, 0.6, 0.6);
const VIEW_DIR = new THREE.Vector3(0, Math.sin(THREE.MathUtils.degToRad(57)), Math.cos(THREE.MathUtils.degToRad(57)));
const DIVE_LOOK = new THREE.Vector3(0, 0.4, 0.05);
const DIVE_DIR = new THREE.Vector3(0, 0.95, 0.31).normalize();
const BASE_FOV = 35;
const DIVE_FOV = 44;
let baseDist = 20;
let diveDist = 6;

function hfovTan(fov, aspect) {
  return Math.tan(THREE.MathUtils.degToRad(fov) / 2) * aspect;
}
function resize() {
  const w = window.innerWidth;
  const h = window.innerHeight;
  renderer.setSize(w, h, false);
  camera.aspect = w / h;
  const portrait = camera.aspect < 0.9;
  const halfW = portrait ? 4.4 : 10.5;
  const halfH = portrait ? 7.5 : 6.2;
  baseDist = Math.max(halfW / hfovTan(BASE_FOV, camera.aspect), halfH / Math.tan(THREE.MathUtils.degToRad(BASE_FOV) / 2));
  diveDist = Math.max(5.2, 2.6 / hfovTan(DIVE_FOV, camera.aspect));
  if (window.innerWidth < 700) {
    candleLight.shadow.mapSize.set(512, 512);
  }
}
window.addEventListener('resize', resize);
resize();

const pointer = new THREE.Vector2(0, 0);
const parallax = new THREE.Vector2(0, 0);
const tmpA = new THREE.Vector3();
const tmpB = new THREE.Vector3();
const tmpLook = new THREE.Vector3();

/* =========================================================================
   Chronologie de l'ouverture (jouée en avant, rembobinée pour refermer)
   ========================================================================= */

const T_END = 3.7;
let T = 0;
let dir = 0; // 1 = ouverture, -1 = fermeture
let state = 'closed';

const clamp01 = (v) => Math.max(0, Math.min(1, v));
const seg = (t, a, b) => clamp01((t - a) / (b - a));
const inOut = (x) => (x < 0.5 ? 4 * x * x * x : 1 - Math.pow(-2 * x + 2, 3) / 2);
const outCubic = (x) => 1 - Math.pow(1 - x, 3);
const inCubic = (x) => x * x * x;
const outBack = (x) => 1 + 2.2 * Math.pow(x - 1, 3) + 1.2 * Math.pow(x - 1, 2);
const bump = (t, a, b) => Math.sin(Math.PI * seg(t, a, b));

let hoverAmt = 0;
let hovering = false;
let gearAngle = 0;
let burstDone = false;

function applyTimeline(t, time) {
  const retract = inOut(seg(t, 0, 0.5));
  latches.forEach((l) => { l.scale.x = Math.max(0.001, 1 - retract); });

  sealSpin.rotation.y = inOut(seg(t, 0.35, 1.35)) * Math.PI * 3;
  const rise = inCubic(seg(t, 1.1, 1.6));
  seal.position.y = SEAL_Y + rise * 0.9 + hoverAmt * 0.04;
  const s = Math.max(0.001, 1 - rise);
  sealSpin.scale.setScalar(s);
  seal.visible = s > 0.002;
  M.gem.emissiveIntensity = 0.8 + 0.5 * Math.sin(time * 2.6) * (1 - retract) + hoverAmt * 0.8 + bump(t, 0.9, 1.5) * 4;

  const open = outBack(seg(t, 1.35, 2.5)) * 2.05;
  lidL.rotation.z = open;
  lidR.rotation.z = -open;

  innerLight.intensity = outCubic(seg(t, 1.5, 2.4)) * 28;
}

function updateCamera(t) {
  const c = inOut(seg(t, 2.0, T_END));
  tmpA.copy(VIEW_DIR).multiplyScalar(baseDist).add(LOOK);
  tmpA.x += parallax.x * 0.9;
  tmpA.y += parallax.y * 0.5;
  tmpB.copy(DIVE_DIR).multiplyScalar(diveDist).add(DIVE_LOOK);
  camera.position.lerpVectors(tmpA, tmpB, c);
  tmpLook.lerpVectors(LOOK, DIVE_LOOK, c);
  camera.fov = BASE_FOV + (DIVE_FOV - BASE_FOV) * c;
  camera.updateProjectionMatrix();
  camera.lookAt(tmpLook);
}

const body = document.body;
const scrollLayer = $('parchemin');
const openBtn = $('open-btn');

function showScroll() {
  state = 'open';
  body.classList.remove('is-opening');
  body.classList.add('is-open');
  scrollLayer.classList.add('is-open');
  setTimeout(() => $('scroll-title').focus({ preventScroll: true }), 60);
}
function openChest(instant = false) {
  if (state !== 'closed' && state !== 'closing') return;
  body.classList.add('is-opening');
  if (instant || reduceMotion) {
    T = T_END;
    dir = 0;
    burstDone = true;
    showScroll();
    return;
  }
  state = 'opening';
  dir = 1;
}
function closeChest() {
  if (state !== 'open' && state !== 'opening') return;
  scrollLayer.classList.remove('is-open');
  body.classList.remove('is-open', 'is-opening');
  scrollLayer.scrollTop = 0;
  if (reduceMotion) {
    T = 0;
    dir = 0;
    state = 'closed';
  } else {
    state = 'closing';
    dir = -1;
  }
  burstDone = false;
  openBtn.focus({ preventScroll: true });
}

openBtn.addEventListener('click', () => openChest());
$('close-btn').addEventListener('click', closeChest);
$('skip-link').addEventListener('click', (e) => {
  e.preventDefault();
  openChest(true);
});
window.addEventListener('keydown', (e) => {
  if (e.key === 'Escape' && state === 'open') closeChest();
});

// Outils de polish (voir README) : ?t=1.8 fige l'animation à cet instant, ?open ouvre directement.
const params = new URLSearchParams(location.search);
if (params.has('open')) {
  openChest(true);
} else if (params.has('t')) {
  T = Math.max(0, Math.min(T_END, parseFloat(params.get('t')) || 0));
  state = 'opening';
  burstDone = true;
  body.classList.add('is-opening');
  if (T >= T_END) showScroll();
}

/* ---------- Survol et clic sur le coffre ---------- */
const raycaster = new THREE.Raycaster();
const ndc = new THREE.Vector2();
function hitChest(clientX, clientY) {
  ndc.set((clientX / window.innerWidth) * 2 - 1, -(clientY / window.innerHeight) * 2 + 1);
  raycaster.setFromCamera(ndc, camera);
  return raycaster.intersectObject(chest, true).length > 0;
}
canvasEl.addEventListener('pointermove', (e) => {
  pointer.set((e.clientX / window.innerWidth) * 2 - 1, (e.clientY / window.innerHeight) * 2 - 1);
  hovering = state === 'closed' && hitChest(e.clientX, e.clientY);
  canvasEl.classList.toggle('is-hover', hovering);
});
canvasEl.addEventListener('pointerleave', () => {
  hovering = false;
  pointer.set(0, 0);
  canvasEl.classList.remove('is-hover');
});
canvasEl.addEventListener('click', (e) => {
  if (state === 'closed' && hitChest(e.clientX, e.clientY)) openChest();
});

/* =========================================================================
   Boucle
   ========================================================================= */

const clock = new THREE.Clock();
let flameFrame = 0;
let flameTimer = 0;

function tick() {
  const dt = Math.min(clock.getDelta(), 0.05);
  const time = clock.elapsedTime;

  if (dir !== 0) {
    T += dt * (dir > 0 ? 1 : -1.7);
    if (dir > 0 && T >= T_END) {
      T = T_END;
      dir = 0;
      showScroll();
    } else if (dir < 0 && T <= 0) {
      T = 0;
      dir = 0;
      state = 'closed';
    }
  }

  // Survol : le coffre se soulève à peine et le joyau s'allume.
  hoverAmt += ((hovering && state === 'closed' ? 1 : 0) - hoverAmt) * Math.min(1, dt * 8);
  chest.position.y = hoverAmt * 0.08;

  applyTimeline(T, time);

  // Engrenages : lents au repos, emballés pendant l'ouverture.
  const gearSpeed = reduceMotion ? 0 : 0.35 + hoverAmt * 0.8 + 7 * bump(T, 0, 1.8);
  gearAngle += dt * gearSpeed * (dir < 0 ? -1 : 1);
  gears.forEach(({ g, speed }) => { g.rotation.z = gearAngle * speed; });

  // Étincelles : une gerbe quand le sceau s'envole, puis un filet doré depuis l'intérieur.
  if (!reduceMotion) {
    if (dir > 0 && !burstDone && T > 1.45) {
      burstDone = true;
      for (let i = 0; i < 70; i++) {
        const a = R() * Math.PI * 2;
        const sp = 1 + R() * 2.5;
        emitSpark(0, SEAL_Y + 0.8, 0, Math.cos(a) * sp, 1 + R() * 2.5, Math.sin(a) * sp, 0.8 + R() * 0.8);
      }
    }
    if (T > 1.6 && state !== 'closed') {
      const n = state === 'open' ? 1 : 3;
      for (let i = 0; i < n; i++) {
        emitSpark((R() - 0.5) * 4, CH.t + 0.4 + R() * 0.6, (R() - 0.5) * 2.6,
          (R() - 0.5) * 0.3, 0.8 + R() * 1.2, (R() - 0.5) * 0.3, 1.2 + R() * 1.4);
      }
    }
  }
  for (let i = 0; i < SPARKS; i++) {
    const s = sparks[i];
    if (s.life <= 0) continue;
    s.life -= dt;
    const k = i * 3;
    if (s.life <= 0) {
      sparkPos[k + 1] = -100;
      continue;
    }
    s.v.y -= dt * 0.6;
    s.v.multiplyScalar(1 - dt * 0.8);
    sparkPos[k] += s.v.x * dt;
    sparkPos[k + 1] += s.v.y * dt;
    sparkPos[k + 2] += s.v.z * dt;
    const f = s.life / s.max;
    const twinkle = 0.6 + 0.4 * Math.sin(time * 20 + i);
    sparkCol[k] = s.c.r * f * twinkle;
    sparkCol[k + 1] = s.c.g * f * twinkle;
    sparkCol[k + 2] = s.c.b * f * twinkle;
  }
  sparkGeo.attributes.position.needsUpdate = true;
  sparkGeo.attributes.color.needsUpdate = true;

  // Poussière qui flotte dans la lumière.
  if (!reduceMotion) {
    for (let i = 0; i < DUST; i++) {
      const k = i * 3;
      const v = dustVel[i];
      dustPos[k] += (v[0] + Math.sin(time * 0.3 + i) * 0.02) * dt;
      dustPos[k + 1] += v[1] * dt;
      dustPos[k + 2] += v[2] * dt;
      if (dustPos[k + 1] > 7.5) dustPos[k + 1] = 0.4;
      if (dustPos[k + 1] < 0.3) dustPos[k + 1] = 7.4;
    }
    dustGeo.attributes.position.needsUpdate = true;
  }

  // Bougie qui vacille.
  const flick = reduceMotion ? 1 : 0.86 + 0.08 * Math.sin(time * 9.3) + 0.05 * Math.sin(time * 23.7 + 1.3) + 0.04 * Math.sin(time * 4.1);
  candleLight.intensity = 70 * flick;
  glow.material.opacity = 0.75 + (flick - 0.86) * 2;
  flame.scale.set(0.36 * (0.95 + (flick - 0.86)), 0.56 * flick, 1);
  flameTimer += dt;
  if (!reduceMotion && flameTimer > 0.12) {
    flameTimer = 0;
    flameFrame = (flameFrame + 1 + Math.floor(R() * 2)) % flameTex.length;
    flame.material.map = flameTex[flameFrame];
  }

  // Parallaxe douce à la souris.
  if (!reduceMotion) parallax.lerp(pointer, Math.min(1, dt * 2.5));
  updateCamera(T);

  renderer.render(scene, camera);
  requestAnimationFrame(tick);
}
requestAnimationFrame(tick);
