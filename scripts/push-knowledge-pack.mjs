#!/usr/bin/env node
// Copies a knowledge pack built by scripts/build-knowledge-pack.mjs onto a
// USB-connected phone running a development build of BOAR, and verifies it.
//
//   npm --prefix scripts run pack:push -- build/knowledge-pack/wiki-vital5.sqlite [--serial SERIAL]
//
// The app picks up any valid pack in its corpus/ folder on the next question.
import { execFileSync } from "node:child_process";
import { createHash } from "node:crypto";
import { existsSync, readFileSync } from "node:fs";
import { basename } from "node:path";
import { DEFAULT_PACKAGE, isDebuggable, isPackageListed, parseAdbDevices, selectDevice } from "./lib/adb-lib.mjs";

const fail = (msg) => {
  console.error(`✗ ${msg}`);
  process.exit(1);
};
const adb = (serial, ...args) => execFileSync("adb", ["-s", serial, ...args], { encoding: "utf8", maxBuffer: 1024 * 1024 * 1024 });

const args = process.argv.slice(2);
const optionValue = (flag) => { const i = args.indexOf(flag); return i >= 0 ? args[i + 1] : undefined; };
const valueIdx = new Set(["--serial", "--package"].map((f) => args.indexOf(f)).filter((i) => i >= 0).map((i) => i + 1));
const file = args.find((a, i) => !a.startsWith("--") && !valueIdx.has(i));
// A native debug build is team.sopa.aoair.native; the default is the release id.
const PACKAGE = optionValue("--package") ?? DEFAULT_PACKAGE;
if (!/^[a-z][a-z0-9_]*(\.[a-z0-9_]+)+$/.test(PACKAGE)) fail(`not an application id: ${PACKAGE}`); // it goes into adb shell commands
if (!file || args.includes("--help")) fail("usage: npm --prefix scripts run pack:push -- <pack.sqlite> [--serial SERIAL] [--package APP_ID]");
if (!existsSync(file)) fail(`${file} not found`);
const name = basename(file);
if (!/^[a-z0-9][a-z0-9-]*\.sqlite$/.test(name)) fail("pack file names must be lowercase letters, digits or dashes, ending in .sqlite");

const found = selectDevice(parseAdbDevices(execFileSync("adb", ["devices"], { encoding: "utf8" })), optionValue("--serial"));
if (found.error) fail(found.error);
const serial = found.serial;
if (!isPackageListed(adb(serial, "shell", `pm list packages ${PACKAGE}`), PACKAGE)) fail(`${PACKAGE} isn't installed on ${serial}`);
if (!isDebuggable(adb(serial, "shell", `dumpsys package ${PACKAGE}`))) fail("the installed app isn't a debuggable build; this needs a development build");

const tmp = `/data/local/tmp/${name}`;
console.log(`copying ${name} to ${serial}…`);
execFileSync("adb", ["-s", serial, "push", file, tmp], { stdio: "inherit" });
// The shell user can read /data/local/tmp, the app's run-as user can write its own files: pipe between them.
adb(serial, "shell", `cat ${tmp} | run-as ${PACKAGE} sh -c 'umask 077; mkdir -p files/corpus && cat > files/corpus/${name}'`);
adb(serial, "shell", `rm -f ${tmp}`);

const local = createHash("sha256").update(readFileSync(file)).digest("hex");
const remote = createHash("sha256")
  .update(execFileSync("adb", ["-s", serial, "exec-out", `run-as ${PACKAGE} cat files/corpus/${name}`], { maxBuffer: 1024 * 1024 * 1024 }))
  .digest("hex");
if (local !== remote) fail("the copy on the phone doesn't match; try again");
console.log(`✓ ${name} is on the phone (sha256 ${local.slice(0, 16)}…). BOAR searches it from the next question.`);
