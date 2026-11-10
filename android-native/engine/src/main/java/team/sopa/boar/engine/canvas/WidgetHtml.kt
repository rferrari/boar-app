package team.sopa.boar.engine.canvas

/**
 * Boar Canvas's live widgets (Open Intelligent UI's sandboxed UI, offline): the chat model writes one small
 * self-contained HTML page (a calculator, a quiz, an interactive diagram) for a finished answer, and the app shows it in
 * a WebView that can't reach anything. Here: pulling the page out of the model's reply, and wrapping it with the
 * policy and the theme. The WebView's own settings (no network, no files, no bridge) are the second wall.
 */
object WidgetHtml {
    /**
     * What a widget's WebView may load: its own page (a data: URL, or about:blank) and nothing else. data: carries its
     * content inline and never reaches the network. The lock answered every request with a 403, the page itself too,
     * so Android showed "Web page not available" instead of the widget (X6, Nov 10).
     */
    fun allowedUrl(url: String?): Boolean {
        val u = url?.trim()?.lowercase() ?: return false
        return u.startsWith("data:") || u == "about:blank"
    }

    /** A widget is a few kilobytes; a longer reply is a model that didn't stop. */
    const val MAX_CHARS = 24_000

    /**
     * Nothing may load: no network, no frames, no forms that leave. Inline script and style only, images from data:.
     * First in <head>, before anything the model wrote can run.
     */
    const val POLICY = "default-src 'none'; script-src 'unsafe-inline'; style-src 'unsafe-inline'; img-src data:; font-src data:; " +
        "connect-src 'none'; frame-src 'none'; form-action 'none'; base-uri 'none'"

    /** The page in the model's reply (inside a ```html fence or bare); null when there's none or it's too long. */
    fun extract(raw: String): String? {
        val text = raw.substringAfter("</think>")
        val fenced = Regex("```(?:html)?\\s*\\n([\\s\\S]*?)```", RegexOption.IGNORE_CASE).find(text)?.groupValues?.get(1)
        val body = (fenced ?: text).trim()
        val start = Regex("<!doctype html|<html|<body|<div|<style|<script|<main|<section|<canvas|<svg", RegexOption.IGNORE_CASE).find(body)?.range?.first ?: return null
        val end = body.lastIndexOf("</html>", ignoreCase = true).takeIf { it >= 0 }?.plus("</html>".length) ?: body.length
        val page = body.substring(start, end).trim()
        if (page.length > MAX_CHARS || !page.contains('<')) return null
        return page
    }

    /** [page] as a full document: the policy, the viewport and the app's colors ([Theme]) first, the model's page after. */
    fun wrap(page: String, theme: Theme): String {
        val head = """<meta charset="utf-8"><meta http-equiv="Content-Security-Policy" content="$POLICY">""" +
            """<meta name="viewport" content="width=device-width, initial-scale=1">""" +
            "<style>:root{--bg:${theme.bg};--fg:${theme.fg};--muted:${theme.muted};--accent:${theme.accent};--card:${theme.card};color-scheme:${if (theme.dark) "dark" else "light"}}" +
            "html,body{margin:0;padding:12px;background:var(--bg);color:var(--fg);font-family:system-ui,sans-serif;font-size:15px}" +
            "button,input,select{font:inherit}button{background:var(--accent);color:var(--bg);border:0;border-radius:8px;padding:8px 12px}</style>"
        // The model's own <head> goes after ours, so it can style but not loosen the policy (a second CSP only narrows).
        val inner = page.replace(Regex("<!doctype[^>]*>", RegexOption.IGNORE_CASE), "")
            .replace(Regex("</?html[^>]*>", RegexOption.IGNORE_CASE), "")
        return "<!doctype html><html><head>$head</head>$inner</html>"
    }

    /** CSS colors, "#RRGGBB". */
    data class Theme(val bg: String, val fg: String, val muted: String, val accent: String, val card: String, val dark: Boolean)
}
