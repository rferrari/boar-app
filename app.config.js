// Debug builds install as team.sopa.aoair.dev (plugins/withDevVariant.js). Expo's CLI opens the
// app by the package in app.json, so `make start` sets BOAR_DEV_APP=1 to point it at BOAR Dev.
// Only for starting Metro: prebuild must keep the base package (the plugin adds the suffix).
module.exports = ({ config }) =>
  process.env.BOAR_DEV_APP === "1"
    ? { ...config, android: { ...config.android, package: "team.sopa.aoair.dev" } }
    : config;
