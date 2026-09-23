package com.montanhajr.bereanbible

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.NavHostController

@Composable
fun BibleReaderScreen(vm: BibleViewModel, navController: NavHostController) {
    val context = LocalContext.current
    val bookId by vm.currentBookId.collectAsState()
    val chapter by vm.currentChapter.collectAsState()
    val targetVerse by vm.targetVerse.collectAsState()
    val appLanguage by vm.settings.appLanguage.collectAsState()
    val isListening by vm.isListening.collectAsState()
    val useFake by vm.useFakeStt.collectAsState()
    val lastHeard by vm.lastHeard.collectAsState()
    val suggestion by vm.suggestion.collectAsState()

    val book = vm.repository.getBook(bookId)
    val bookName = book?.names?.get(appLanguage) ?: book?.name ?: bookId
    val verses = vm.repository.getChapter(bookId, chapter)

    val listState = rememberLazyListState()

    val micPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            vm.setUseFakeStt(false)
            vm.startListening()
        }
    }

    LaunchedEffect(bookId, chapter, targetVerse) {
        if (verses.isNotEmpty() && targetVerse != null) {
            val targetIndex = verses.indexOfFirst { it.verse == targetVerse }.coerceAtLeast(0)
            listState.animateScrollToItem(targetIndex)
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(
                    "$bookName $chapter",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.weight(1f)
                )
                FilledIconButton(
                    onClick = {
                        if (isListening) {
                            vm.stopListening()
                        } else if (useFake) {
                            vm.startListening()
                        } else {
                            val granted = ContextCompat.checkSelfPermission(
                                context, Manifest.permission.RECORD_AUDIO
                            ) == PackageManager.PERMISSION_GRANTED
                            if (granted) vm.startListening() else micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    },
                    colors = if (isListening) {
                        IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError
                        )
                    } else {
                        IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                ) {
                    Icon(
                        if (isListening) Icons.Filled.MicOff else Icons.Filled.Mic,
                        contentDescription = if (isListening) stringResource(R.string.stop_listening) else stringResource(R.string.start_listening)
                    )
                }
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = { navController.navigate(Screen.Books.route) }) {
                    Text(stringResource(R.string.change_book))
                }
            }

            Spacer(Modifier.height(8.dp))

            Row {
                Button(
                    onClick = { if (chapter > 1) vm.openReference(bookId, chapter - 1) },
                    enabled = chapter > 1
                ) { Text(stringResource(R.string.previous_chapter)) }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = { if (book != null && chapter < book.chapters) vm.openReference(bookId, chapter + 1) },
                    enabled = book != null && chapter < book.chapters
                ) { Text(stringResource(R.string.next_chapter)) }
            }

            Spacer(Modifier.height(12.dp))

            if (verses.isEmpty()) {
                Text(
                    stringResource(R.string.placeholder_text),
                    style = MaterialTheme.typography.bodyMedium
                )
            } else {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(bottom = if (isListening) 160.dp else 16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(verses) { verse ->
                        Row(Modifier.padding(vertical = 4.dp)) {
                            Text("${verse.verse}  ", fontWeight = FontWeight.Bold)
                            Text(verse.text)
                        }
                    }
                }
            }
        }

        if (isListening) {
            Card(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
            ) {
                Column(Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.Mic,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.listening_active),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    if (lastHeard.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.heard_label, lastHeard),
                            style = MaterialTheme.typography.bodySmall,
                            fontStyle = FontStyle.Italic,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    suggestion?.let { match ->
                        Spacer(Modifier.height(8.dp))
                        HorizontalDivider()
                        Spacer(Modifier.height(8.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = vm.displayReference(match.reference),
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = stringResource(R.string.snippet_label, match.originalText),
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            Button(onClick = { vm.acceptSuggestion(match) }) {
                                Text(stringResource(R.string.go_to_verse))
                            }
                            Spacer(Modifier.width(4.dp))
                            OutlinedButton(onClick = { vm.ignoreSuggestion() }) {
                                Text(stringResource(R.string.ignore_label))
                            }
                        }
                    } ?: run {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.waiting_reference),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun BookSelectorScreen(vm: BibleViewModel, navController: NavHostController) {
    val appLanguage by vm.settings.appLanguage.collectAsState()
    LazyColumn(Modifier.fillMaxSize().padding(16.dp)) {
        items(BibleData.BOOKS) { book ->
            val bookName = book.names[appLanguage] ?: book.name
            Text(
                bookName,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { navController.navigate(Screen.Chapters.path(book.id)) }
                    .padding(vertical = 12.dp),
                style = MaterialTheme.typography.bodyLarge
            )
            HorizontalDivider()
        }
    }
}

@Composable
fun ChapterSelectorScreen(vm: BibleViewModel, bookId: String, navController: NavHostController) {
    val book = vm.repository.getBook(bookId) ?: return
    val appLanguage by vm.settings.appLanguage.collectAsState()
    val bookName = book.names[appLanguage] ?: book.name

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text(bookName, style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        LazyColumn {
            items((1..book.chapters).toList()) { chapterNumber ->
                Text(
                    stringResource(R.string.chapter_label, chapterNumber),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            vm.openReference(bookId, chapterNumber)
                            navController.navigate(Screen.Bible.route) {
                                popUpTo(Screen.Bible.route) { inclusive = true }
                            }
                        }
                        .padding(vertical = 10.dp)
                )
                HorizontalDivider()
            }
        }
    }
}

@Composable
fun SearchScreen(vm: BibleViewModel) {
    val query by vm.searchQuery.collectAsState()
    val results by vm.searchResults.collectAsState()
    val appLanguage by vm.settings.appLanguage.collectAsState()

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = vm::onSearchQueryChange,
            label = { Text(stringResource(R.string.search_hint)) },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))

        LazyColumn(Modifier.fillMaxSize()) {
            items(results) { verse ->
                val book = vm.repository.getBook(verse.bookId)
                val bookName = book?.names?.get(appLanguage) ?: book?.name ?: verse.bookId
                Text("$bookName ${verse.chapter}:${verse.verse}", fontWeight = FontWeight.Bold)
                Text(verse.text, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(8.dp))
            }
        }

        if (query.length >= 3 && results.isEmpty()) {
            Text(stringResource(R.string.no_results))
        }
    }
}

@Composable
fun ListeningScreen(vm: BibleViewModel) {
    val context = LocalContext.current
    val isListening by vm.isListening.collectAsState()
    val useFake by vm.useFakeStt.collectAsState()
    val suggestion by vm.suggestion.collectAsState()
    val history by vm.sessionManager.history.collectAsState()
    val detectionState by vm.detectionState.collectAsState()
    val lastHeard by vm.lastHeard.collectAsState()
    var fakeText by remember { mutableStateOf("") }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            vm.setUseFakeStt(false)
            vm.startListening()
        }
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text(stringResource(R.string.sermon_mode), style = MaterialTheme.typography.headlineSmall)
        Text(
            stringResource(R.string.audio_privacy_notice),
            style = MaterialTheme.typography.bodySmall
        )
        Spacer(Modifier.height(4.dp))
        Text(stringResource(R.string.state_label, detectionState.name), style = MaterialTheme.typography.labelMedium)

        if (isListening && lastHeard.isNotEmpty()) {
            Card(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Text(
                    text = stringResource(R.string.heard_label, lastHeard),
                    modifier = Modifier.padding(8.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    fontStyle = FontStyle.Italic
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        Button(onClick = {
            if (isListening) {
                vm.stopListening()
            } else if (useFake) {
                vm.startListening()
            } else {
                val granted = ContextCompat.checkSelfPermission(
                    context, Manifest.permission.RECORD_AUDIO
                ) == PackageManager.PERMISSION_GRANTED
                if (granted) vm.startListening() else micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }) {
            Icon(if (isListening) Icons.Filled.MicOff else Icons.Filled.Mic, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(if (isListening) stringResource(R.string.stop_listening) else stringResource(R.string.start_listening))
        }

        Spacer(Modifier.height(16.dp))

        if (useFake) {
            Text(stringResource(R.string.test_input_label),
                style = MaterialTheme.typography.labelLarge)
            OutlinedTextField(
                value = fakeText,
                onValueChange = { fakeText = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.test_input_hint)) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
            )
            Button(
                onClick = { vm.submitFakeTranscript(fakeText) },
                enabled = isListening && fakeText.isNotBlank()
            ) { Text(stringResource(R.string.simulate_speech)) }
        }

        Spacer(Modifier.height(16.dp))

        suggestion?.let { match ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("${vm.displayReference(match.reference)} ${stringResource(R.string.detected_suffix)}", fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.snippet_label, match.originalText), style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(8.dp))
                    Row {
                        Button(onClick = { vm.acceptSuggestion(match) }) { Text(stringResource(R.string.open_passage)) }
                        Spacer(Modifier.width(8.dp))
                        OutlinedButton(onClick = { vm.ignoreSuggestion() }) { Text(stringResource(R.string.ignore_label)) }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        Text(stringResource(R.string.session_history), style = MaterialTheme.typography.titleMedium)
        LazyColumn(Modifier.fillMaxSize()) {
            items(history.reversed()) { entry ->
                val mark = if (entry.accepted) "✓" else "×"
                Text("$mark ${entry.displayText}")
            }
        }
    }
}

@Composable
fun SettingsScreen(vm: BibleViewModel) {
    val navigationMode by vm.settings.navigationMode.collectAsState()
    val appLanguage by vm.settings.appLanguage.collectAsState()
    val useFake by vm.useFakeStt.collectAsState()

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text(stringResource(R.string.settings_label), style = MaterialTheme.typography.headlineSmall)

        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.nav_mode_label), style = MaterialTheme.typography.titleMedium)
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(
                selected = navigationMode == NavigationMode.CONFIRM_BEFORE_OPEN,
                onClick = { vm.settings.setNavigationMode(NavigationMode.CONFIRM_BEFORE_OPEN) }
            )
            Text(stringResource(R.string.confirm_before_open))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(
                selected = navigationMode == NavigationMode.AUTO_OPEN,
                onClick = { vm.settings.setNavigationMode(NavigationMode.AUTO_OPEN) }
            )
            Text(stringResource(R.string.auto_open))
        }

        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.interface_language), style = MaterialTheme.typography.titleMedium)
        AppLanguage.entries.forEach { lang ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(
                    selected = appLanguage == lang,
                    onClick = { vm.settings.setAppLanguage(lang) }
                )
                Text(
                    when (lang) {
                        AppLanguage.PORTUGUESE -> stringResource(R.string.lang_portuguese)
                        AppLanguage.SPANISH -> stringResource(R.string.lang_spanish)
                        AppLanguage.ENGLISH -> "English"
                    }
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(checked = useFake, onCheckedChange = { vm.setUseFakeStt(it) })
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.use_fake_stt))
        }
    }
}
