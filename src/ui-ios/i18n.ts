import i18n from "../i18n";
import en from "./locales/en.json";
import pt from "./locales/pt.json";

/**
 * The iOS UI's strings. src/i18n holds the Android UI's; the engine uses no translation keys, so each UI
 * brings its own and this one is laid over the shared i18n instance (deep, overwriting the 14 keys both
 * UIs name with different text). Imported once, before the first render (App.tsx).
 */
i18n.addResourceBundle("en", "translation", en, true, true);
i18n.addResourceBundle("pt", "translation", pt, true, true);

export default i18n;
