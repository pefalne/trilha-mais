// Depois do "expo export": o app vira /app (app.html) e a landing page estática
// (web-estatico/) ocupa a raiz do site, com /termos, /privacidade e 404.
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const raiz = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const dist = path.join(raiz, 'dist');
const estatico = path.join(raiz, 'web-estatico');

if (!fs.existsSync(path.join(dist, 'index.html'))) throw new Error('dist/index.html não existe: rode o expo export antes.');
// Celular: o iPhone dava zoom sozinho ao tocar num campo (fonte menor que 16 px) e a tela
// ficava ampliada. Trava a escala inicial e usa 16 px nos campos só em tela de toque.
const ESCALA = '<meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1, viewport-fit=cover" />';
const CSS_CAMPOS = '<style>html,body{-webkit-text-size-adjust:100%;text-size-adjust:100%}@media (pointer: coarse){input,textarea,select{font-size:16px!important}}</style>';
let htmlApp = fs.readFileSync(path.join(dist, 'index.html'), 'utf8');
htmlApp = /<meta[^>]+name=["']viewport["'][^>]*>/i.test(htmlApp)
  ? htmlApp.replace(/<meta[^>]+name=["']viewport["'][^>]*>/i, ESCALA)
  : htmlApp.replace('<head>', `<head>${ESCALA}`);
htmlApp = htmlApp.replace('</head>', `${CSS_CAMPOS}</head>`);
fs.writeFileSync(path.join(dist, 'app.html'), htmlApp);
fs.unlinkSync(path.join(dist, 'index.html'));

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
