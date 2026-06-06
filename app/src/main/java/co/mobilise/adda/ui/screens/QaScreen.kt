package co.mobilise.adda.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import co.mobilise.adda.client.HostClient
import co.mobilise.adda.export.ExportFormat
import co.mobilise.adda.export.SessionExporter
import co.mobilise.adda.export.buildSession
import co.mobilise.adda.llm.LlmService
import co.mobilise.adda.state.AppMode
import co.mobilise.adda.state.AppViewModel
import co.mobilise.adda.tts.rememberSpeaker
import co.mobilise.adda.ui.components.AddaLogo
import co.mobilise.adda.ui.qa.ChatRow
import co.mobilise.adda.ui.qa.LangToggle
import co.mobilise.adda.ui.qa.QaViewModel
import co.mobilise.adda.ui.theme.AddaBackground
import co.mobilise.adda.ui.theme.AddaError
import co.mobilise.adda.ui.theme.AddaMuted
import co.mobilise.adda.ui.theme.AddaOnPrimary
import co.mobilise.adda.ui.theme.AddaOutline
import co.mobilise.adda.ui.theme.AddaPrimary
import co.mobilise.adda.ui.theme.AddaSuccess
import co.mobilise.adda.ui.theme.AddaSurface
import co.mobilise.adda.ui.theme.AddaText
import co.mobilise.adda.util.FileUtils
import co.mobilise.adda.util.ImageUtils
import co.mobilise.adda.util.rememberAirplaneMode
import co.mobilise.adda.util.rememberHostReachable
import co.mobilise.adda.voice.rememberVoiceInput
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QaScreen(
    app: AppViewModel,
    onBack: () -> Unit,
    vm: QaViewModel = viewModel(),
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current
    val haptics = LocalHapticFeedback.current
    val speaker = rememberSpeaker()
    val listState = rememberLazyListState()
    val airplane = rememberAirplaneMode()
    val hostReachable = rememberHostReachable(
        baseUrl = app.baseUrl,
        enabled = app.mode == AppMode.CLIENT,
    )

    var input by remember { mutableStateOf("") }
    val asker = app.displayName.ifBlank { if (app.mode == AppMode.HOST) "Host" else "You" }

    // HOST answers on the local LLM; CLIENT sends to the host's /ask. Same UI.
    // Multi-turn: thread keyed by asker so each person's context stays separate.
    val responder: (String) -> kotlinx.coroutines.flow.Flow<String> = { prompt ->
        if (app.mode == AppMode.CLIENT) {
            HostClient.askFlow(baseUrl = app.baseUrl, student = asker, question = prompt)
        } else {
            LlmService.askInThread(threadId = asker, question = prompt)
        }
    }

    fun newChat() {
        vm.clear()
        if (app.mode == AppMode.CLIENT) {
            scope.launch { HostClient.reset(app.baseUrl, asker) }
        } else {
            LlmService.clearThread(asker)
        }
    }

    // ---- export (optional; never touches the core Q&A flow) ----------------
    var exportMenu by remember { mutableStateOf(false) }
    fun export(format: ExportFormat) {
        exportMenu = false
        if (vm.messages.none { it.isAi && it.text.isNotBlank() }) {
            Toast.makeText(context, "Pehle kuch poochho, phir export", Toast.LENGTH_SHORT).show()
            return
        }
        val session = buildSession(vm.messages.toList(), app.subject)
        Toast.makeText(context, "${format.label} ban raha hai…", Toast.LENGTH_SHORT).show()
        scope.launch {
            runCatching {
                val file = withContext(Dispatchers.IO) { SessionExporter.write(context, session, format) }
                SessionExporter.share(context, file, format)
            }.onFailure {
                Toast.makeText(context, "Export fail: ${it.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    val voice = rememberVoiceInput(
        onPartial = { input = it },
        onFinal = { input = it },
        onError = { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() },
    )

    // ---- image / file (multimodal) input ----------------------------------
    var pickedImage by remember { mutableStateOf<Bitmap?>(null) }
    var pickedDocText by remember { mutableStateOf<String?>(null) }
    var pickedFileName by remember { mutableStateOf<String?>(null) }
    var cameraUri by remember { mutableStateOf<Uri?>(null) }

    fun clearAttachment() {
        pickedImage = null
        pickedDocText = null
        pickedFileName = null
    }

    fun loadImage(uri: Uri) {
        scope.launch {
            val bmp = withContext(Dispatchers.IO) { ImageUtils.decodeScaled(context, uri) }
            if (bmp != null) {
                pickedImage = bmp
                pickedDocText = null
                pickedFileName = null
            } else {
                Toast.makeText(context, "Image load nahi hui", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri -> if (uri != null) loadImage(uri) }

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture(),
    ) { success -> if (success) cameraUri?.let { loadImage(it) } }

    val cameraPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            val uri = ImageUtils.newCameraImageUri(context)
            cameraUri = uri
            cameraLauncher.launch(uri)
        } else {
            Toast.makeText(context, "Camera permission chahiye", Toast.LENGTH_SHORT).show()
        }
    }

    fun openCamera() {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) {
            val uri = ImageUtils.newCameraImageUri(context)
            cameraUri = uri
            cameraLauncher.launch(uri)
        } else {
            cameraPermLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    fun openGallery() {
        galleryLauncher.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
        )
    }

    val documentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val mime = FileUtils.mimeOf(context, uri)
        val name = FileUtils.displayName(context, uri)
        when {
            mime.startsWith("image/") -> loadImage(uri)
            mime == "application/pdf" -> scope.launch {
                val bmp = withContext(Dispatchers.IO) { FileUtils.renderPdfFirstPage(context, uri) }
                if (bmp != null) {
                    pickedImage = bmp
                    pickedDocText = null
                    pickedFileName = name
                } else {
                    Toast.makeText(context, "PDF read nahi hui", Toast.LENGTH_SHORT).show()
                }
            }
            mime.startsWith("text/") || mime.isBlank() -> scope.launch {
                val txt = withContext(Dispatchers.IO) { FileUtils.readTextFile(context, uri) }
                if (!txt.isNullOrBlank()) {
                    pickedDocText = txt
                    pickedImage = null
                    pickedFileName = name
                } else {
                    Toast.makeText(context, "Ye file padhi nahi ja saki", Toast.LENGTH_SHORT).show()
                }
            }
            else -> Toast.makeText(
                context, "Ye file type support nahi (PDF/image/text use karo)", Toast.LENGTH_SHORT,
            ).show()
        }
    }

    fun openDocument() {
        runCatching {
            documentLauncher.launch(arrayOf("application/pdf", "text/*", "image/*"))
        }.onFailure { Toast.makeText(context, "File picker nahi khula", Toast.LENGTH_SHORT).show() }
    }

    // HOST runs the vision model locally; CLIENT posts the image (base64) to host.
    val imageResponder: (String, Bitmap) -> kotlinx.coroutines.flow.Flow<String> = { prompt, bmp ->
        if (app.mode == AppMode.CLIENT) {
            HostClient.askImageFlow(app.baseUrl, asker, prompt, ImageUtils.toBase64Jpeg(bmp))
        } else {
            LlmService.askWithImage(prompt, bmp)
        }
    }

    // Auto-scroll to the newest content as it streams in.
    LaunchedEffect(vm.messages.size, vm.messages.lastOrNull()?.text?.length) {
        if (vm.messages.isNotEmpty()) {
            listState.animateScrollToItem(vm.messages.lastIndex)
        }
    }

    // Haptic tick when an answer finishes streaming.
    var wasResponding by remember { mutableStateOf(false) }
    LaunchedEffect(vm.isResponding) {
        if (wasResponding && !vm.isResponding) {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        }
        wasResponding = vm.isResponding
    }

    fun submit() {
        if (vm.isResponding) return
        val img = pickedImage
        val docText = pickedDocText
        when {
            img != null -> {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                vm.sendImage(input, asker, img, imageResponder, fileName = pickedFileName)
                clearAttachment(); input = ""; keyboard?.hide()
            }
            docText != null -> {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                vm.sendDoc(input, pickedFileName ?: "file", docText, asker, responder)
                clearAttachment(); input = ""; keyboard?.hide()
            }
            input.isNotBlank() -> {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                vm.send(input, asker, responder)
                input = ""; keyboard?.hide()
            }
        }
    }

    Scaffold(
        containerColor = AddaBackground,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            app.subject.ifBlank { "Adda Q&A" },
                            style = MaterialTheme.typography.titleLarge,
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val dotColor =
                                if (app.mode == AppMode.CLIENT && !hostReachable) AddaError else AddaSuccess
                            androidx.compose.foundation.Canvas(Modifier.size(7.dp)) {
                                drawCircle(dotColor)
                            }
                            Spacer(Modifier.width(6.dp))
                            val base = if (app.mode == AppMode.CLIENT) {
                                "via ${app.hostName.ifBlank { "host" }}"
                            } else {
                                "on-device"
                            }
                            Text(
                                (if (airplane) "✈ Airplane · " else "Offline · ") + base,
                                style = MaterialTheme.typography.labelMedium,
                                color = AddaMuted,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    Box {
                        IconButton(
                            onClick = { exportMenu = true },
                            enabled = !vm.isResponding && vm.messages.isNotEmpty(),
                        ) {
                            Icon(Icons.Rounded.FileDownload, contentDescription = "Export session", tint = AddaMuted)
                        }
                        DropdownMenu(expanded = exportMenu, onDismissRequest = { exportMenu = false }) {
                            ExportFormat.entries.forEach { fmt ->
                                DropdownMenuItem(text = { Text(fmt.label) }, onClick = { export(fmt) })
                            }
                        }
                    }
                    IconButton(onClick = { newChat() }, enabled = !vm.isResponding) {
                        Icon(
                            Icons.Rounded.RestartAlt,
                            contentDescription = "New chat",
                            tint = AddaMuted,
                        )
                    }
                    LangToggle(lang = vm.lang, onToggle = vm::toggleLang)
                    Spacer(Modifier.width(8.dp))
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = AddaBackground,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
                ),
            )
        },
        bottomBar = {
            Surface(color = AddaBackground) {
                Column(
                    Modifier
                        .navigationBarsPadding()
                        .imePadding(),
                ) {
                    pickedImage?.let { bmp ->
                        ImageAttachmentPreview(
                            bmp = bmp,
                            caption = pickedFileName?.let { "📄 $it · page 1" },
                            onRemove = { clearAttachment() },
                        )
                    }
                    if (pickedImage == null && pickedDocText != null) {
                        DocAttachmentPreview(
                            fileName = pickedFileName ?: "file",
                            onRemove = { clearAttachment() },
                        )
                    }
                    InputBar(
                        value = input,
                        onValueChange = { input = it },
                        onSend = ::submit,
                        onMic = { voice.toggle(vm.lang.sttTag) },
                        listening = voice.listening,
                        sendEnabled = (input.isNotBlank() || pickedImage != null || pickedDocText != null) &&
                            !vm.isResponding,
                        onCamera = { openCamera() },
                        onGallery = { openGallery() },
                        onDocument = { openDocument() },
                        attachEnabled = !vm.isResponding,
                    )
                }
            }
        },
    ) { inner ->
        Column(modifier = Modifier.fillMaxSize().padding(inner)) {
            if (app.mode == AppMode.CLIENT && !hostReachable) {
                ReconnectingBanner()
            }
            if (vm.messages.isEmpty()) {
                EmptyState(modifier = Modifier.weight(1f).fillMaxSize())
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f).fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    items(vm.messages, key = { it.id }) { msg ->
                        ChatRow(
                            message = msg,
                            speakingId = speaker.speakingId,
                            onSpeak = { speaker.toggle(msg.id.toString(), msg.text) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ReconnectingBanner() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(AddaError.copy(alpha = 0.14f))
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        CircularProgressIndicator(color = AddaError, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
        Text(
            "Host se reconnect ho raha hai…",
            style = MaterialTheme.typography.labelMedium,
            color = AddaError,
        )
    }
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Box(modifier = modifier.padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            AddaLogo(size = 72)
            Spacer(Modifier.height(20.dp))
            Text(
                "Apna pehla doubt poochho",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Type ya mic se — answer fully on-device aayega, bina internet.",
                style = MaterialTheme.typography.bodyMedium,
                color = AddaMuted,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun ImageAttachmentPreview(bmp: Bitmap, onRemove: () -> Unit, caption: String? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 14.dp, end = 14.dp, top = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = "Attached image",
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(56.dp).clip(RoundedCornerShape(12.dp)),
            )
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(2.dp)
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(AddaBackground.copy(alpha = 0.75f))
                    .clickable { onRemove() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Close, "Remove", tint = AddaText, modifier = Modifier.size(12.dp))
            }
        }
        Spacer(Modifier.width(10.dp))
        Text(
            caption ?: "Image attached · sawaal type karo ya seedha bhejo",
            style = MaterialTheme.typography.labelMedium,
            color = AddaMuted,
        )
    }
}

@Composable
private fun DocAttachmentPreview(fileName: String, onRemove: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 14.dp, end = 14.dp, top = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(AddaSurface),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Description, contentDescription = null, tint = AddaPrimary, modifier = Modifier.size(20.dp))
        }
        Text(
            fileName,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
            maxLines = 1,
        )
        Icon(
            Icons.Rounded.Close,
            contentDescription = "Remove file",
            tint = AddaMuted,
            modifier = Modifier.size(18.dp).clickable { onRemove() },
        )
    }
}

@Composable
private fun InputBar(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    onMic: () -> Unit,
    listening: Boolean,
    sendEnabled: Boolean,
    onCamera: () -> Unit,
    onGallery: () -> Unit,
    onDocument: () -> Unit,
    attachEnabled: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        // Attach image (camera / gallery)
        Box {
            var menuOpen by remember { mutableStateOf(false) }
            IconButton(
                onClick = { menuOpen = true },
                enabled = attachEnabled,
                modifier = Modifier.size(44.dp),
            ) {
                Icon(Icons.Rounded.AddPhotoAlternate, contentDescription = "Attach image", tint = AddaMuted)
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text("Camera") },
                    leadingIcon = { Icon(Icons.Rounded.PhotoCamera, null) },
                    onClick = { menuOpen = false; onCamera() },
                )
                DropdownMenuItem(
                    text = { Text("Gallery") },
                    leadingIcon = { Icon(Icons.Rounded.Image, null) },
                    onClick = { menuOpen = false; onGallery() },
                )
                DropdownMenuItem(
                    text = { Text("Document (PDF/text)") },
                    leadingIcon = { Icon(Icons.Rounded.Description, null) },
                    onClick = { menuOpen = false; onDocument() },
                )
            }
        }

        // Mic
        IconButton(onClick = onMic, modifier = Modifier.size(44.dp)) {
            Icon(
                imageVector = if (listening) Icons.Rounded.Stop else Icons.Rounded.Mic,
                contentDescription = if (listening) "Stop" else "Speak",
                tint = if (listening) AddaPrimary else AddaMuted,
            )
        }

        OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text("Ask a doubt…", color = AddaMuted) },
                shape = RoundedCornerShape(24.dp),
                maxLines = 4,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { onSend() }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = AddaSurface,
                    unfocusedContainerColor = AddaSurface,
                    focusedBorderColor = AddaPrimary,
                    unfocusedBorderColor = AddaOutline,
                    cursorColor = AddaPrimary,
                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                ),
            )

            // Send (amber circle)
            val sendColor = if (sendEnabled) AddaPrimary else AddaOutline
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(sendColor)
                    .clickable(enabled = sendEnabled, onClick = onSend),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.AutoMirrored.Rounded.Send,
                    contentDescription = "Send",
                    tint = if (sendEnabled) AddaOnPrimary else AddaMuted,
                    modifier = Modifier.size(20.dp),
                )
            }
    }
}
