// Genera maqueta-evolucion.html (SVG con puntos con letra) para renderizarla a PNG.
const fs = require('fs');
const col = { SA: '#2a78d6', E: '#eb6834', SE: '#1baf7a' };
const tint = { SA: '#c6dcf8', E: '#fbd2c0', SE: '#bfead9' };
const dark = { SA: '#14407c', E: '#8f3a0e', SE: '#0b5e41' };
const ink = '#14285a', mut = '#5a6985', grid = '#dbe3f0', card = '#f1f5fb';

function marker(x, y, code, right, val, labelDy = -17) {
  let o = '';
  if (right) {
    o += `<circle cx="${x}" cy="${y}" r="12" fill="${col[code]}"/><text x="${x}" y="${y + 4}" text-anchor="middle" font-size="11" font-weight="600" fill="#fff">${code}</text>`;
  } else {
    o += `<circle cx="${x}" cy="${y}" r="12" fill="${tint[code]}" stroke="${col[code]}" stroke-width="2"/><text x="${x}" y="${y + 4}" text-anchor="middle" font-size="11" font-weight="600" fill="${dark[code]}">${code}</text>`;
  }
  if (val !== undefined) o += `<text x="${x}" y="${y + labelDy}" text-anchor="middle" font-size="11" fill="${mut}">${val}</text>`;
  return o;
}

let s = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 380 650" width="760" height="1300" font-family="Nunito, Segoe UI, Arial, sans-serif"><rect width="380" height="650" fill="#ffffff"/>`;
s += `<rect x="8" y="8" width="364" height="268" rx="14" fill="${card}"/>`;
s += `<text x="22" y="32" font-size="14" font-weight="700" fill="${ink}">Un solo día de datos</text>`;
const y1 = k => 236 - k * 3.2;
for (let k = 0; k <= 50; k += 10) s += `<line x1="52" x2="356" y1="${y1(k)}" y2="${y1(k)}" stroke="${grid}"/><text x="44" y="${y1(k) + 4}" text-anchor="end" font-size="11" fill="${mut}">${k}</text>`;
s += `<text x="100" y="256" text-anchor="middle" font-size="11" fill="${mut}">25/11</text><text x="342" y="256" text-anchor="end" font-size="11" fill="${mut}">26/11</text>`;
[['SA', -48, 10, 38], ['E', 0, 31.5, 13], ['SE', 48, 13, 38]].forEach(g => {
  const bx = 100 + g[1] + 24;
  s += marker(bx - 9, y1(g[2]), g[0], true, g[2]);
  s += marker(bx + 9, y1(g[3]), g[0], false, g[3]);
});

s += `<rect x="8" y="292" width="364" height="350" rx="14" fill="${card}"/>`;
s += `<text x="22" y="316" font-size="14" font-weight="700" fill="${ink}">Varios días</text>`;
const y2 = k => 520 - k * 3.6;
for (let k = 0; k <= 50; k += 10) s += `<line x1="52" x2="356" y1="${y2(k)}" y2="${y2(k)}" stroke="${grid}"/><text x="44" y="${y2(k) + 4}" text-anchor="end" font-size="11" fill="${mut}">${k}</text>`;
const xs = [80, 160, 240, 320], dl = ['25/11', '27/11', '29/11', '01/12'];
dl.forEach((t, i) => (s += `<text x="${xs[i]}" y="540" text-anchor="middle" font-size="11" fill="${mut}">${t}</text>`));
const d2 = [['SA', [10, 14, 18, 22], [38, 38, 39, 39]], ['E', [20, 24, 27, 30], [31, 31, 32, 32]], ['SE', [13, 16, 20, 25], [35, 36, 36, 37]]];
d2.forEach(g => {
  const pr = g[1].map((v, i) => `${xs[i]},${y2(v)}`).join(' ');
  const pl = g[2].map((v, i) => `${xs[i]},${y2(v)}`).join(' ');
  s += `<polyline points="${pr}" fill="none" stroke="${col[g[0]]}" stroke-width="2.5" stroke-linejoin="round"/>`;
  s += `<polyline points="${pl}" fill="none" stroke="${col[g[0]]}" stroke-opacity="0.55" stroke-width="2.5" stroke-dasharray="6 5" stroke-linejoin="round"/>`;
  g[1].forEach((v, i) => { if (i < 3) s += `<circle cx="${xs[i]}" cy="${y2(v)}" r="4" fill="${col[g[0]]}"/>`; });
  g[2].forEach((v, i) => { if (i < 3) s += `<circle cx="${xs[i]}" cy="${y2(v)}" r="4" fill="${tint[g[0]]}" stroke="${col[g[0]]}" stroke-width="1.5"/>`; });
});
// Letras solo en un punto de cada línea, cada agarre en un día distinto para no tapar nada
const at={SA:0,E:1,SE:2};
d2.forEach(g => {
  const i = at[g[0]];
  s += marker(xs[i], y2(g[1][i]), g[0], true, g[1][i]);
  s += marker(xs[i], y2(g[2][i]), g[0], false, g[2][i]);
});

s += `<g transform="translate(22,566)"><circle cx="8" cy="0" r="8" fill="${col.SA}"/><text x="22" y="4" font-size="12" fill="${ink}">Derecha: círculo lleno, línea continua</text></g>`;
s += `<g transform="translate(22,590)"><circle cx="8" cy="0" r="8" fill="${tint.SA}" stroke="${col.SA}" stroke-width="2"/><text x="22" y="4" font-size="12" fill="${ink}">Izquierda: círculo claro, línea discontinua</text></g>`;
s += `<text x="22" y="614" font-size="12" fill="${mut}">SA semiarqueo · E extensión · SE semiestirado</text>`;
s += `<text x="22" y="632" font-size="12" fill="${mut}">BE · BA · BP · PA = los cuatro bidedo</text>`;
s += '</svg>';
fs.writeFileSync(__dirname + '/maqueta-evolucion.svg', s);
fs.writeFileSync(__dirname + '/maqueta-evolucion.html', `<!doctype html><meta charset="utf-8"><body style="margin:0;background:#fff">${s}</body>`);
console.log('ok');
