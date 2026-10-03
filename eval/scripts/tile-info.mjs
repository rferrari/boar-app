#!/usr/bin/env node
// Prints "<sha256> <url>" of a hosted 1°x1° places tile from a world-places gazetteer's tile index, or nothing when
// the gazetteer has no tile index or the tile is not hosted (src/rag/pois.ts loadTileCatalog reads the same table).
// Usage: node scripts/tile-info.mjs <world-places.sqlite> <tile-id, e.g. t-N52E013>
import { DatabaseSync } from "node:sqlite";
const [path, id] = process.argv.slice(2);
try {
  const db = new DatabaseSync(path, { readOnly: true });
  const cols = db.prepare("PRAGMA table_info(tiles)").all().map((c) => c.name);
  if (cols.includes("url")) {
    const t = db.prepare("SELECT sha256, url FROM tiles WHERE id = ?").get(id);
    if (t?.url && t?.sha256) console.log(`${t.sha256} ${t.url}`);
  }
} catch {}
