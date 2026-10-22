// Pure adb helpers for scripts/push-knowledge-pack.mjs: adb output parsing and
// device selection. Nothing here runs a process, so the tests need no phone.

export const DEFAULT_PACKAGE = "team.sopa.aoair";

/** Parses `adb devices` output into [{ serial, state }]. */
export function parseAdbDevices(output) {
  return output
    .split("\n")
    .slice(1)
    .map((l) => l.trim())
    .filter(Boolean)
    .map((l) => {
      const [serial, state] = l.split(/\s+/);
      return { serial, state };
    });
}

/** Picks the device to use, or explains exactly why none can be used. */
export function selectDevice(devices, wanted) {
  if (wanted) {
    const d = devices.find((x) => x.serial === wanted);
    if (!d) return { error: `device ${wanted} is not connected` };
    if (d.state !== "device") return { error: stateHint(d) };
    return { serial: d.serial };
  }
  const ready = devices.filter((d) => d.state === "device");
  if (ready.length === 1) return { serial: ready[0].serial };
  if (ready.length > 1) return { error: `several devices connected (${ready.map((d) => d.serial).join(", ")}); pick one with --serial` };
  if (devices.length) return { error: devices.map(stateHint).join("; ") };
  return { error: "no Android device connected. Plug it in over USB with USB debugging enabled, then check `adb devices`." };
}

function stateHint(d) {
  if (d.state === "unauthorized") return `${d.serial} is unauthorized: unlock the phone and accept the "Allow USB debugging" prompt`;
  if (d.state === "offline") return `${d.serial} is offline: reconnect the cable or run \`adb kill-server\``;
  return `${d.serial} is in state "${d.state}"`;
}

export function isDebuggable(dumpsysOutput) {
  return /pkgFlags=\[[^\]]*\bDEBUGGABLE\b/.test(dumpsysOutput);
}

export function isPackageListed(pmOutput, pkg) {
  return pmOutput.split("\n").some((l) => l.trim() === `package:${pkg}`);
}
