package team.sopa.boar.engine.eval

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.time.OffsetDateTime

/**
 * Sharing an evaluation run with the BOAR project (supabase/functions/submit-results): what gets sent and what the
 * server's answer means (port of src/eval/shareResults.pure.ts). Nothing leaves the phone until the user confirms.
 */
object ShareResults {
    data class Device(
        val platform: String = "android",
        val osVersion: String? = null,
        val apiLevel: Int? = null,
        val brand: String? = null,
        val model: String? = null,
        val soc: String? = null,
        val socManufacturer: String? = null,
        val hardware: String? = null,
        val ramBytes: Long? = null,
        val cpuCores: Int? = null,
        val cpuFeatures: List<String>? = null,
        val coreMaxFreqKHz: List<Long>? = null,
    )

    fun submitResultsUrl(supabaseUrl: String?, publishableKey: String?): String? =
        if (supabaseUrl.isNullOrEmpty() || publishableKey.isNullOrEmpty()) null
        else supabaseUrl.trimEnd('/') + "/functions/v1/submit-results"

    /** buildSubmission: the run's description and every row; absent values are left out, as JSON.stringify drops undefined. */
    fun buildSubmission(rows: List<JsonObject>, d: Device, appVersion: String): JsonObject {
        val first = rows.first()
        val run = LinkedHashMap<String, JsonElement>()
        fun put(k: String, v: Any?) {
            when (v) {
                null -> {}
                is String -> run[k] = JsonPrimitive(v)
                is Number -> run[k] = JsonPrimitive(v)
                is List<*> -> run[k] = JsonArray(v.map { if (it is Number) JsonPrimitive(it) else JsonPrimitive(it.toString()) })
                else -> run[k] = JsonPrimitive(v.toString())
            }
        }
        run["runId"] = first["runId"]!!
        run["evalSetVersion"] = first["evalSetVersion"]!!
        put("appVersion", appVersion)
        put("platform", d.platform)
        put("osVersion", d.osVersion)
        put("apiLevel", d.apiLevel)
        put("deviceBrand", d.brand)
        put("deviceModel", d.model)
        put("soc", d.soc?.ifEmpty { null })
        put("socManufacturer", d.socManufacturer?.ifEmpty { null })
        put("hardware", d.hardware?.ifEmpty { null })
        put("ramBytes", d.ramBytes?.takeIf { it > 0 })
        put("cpuCores", d.cpuCores)
        // An empty flag list means unknown, not "no i8mm or dotprod".
        put("cpuFeatures", d.cpuFeatures?.takeIf { it.isNotEmpty() })
        put("coreMaxFreqKHz", d.coreMaxFreqKHz)
        return JsonObject(mapOf("run" to JsonObject(run), "rows" to JsonArray(rows)))
    }

    enum class Result(val key: String) {
        SHARED("shared"), SHARED_PENDING("shared-pending"), ALREADY_SHARED("already-shared"), RATE_LIMITED("rate-limited"),
        COOLDOWN("cooldown"), NETWORK_LIMITED("network-limited"), REJECTED("rejected"), OFFLINE("offline"), BUSY("busy"),
        SERVER_ERROR("server-error"), KEY_FAILED("key-failed"), FAILED("failed");

        val succeeded get() = this == SHARED || this == SHARED_PENDING || this == ALREADY_SHARED
        val canRetry get() = this == OFFLINE || this == BUSY || this == SERVER_ERROR || this == FAILED
    }

    data class Outcome(val result: Result, val retryAt: String? = null, val status: Int? = null, val detail: String? = null)

    /** What submit-results answered, as something the screen can say. */
    fun outcome(status: Int, body: JsonObject?): Outcome {
        val error = (body?.get("error") as? JsonPrimitive)?.takeIf { it.isString }?.content
        return when {
            status == 201 -> Outcome(if ((body?.get("pendingReview") as? JsonPrimitive)?.booleanOrNull == true) Result.SHARED_PENDING else Result.SHARED, status = status)
            status == 409 -> Outcome(Result.ALREADY_SHARED, status = status)
            status == 429 -> {
                if (error == "network_limited") Outcome(Result.NETWORK_LIMITED, status = status, detail = error)
                else {
                    val at = (body?.get("retryAt") as? JsonPrimitive)?.contentOrNull?.takeIf { runCatching { OffsetDateTime.parse(it) }.isSuccess }
                    Outcome(if (error == "cooldown") Result.COOLDOWN else Result.RATE_LIMITED, at, status, error)
                }
            }
            status in 400..499 -> Outcome(Result.REJECTED, status = status, detail = error)
            status == 503 -> Outcome(Result.BUSY, status = status, detail = error)
            status >= 500 -> Outcome(Result.SERVER_ERROR, status = status, detail = error)
            else -> Outcome(Result.FAILED, status = status, detail = error)
        }
    }

    /** The string the phone's key signs: the server's one-time challenge, then the exact payload. */
    fun signedMessage(challenge: String, payload: String) = "$challenge.$payload"

    fun parseCpuFeatures(line: String?): List<String> = (line ?: "").split(Regex("\\s+")).filter { it.isNotEmpty() }

    fun describeChipset(d: Device): String? =
        if (!d.soc.isNullOrEmpty()) listOfNotNull(d.socManufacturer?.ifEmpty { null }, d.soc).joinToString(" ") else d.hardware?.ifEmpty { null }

    /** "1 × 3.35 GHz + 3 × 3.2 GHz + 4 × 2.2 GHz": cores grouped by top frequency, fastest first. */
    fun describeCores(freqs: List<Long>?): String? {
        val known = freqs.orEmpty().filter { it > 0 }
        if (known.isEmpty()) return null
        return known.groupingBy { it }.eachCount().entries.sortedByDescending { it.key }.joinToString(" + ") { (f, n) ->
            "$n × ${java.math.BigDecimal(f / 1e6).setScale(2, java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()} GHz"
        }
    }
}
