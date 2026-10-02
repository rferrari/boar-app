import { describe, expect, it } from "vitest";
import { DrawerActions, DrawerRouter, StackRouter } from "@react-navigation/routers";
import { saveNavState, savedNavState, withDrawersClosed } from "./navState";

const drawerOpts = { routeNames: ["Chat"], routeParamList: {}, routeGetIdList: {} };
const drawer = DrawerRouter({});
const isOpen = (s: { history: { type: string }[] }) => s.history.some((h) => h.type === "drawer");

/** Root stack (Main, Settings) with the drawer navigator nested in Main, as RootNavigator builds it. */
function rootWith(drawerState: unknown) {
  const stack = StackRouter({});
  const root = stack.getInitialState({ routeNames: ["Main", "Settings"], routeParamList: {}, routeGetIdList: {} });
  return { ...root, routes: root.routes.map((r) => (r.name === "Main" ? { ...r, state: drawerState } : r)) };
}

describe("saved navigation state", () => {
  it("returns what was saved last, so a remount reopens the same screen", () => {
    const state = { index: 1, routes: [{ name: "Main" }, { name: "Settings" }] };
    saveNavState(state);
    // A copy now (drawer status dropped), with the same screens.
    expect(savedNavState()).toEqual(state);
  });
});

describe("nav state across a font-size remount (menu tap opens the drawer)", () => {
  it("a state saved with the drawer open comes back closed, so the menu's openDrawer still opens it", () => {
    const closed = drawer.getInitialState(drawerOpts);
    const open = drawer.getStateForAction(closed, DrawerActions.openDrawer(), drawerOpts) as typeof closed;
    expect(isOpen(open)).toBe(true);
    // Without the fix: restoring `open` makes openDrawer a no-op (the tap "does nothing").
    expect(drawer.getStateForAction(open, DrawerActions.openDrawer(), drawerOpts)).toBe(open);

    saveNavState(rootWith(open));
    const restored = savedNavState<ReturnType<typeof rootWith>>()!;
    const main = restored.routes.find((r) => r.name === "Main")!.state as typeof open;
    expect(isOpen(main)).toBe(false);
    const rehydrated = drawer.getRehydratedState(main, drawerOpts);
    const afterTap = drawer.getStateForAction(rehydrated, DrawerActions.openDrawer(), drawerOpts) as typeof open;
    expect(isOpen(afterTap)).toBe(true);
  });

  it("keeps the screen the user was on (only the drawer status is dropped)", () => {
    const stack = StackRouter({});
    const opts = { routeNames: ["Main", "Settings"], routeParamList: {}, routeGetIdList: {} };
    const onSettings = stack.getStateForAction(stack.getInitialState(opts), { type: "NAVIGATE", payload: { name: "Settings" } }, opts)!;
    expect(withDrawersClosed(onSettings)).toEqual(onSettings);
  });
});
