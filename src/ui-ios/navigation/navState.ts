/**
 * Keeps the navigation state across a remount of the NavigationContainer.
 * Android recreates the Activity when the system font size changes (FS-1,
 * plan B); iOS remounts the tree on a Dynamic Type change (FS-3). The JS
 * context survives but the React tree mounts again, and this puts the user
 * back on the same screen (Settings, Models…). Pure for vitest.
 */
let saved: unknown;

export function saveNavState(state: unknown): void {
  saved = state;
}

export function savedNavState<T>(): T | undefined {
  return (saved === undefined ? undefined : withDrawersClosed(saved)) as T | undefined;
}

type NavNode = { type?: string; history?: { type?: string }[]; routes?: { state?: unknown }[] };

/**
 * The same state with every drawer closed. A font change while the drawer is open (the icon-align
 * AX captures: drawer, then a bigger size) would otherwise restore "open" in the router state; if the
 * fresh drawer view doesn't draw it open, the menu's openDrawer is a no-op and the tap does nothing.
 * The drawer is a transient overlay, never a place to come back to.
 */
export function withDrawersClosed(state: unknown): unknown {
  if (!state || typeof state !== "object") return state;
  const node = state as NavNode;
  const routes = node.routes?.map((r) => (r.state ? { ...r, state: withDrawersClosed(r.state) } : r));
  const history = node.type === "drawer" ? node.history?.filter((h) => h.type !== "drawer") : node.history;
  return { ...node, ...(routes ? { routes } : null), ...(history ? { history } : null) };
}
