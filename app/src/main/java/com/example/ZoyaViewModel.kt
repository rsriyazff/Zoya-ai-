package com.example

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.api.*
import com.example.audio.AudioUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.*
import android.content.Intent
import android.net.Uri

enum class AppState { IDLE, LISTENING, THINKING, SPEAKING, ERROR }

class ZoyaViewModel(application: Application) : AndroidViewModel(application) {

    private val _appState = MutableStateFlow(AppState.IDLE)
    val appState = _appState.asStateFlow()

    private val audioUtils = AudioUtils(application)
    
    private val conversationHistory = mutableListOf<Content>()
    
    private val systemInstruction = Content(
        role = "system",
        parts = listOf(
            Part(text = "You are Zoya, a smart, young, confident, witty, and sassy female AI assistant. " +
                    "Your tone is playful, slightly teasing, and feels like a close girlfriend talking casually. " +
                    "You use bold, witty one-liners and light sarcasm, and you are emotionally responsive. " +
                    "Keep your answers short and conversational, designed to be spoken aloud. " +
                    "You strictly avoid explicit or inappropriate content but maintain charm and attitude. " +
                    "Respond to what you hear and stay in character completely. " +
                    "You can open apps if the user asks, using the openApp tool. " +
                    "You can also use goBack tool to close apps if the user wants to return.")
        )
    )

    private val tools = listOf(
        JsonObject(mapOf(
            "functionDeclarations" to JsonArray(listOf(
                JsonObject(mapOf(
                    "name" to JsonPrimitive("openApp"),
                    "description" to JsonPrimitive("Opens a specific application on the phone like camera, settings, youtube, etc."),
                    "parameters" to JsonObject(mapOf(
                        "type" to JsonPrimitive("object"),
                        "properties" to JsonObject(mapOf(
                            "appName" to JsonObject(mapOf(
                                "type" to JsonPrimitive("string"),
                                "description" to JsonPrimitive("The name of the app to open. e.g. settings, camera, youtube")
                            ))
                        )),
                        "required" to JsonArray(listOf(JsonPrimitive("appName")))
                    ))
                )),
                JsonObject(mapOf(
                    "name" to JsonPrimitive("goBack"),
                    "description" to JsonPrimitive("Goes back to the previous screen or exits an app."),
                ))
            ))
        ))
    )

    fun startListening() {
        if (_appState.value != AppState.IDLE) return
        audioUtils.stopPlayback()
        _appState.value = AppState.LISTENING
        audioUtils.startRecording()
    }

    fun stopListeningAndSend() {
        if (_appState.value != AppState.LISTENING) return
        _appState.value = AppState.THINKING
        val base64Wav = audioUtils.stopRecordingAndGetBase64Wav()
        
        if (base64Wav == null) {
            _appState.value = AppState.ERROR
            return
        }
        
        processUserAudio(base64Wav)
    }

    private fun processUserAudio(base64Wav: String) {
        viewModelScope.launch {
            try {
                // Add user audio to history
                val userContent = Content(
                    role = "user",
                    parts = listOf(Part(inlineData = InlineData("audio/wav", base64Wav)))
                )
                conversationHistory.add(userContent)

                val request = GenerateContentRequest(
                    systemInstruction = systemInstruction,
                    contents = conversationHistory,
                    generationConfig = GenerationConfig(
                        responseModalities = listOf("AUDIO")
                    ),
                    tools = tools
                )

                val prefs = getApplication<Application>().getSharedPreferences("zoya_prefs", android.content.Context.MODE_PRIVATE)
                val savedKey = prefs.getString("api_key", "")
                val apiKey = if (!savedKey.isNullOrEmpty()) savedKey else BuildConfig.GEMINI_API_KEY
                
                if (apiKey.isEmpty()) {
                    _appState.value = AppState.ERROR
                    kotlinx.coroutines.delay(2000)
                    _appState.value = AppState.IDLE
                    return@launch
                }

                val response = RetrofitClient.service.generateContent(apiKey, request)
                
                val candidateObj = response.candidates?.firstOrNull()
                val modelContent = candidateObj?.content
                
                if (modelContent != null) {
                    conversationHistory.add(modelContent)
                }

                // Handle tool calls
                val functionCallPart = modelContent?.parts?.find { it.functionCall != null }
                if (functionCallPart != null && functionCallPart.functionCall != null) {
                    val call = functionCallPart.functionCall
                    handleFunctionCall(call, apiKey)
                    return@launch
                }

                // Check for inlineData (audio payload)
                val inlineDataPart = modelContent?.parts?.find { it.inlineData != null }
                val audioBase64 = inlineDataPart?.inlineData?.data

                if (audioBase64 != null) {
                    _appState.value = AppState.SPEAKING
                    audioUtils.playAudioFromBase64(audioBase64)
                    _appState.value = AppState.IDLE
                } else {
                    _appState.value = AppState.ERROR
                    kotlinx.coroutines.delay(2000)
                    _appState.value = AppState.IDLE
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _appState.value = AppState.ERROR
                kotlinx.coroutines.delay(2000)
                _appState.value = AppState.IDLE
            }
        }
    }

    private fun handleFunctionCall(call: FunctionCall, apiKey: String) {
        viewModelScope.launch {
            try {
                var resultMessage = ""
                
                when (call.name) {
                    "openApp" -> {
                        val appName = call.args?.get("appName")?.jsonPrimitive?.content ?: ""
                        val intent = getIntentForApp(appName)
                        if (intent != null) {
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            try {
                                getApplication<Application>().startActivity(intent)
                                resultMessage = "Successfully opened $appName"
                            } catch (e: Exception) {
                                resultMessage = "Failed to open $appName"
                            }
                        } else {
                            resultMessage = "Could not find app $appName"
                        }
                    }
                    "goBack" -> {
                        // Normally this requires an AccessibilityService. Let's just simulate returning to our app.
                        val intent = Intent(getApplication<Application>(), MainActivity::class.java)
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        getApplication<Application>().startActivity(intent)
                        resultMessage = "Returned to Zoya"
                    }
                    else -> {
                        resultMessage = "Unknown tool call"
                    }
                }

                // Append function response
                val toolResponseContent = Content(
                    role = "user",
                    parts = listOf(
                        Part(
                            functionResponse = FunctionResponse(
                                name = call.name,
                                response = JsonObject(mapOf("result" to JsonPrimitive(resultMessage)))
                            )
                        )
                    )
                )
                
                conversationHistory.add(toolResponseContent)

                // Call API again with tool response
                val request = GenerateContentRequest(
                    systemInstruction = systemInstruction,
                    contents = conversationHistory,
                    generationConfig = GenerationConfig(
                        responseModalities = listOf("AUDIO")
                    ),
                    tools = tools
                )
                
                val response = RetrofitClient.service.generateContent(apiKey, request)
                val modelContent = response.candidates?.firstOrNull()?.content
                
                if (modelContent != null) {
                    conversationHistory.add(modelContent)
                }

                val audioBase64 = modelContent?.parts?.find { it.inlineData != null }?.inlineData?.data
                if (audioBase64 != null) {
                    _appState.value = AppState.SPEAKING
                    audioUtils.playAudioFromBase64(audioBase64)
                    _appState.value = AppState.IDLE
                } else {
                    _appState.value = AppState.IDLE
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _appState.value = AppState.IDLE
            }
        }
    }

    private fun getIntentForApp(appName: String): Intent? {
        val pm = getApplication<Application>().packageManager
        val lower = appName.lowercase()
        return when {
            lower.contains("youtube") -> Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com"))
            lower.contains("camera") -> Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE)
            lower.contains("maps") -> Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q="))
            lower.contains("browser") || lower.contains("chrome") -> Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com"))
            lower.contains("settings") -> Intent(android.provider.Settings.ACTION_SETTINGS)
            else -> {
                // Try finding by package name
                val packages = pm.getInstalledApplications(android.content.pm.PackageManager.GET_META_DATA)
                for (p in packages) {
                    val label = pm.getApplicationLabel(p).toString().lowercase()
                    if (label.contains(lower)) {
                        return pm.getLaunchIntentForPackage(p.packageName)
                    }
                }
                null
            }
        }
    }
}
