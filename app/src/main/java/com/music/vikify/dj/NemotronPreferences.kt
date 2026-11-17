package com.music.vikify.dj

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.vikify.data.settings.AppSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.json.JSONObject

/** Connection controls never send music requests or change the playback queue. */
@Composable
internal fun NemotronPreferencesContent(modifier: Modifier = Modifier) {
    val savedKey by AppSettings.nvidiaApiKey.collectAsStateWithLifecycle()
    val savedModel by AppSettings.nvidiaModel.collectAsStateWithLifecycle()
    val clientStatus by NemotronClient.status.collectAsStateWithLifecycle()
    var draftKey by remember { mutableStateOf(savedKey) }
    var draftModel by remember { mutableStateOf(savedModel.ifBlank { AppSettings.NVIDIA_DEFAULT_MODEL }) }
    var showKey by remember { mutableStateOf(false) }
    var testing by remember { mutableStateOf(false) }
    var feedback by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val key = draftKey.trim()
    val model = draftModel.trim()
    val dirty = key != savedKey.trim() || model != savedModel.ifBlank { AppSettings.NVIDIA_DEFAULT_MODEL }.trim()
    val configured = NemotronClient.isConfigured()

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("NVIDIA Nemotron", style = MaterialTheme.typography.titleMedium)
        Text("Connect with your NVIDIA API key. Your app talks directly to NVIDIA; no server setup is needed.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(if (configured) "API key configured" else "Add your API key to enable Nemotron conversations",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(
            value = draftKey,
            onValueChange = { draftKey = it; feedback = null },
            label = { Text("NVIDIA API key") },
            placeholder = { Text(if (configured && savedKey.isBlank()) "Built-in key configured" else "Paste your API key") },
            singleLine = true,
            enabled = !testing,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
            visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = { TextButton(onClick = { showKey = !showKey }, enabled = !testing) {
                Text(if (showKey) "Hide" else "Show")
            } },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = draftModel,
            onValueChange = { draftModel = it.take(200); feedback = null },
            label = { Text("Model") },
            singleLine = true,
            enabled = !testing,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                enabled = !testing && model.isNotBlank() && dirty,
                onClick = {
                    AppSettings.setNvidiaApiKey(key)
                    AppSettings.setNvidiaModel(model)
                    draftKey = key
                    draftModel = model
                    showKey = false
                    feedback = if (NemotronClient.isConfigured()) "Saved. You can test the connection below." else "Saved. Add an API key to connect."
                },
                modifier = Modifier.weight(1f),
            ) { Text("Save") }
            OutlinedButton(
                enabled = !testing && (draftKey.isNotBlank() || savedKey.isNotBlank()),
                onClick = {
                    AppSettings.setNvidiaApiKey("")
                    draftKey = ""
                    showKey = false
                    feedback = if (NemotronClient.isConfigured()) "Personal key cleared. The built-in key is configured." else "API key cleared."
                },
                modifier = Modifier.weight(1f),
            ) { Text("Clear key") }
        }
        OutlinedButton(
            enabled = !testing && !dirty && configured,
            onClick = {
                testing = true
                feedback = null
                scope.launch {
                    try {
                        val response = NemotronClient.json(
                            "Return a JSON object with a short reply field greeting the listener. Do not recommend or play music.",
                            "{\"message\":\"Hello\",\"purpose\":\"connection_test\"}",
                        )
                        val reply = response?.let { runCatching { JSONObject(it).optString("reply").trim() }.getOrNull() }
                        feedback = if (!reply.isNullOrBlank()) "Connected. Nemotron is ready to chat."
                            else NemotronClient.status.value ?: "The connection did not return a usable reply. Check your key and model, then try again."
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        feedback = "Couldn’t connect. Check your connection, API key and model, then try again."
                    } finally {
                        testing = false
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(if (testing) "Connecting…" else "Test connection") }
        val message = if (dirty) "Save your changes before testing." else feedback ?: clientStatus
        message?.let { Text(it, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}
