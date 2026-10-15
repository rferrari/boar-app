package team.sopa.boar.share

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import team.sopa.boar.BuildConfig
import team.sopa.boar.engine.CpuTopology
import team.sopa.boar.engine.eval.ShareResults
import team.sopa.boar.engine.settings.AppSettings
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Sends one evaluation run to submit-results, signed with this phone's hardware key (src/eval/shareResults.ts): ask
 * for a one-time challenge, sign `challenge.payload`, send. The first share (or one after the server forgot the key)
 * makes the key with that challenge in its attestation. Only after the user confirms what's listed.
 */
class ResultsSharing(private val context: Context, private val settings: AppSettings) {
    private val endpoint = ShareResults.submitResultsUrl(BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_PUBLISHABLE_KEY)

    /** False in builds without the Supabase values: no Share button. */
    val available: Boolean get() = endpoint != null

    /** Everything about this phone that a shared run carries (listed before sending). */
    fun device(): ShareResults.Device {
        val ram = runCatching { ActivityManager.MemoryInfo().also { context.getSystemService(ActivityManager::class.java).getMemoryInfo(it) }.totalMem }.getOrNull()
        val features = runCatching { File("/proc/cpuinfo").readLines().firstOrNull { it.startsWith("Features") }?.substringAfter(":") }.getOrNull()
        val freqs = CpuTopology.read().cores.sortedBy { it.id }.map { it.maxKhz }
        return ShareResults.Device(
            osVersion = Build.VERSION.RELEASE, apiLevel = Build.VERSION.SDK_INT, brand = Build.BRAND, model = Build.MODEL,
            soc = if (Build.VERSION.SDK_INT >= 31) Build.SOC_MODEL.takeIf { it != Build.UNKNOWN } else null,
            socManufacturer = if (Build.VERSION.SDK_INT >= 31) Build.SOC_MANUFACTURER.takeIf { it != Build.UNKNOWN } else null,
            hardware = Build.HARDWARE, ramBytes = ram,
            cpuCores = freqs.size.takeIf { it > 0 }, cpuFeatures = ShareResults.parseCpuFeatures(features),
            coreMaxFreqKHz = freqs.takeIf { f -> f.any { it > 0 } },
        )
    }

    fun appVersion(): String = runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "?"

    enum class Step { PREPARE, CHALLENGE, KEY, SIGN, SEND }
    /** A log line for the progress screen: the step, and what it found (challenge prefix, key kind, size). */
    class StepInfo(val step: Step, val challenge: String? = null, val key: String? = null, val bytes: Int? = null, val rows: Int? = null)

    private class Offline(m: String) : Exception(m)
    private class KeyFailed(m: String) : Exception(m)

    private fun post(body: JsonObject): Pair<Int, JsonObject?> {
        val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"; connectTimeout = 30_000; readTimeout = 30_000; doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("apikey", BuildConfig.SUPABASE_PUBLISHABLE_KEY)
        }
        try {
            try { conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) } } catch (e: java.io.IOException) { throw Offline(e.message ?: e.toString()) }
            val status = try { conn.responseCode } catch (e: java.io.IOException) { throw Offline(e.message ?: e.toString()) }
            val text = runCatching { (if (status >= 400) conn.errorStream else conn.inputStream)?.bufferedReader()?.readText() }.getOrNull()
            return status to text?.let { runCatching { Json.parseToJsonElement(it).jsonObject }.getOrNull() }
        } finally {
            conn.disconnect()
        }
    }

    private fun obj(vararg p: Pair<String, Any?>) = JsonObject(p.mapNotNull { (k, v) ->
        when (v) { null -> null; is String -> k to JsonPrimitive(v); is JsonElement -> k to v; is List<*> -> k to JsonArray(v.map { JsonPrimitive(it.toString()) }); else -> k to JsonPrimitive(v.toString()) }
    }.toMap())

    /** A key for this share: attested, or, when the secure hardware refuses, an unattested one whose runs wait for review. */
    private fun newDevice(challenge: String, onStep: (StepInfo) -> Unit): JsonObject = try {
        val k = DeviceKey.create(challenge)
        onStep(StepInfo(Step.KEY, key = "new-attested"))
        obj("platform" to "android", "attestation" to k.attestation)
    } catch (e: DeviceKey.AttestationFailed) {
        try {
            val k = DeviceKey.createUnattested()
            onStep(StepInfo(Step.KEY, key = "new-unattested"))
            obj("platform" to "android", "attestation" to emptyList<String>(), "publicKey" to k.publicKey, "attestationError" to (e.message ?: "").take(300))
        } catch (e2: Exception) {
            throw KeyFailed(e2.message ?: e2.toString())
        }
    }

    suspend fun share(rows: List<JsonObject>, onStep: (StepInfo) -> Unit): ShareResults.Outcome = withContext(Dispatchers.IO) {
        if (!available || rows.isEmpty()) return@withContext ShareResults.Outcome(ShareResults.Result.FAILED)
        onStep(StepInfo(Step.PREPARE, rows = rows.size))
        val payload = ShareResults.buildSubmission(rows, device(), appVersion()).toString()
        try {
            repeat(2) { attempt ->
                onStep(StepInfo(Step.CHALLENGE))
                val (cs, cb) = post(obj("step" to "challenge"))
                val challenge = (cb?.get("challenge") as? JsonPrimitive)?.contentOrNull
                if (cs != 200 || challenge == null) return@withContext ShareResults.outcome(cs, cb)
                onStep(StepInfo(Step.CHALLENGE, challenge = challenge.take(8)))
                val known = if (DeviceKey.has()) settings.shareDeviceId else null
                val device = if (known != null) {
                    onStep(StepInfo(Step.KEY, key = "existing"))
                    obj("platform" to "android", "id" to known)
                } else {
                    onStep(StepInfo(Step.KEY))
                    newDevice(challenge, onStep)
                }
                onStep(StepInfo(Step.SIGN))
                val signature = try { DeviceKey.sign(ShareResults.signedMessage(challenge, payload)) } catch (e: Exception) { throw KeyFailed(e.message ?: e.toString()) }
                onStep(StepInfo(Step.SEND, bytes = payload.length, rows = rows.size))
                val (ss, sb) = post(obj("step" to "submit", "challenge" to challenge, "payload" to payload, "signature" to signature, "device" to device))
                (sb?.get("device") as? JsonPrimitive)?.contentOrNull?.let { settings.shareDeviceId = it }
                // The server doesn't know this key (its data was reset): a new one, once.
                if (ss == 412 && attempt == 0) {
                    settings.shareDeviceId = null
                    DeviceKey.delete()
                    return@repeat
                }
                return@withContext ShareResults.outcome(ss, sb)
            }
            ShareResults.Outcome(ShareResults.Result.FAILED)
        } catch (e: Offline) {
            ShareResults.Outcome(ShareResults.Result.OFFLINE, detail = e.message)
        } catch (e: KeyFailed) {
            ShareResults.Outcome(ShareResults.Result.KEY_FAILED, detail = e.message)
        } catch (e: Exception) {
            ShareResults.Outcome(ShareResults.Result.FAILED, detail = (e.message ?: e.toString()).take(300))
        }
    }
}
