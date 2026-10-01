/** The Sanctuary preview's art (assets/sanctuary, made by scripts/prepare-sanctuary-assets.py). */
import type { SessionId } from "./catalog";

export const ENTRANCES = [
  require("../../../assets/sanctuary/entrance-1.webp"),
  require("../../../assets/sanctuary/entrance-2.webp"),
  require("../../../assets/sanctuary/entrance-3.webp"),
  require("../../../assets/sanctuary/entrance-4.webp"),
];

export const SESSION_BOARS: Record<SessionId, number> = {
  combat: require("../../../assets/sanctuary/boar-combat.webp"),
  music: require("../../../assets/sanctuary/boar-music.webp"),
  code: require("../../../assets/sanctuary/boar-code.webp"),
  survive: require("../../../assets/sanctuary/boar-survive.webp"),
};
