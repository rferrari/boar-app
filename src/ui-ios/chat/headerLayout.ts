/**
 * How the chat header fits its width. The bar holds icon buttons (menu,
 * new chat), the avatar, the name + model and the offline seal.
 * Name and model always stay; to make room the seal drops its text first
 * (the icon still opens the explanation), then the avatar goes.
 *
 * Widths are points. Text widths scale with the font scale; buttons don't.
 */
export interface HeaderFitInput {
  width: number;
  fontScale: number;
  /** Minimum touch target of the platform (44 iOS, 48 Android). */
  touch: number;
  /** Icon buttons in the bar (menu, new chat). */
  buttons: number;
  /** Row padding (both sides) plus the gaps between its children. */
  chrome: number;
  /** Avatar disc plus its gap. */
  avatar: number;
  /** Characters in the seal's text. */
  sealChars: number;
}

export interface HeaderFit {
  seal: "text" | "icon";
  avatar: boolean;
}

/** Name ("boar") plus about nine characters of the model ("Qwen3 4B…"), at font scale 1. */
export const TITLE_MIN = 108;
/** Baloo 13 caps with .04em tracking: ~8 pt per character, plus icon, gap and padding (mockup: "OFFLINE" pill = 95 pt). */
export function sealTextWidth(chars: number): number {
  return chars * 8 + 42;
}

export function headerFit(o: HeaderFitInput): HeaderFit {
  const scale = Math.max(1, o.fontScale);
  const need = TITLE_MIN * scale;
  const free = o.width - o.chrome - o.buttons * o.touch;
  if (free - o.avatar - sealTextWidth(o.sealChars) * scale >= need) return { seal: "text", avatar: true };
  if (free - o.avatar - o.touch >= need) return { seal: "icon", avatar: true };
  return { seal: "icon", avatar: false };
}

/**
 * What the header seal says, per build (Prism CH-4): "Offline" only where it is literally true (the build
 * without INTERNET); the downloader build says "On device" everywhere, in the pill, the spoken name (which
 * contains the visible one, WCAG 2.5.3), the icon-only form and the sheet's title.
 */
export function sealCopy(offlineBuild: boolean) {
  return offlineBuild
    ? { pill: "chat.header.offlineSeal", pillSpoken: "chat.header.offlineSealSpoken", button: "chat.header.offlineShort", title: "chat.header.offlineTitle", body: "chat.header.offlineBody", icon: "wifi-off" as const }
    : { pill: "chat.header.offlineAnswersSeal", pillSpoken: "chat.header.onDeviceSealSpoken", button: "chat.header.onDeviceShort", title: "chat.header.onDeviceTitle", body: "chat.header.offlineBodyDownloader", icon: "smartphone" as const };
}
