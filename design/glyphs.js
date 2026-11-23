// Shared pictograms for the three AccessAuto design mockups.
// Elements declare what they want via data attributes; this file fills them in.

function hand(kind) {
  const up = {
    open: [1, 1, 1, 1, 1], one: [0, 1, 0, 0, 0], two: [0, 1, 1, 0, 0],
    three: [0, 1, 1, 1, 0], fist: [0, 0, 0, 0, 0], thumb: [2, 0, 0, 0, 0],
  }[kind];
  const xs = [26, 40, 54, 68], hs = [46, 54, 46, 34];
  let p = '<rect x="18" y="60" width="64" height="50" rx="18"/>';
  xs.forEach((x, i) => {
    const h = up[i + 1] ? hs[i] : 10;
    p += `<rect x="${x - 6}" y="${64 - h}" width="12" height="${h + 14}" rx="6"/>`;
  });
  if (up[0] === 1) p += '<rect x="14" y="50" width="13" height="46" rx="6.5" transform="rotate(-32 20.5 92)"/>';
  if (up[0] === 2) p += '<rect x="20" y="10" width="15" height="60" rx="7.5"/>';
  return `<svg viewBox="0 0 100 114" fill="currentColor">${p}</svg>`;
}

function face(kind) {
  const extra = kind === 'brows'
    ? '<path d="M27 33q9-10 19-4M54 29q10-6 19 4"/>'
    : '<path d="M97 34v26m-8-9l8 9 8-9"/>';
  return `<svg viewBox="0 0 110 100" fill="none" stroke="currentColor" stroke-width="6" stroke-linecap="round" stroke-linejoin="round">
    <circle cx="50" cy="54" r="36"/><circle cx="37" cy="52" r="3.5" fill="currentColor" stroke="none"/>
    <circle cx="63" cy="52" r="3.5" fill="currentColor" stroke="none"/><path d="M40 70h20"/>${extra}</svg>`;
}

// 21-point hand skeleton in a "two fingers" pose, like the camera overlay.
function skeleton() {
  const chains = [
    [[50, 110], [35, 95], [25, 82], [21, 69]],
    [[50, 110], [40, 70], [38, 50], [37, 35], [36, 21]],
    [[50, 110], [51, 68], [52, 46], [53, 30], [54, 15]],
    [[50, 110], [61, 71], [65, 60], [63, 68], [60, 76]],
    [[50, 110], [70, 78], [74, 68], [72, 75], [69, 82]],
  ];
  let lines = '', dots = '';
  chains.forEach(c => {
    lines += `<polyline points="${c.map(q => q.join(',')).join(' ')}"/>`;
    c.forEach(q => { dots += `<circle cx="${q[0]}" cy="${q[1]}" r="2.6"/>`; });
  });
  return `<svg viewBox="0 0 100 124"><g fill="none" stroke="currentColor" stroke-width="1.6" opacity=".7">${lines}</g><g fill="currentColor">${dots}</g></svg>`;
}

// Instrument gauge: p is 0..1 along a 240-degree sweep.
function gauge(p, color) {
  const a = -120 + 240 * p;
  return `<svg viewBox="0 0 120 120" fill="none">
    <circle cx="60" cy="60" r="48" stroke="currentColor" stroke-width="10" pathLength="360" stroke-dasharray="2 10 2 10 2 10 2 10 2 10 2 10 2 10 2 10 2 10 2 10 2 10 2 10 2 10 2 10 2 10 2 10 2 10 2 10 2 10 2 10 2 360" transform="rotate(149.5 60 60)" opacity=".85"/>
    <circle cx="60" cy="60" r="38" stroke="${color}" stroke-width="5" stroke-linecap="round" pathLength="360" stroke-dasharray="${240 * p} 360" transform="rotate(150 60 60)"/>
    <line x1="60" y1="60" x2="60" y2="20" stroke="${color}" stroke-width="4" stroke-linecap="round" transform="rotate(${a} 60 60)"/>
    <circle cx="60" cy="60" r="7" fill="currentColor"/></svg>`;
}

document.querySelectorAll('[data-hand]').forEach(e => { e.innerHTML = hand(e.dataset.hand); });
document.querySelectorAll('[data-face]').forEach(e => { e.innerHTML = face(e.dataset.face); });
document.querySelectorAll('[data-skel]').forEach(e => { e.innerHTML = skeleton(); });
document.querySelectorAll('[data-gauge]').forEach(e => { e.innerHTML = gauge(+e.dataset.gauge, e.dataset.color); });
