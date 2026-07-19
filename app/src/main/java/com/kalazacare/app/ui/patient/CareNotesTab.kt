package com.kalazacare.app.ui.patient

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kalazacare.app.data.model.CareNote
import com.kalazacare.app.ui.CareNoteViewModel
import com.kalazacare.app.ui.components.EmptyState
import com.kalazacare.app.ui.theme.KalazaRed
import java.time.format.DateTimeFormatter

@Composable
fun CareNotesTab(
    notes: List<CareNote>,
    patientId: String,
    viewModel: CareNoteViewModel,
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var editTarget by remember { mutableStateOf<CareNote?>(null) }
    val context = androidx.compose.ui.platform.LocalContext.current

    Box(modifier = Modifier.fillMaxSize()) {
        if (notes.isEmpty()) {
            EmptyState(
                title = "No Notes",
                message = "No care notes added yet.",
                icon = Icons.Default.Edit
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 80.dp),
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(notes) { note ->
                    CareNoteItem(note, onEdit = { editTarget = note })
                }
            }
        }

        FloatingActionButton(
            onClick = { showAddDialog = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            containerColor = KalazaRed,
            contentColor = MaterialTheme.colorScheme.onPrimary
        ) {
            Icon(Icons.Default.Add, "Add Care Note")
        }
    }

    if (showAddDialog) {
        AddCareNoteDialog(
            onDismiss = { showAddDialog = false },
            onSave = { noteText ->
                viewModel.addNote(patientId, noteText)
                showAddDialog = false
            }
        )
    }

    editTarget?.let { note ->
        AddCareNoteDialog(
            title = "Edit Care Note",
            initialText = note.note,
            onDismiss = { editTarget = null },
            onSave = { newText ->
                viewModel.updateNote(note, newText) { _, message ->
                    Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                }
                editTarget = null
            }
        )
    }
}

@Composable
private fun CareNoteItem(note: CareNote, onEdit: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth()) {
        // Timeline dot and line
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(KalazaRed)
            )
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .height(64.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant)
            )
        }
        
        Spacer(modifier = Modifier.width(16.dp))

        // Note Content
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = note.staffName,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = KalazaRed
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = note.timestamp.format(DateTimeFormatter.ofPattern("dd MMM, HH:mm")),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        IconButton(onClick = onEdit, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit note",
                                tint = KalazaRed, modifier = Modifier.size(16.dp))
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = note.note,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
private fun AddCareNoteDialog(
    title: String = "Add Care Note",
    initialText: String = "",
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var noteText by remember { mutableStateOf(initialText) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = noteText,
                onValueChange = { noteText = it },
                label = { Text("Note Details") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp),
                maxLines = 5
            )
        },
        confirmButton = {
            Button(
                onClick = { onSave(noteText) },
                colors = ButtonDefaults.buttonColors(containerColor = KalazaRed),
                enabled = noteText.isNotBlank()
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
