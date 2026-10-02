/**
 * Where the chat gets "use my location" from: Loom's locateForUser (explains
 * in context, asks the system once, then reads a GPS-only fix). The places
 * card shows the button whenever this exists and the permission isn't
 * already denied; see showUseLocation in placesFormat.ts.
 */
export type { LocationRequest } from "../../services/location";
export { locateForUser as locate } from "../../services/location";
