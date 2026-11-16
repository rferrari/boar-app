package team.sopa.boar.engine.canvas
import org.junit.Test
/**
 * Validator Q-A3 (t9 0115): 8 layouts x every order of 3 values. Before: 6 of 8 right cards kept whole, and 11 of 40
 * swapped ones shown pruned with a wrong value; now 8 of 8 and 0 of 40.
 */
class VisualPairingMatrixTest { @Test fun `every layout keeps the right card whole and shows no wrong value`() {
    val names = listOf("Paris", "Lyon", "Marseille"); val vals = listOf(2.1, 0.5, 0.9)
    val layouts = listOf<(List<String>, List<String>) -> String>(
        { n, v -> "${v[0]} million people live in ${n[0]}. ${v[1]} million people live in ${n[1]}. ${v[2]} million people live in ${n[2]}." },
        { n, v -> "${v[0]} million people live in ${n[0]}, ${v[1]} million in ${n[1]} and ${v[2]} million in ${n[2]}." },
        { n, v -> "${n[0]} has ${v[0]} million people. ${n[1]} has ${v[1]} million. ${n[2]} has ${v[2]} million." },
        { n, v -> "${n[0]} has ${v[0]} million people, ${n[1]} ${v[1]} million and ${n[2]} ${v[2]} million." },
        { n, v -> "${n[0]}: ${v[0]} million.\n${n[1]}: ${v[1]} million.\n${n[2]}: ${v[2]} million." },
        { n, v -> "- ${n[0]} — ${v[0]} million\n- ${n[1]} — ${v[1]} million\n- ${n[2]} — ${v[2]} million" },
        { n, v -> "${v[0]} million (${n[0]}), ${v[1]} million (${n[1]}), ${v[2]} million (${n[2]})." },
        { n, v -> "The largest is ${n[0]} with ${v[0]} million. Next comes ${n[1]}, with ${v[1]} million. Then ${n[2]} at ${v[2]} million." },
    )
    fun perms(l: List<Int>): List<List<Int>> = if (l.size <= 1) listOf(l) else l.flatMap { x -> perms(l - x).map { listOf(x) + it } }
    var right = 0; var rightN = 0; var wrong = 0; var wrongN = 0
    for ((li, lay) in layouts.withIndex()) {
        val ans = lay(names, vals.map { it.toString() })
        for (p in perms(listOf(0, 1, 2))) {
            val items = names.indices.map { names[it] to vals[p[it]] }
            val json = """{"type":"bars","unit":"million","items":[${items.joinToString(",") { """{"label":"${it.first}","value":${it.second}}""" }}]}"""
            val r = Visual.parse(json, ans)
            val shown = (r as? Visual.Parsed.Ok)?.visual as? Visual.Series
            if (p == listOf(0, 1, 2)) { rightN++; if (shown != null && shown.items.size == 3) right++ }
            else { wrongN++; if (shown != null && shown.items.any { (l, x) -> vals[names.indexOf(l)] != x }) { wrong++; println("WRONG L$li $p ${shown.items}") } }
        }
    }
    org.junit.Assert.assertEquals("right cards kept whole", rightN, right)
    org.junit.Assert.assertEquals("wrong values shown", 0, wrong)
} }
