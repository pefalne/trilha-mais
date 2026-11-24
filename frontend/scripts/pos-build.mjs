// Depois do "expo export": o app vira /app (app.html) e a landing page estática
// (web-estatico/) ocupa a raiz do site, com /termos, /privacidade e 404.
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const raiz = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const dist = path.join(raiz, 'dist');
const estatico = path.join(raiz, 'web-estatico');

if (!fs.existsSync(path.join(dist, 'index.html'))) throw new Error('dist/index.html não existe: rode o expo export antes.');
fs.renameSync(path.join(dist, 'index.html'), path.join(dist, 'app.html'));

const copiar = (de, para) => {
  for (const nome of fs.readdirSync(de)) {
    const o = path.join(de, nome), d = path.join(para, nome);
    if (fs.statSync(o).isDirectory()) { fs.mkdirSync(d, { recursive: true }); copiar(o, d); }
    else fs.copyFileSync(o, d);
  }
};
copiar(estatico, dist);
fs.renameSync(path.join(dist, 'inicio.html'), path.join(dist, 'index.html'));
console.log('pós-build ok: / = landing page, /app = aplicativo');
