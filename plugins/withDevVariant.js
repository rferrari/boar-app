const { withAppBuildGradle, withDangerousMod } = require("@expo/config-plugins");
const fs = require("fs");
const path = require("path");

const DEV_SUFFIX = ".dev";
const DEV_NAME = "BOAR Dev";

/**
 * Debug builds install as team.sopa.aoair.dev ("BOAR Dev"), so a development
 * build and the release APK can sit on the same phone, each with its own data
 * and models. Release builds keep the plain package and name.
 */
function withDevVariant(config) {
  config = withAppBuildGradle(config, (config) => {
    let gradle = config.modResults.contents;
    if (!gradle.includes(`applicationIdSuffix "${DEV_SUFFIX}"`)) {
      gradle = gradle.replace(
        /(buildTypes \{\n\s*debug \{\n)/,
        `$1            applicationIdSuffix "${DEV_SUFFIX}"\n`
      );
    }
    config.modResults.contents = gradle;
    return config;
  });

  // The debug source set overrides app_name from src/main.
  return withDangerousMod(config, [
    "android",
    async (config) => {
      const dir = path.join(config.modRequest.platformProjectRoot, "app", "src", "debug", "res", "values");
      fs.mkdirSync(dir, { recursive: true });
      fs.writeFileSync(
        path.join(dir, "strings.xml"),
        `<resources>\n  <string name="app_name">${DEV_NAME}</string>\n</resources>\n`
      );
      return config;
    },
  ]);
}

module.exports = withDevVariant;
