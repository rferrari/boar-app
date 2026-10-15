package team.sopa.boar.engine.eval

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import team.sopa.boar.engine.eval.ShareResults.Result

/** The cases of src/eval/shareResults.test.ts. */
class ShareResultsTest {
    private fun row(q: String) = JsonObject(mapOf("runId" to JsonPrimitive("eval-1"), "evalSetVersion" to JsonPrimitive("1"),
        "queryId" to JsonPrimitive(q), "configId" to JsonPrimitive("model:qwen"), "outcome" to JsonPrimitive("success")))
    private fun body(s: String) = Json.parseToJsonElement(s).jsonObject

    @Test fun `the URL needs both values and ignores a trailing slash`() {
        assertNull(ShareResults.submitResultsUrl(null, "k"))
        assertNull(ShareResults.submitResultsUrl("https://x.supabase.co", ""))
        assertEquals("https://x.supabase.co/functions/v1/submit-results", ShareResults.submitResultsUrl("https://x.supabase.co/", "k"))
    }

    @Test fun `the submission carries the run, the device, and every row, leaving out what's unknown`() {
        val s = ShareResults.buildSubmission(listOf(row("a"), row("b")), ShareResults.Device(osVersion = "15", brand = "POCO", model = "X6", ramBytes = 12_000_000_000), "1.0.0")
        assertEquals("""{"run":{"runId":"eval-1","evalSetVersion":"1","appVersion":"1.0.0","platform":"android","osVersion":"15","deviceBrand":"POCO","deviceModel":"X6","ramBytes":12000000000},"rows":[${row("a")},${row("b")}]}""", s.toString())
        val run = ShareResults.buildSubmission(listOf(row("a")), ShareResults.Device(soc = "", hardware = "mt6897", cpuCores = 8, cpuFeatures = listOf("i8mm"), coreMaxFreqKHz = listOf(2200000, 3350000), apiLevel = 35, ramBytes = 0), "1")["run"]!!.jsonObject
        assertEquals(null, run["soc"]); assertEquals(null, run["ramBytes"])
        assertEquals("""["i8mm"]""", run["cpuFeatures"].toString()); assertEquals("[2200000,3350000]", run["coreMaxFreqKHz"].toString())
        assertNull(ShareResults.buildSubmission(listOf(row("a")), ShareResults.Device(cpuFeatures = emptyList()), "1")["run"]!!.jsonObject["cpuFeatures"])
    }

    @Test fun `server answers become outcomes`() {
        mapOf(201 to Result.SHARED, 409 to Result.ALREADY_SHARED, 429 to Result.RATE_LIMITED, 400 to Result.REJECTED, 401 to Result.REJECTED,
            412 to Result.REJECTED, 500 to Result.SERVER_ERROR, 503 to Result.BUSY, 0 to Result.FAILED).forEach { (s, r) -> assertEquals("$s", r, ShareResults.outcome(s, null).result) }
        val at = "2026-09-29T16:23:54.043438+00:00"
        assertEquals(ShareResults.Outcome(Result.COOLDOWN, at, 429, "cooldown"), ShareResults.outcome(429, body("""{"error":"cooldown","retryAt":"$at"}""")))
        assertEquals(at, ShareResults.outcome(429, body("""{"error":"rate_limited","retryAt":"$at"}""")).retryAt)
        assertNull(ShareResults.outcome(429, body("""{"error":"rate_limited","retryAt":"soon"}""")).retryAt)
        assertEquals(Result.NETWORK_LIMITED, ShareResults.outcome(429, body("""{"error":"network_limited"}""")).result)
        assertEquals(Result.SHARED_PENDING, ShareResults.outcome(201, body("""{"pendingReview":true}""")).result)
        assertEquals(Result.SHARED, ShareResults.outcome(201, body("""{"pendingReview":false}""")).result)
        assertEquals(ShareResults.Outcome(Result.BUSY, status = 503, detail = "review_queue_full"), ShareResults.outcome(503, body("""{"error":"review_queue_full"}""")))
        assertTrue(listOf(Result.OFFLINE, Result.BUSY, Result.SERVER_ERROR, Result.FAILED).all { it.canRetry })
        assertFalse(listOf(Result.SHARED, Result.REJECTED, Result.RATE_LIMITED, Result.KEY_FAILED).any { it.canRetry })
        assertTrue(Result.SHARED_PENDING.succeeded); assertFalse(Result.OFFLINE.succeeded)
    }

    @Test fun `signing input and hardware descriptions`() {
        assertEquals("""abc.{"run":{}}""", ShareResults.signedMessage("abc", """{"run":{}}"""))
        assertEquals(listOf("fp", "asimd", "asimddp", "i8mm"), ShareResults.parseCpuFeatures("fp asimd  asimddp i8mm\n"))
        assertEquals("Mediatek MT6897", ShareResults.describeChipset(ShareResults.Device(soc = "MT6897", socManufacturer = "Mediatek")))
        assertEquals("qcom", ShareResults.describeChipset(ShareResults.Device(hardware = "qcom")))
        assertEquals("1 × 3.35 GHz + 3 × 3.2 GHz + 4 × 2.2 GHz", ShareResults.describeCores(listOf(2200000, 2200000, 2200000, 2200000, 3200000, 3200000, 3200000, 3350000)))
        assertNull(ShareResults.describeCores(listOf(0, 0)))
    }
}
