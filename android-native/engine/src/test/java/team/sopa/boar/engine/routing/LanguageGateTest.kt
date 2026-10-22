package team.sopa.boar.engine.routing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import team.sopa.boar.engine.routing.LanguageGate.Decision

/**
 * Boar Polyglot's gate: what must pass to the model as today (English, Portuguese, Spanish, short phrases, thanks,
 * names, quotes, code, translation requests, follow-ups) and what gets the instant "BOAR doesn't speak X yet" reply
 * (full sentences in other languages: by script, or by the phone's detector for Latin text).
 */
class LanguageGateTest {
    /** A sure detector result for [code], as Android's TextClassifier gives it for a clear sentence. */
    private fun sure(code: String) = listOf(code to 0.97f, "en" to 0.02f)

    private val pass = listOf(
        // thanks, greetings, yes/no, in any language
        "ok", "okay", "OK!", "thanks", "Thanks!", "thank you", "obrigado", "obrigada", "merci", "merci beaucoup", "danke schön",
        "Danke", "gracias", "muchas gracias", "grazie mille", "спасибо", "спасибо большое", "谢谢", "ありがとう",
        "ありがとうございます", "감사합니다", "شكرا", "תודה רבה", "धन्यवाद", "ขอบคุณครับ", "ευχαριστώ πολύ", "yes", "no", "hi",
        "olá", "oi", "hola", "bonjour", "ciao", "привет", "你好", "こんにちは", "안녕하세요", "oui", "ja", "nein", "Teşekkürler",
        "terima kasih", "dziękuję", "Bonjour, merci beaucoup mon ami",
        // nothing to measure: emoji, numbers, links, code, punctuation
        "😀😀😀", "👍", "🙏🙏 🙏 🙏", "12345", "3.14159 + 2.71828 = 5.85987", "https://example.com/some/long/path?x=1",
        "contact me at someone@example.org", "`SELECT * FROM users WHERE id = 1;`",
        "```\nfn main() { println!(\"hola mundo\"); }\n```", "const x = { a: 1, b: 2 };", "???", "...", "",
        // names and places
        "Zürich?", "São Paulo", "Москва?", "東京", "Dostoevsky", "Kraków Wrocław Gdańsk Poznań",
        // English and Portuguese with foreign names and quotes
        "What is the capital of Switzerland, Zürich or Bern?", "How far is São Paulo from Rio de Janeiro?",
        "What does 'je ne sais quoi' mean?", "What does «Schadenfreude ist die schönste Freude» mean in English?",
        "Who wrote „Die Leiden des jungen Werthers“?", "Where is Ногинск located?", "Is 東京タワー taller than the Eiffel Tower?",
        "O que significa \"Ich bin ein Berliner\"?", "Quem escreveu \"Война и мир\"?", "Como se diz 'obrigado' em japonês?",
        "Qual é a capital da Alemanha?", "Onde fica Kraków?", "Por que o céu é azul?",
        "Translate 'danke schön' to English", "translate this to French: where is the train station",
        "Traduza para o português: Je voudrais un café s'il vous plaît", "traduz isso: Wo ist der Bahnhof bitte",
        "How do you say я люблю тебя in English?", "What's the difference between こんにちは and こんばんは?",
        "Can you explain what 你好吗 means?", "Is it true that Здравствуйте is hard to pronounce?",
        "I want to learn Polish. Jak się masz is my first phrase, what does it mean?",
        // plain English and Portuguese
        "What should I pack for a three day hike in the mountains?", "How do I purify water without a filter?",
        "Como faço para purificar água sem filtro?", "Quais são os sintomas de desidratação?",
        "Você pode me ajudar a planejar uma viagem?",
        // Spanish, allowed for now (with inverted marks or three Spanish-only words, whatever the detector says)
        "¿Dónde está la estación de tren más cercana?", "¿Cuánto tiempo hay que hervir el agua?",
        "Mi hijo tiene fiebre, qué puedo hacer", "Es muy peligroso hacer fuego aquí",
        // three-word foreign phrases
        "c'est la vie", "wie geht's dir", "come stai oggi", "hoe gaat het", "jak się masz", "что это такое", "这是什么",
    )

    @Test fun passesWithASureDetector() {
        // Even a detector sure it's French doesn't block these: each is short, quoted, a thanks, a name or has English/Portuguese in it.
        val failures = pass.filter { LanguageGate.decide(it, sure("fr")) != Decision.Allowed }
        assertTrue("blocked: $failures", failures.isEmpty())
    }

    @Test fun passesWithoutADetector() {
        val failures = pass.filter { LanguageGate.decide(it, null) != Decision.Allowed }
        assertTrue("blocked: $failures", failures.isEmpty())
    }

    /** No function word to go on: the detector names an allowed language. */
    private val allowedByDetector = listOf(
        "es" to "Necesito ayuda para planear un viaje a la montaña",
        "es" to "Quiero saber cuánto tiempo tarda en hervir el agua",
        "es" to "Cuál es la mejor manera de hervir agua en el campo",
        "en" to "Explain photosynthesis in simple terms",
        "en" to "Tell me something interesting about volcanoes today",
        "en" to "Give me five tips for staying warm overnight",
        "pt" to "Preciso saber quanto tempo dura uma bateria",
        "pt" to "Me explica fotossíntese em palavras simples",
    )

    @Test fun allowedLanguagesPassWhenDetected() {
        for ((code, q) in allowedByDetector) assertEquals(q, Decision.Allowed, LanguageGate.decide(q, sure(code)))
        assertTrue("es" in LanguageGate.ALLOWED)
        assertTrue(pass.size + allowedByDetector.size >= 80)
    }

    @Test fun unsureDetectorPasses() {
        val q = "Je voudrais savoir quel temps il fera demain"
        assertEquals(Decision.Allowed, LanguageGate.decide(q, listOf("fr" to 0.85f, "it" to 0.05f)))
        assertEquals(Decision.Allowed, LanguageGate.decide(q, listOf("fr" to 0.91f, "ca" to 0.70f)))
        assertEquals(Decision.Allowed, LanguageGate.decide(q, emptyList()))
        assertEquals(Decision.Allowed, LanguageGate.decide(q, listOf("und" to 0.99f)))
        assertEquals(Decision.Allowed, LanguageGate.decide(q, false) { throw IllegalStateException("no classifier") })
    }

    @Test fun followUpsPass() {
        val shortOnes = listOf("Und auf Deutsch bitte, danke sehr", "Et en français s'il vous plaît", "А на русском языке можно?", "用中文回答可以吗")
        for (q in shortOnes) assertEquals(q, Decision.Allowed, LanguageGate.decide(q, sure("de"), followUp = true))
        // A full question isn't a follow-up however the chat went.
        val long = "Почему небо голубое а трава зелёная летом"
        assertEquals(Decision.Unsupported("ru"), LanguageGate.decide(long, null, followUp = true))
    }

    /** Latin script: blocked only on a sure detector result. */
    private val latin = listOf(
        "fr" to "Quelle est la meilleure façon de purifier l'eau en randonnée ?",
        "fr" to "Je voudrais savoir combien de temps il faut pour faire bouillir de l'eau",
        "fr" to "Est-ce que je peux boire l'eau de la rivière sans la filtrer ?",
        "de" to "Wie lange dauert es, bis das Wasser kocht?",
        "de" to "Ich möchte wissen, wie man ohne Kompass nach Norden findet",
        "de" to "Was soll ich für eine dreitägige Wanderung einpacken?",
        "it" to "Quale sarebbe il modo migliore per purificare l'acqua durante un'escursione?",
        "it" to "Quanto tempo ci vuole per far bollire l'acqua in montagna?",
        "it" to "Vorrei sapere come accendere un fuoco senza fiammiferi",
        "nl" to "Hoe lang duurt het voordat het water kookt?",
        "nl" to "Wat is de beste manier om water te zuiveren tijdens een wandeling?",
        "pl" to "Jak długo trzeba gotować wodę, żeby była bezpieczna?",
        "pl" to "Jak dojechać do dworca kolejowego najszybciej?",
        "tr" to "Suyu kaynatmak ne kadar sürer ve güvenli mi?",
        "tr" to "Kamp yaparken ateş nasıl yakılır bana anlatır mısın?",
        "id" to "Berapa lama waktu yang dibutuhkan untuk merebus air?",
        "id" to "Bagaimana cara menyaring air sungai agar aman diminum?",
        "sv" to "Hur länge ska man koka vattnet innan man dricker det?",
        "vi" to "Làm thế nào để lọc nước sông cho an toàn khi uống?",
        "fi" to "Kuinka kauan vettä pitää keittää ennen juomista?",
    )

    /** Other scripts: blocked by their letters, no detector needed. */
    private val scripts = listOf(
        "ru" to "Как долго нужно кипятить воду, чтобы она была безопасной?",
        "ru" to "Что взять с собой в поход на три дня в горы?",
        "ru" to "Почему небо голубое а трава зелёная летом",
        "uk" to "Як довго потрібно кип'ятити воду, щоб вона була безпечною?",
        "uk" to "Що взяти з собою в похід на три дні?",
        "zh" to "烧水需要多长时间才能安全饮用？",
        "zh" to "在山里徒步三天应该带些什么东西",
        "ja" to "水を安全に飲むためにはどのくらい沸騰させればいいですか？",
        "ja" to "三日間の山歩きに何を持っていけばいいですか",
        "ko" to "물을 안전하게 마시려면 얼마나 끓여야 하나요?",
        "ko" to "삼일 동안 산행할 때 무엇을 챙겨야 하나요?",
        "ar" to "كم من الوقت يجب أن أغلي الماء حتى يصبح آمنا للشرب؟",
        "ar" to "ماذا يجب أن أحمل معي في رحلة تخييم لمدة ثلاثة أيام",
        "he" to "כמה זמן צריך להרתיח מים כדי שיהיו בטוחים לשתייה?",
        "he" to "מה כדאי לקחת לטיול של שלושה ימים בהרים",
        "hi" to "पानी को सुरक्षित पीने के लिए कितनी देर उबालना चाहिए?",
        "hi" to "तीन दिन की पहाड़ी यात्रा के लिए क्या ले जाना चाहिए",
        "th" to "ต้องต้มน้ำนานเท่าไรถึงจะปลอดภัยสำหรับดื่ม",
        "th" to "ไปเดินป่าสามวันควรเตรียมอะไรไปบ้าง",
        "el" to "Πόση ώρα πρέπει να βράσω το νερό για να είναι ασφαλές;",
        "el" to "Τι πρέπει να πάρω μαζί μου για πεζοπορία τριών ημερών",
        "ka" to "რამდენ ხანს უნდა ვადუღო წყალი რომ უსაფრთხო იყოს",
        "hy" to "Որքան ժամանակ պետք է եռացնել ջուրը որ այն անվտանգ լինի",
        "bn" to "পানি নিরাপদে পান করার জন্য কতক্ষণ ফোটাতে হবে",
    )

    @Test fun blocksLatinScriptWithASureDetector() {
        for ((code, q) in latin) assertEquals(q, Decision.Unsupported(code), LanguageGate.decide(q, sure(code)))
    }

    @Test fun blocksOtherScriptsWithoutADetector() {
        for ((code, q) in scripts) assertEquals(q, Decision.Unsupported(code), LanguageGate.decide(q, null))
        assertTrue(latin.size + scripts.size >= 40)
    }

    @Test fun cyrillicPrefersTheDetector() {
        val q = "Колко време трябва да се вари водата за да е безопасна"
        assertEquals(Decision.Unsupported("ru"), LanguageGate.decide(q, null))
        assertEquals(Decision.Unsupported("bg"), LanguageGate.decide(q, listOf("bg" to 0.93f, "ru" to 0.05f)))
        // A detector naming a language of another script is ignored.
        assertEquals(Decision.Unsupported("ru"), LanguageGate.decide(q, listOf("en" to 0.99f)))
    }

    @Test fun latinPassesWithoutADetector() {
        for ((_, q) in latin) assertEquals(q, Decision.Allowed, LanguageGate.decide(q, null))
    }

    @Test fun detectorCodesAreNormalized() {
        assertEquals(Decision.Unsupported("he"), LanguageGate.decide("כמה זמן צריך להרתיח מים כדי שיהיו בטוחים", listOf("iw" to 0.99f)))
        assertEquals(Decision.Unsupported("id"), LanguageGate.decide("Berapa lama waktu yang dibutuhkan untuk merebus air?", listOf("in-ID" to 0.98f)))
    }

    @Test fun detectorOnlyCalledWhenNeeded() {
        var calls = 0
        LanguageGate.decide("What should I pack for a hike?", false) { calls++; null }
        LanguageGate.decide("merci", false) { calls++; null }
        LanguageGate.decide("Как долго нужно кипятить воду?", false) { calls++; null }
        assertEquals(1, calls) // only the Cyrillic one asks (ru/uk/bg)
    }

    @Test fun fuzzNeverThrowsAndIsFast() {
        val rnd = java.util.Random(20261018L)
        val pools = listOf(
            "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ  .,;:!?'\"«»„“”‘’`{}()[]@/#0123456789\n\t-_",
            "абвгдеёжзийклмнопрстуфхцчшщъыьэюяАБВГДЕЖЗ ",
            "的一是不了人我在有他这中大来上国个到说们为子和你地出道也时年得就那要下以生会自着去之过家学对可她里后小么心多天而能好都然没日于起还发成事只作当想看文无开手十用主行方又如前所本见经头面公同三已老从动两长",
            "あいうえおかきくけこアイウエオカキクケコ가나다라마바사아자차카타파하 ",
            "ابتثجحخدذرسشصضطظعغفقكلمنهوي אבגדהוזחטיכלמנסעפצקרשת ",
            "αβγδεζηθικλμνξοπρστυφχψω अआइईउऊएऐओऔकखगघङ กขคงจฉชซฌญ ",
            "😀🙏👍🔥\uD83D‍️́̀𐀀\uDBFF",
        ).map { it.toCharArray() }
        fun random(len: Int): String {
            val sb = StringBuilder(len)
            val pool = pools[rnd.nextInt(pools.size)]
            repeat(len) { sb.append(if (rnd.nextInt(10) == 0) pools[rnd.nextInt(pools.size)].let { p -> p[rnd.nextInt(p.size)] } else pool[rnd.nextInt(pool.size)]) }
            return sb.toString()
        }
        val detectors = listOf<List<Pair<String, Float>>?>(null, emptyList(), sure("fr"), listOf("" to 1f), listOf("zh-Hant-TW" to 0.99f), listOf("xx" to Float.NaN))
        repeat(2_000) { i -> LanguageGate.decide(random(rnd.nextInt(300)), detectors[i % detectors.size], followUp = i % 7 == 0) }
        // Warm up, then time 2,000-character inputs.
        val long = List(50) { random(2_000) }
        long.forEach { LanguageGate.decide(it, sure("fr")) }
        var worst = 0L
        for (s in long) {
            val t = System.nanoTime()
            LanguageGate.decide(s, sure("fr"))
            worst = maxOf(worst, System.nanoTime() - t)
        }
        assertTrue("worst ${worst / 1e6} ms", worst < 5_000_000L)
        // Adversarial: unmatched quotes and backticks everywhere.
        for (s in listOf("«".repeat(2_000), "'a".repeat(1_000), "`".repeat(2_001), "\"x ".repeat(700), "{;".repeat(1_000), "Ab ".repeat(700))) {
            val t = System.nanoTime()
            LanguageGate.decide(s, sure("fr"))
            assertTrue("slow on ${s.take(6)}", System.nanoTime() - t < 20_000_000L)
        }
    }
}
