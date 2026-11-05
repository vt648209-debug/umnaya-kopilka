'use strict';
// Готовит www для Capacitor: собранная PWA (локально — standalone/github, в репозитории сайта — корень репо).
// Service worker внутри приложения не нужен (файлы и так лежат в APK).
const fs = require('fs');
const path = require('path');
const local = path.join(__dirname, '..', 'standalone', 'github');
const src = fs.existsSync(local) ? local : path.join(__dirname, '..');
const dst = path.join(__dirname, 'www');
fs.rmSync(dst, { recursive: true, force: true });
fs.mkdirSync(dst);
for (const f of ['index.html', 'manifest.webmanifest', 'offers.json', 'icon-192.png', 'icon-512.png', 'apple-touch-icon.png']) {
  if (fs.existsSync(path.join(src, f))) fs.copyFileSync(path.join(src, f), path.join(dst, f));
}
if (!fs.existsSync(path.join(dst, 'index.html'))) throw new Error('нет index.html в ' + src);
console.log('www из', src, ':', fs.readdirSync(dst).join(', '));
