package com.eqraa.reader.reader

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.speech.tts.TextToSpeech
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import com.eqraa.reader.settings.ReadingPreferences

/**
 * Data class representing a dictionary definition
 */
data class DictionaryEntry(
    val word: String,
    val phonetic: String?,
    val meanings: List<Meaning>,
    val synonyms: List<String>
)

data class Meaning(
    val partOfSpeech: String,
    val definitions: List<Definition>
)

data class Definition(
    val definition: String,
    val example: String?
)

/**
 * Data class representing a translation
 */
data class Translation(
    val sourceText: String,
    val targetLanguage: String,
    val translatedText: String
)

/**
 * Service for dictionary lookups using Free Dictionary API
 */
object DictionaryService {
    
    private const val API_URL = "https://api.dictionaryapi.dev/api/v2/entries/en/"
    
    /**
     * Look up a word in the dictionary
     */
    suspend fun lookup(word: String): Result<DictionaryEntry> = withContext(Dispatchers.IO) {
        try {
            val cleanWord = word.trim().lowercase().split("\\s+".toRegex()).first()
            val url = URL("$API_URL${URLEncoder.encode(cleanWord, "UTF-8")}")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            
            if (connection.responseCode == 200) {
                val response = connection.inputStream.bufferedReader().readText()
                val entry = parseResponse(cleanWord, response)
                Result.success(entry)
            } else {
                // Return a fallback entry if API fails
                Result.success(createFallbackEntry(cleanWord))
            }
        } catch (e: Exception) {
            // Return fallback on error
            Result.success(createFallbackEntry(word))
        }
    }
    
    private fun parseResponse(word: String, json: String): DictionaryEntry {
        try {
            val array = JSONArray(json)
            if (array.length() == 0) return createFallbackEntry(word)
            
            val entry = array.getJSONObject(0)
            
            // Get phonetic
            var phonetic: String? = null
            if (entry.has("phonetic")) {
                phonetic = entry.optString("phonetic")
            }
            if (phonetic.isNullOrEmpty() && entry.has("phonetics")) {
                val phonetics = entry.getJSONArray("phonetics")
                for (i in 0 until phonetics.length()) {
                    val p = phonetics.getJSONObject(i).optString("text")
                    if (p.isNotEmpty()) {
                        phonetic = p
                        break
                    }
                }
            }
            
            // Get meanings
            val meanings = mutableListOf<Meaning>()
            val allSynonyms = mutableSetOf<String>()
            
            if (entry.has("meanings")) {
                val meaningsArray = entry.getJSONArray("meanings")
                for (i in 0 until minOf(meaningsArray.length(), 3)) {
                    val meaningObj = meaningsArray.getJSONObject(i)
                    val partOfSpeech = meaningObj.optString("partOfSpeech", "")
                    
                    // Get definitions
                    val definitions = mutableListOf<Definition>()
                    if (meaningObj.has("definitions")) {
                        val defsArray = meaningObj.getJSONArray("definitions")
                        for (j in 0 until minOf(defsArray.length(), 2)) {
                            val defObj = defsArray.getJSONObject(j)
                            definitions.add(
                                Definition(
                                    definition = defObj.optString("definition", ""),
                                    example = defObj.optString("example").takeIf { it.isNotEmpty() }
                                )
                            )
                        }
                    }
                    
                    // Get synonyms
                    if (meaningObj.has("synonyms")) {
                        val synsArray = meaningObj.getJSONArray("synonyms")
                        for (j in 0 until minOf(synsArray.length(), 5)) {
                            allSynonyms.add(synsArray.getString(j))
                        }
                    }
                    
                    if (definitions.isNotEmpty()) {
                        meanings.add(Meaning(partOfSpeech, definitions))
                    }
                }
            }
            
            return DictionaryEntry(
                word = entry.optString("word", word),
                phonetic = phonetic,
                meanings = meanings.ifEmpty { listOf(Meaning("", listOf(Definition("Definition not available", null)))) },
                synonyms = allSynonyms.take(6).toList()
            )
        } catch (e: Exception) {
            return createFallbackEntry(word)
        }
    }
    
    private fun createFallbackEntry(word: String): DictionaryEntry {
        return DictionaryEntry(
            word = word,
            phonetic = null,
            meanings = listOf(
                Meaning(
                    partOfSpeech = "",
                    definitions = listOf(
                        Definition(
                            definition = "Definition not available. Please check your internet connection.",
                            example = null
                        )
                    )
                )
            ),
            synonyms = emptyList()
        )
    }
}

/**
 * Service for text translation to Arabic using MyMemory API (free, no API key required)
 */
object TranslationService {
    
    private const val API_URL = "https://api.mymemory.translated.net/get"
    private const val TARGET_LANG = "ar" // Arabic only
    
    /**
     * Translate text to Arabic
     */
    suspend fun translateToArabic(text: String): Result<Translation> = withContext(Dispatchers.IO) {
        try {
            val encodedText = URLEncoder.encode(text, "UTF-8")
            val langPair = "en|$TARGET_LANG"
            val url = URL("$API_URL?q=$encodedText&langpair=$langPair")
            
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            
            if (connection.responseCode == 200) {
                val response = connection.inputStream.bufferedReader().readText()
                val translation = parseTranslation(text, response)
                Result.success(translation)
            } else {
                Result.success(Translation(text, TARGET_LANG, "الترجمة غير متاحة"))
            }
        } catch (e: Exception) {
            Result.success(Translation(text, TARGET_LANG, "الترجمة غير متاحة"))
        }
    }
    
    /**
     * Legacy method for backward compatibility
     */
    suspend fun translate(text: String, targetLang: String = TARGET_LANG): Result<Translation> = 
        translateToArabic(text)
    
    private fun parseTranslation(sourceText: String, json: String): Translation {
        return try {
            val obj = org.json.JSONObject(json)
            val responseData = obj.getJSONObject("responseData")
            val translatedText = responseData.getString("translatedText")
            Translation(sourceText, TARGET_LANG, translatedText)
        } catch (e: Exception) {
            Translation(sourceText, TARGET_LANG, "الترجمة غير متاحة")
        }
    }
}

/**
 * Helper object for clipboard operations
 */
object ClipboardHelper {
    fun copyToClipboard(context: Context, text: String, label: String = "Copied text") {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
    }
}

/**
 * Helper class for Text-to-Speech pronunciation
 */
class TtsHelper(context: Context) {
    
    private var tts: TextToSpeech? = null
    private var isInitialized = false
    
    init {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.US
                isInitialized = true
            }
        }
    }
    
    fun speak(text: String) {
        if (isInitialized) {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "utterance_id")
        }
    }
    
    fun stop() {
        tts?.stop()
    }
    
    fun shutdown() {
        tts?.shutdown()
        tts = null
    }
}

/**
 * Standard interface for all AI Companion providers
 */
interface AiService {
    suspend fun breakDownSelection(selection: String, bookTitle: String, chapterTitle: String): Result<String>
    suspend fun summarizeChapter(selectedText: String, bookTitle: String, chapterTitle: String): Result<String>
    suspend fun explainSymbolism(selectedText: String, bookTitle: String, chapterTitle: String): Result<String>
    suspend fun askQuestion(question: String, selectedText: String, bookTitle: String, chapterTitle: String): Result<String>
    suspend fun translateToArabic(text: String): Result<String>
    suspend fun explainInArabic(text: String, bookTitle: String, chapterTitle: String): Result<String>
}

/**
 * Factory and provider for AI services based on user preferences
 */
object AiServiceFactory {
    fun getService(context: android.content.Context): AiService {
        val prefs = ReadingPreferences(context)
        return when (prefs.aiProvider) {
            1 -> OpenRouterService
            2 -> OllamaService(prefs.ollamaUrl)
            else -> GroqService(prefs.groqApiKey)
        }
    }
}

/**
 * Service for local AI responses using Ollama
 */
class OllamaService(private val baseUrl: String) : AiService {
    
    private val model = "nous-hermes"

    override suspend fun breakDownSelection(selection: String, bookTitle: String, chapterTitle: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val prompt = """
                You are a literary expert helping a non-native reader.
                Book: "$bookTitle", Chapter: "$chapterTitle".
                
                Explain this phrase in 2-3 sentences:
                "$selection"
                
                Be concise. No bullet points or formatting.
            """.trimIndent()
            val response = makeRequest(prompt)
            Result.success(response)
        } catch (e: Exception) { Result.failure(e) }
    }

    override suspend fun summarizeChapter(selectedText: String, bookTitle: String, chapterTitle: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val prompt = "Summarize chapter \"$chapterTitle\" from the book \"$bookTitle\" in clear, concise English. Use this reference text: \"$selectedText\""
            val response = makeRequest(prompt)
            Result.success(response)
        } catch (e: Exception) { Result.failure(e) }
    }

    override suspend fun explainSymbolism(selectedText: String, bookTitle: String, chapterTitle: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val prompt = """
                You are a helpful reading companion. Speak in clear, friendly English.
                CONTEXT: Book: "$bookTitle", Chapter: "$chapterTitle".
                TEXT: "$selectedText"
                TASK:
                1. MEANING: Explain the hidden symbolism in one concise sentence.
                2. INSIGHT: Provide a modern, relatable analogy.
                FORMAT:
                **Meaning:** [Symbolism explanation]
                **Insight:** [Relatable analogy]
            """.trimIndent()
            val response = makeRequest(prompt)
            Result.success(response)
        } catch (e: Exception) { Result.failure(e) }
    }

    override suspend fun askQuestion(question: String, selectedText: String, bookTitle: String, chapterTitle: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val prompt = "As a reading companion, the book is \"$bookTitle\" and the chapter is \"$chapterTitle\". The text: \"$selectedText\". Answer this question in friendly, clear English: $question"
            val response = makeRequest(prompt)
            Result.success(response)
        } catch (e: Exception) { Result.failure(e) }
    }

    override suspend fun translateToArabic(text: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val prompt = """
                You are a professional translator. Translate the following English text to Modern Standard Arabic (العربية الفصحى).
                Provide ONLY the Arabic translation, nothing else. Be accurate and preserve the original meaning.
                
                Text to translate: "$text"
            """.trimIndent()
            val response = makeRequest(prompt)
            Result.success(response)
        } catch (e: Exception) { Result.failure(e) }
    }

    override suspend fun explainInArabic(text: String, bookTitle: String, chapterTitle: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val prompt = """
                You are a literary expert. Explain the following text in clear, simple Arabic (العربية).
                Book: "$bookTitle", Chapter: "$chapterTitle".
                
                Text to explain: "$text"
                
                Provide the explanation directly in Arabic.
            """.trimIndent()
            val response = makeRequest(prompt)
            Result.success(response)
        } catch (e: Exception) { Result.failure(e) }
    }

    private fun makeRequest(prompt: String): String {
        return try {
            val url = URL("${baseUrl.trimEnd('/')}/api/generate")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true
            connection.connectTimeout = 60000
            connection.readTimeout = 60000

            val requestBody = JSONObject().apply {
                put("model", model)
                put("prompt", prompt)
                put("stream", false)
            }.toString()

            connection.outputStream.bufferedWriter().use { it.write(requestBody) }

            if (connection.responseCode == 200) {
                val response = connection.inputStream.bufferedReader().readText()
                val obj = JSONObject(response)
                obj.getString("response").trim()
            } else {
                "Ollama Error ${connection.responseCode}: ${connection.errorStream?.bufferedReader()?.readText()}"
            }
        } catch (e: Exception) {
            "Ollama connection failed. Is it running? Error: ${e.message}"
        }
    }
}

/**
 * Service for AI responses using Groq API
 */
class GroqService(private val customApiKey: String?) : AiService {
    
    private val API_URL = "https://api.groq.com/openai/v1/chat/completions"
    private val DEFAULT_KEY = "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" // User must provide their own key in settings
    private val API_KEY = if (customApiKey.isNullOrBlank()) DEFAULT_KEY else customApiKey

    override suspend fun breakDownSelection(selection: String, bookTitle: String, chapterTitle: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val prompt = """
                You are a literary expert helping a non-native reader.
                Book: "$bookTitle", Chapter: "$chapterTitle".
                
                Explain this phrase in 2-3 sentences:
                "$selection"
                
                Be concise. No bullet points or formatting.
            """.trimIndent()
            if (API_KEY.isBlank()) return@withContext Result.failure(Exception("API Key not set"))
            Result.success(makeRequest(prompt))
        } catch (e: Exception) { Result.failure(e) }
    }

    override suspend fun summarizeChapter(selectedText: String, bookTitle: String, chapterTitle: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val prompt = "Summarize chapter \"$chapterTitle\" from the book \"$bookTitle\" in clear, concise English. Reference text: \"$selectedText\"."
            Result.success(makeRequest(prompt))
        } catch (e: Exception) { Result.failure(e) }
    }

    override suspend fun explainSymbolism(selectedText: String, bookTitle: String, chapterTitle: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val prompt = """
                You are a helpful reading companion. Speak in clear, friendly English.
                CONTEXT: Book: "$bookTitle", Chapter: "$chapterTitle".
                TEXT: "$selectedText"
                TASK:
                1. MEANING: Explain hidden symbolism concisely.
                2. INSIGHT: Provide a modern, relatable analogy.
                FORMAT:
                **Meaning:** [Explanation]
                **Insight:** [Analogy]
            """.trimIndent()
            Result.success(makeRequest(prompt))
        } catch (e: Exception) { Result.failure(e) }
    }

    override suspend fun askQuestion(question: String, selectedText: String, bookTitle: String, chapterTitle: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val prompt = "Book: \"$bookTitle\", Chapter: \"$chapterTitle\". Text: \"$selectedText\". Answer this question in friendly, clear English: $question"
            Result.success(makeRequest(prompt))
        } catch (e: Exception) { Result.failure(e) }
    }

    override suspend fun translateToArabic(text: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val prompt = """
                You are a professional translator. Translate the following English text to Modern Standard Arabic (العربية الفصحى).
                Provide ONLY the Arabic translation, nothing else. Be accurate and preserve the original meaning.
                
                Text to translate: "$text"
            """.trimIndent()
            Result.success(makeRequest(prompt))
        } catch (e: Exception) { Result.failure(e) }
    }

    override suspend fun explainInArabic(text: String, bookTitle: String, chapterTitle: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val prompt = """
                You are a literary expert. Explain the following text in clear, simple Arabic (العربية).
                Book: "$bookTitle", Chapter: "$chapterTitle".
                
                Text to explain: "$text"
                
                Provide the explanation directly in Arabic.
            """.trimIndent()
            Result.success(makeRequest(prompt))
        } catch (e: Exception) { Result.failure(e) }
    }

    private fun makeRequest(prompt: String): String {
        val url = URL(API_URL)
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.setRequestProperty("Content-Type", "application/json")
        connection.setRequestProperty("Authorization", "Bearer $API_KEY")
        connection.doOutput = true
        
        val requestBody = JSONObject().apply {
            put("model", "llama-3.3-70b-versatile")
            put("messages", JSONArray().apply {
                put(JSONObject().apply { put("role", "system"); put("content", "You are a helpful reading companion.") })
                put(JSONObject().apply { put("role", "user"); put("content", prompt) })
            })
        }.toString()

        connection.outputStream.bufferedWriter().use { it.write(requestBody) }

        return if (connection.responseCode == 200) {
            val response = connection.inputStream.bufferedReader().readText()
            val obj = JSONObject(response)
            obj.getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content")
        } else {
            "Groq API Error ${connection.responseCode}"
        }
    }
}

/**
 * Service for AI responses using OpenRouter API
 */
object OpenRouterService : AiService {
    
    private const val API_URL = "https://openrouter.ai/api/v1/chat/completions"
    private const val API_KEY = "" // Replace with valid key or load from config

    override suspend fun breakDownSelection(selection: String, bookTitle: String, chapterTitle: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val prompt = """
                You are a literary expert helping a non-native reader.
                Book: "$bookTitle", Chapter: "$chapterTitle".
                
                Explain this phrase in 2-3 sentences:
                "$selection"
                
                Be concise. No bullet points or formatting.
            """.trimIndent()
            Result.success(makeRequest(prompt))
        } catch (e: Exception) { Result.failure(e) }
    }

    override suspend fun summarizeChapter(selectedText: String, bookTitle: String, chapterTitle: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val prompt = "Summarize chapter \"$chapterTitle\" from \"$bookTitle\" in clear, concise English. Reference: \"$selectedText\"."
            Result.success(makeRequest(prompt))
        } catch (e: Exception) { Result.failure(e) }
    }

    override suspend fun explainSymbolism(selectedText: String, bookTitle: String, chapterTitle: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val prompt = """
                You are a helpful reading companion. Speak in clear, friendly English.
                Analyze symbolism in "$bookTitle": "$selectedText".
                FORMAT:
                **Meaning:** [Symbolism]
                **Insight:** [Modern Analogy]
            """.trimIndent()
            Result.success(makeRequest(prompt))
        } catch (e: Exception) { Result.failure(e) }
    }

    override suspend fun askQuestion(question: String, selectedText: String, bookTitle: String, chapterTitle: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val prompt = "As a reading companion, book: \"$bookTitle\", chapter: \"$chapterTitle\". Text: \"$selectedText\". Answer in friendly, clear English: $question"
            Result.success(makeRequest(prompt))
        } catch (e: Exception) { Result.failure(e) }
    }

    override suspend fun translateToArabic(text: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val prompt = """
                You are a professional translator. Translate the following English text to Modern Standard Arabic (العربية الفصحى).
                Provide ONLY the Arabic translation, nothing else. Be accurate and preserve the original meaning.
                
                Text to translate: "$text"
            """.trimIndent()
            Result.success(makeRequest(prompt))
        } catch (e: Exception) { Result.failure(e) }
    }

    override suspend fun explainInArabic(text: String, bookTitle: String, chapterTitle: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val prompt = """
                You are a literary expert. Explain the following text in clear, simple Arabic (العربية).
                Book: "$bookTitle", Chapter: "$chapterTitle".
                
                Text to explain: "$text"
                
                Provide the explanation directly in Arabic.
            """.trimIndent()
            Result.success(makeRequest(prompt))
        } catch (e: Exception) { Result.failure(e) }
    }

    private fun makeRequest(prompt: String): String {
        val url = URL(API_URL)
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.setRequestProperty("Content-Type", "application/json")
        connection.setRequestProperty("Authorization", "Bearer $API_KEY")
        connection.setRequestProperty("HTTP-Referer", "https://github.com/readium/kotlin-toolkit")
        connection.setRequestProperty("X-Title", "Eqraa Reader")
        connection.doOutput = true
        
        val requestBody = JSONObject().apply {
            put("model", "mistralai/mistral-7b-instruct:free")
            put("messages", JSONArray().apply {
                put(JSONObject().apply { put("role", "system"); put("content", "You are a helpful reading companion.") })
                put(JSONObject().apply { put("role", "user"); put("content", prompt) })
            })
        }.toString()

        connection.outputStream.bufferedWriter().use { it.write(requestBody) }

        return if (connection.responseCode == 200) {
            val response = connection.inputStream.bufferedReader().readText()
            val obj = JSONObject(response)
            obj.getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content")
        } else {
            "OpenRouter Error ${connection.responseCode}: ${connection.errorStream?.bufferedReader()?.readText()}"
        }
    }
}
