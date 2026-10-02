// The engine's AnswerEvent contract (src/routing/events.ts), re-exported for the chat.
export * from "../../routing/events";

/** receipt.modelId of an answer that is only the source passage, with no model. */
export const EXTRACTIVE_MODEL_ID = "extractive";
/** The engine's fixed "no offline source" answer (health without a good source): no model ran. */
export const GROUNDING_GUARD_MODEL_ID = "grounding-guard";
/** The engine's exact arithmetic answer (a temperature conversion, Tusk 7e9687a): no model ran. */
export const CALCULATOR_MODEL_ID = "calculator";
