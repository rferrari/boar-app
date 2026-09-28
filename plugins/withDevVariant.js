const { withAppBuildGradle, withDangerousMod } = require("@expo/config-plugins");
const fs = require("fs");
const path = require("path");

const DEV_SUFFIX = ".dev";
const DEV_NAME = "BOAR Dev";

/**
 * Debug builds install as team.sopa.aoair.dev ("BOAR Dev"), so a development build and the
 * release APK can sit on the same phone, each with its own data and models. Release builds keep
 * the plain package and name.
 *
 * The default applicationId is the .dev one and release builds set the plain one back (Android
 * Gradle's variant API), rather than debug adding a suffix: Expo's CLI opens the app by the
 * applicationId it reads in build.gradle, so with a suffix it looked for team.sopa.aoair and
 * failed with "No development build (team.sopa.aoair) … is installed".
 */
function withDevVariant(config) {
  config = withAppBuildGradle(config, (config) => {
    let gradle = config.modResults.contents;
    const pkg = config.android?.package;
    if (!pkg) throw new Error("withDevVariant: android.package is not set");
    if (!gradle.includes(`applicationId '${pkg}${DEV_SUFFIX}'`)) {
      gradle = gradle.replace(`applicationId '${pkg}'`, `applicationId '${pkg}${DEV_SUFFIX}'`);
      gradle += `
// withDevVariant: release builds keep the plain package; debug builds are ${pkg}${DEV_SUFFIX}.
androidComponents {
    onVariants(selector().withBuildType("release")) { variant ->
        variant.applicationId.set("${pkg}")
    }
}
`;
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
