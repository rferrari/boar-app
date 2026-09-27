const { withGradleProperties } = require("@expo/config-plugins");

/**
 * The experimental external engines (modules/native-engine) are executables shipped as
 * lib*.so. Android only lets an app execute files in its extracted native library folder, so
 * native libraries must be packaged the legacy way: stored for extraction at install time
 * instead of loaded straight from the APK. The Expo template reads this property in
 * android/app/build.gradle (packagingOptions.jniLibs.useLegacyPackaging).
 */
function withNativeEngines(config) {
  return withGradleProperties(config, (config) => {
    const props = config.modResults.filter((p) => !(p.type === "property" && p.key === "expo.useLegacyPackaging"));
    props.push({ type: "property", key: "expo.useLegacyPackaging", value: "true" });
    config.modResults = props;
    return config;
  });
}

module.exports = withNativeEngines;
