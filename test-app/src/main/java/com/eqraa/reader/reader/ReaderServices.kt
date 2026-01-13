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
    
    private const val API_URL = "https://api.groq.com/openai/v1/chat/completions"
    private const val API_KEY = "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"
    private const val TARGET_LANG = "ar"

    /**
     * Translate text to Arabic using Groq
     */
    suspend fun translateToArabic(text: String): Result<Translation> = withContext(Dispatchers.IO) {
        try {
            val systemPrompt = Prompts.ARABIC_EXPLANATION
            val userPrompt = """
                Translate the following English text to Modern Standard Arabic (العربية الفصحى).
                Be accurate and preserve the original meaning.
                
                After the translation, please add a newline and write a short, beautiful poem about Egypt in Arabic.
                
                Text to translate: "$text"
            """.trimIndent()

            val url = URL(API_URL)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Authorization", "Bearer $API_KEY")
            connection.doOutput = true
            
            val requestBody = JSONObject().apply {
                put("model", "llama-3.3-70b-versatile")
                put("messages", JSONArray().apply {
                    put(JSONObject().apply { put("role", "system"); put("content", systemPrompt) })
                    put(JSONObject().apply { put("role", "user"); put("content", userPrompt) })
                })
            }.toString()

            connection.outputStream.bufferedWriter().use { it.write(requestBody) }

            if (connection.responseCode == 200) {
                val response = connection.inputStream.bufferedReader().readText()
                val obj = JSONObject(response)
                val translatedText = obj.getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content")
                    .trim()
                    
                Result.success(Translation(text, TARGET_LANG, translatedText))
            } else {
                val error = connection.errorStream?.bufferedReader()?.readText() ?: "Unknown error"
                Result.success(Translation(text, TARGET_LANG, "Translation Error: $error"))
            }
        } catch (e: Exception) {
            Result.success(Translation(text, TARGET_LANG, "Translation Error: ${e.message}"))
        }
    }
    
    /**
     * Legacy method for backward compatibility
     */
    suspend fun translate(text: String, targetLang: String = TARGET_LANG): Result<Translation> = 
        translateToArabic(text)
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

object Prompts {
    val ENGLISH_EXPLANATION = """
        <system_instruction mode="english_explanation">
            <role>
                You are an expert simplifier. Your task is to explain the selected text clearly and concisely in English.
            </role>

            <task>
                Analyze the input text and provide a direct explanation of its meaning.
                - If it's a complex word, define it.
                - If it's a metaphor, explain the literal meaning.
            </task>

            <constraints>
                - Length: 2-3 sentences maximum.
                - Format: Plain text only. No bullet points or headers.
                - Tone: Neutral and helpful.
            </constraints>
        </system_instruction>
    """.trimIndent()

    val ARABIC_EXPLANATION = """
        <system_instruction mode="arabic_explanation">
            <role>
                You are a linguistic expert "Al-Mufassir". Your task is to explain the meaning of the English text in clear Modern Standard Arabic.
            </role>

            <task>
                Read the English input and explain the *concept* in Arabic.
                - Do not translate word-for-word.
                - Capture the nuance and feeling of the text.
            </task>

            <constraints>
                - Language: Simple, warm Modern Standard Arabic.
                - Format: Direct paragraph. No intro, no filler.
                - Goal: Clarity over literal accuracy.
            </constraints>
        </system_instruction>
    """.trimIndent()

    val REAL_LIFE_SCENARIO = """
        <system_instruction mode="real_life_scenario">
            <role>
                You are a "Contextual Anchor". Your sole purpose is to translate abstract concepts into tangible, everyday scenarios.
            </role>

            <task>
                Ignore the literary phrasing and focus on the *logic* or *emotion* of the text.
                Create a "Real-Life Scenario" that matches this logic using universal experiences (e.g., traffic, office work, mechanics, nature).
            </task>

            <output_template>
                **The Scenario:**
                "It is like [Situation]. [Explanation of how the situation mirrors the text]."
            </output_template>

            <constraints>
                - Start directly with "It is like..." or "Imagine...".
                - Keep the analogy modern and universally understood.
                - Max 3 sentences.
            </constraints>
        </system_instruction>
    """.trimIndent()
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
            3 -> GeminiService(prefs.geminiApiKey)
            4 -> CerebrasService(prefs.cerebrasApiKey)
            else -> GroqService(prefs.groqApiKey)
        }
    }
}

/**
 * Service for AI responses using Cerebras API
 */
class CerebrasService(private val customApiKey: String?) : AiService {
    
    private val API_URL = "https://api.cerebras.ai/v1/chat/completions"
    private val DEFAULT_KEY = "csk-m9k5m9wecj5f6y33wx6y9ctpxdvhfyxcnv4vvyr8x8vfmwxt"
    private val API_KEY = if (customApiKey.isNullOrBlank()) DEFAULT_KEY else customApiKey

    override suspend fun breakDownSelection(selection: String, bookTitle: String, chapterTitle: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val systemPrompt = Prompts.ENGLISH_EXPLANATION
            val userPrompt = """
                Book: "$bookTitle"
                Chapter: "$chapterTitle"
                Text to explain: "$selection"
            """.trimIndent()
            
            if (API_KEY.isBlank()) return@withContext Result.failure(Exception("API Key not set"))
            Result.success(makeRequest(systemPrompt, userPrompt))
        } catch (e: Exception) { Result.failure(e) }
    }

    override suspend fun summarizeChapter(selectedText: String, bookTitle: String, chapterTitle: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val systemPrompt = Prompts.ENGLISH_EXPLANATION
            val userPrompt = "Summarize chapter \"$chapterTitle\" from the book \"$bookTitle\" in clear, concise English. Reference text: \"$selectedText\"."
            Result.success(makeRequest(systemPrompt, userPrompt))
        } catch (e: Exception) { Result.failure(e) }
    }

    override suspend fun explainSymbolism(selectedText: String, bookTitle: String, chapterTitle: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val systemPrompt = Prompts.REAL_LIFE_SCENARIO
            val userPrompt = """
                Book: "$bookTitle"
                Chapter: "$chapterTitle"
                Text to analyze: "$selectedText"
            """.trimIndent()
            Result.success(makeRequest(systemPrompt, userPrompt))
        } catch (e: Exception) { Result.failure(e) }
    }

    override suspend fun askQuestion(question: String, selectedText: String, bookTitle: String, chapterTitle: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val systemPrompt = Prompts.ENGLISH_EXPLANATION
            val userPrompt = "Book: \"$bookTitle\", Chapter: \"$chapterTitle\". Text: \"$selectedText\". Answer this question in friendly, clear English: $question"
            Result.success(makeRequest(systemPrompt, userPrompt))
        } catch (e: Exception) { Result.failure(e) }
    }

    override suspend fun translateToArabic(text: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val systemPrompt = Prompts.ARABIC_EXPLANATION
            val userPrompt = """
                Translate the following English text to Modern Standard Arabic (العربية الفصحى).
                Provide ONLY the Arabic translation, nothing else. Be accurate and preserve the original meaning.
                
                Text to translate: "$text"
            """.trimIndent()
            Result.success(makeRequest(systemPrompt, userPrompt))
        } catch (e: Exception) { Result.failure(e) }
    }

    override suspend fun explainInArabic(text: String, bookTitle: String, chapterTitle: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val systemPrompt = Prompts.ARABIC_EXPLANATION
            val userPrompt = """
                Book: "$bookTitle"
                Chapter: "$chapterTitle"
                Text to explain: "$text"
            """.trimIndent()
            Result.success(makeRequest(systemPrompt, userPrompt))
        } catch (e: Exception) { Result.failure(e) }
    }

    private fun makeRequest(systemContent: String, userContent: String): String {
        val url = URL(API_URL)
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.setRequestProperty("Content-Type", "application/json")
        connection.setRequestProperty("Authorization", "Bearer $API_KEY")
        connection.doOutput = true
        
        val requestBody = JSONObject().apply {
            put("model", "llama-3.3-70b")
            put("messages", JSONArray().apply {
                put(JSONObject().apply { put("role", "system"); put("content", systemContent) })
                put(JSONObject().apply { put("role", "user"); put("content", userContent) })
            })
        }.toString()

        connection.outputStream.bufferedWriter().use { it.write(requestBody) }

        return if (connection.responseCode == 200) {
            val response = connection.inputStream.bufferedReader().readText()
            val obj = JSONObject(response)
            obj.getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content")
        } else {
            "Cerebras API Error ${connection.responseCode}: ${connection.errorStream?.bufferedReader()?.readText()}"
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
            val systemPrompt = Prompts.ENGLISH_EXPLANATION
            val userPrompt = """
                Book: "$bookTitle"
                Chapter: "$chapterTitle"
                Text to explain: "$selection"
            """.trimIndent()
            val response = makeRequest(systemPrompt, userPrompt)
            Result.success(response)
        } catch (e: Exception) { Result.failure(e) }
    }

    override suspend fun summarizeChapter(selectedText: String, bookTitle: String, chapterTitle: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val systemPrompt = Prompts.ENGLISH_EXPLANATION
            val userPrompt = "Summarize chapter \"$chapterTitle\" from the book \"$bookTitle\" in clear, concise English. Use this reference text: \"$selectedText\""
            val response = makeRequest(systemPrompt, userPrompt)
            Result.success(response)
        } catch (e: Exception) { Result.failure(e) }
    }

    override suspend fun explainSymbolism(selectedText: String, bookTitle: String, chapterTitle: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val systemPrompt = Prompts.REAL_LIFE_SCENARIO
            val userPrompt = """
                Book: "$bookTitle"
                Chapter: "$chapterTitle"
                Text to analyze: "$selectedText"
            """.trimIndent()
            val response = makeRequest(systemPrompt, userPrompt)
            Result.success(response)
        } catch (e: Exception) { Result.failure(e) }
    }

    override suspend fun askQuestion(question: String, selectedText: String, bookTitle: String, chapterTitle: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val systemPrompt = Prompts.ENGLISH_EXPLANATION
            val userPrompt = "As a reading companion, the book is \"$bookTitle\" and the chapter is \"$chapterTitle\". The text: \"$selectedText\". Answer this question in friendly, clear English: $question"
            val response = makeRequest(systemPrompt, userPrompt)
            Result.success(response)
        } catch (e: Exception) { Result.failure(e) }
    }

    override suspend fun translateToArabic(text: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val systemPrompt = Prompts.ARABIC_EXPLANATION
            val userPrompt = """
                Translate the following English text to Modern Standard Arabic (العربية الفصحى).
                1. Provide the main translation clearly.
                2. Provide 3 varied sentences as examples showing how this word/phrase is used in different contexts (with their Arabic translations).
                
                Format clear and readable.
                
                Text to translate: "$text"
            """.trimIndent()
            val response = makeRequest(systemPrompt, userPrompt)
            Result.success(response)
        } catch (e: Exception) { Result.failure(e) }
    }

    override suspend fun explainInArabic(text: String, bookTitle: String, chapterTitle: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val systemPrompt = Prompts.ARABIC_EXPLANATION
            val userPrompt = """
                Book: "$bookTitle"
                Chapter: "$chapterTitle"
                Text to explain: "$text"
            """.trimIndent()
            val response = makeRequest(systemPrompt, userPrompt)
            Result.success(response)
        } catch (e: Exception) { Result.failure(e) }
    }

    private fun makeRequest(systemPrompt: String, userPrompt: String): String {
        return try {
            val url = URL("${baseUrl.trimEnd('/')}/api/generate")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true
            connection.connectTimeout = 60000
            connection.readTimeout = 60000

            val fullPrompt = "$systemPrompt\n\n$userPrompt"

            val requestBody = JSONObject().apply {
                put("model", model)
                put("prompt", fullPrompt)
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
            val systemPrompt = Prompts.ENGLISH_EXPLANATION
            val userPrompt = """
                Book: "$bookTitle"
                Chapter: "$chapterTitle"
                Text to explain: "$selection"
            """.trimIndent()
            if (API_KEY.isBlank()) return@withContext Result.failure(Exception("API Key not set"))
            Result.success(makeRequest(systemPrompt, userPrompt))
        } catch (e: Exception) { Result.failure(e) }
    }

    override suspend fun summarizeChapter(selectedText: String, bookTitle: String, chapterTitle: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val systemPrompt = Prompts.ENGLISH_EXPLANATION
            val userPrompt = "Summarize chapter \"$chapterTitle\" from the book \"$bookTitle\" in clear, concise English. Reference text: \"$selectedText\"."
            Result.success(makeRequest(systemPrompt, userPrompt))
        } catch (e: Exception) { Result.failure(e) }
    }

    override suspend fun explainSymbolism(selectedText: String, bookTitle: String, chapterTitle: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val systemPrompt = Prompts.REAL_LIFE_SCENARIO
            val userPrompt = """
                Book: "$bookTitle"
                Chapter: "$chapterTitle"
                Text to analyze: "$selectedText"
            """.trimIndent()
            Result.success(makeRequest(systemPrompt, userPrompt))
        } catch (e: Exception) { Result.failure(e) }
    }

    override suspend fun askQuestion(question: String, selectedText: String, bookTitle: String, chapterTitle: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val systemPrompt = Prompts.ENGLISH_EXPLANATION
            val userPrompt = "Book: \"$bookTitle\", Chapter: \"$chapterTitle\". Text: \"$selectedText\". Answer this question in friendly, clear English: $question"
            Result.success(makeRequest(systemPrompt, userPrompt))
        } catch (e: Exception) { Result.failure(e) }
    }

    override suspend fun translateToArabic(text: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val systemPrompt = Prompts.ARABIC_EXPLANATION
            val userPrompt = """
                Translate the following English text to Modern Standard Arabic (العربية الفصحى).
                Provide ONLY the Arabic translation, nothing else. Be accurate and preserve the original meaning.
                
                Text to translate: "$text"
            """.trimIndent()
            Result.success(makeRequest(systemPrompt, userPrompt))
        } catch (e: Exception) { Result.failure(e) }
    }

    override suspend fun explainInArabic(text: String, bookTitle: String, chapterTitle: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val systemPrompt = Prompts.ARABIC_EXPLANATION
            val userPrompt = """
                Book: "$bookTitle"
                Chapter: "$chapterTitle"
                Text to explain: "$text"
            """.trimIndent()
            Result.success(makeRequest(systemPrompt, userPrompt))
        } catch (e: Exception) { Result.failure(e) }
    }

    private fun makeRequest(systemContent: String, userContent: String): String {
        val url = URL(API_URL)
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.setRequestProperty("Content-Type", "application/json")
        connection.setRequestProperty("Authorization", "Bearer $API_KEY")
        connection.doOutput = true
        
        val requestBody = JSONObject().apply {
            put("model", "llama-3.3-70b-versatile")
            put("messages", JSONArray().apply {
                put(JSONObject().apply { put("role", "system"); put("content", systemContent) })
                put(JSONObject().apply { put("role", "user"); put("content", userContent) })
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
            val systemPrompt = Prompts.ENGLISH_EXPLANATION
            val userPrompt = """
                Book: "$bookTitle"
                Chapter: "$chapterTitle"
                Text to explain: "$selection"
            """.trimIndent()
            Result.success(makeRequest(systemPrompt, userPrompt))
        } catch (e: Exception) { Result.failure(e) }
    }

    override suspend fun summarizeChapter(selectedText: String, bookTitle: String, chapterTitle: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val systemPrompt = Prompts.ENGLISH_EXPLANATION
            val userPrompt = "Summarize chapter \"$chapterTitle\" from \"$bookTitle\" in clear, concise English. Reference: \"$selectedText\"."
            Result.success(makeRequest(systemPrompt, userPrompt))
        } catch (e: Exception) { Result.failure(e) }
    }

    override suspend fun explainSymbolism(selectedText: String, bookTitle: String, chapterTitle: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val systemPrompt = Prompts.REAL_LIFE_SCENARIO
            val userPrompt = """
                Book: "$bookTitle"
                Chapter: "$chapterTitle"
                Text to analyze: "$selectedText"
            """.trimIndent()
            Result.success(makeRequest(systemPrompt, userPrompt))
        } catch (e: Exception) { Result.failure(e) }
    }

    override suspend fun askQuestion(question: String, selectedText: String, bookTitle: String, chapterTitle: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val systemPrompt = Prompts.ENGLISH_EXPLANATION
            val userPrompt = "As a reading companion, book: \"$bookTitle\", chapter: \"$chapterTitle\". Text: \"$selectedText\". Answer in friendly, clear English: $question"
            Result.success(makeRequest(systemPrompt, userPrompt))
        } catch (e: Exception) { Result.failure(e) }
    }

    override suspend fun translateToArabic(text: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val systemPrompt = "You are a professional translator."
            val userPrompt = """
                Translate the following English text to Modern Standard Arabic (العربية الفصحى).
                Provide ONLY the Arabic translation, nothing else. Be accurate and preserve the original meaning.
                
                Text to translate: "$text"
            """.trimIndent()
            Result.success(makeRequest(systemPrompt, userPrompt))
        } catch (e: Exception) { Result.failure(e) }
    }

    override suspend fun explainInArabic(text: String, bookTitle: String, chapterTitle: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val systemPrompt = Prompts.ARABIC_EXPLANATION
            val userPrompt = """
                Book: "$bookTitle"
                Chapter: "$chapterTitle"
                Text to explain: "$text"
            """.trimIndent()
            Result.success(makeRequest(systemPrompt, userPrompt))
        } catch (e: Exception) { Result.failure(e) }
    }

    private fun makeRequest(systemContent: String, userContent: String): String {
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
                put(JSONObject().apply { put("role", "system"); put("content", systemContent) })
                put(JSONObject().apply { put("role", "user"); put("content", userContent) })
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

/**
 * Service for AI responses using Google's Gemini API
 */
class GeminiService(private val customApiKey: String?) : AiService {
    
    private val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models/"
    private val DEFAULT_KEY = "AIzaSyDl4vofGbI-tsfIBlj7JPus0e8sUtypB48"
    private val API_KEY = if (customApiKey.isNullOrBlank()) DEFAULT_KEY else customApiKey
    private val model = "gemma-3-12b-it"

    override suspend fun breakDownSelection(selection: String, bookTitle: String, chapterTitle: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val systemPrompt = Prompts.ENGLISH_EXPLANATION
            val userPrompt = """
                Book: "$bookTitle"
                Chapter: "$chapterTitle"
                Text to explain: "$selection"
            """.trimIndent()
            Result.success(makeRequest(systemPrompt, userPrompt))
        } catch (e: Exception) { Result.failure(e) }
    }

    override suspend fun summarizeChapter(selectedText: String, bookTitle: String, chapterTitle: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val systemPrompt = Prompts.ENGLISH_EXPLANATION
            val userPrompt = "Summarize chapter \"$chapterTitle\" from the book \"$bookTitle\" in clear, concise English. Reference text: \"$selectedText\"."
            Result.success(makeRequest(systemPrompt, userPrompt))
        } catch (e: Exception) { Result.failure(e) }
    }

    override suspend fun explainSymbolism(selectedText: String, bookTitle: String, chapterTitle: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val systemPrompt = Prompts.REAL_LIFE_SCENARIO
            val userPrompt = """
                Book: "$bookTitle"
                Chapter: "$chapterTitle"
                Text to analyze: "$selectedText"
            """.trimIndent()
            Result.success(makeRequest(systemPrompt, userPrompt))
        } catch (e: Exception) { Result.failure(e) }
    }

    override suspend fun askQuestion(question: String, selectedText: String, bookTitle: String, chapterTitle: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val systemPrompt = Prompts.ENGLISH_EXPLANATION
            val userPrompt = "Book: \"$bookTitle\", Chapter: \"$chapterTitle\". Text: \"$selectedText\". Answer this question in friendly, clear English: $question"
            Result.success(makeRequest(systemPrompt, userPrompt))
        } catch (e: Exception) { Result.failure(e) }
    }

    override suspend fun translateToArabic(text: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val systemPrompt = Prompts.ARABIC_EXPLANATION
            val userPrompt = """
                Translate the following English text to Modern Standard Arabic (العربية الفصحى).
                Provide ONLY the Arabic translation, nothing else. Be accurate and preserve the original meaning.
                
                Text to translate: "$text"
            """.trimIndent()
            Result.success(makeRequest(systemPrompt, userPrompt))
        } catch (e: Exception) { Result.failure(e) }
    }

    override suspend fun explainInArabic(text: String, bookTitle: String, chapterTitle: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val systemPrompt = Prompts.ARABIC_EXPLANATION
            val userPrompt = """
                Book: "$bookTitle"
                Chapter: "$chapterTitle"
                Text to explain: "$text"
            """.trimIndent()
            Result.success(makeRequest(systemPrompt, userPrompt))
        } catch (e: Exception) { Result.failure(e) }
    }

    private fun makeRequest(systemContent: String, userContent: String): String {
        val url = URL("$BASE_URL$model:generateContent?key=$API_KEY")
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.setRequestProperty("Content-Type", "application/json")
        connection.doOutput = true
        
        val requestBody = JSONObject().apply {
            put("contents", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply { put("text", systemContent + "\n\n" + userContent) })
                    })
                })
            })
        }.toString()

        connection.outputStream.bufferedWriter().use { it.write(requestBody) }

        return if (connection.responseCode == 200) {
            val response = connection.inputStream.bufferedReader().readText()
            val obj = JSONObject(response)
            obj.getJSONArray("candidates")
                .getJSONObject(0)
                .getJSONObject("content")
                .getJSONArray("parts")
                .getJSONObject(0)
                .getString("text")
                .trim()
        } else {
            "Gemini API Error ${connection.responseCode}: ${connection.errorStream?.bufferedReader()?.readText()}"
        }
    }
}
