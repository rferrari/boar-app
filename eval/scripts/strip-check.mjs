// Compare control vs candidate gate rows: reference/bibliography lines, [n] citation counts, places dups.
// Usage (from eval/results/gates): node ../../scripts/strip-check.mjs <control-label> <candidate-label>
import fs from 'node:fs'; import path from 'node:path';
const [ctlDir, candDir] = process.argv.slice(2);
const REF = /^[\s>*_#-]*(\*\*)?\s*(fontes?|refer[êe]ncias?|references?|sources?|bibliograph\w*|bibliografia)\s*(\*\*)?\s*:|retrieved on|acessad[oa] em|accessed on/im;
const cites = s => (s.match(/\[\d+\]/g) || []).length;
function walk(d, out = []) { for (const f of fs.readdirSync(d)) { const p = path.join(d, f); if (fs.statSync(p).isDirectory()) walk(p, out); else if (f.endsWith('.jsonl')) out.push(p); } return out; }
function load(dir) { const m = new Map(); for (const p of walk(dir)) { const rel = path.relative(dir, p); const counts = {}; for (const line of fs.readFileSync(p, 'utf8').split('\n')) { if (!line.trim()) continue; let r; try { r = JSON.parse(line); } catch { continue; } if (typeof r.answer !== 'string') continue; const k0 = `${rel}|${r.queryId}|${r.seed ?? ''}`; counts[k0] = (counts[k0] || 0) + 1; m.set(`${k0}|${counts[k0]}`, r); } } return m; }
const ctl = load(ctlDir), cand = load(candDir);
const refHits = d => [...d].filter(([, r]) => (r.tokensGenerated ?? 1) > 0 && REF.test(r.answer)).map(([k, r]) => ({ k, line: r.answer.split('\n').find(l => REF.test(l)) }));
const cRef = refHits(cand), kRef = refHits(ctl);
console.log(`rows control=${ctl.size} candidate=${cand.size}`);
console.log(`ref lines in MODEL rows (tokensGenerated>0): control=${kRef.length} candidate=${cRef.length}`);
for (const h of cRef) console.log('  CAND REF', h.k, '::', h.line.slice(0, 120));
for (const h of kRef.slice(0, 40)) console.log('  ctl ref', h.k, '::', h.line.slice(0, 100));
let cc = 0, kc = 0, lost = [], same = 0, diffText = 0, paired = 0;
for (const [k, r] of cand) { const c = ctl.get(k); if (!c) continue; paired++; const a = cites(r.answer), b = cites(c.answer); cc += a; kc += b; if (r.answer === c.answer) same++; else diffText++; if (a < b) lost.push({ k, ctl: b, cand: a, ctlRef: REF.test(c.answer) }); }
console.log(`paired=${paired} identical=${same} differ=${diffText}`);
console.log(`[n] total paired: control=${kc} candidate=${cc} (delta ${cc - kc})`);
console.log(`rows with fewer [n] than control: ${lost.length}`);
for (const l of lost) console.log('  LOST', l.k, `${l.ctl}->${l.cand}`, l.ctlRef ? '(control had ref line)' : '');
// places duplicates
for (const [k, r] of cand) if (/places/.test(k)) { const names = [...r.answer.matchAll(/^\s*\d+\.\s+(.+?)\s+—/gm)].map(m => m[1].toLowerCase().trim()); const d = names.filter((n, i) => names.indexOf(n) !== i); if (d.length || /places-008/.test(k)) console.log('  PLACES', k, `names=${names.length}`, d.length ? `DUP ${d.join(', ')}` : 'no dup'); }
