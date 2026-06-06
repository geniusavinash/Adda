package co.mobilise.adda.llm

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

/**
 * Pre-cached Hinglish answers used as a fallback when the on-device model isn't
 * loaded (e.g. the .task wasn't pushed yet). Streamed char-by-char so the UI
 * still shows a live, believable answer — the demo never dead-ends.
 */
object DemoCache {

    private data class DemoQa(val keys: List<String>, val answer: String)

    private val canned = listOf(
        DemoQa(
            keys = listOf("pythagoras", "pythagorean", "hypotenuse"),
            answer = "Pythagoras theorem kehta hai: right-angle triangle mein hypotenuse ka " +
                "square baaki do sides ke squares ke sum ke barabar hota hai.\n\n" +
                "```\nc² = a² + b²\n```\n\nYahan c hypotenuse hai (sabse lambi side), a aur b " +
                "baaki do sides. Example: a=3, b=4 to c=5.",
        ),
        DemoQa(
            keys = listOf("newton", "second law", "force", "f = ma", "f=ma"),
            answer = "Newton ka second law: kisi object pe force = uske mass × acceleration.\n\n" +
                "```\nF = m × a\n```\n\nMatlab same mass pe zyada force lagao to zyada " +
                "acceleration milega. Force Newton (N) mein hota hai.",
        ),
        DemoQa(
            keys = listOf("photosynthesis", "pradhanya", "chlorophyll"),
            answer = "Photosynthesis mein plants sunlight, paani aur CO₂ se apna khaana " +
                "(glucose) banate hain aur oxygen chhodte hain.\n\n" +
                "```\n6CO₂ + 6H₂O + light → C₆H₁₂O₆ + 6O₂\n```\n\nYe leaves ke chlorophyll " +
                "mein hota hai — isi liye patte hare hote hain.",
        ),
        DemoQa(
            keys = listOf("quadratic", "sridharacharya", "roots"),
            answer = "Quadratic equation ax²+bx+c=0 ke roots nikaalne ka formula:\n\n" +
                "```\nx = (-b ± √(b² - 4ac)) / 2a\n```\n\nb²-4ac ko discriminant kehte hain — " +
                "agar wo positive hai to do real roots milte hain.",
        ),
    )

    private const val DEFAULT_ANSWER =
        "Main Adda hoon 🙂 Abhi on-device model load nahi hua, isliye ye ek demo answer hai. " +
            "Asli setup mein main aapke phone pe hi — bina internet — aapke doubts ka jawab deta hoon. " +
            "Model ready hone par phir se poochho!"

    fun answerFor(question: String): String {
        val q = question.lowercase()
        return canned.firstOrNull { qa -> qa.keys.any { q.contains(it) } }?.answer ?: DEFAULT_ANSWER
    }

    /** Stream the cached answer char-by-char to mimic live token generation. */
    fun stream(question: String): Flow<String> = flow {
        val text = answerFor(question)
        var i = 0
        while (i < text.length) {
            val end = minOf(i + 3, text.length)
            emit(text.substring(i, end))
            i = end
            delay(26)
        }
    }.flowOn(Dispatchers.Default)
}
