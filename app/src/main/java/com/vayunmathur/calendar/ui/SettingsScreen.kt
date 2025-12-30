package com.vayunmathur.calendar.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.vayunmathur.calendar.ContactViewModel
import com.vayunmathur.calendar.Route

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: ContactViewModel, backStack: NavBackStack<Route>) {
    val calendars by viewModel.calendars.collectAsState()
    val visibility by viewModel.calendarVisibility.collectAsState()

    // state for currently editing calendar id and temporary selected color
    var editingCalendarId by remember { mutableStateOf<Long?>(null) }
    var tempColor by remember { mutableStateOf(0) }

    // state for add-calendar dialog
    var showAddDialog by remember { mutableStateOf(false) }
    var newDisplayName by remember { mutableStateOf("") }
    var newColor by remember { mutableStateOf(0xFF2196F3.toInt()) }

    // state for selection and rename/delete dialogs
    var selectedCalendarId by remember { mutableStateOf<Long?>(null) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var renameText by remember { mutableStateOf("") }

    val grouped = calendars.groupBy { it.accountName }

    Scaffold(
        topBar = {
            TopAppBar({Text("Settings")}, navigationIcon = {
                IconButton({
                    backStack.removeAt(backStack.lastIndex)
                }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                }
            }, actions = {
                if(selectedCalendarId != null) {
                    IconButton(onClick = {
                        // open rename dialog prefilled
                        val cal = calendars.find { it.id == selectedCalendarId }
                        renameText = cal?.displayName ?: ""
                        showRenameDialog = true
                    }) {
                        Icon(Icons.Filled.Edit, contentDescription = "Rename")
                    }
                    IconButton(onClick = { showDeleteConfirm = true }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Delete")
                    }
                }
            })
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Filled.Add, contentDescription = "Add calendar")
            }
        },
        contentWindowInsets = WindowInsets()
    ) { paddingValues ->
        // wrap content in a Box so we can overlay action buttons in the corner
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(8.dp)) {
                grouped.forEach { (account, cals) ->
                    item {
                        Text(text = account.ifEmpty { "(No account)" }, modifier = Modifier.padding(vertical = 8.dp))
                    }
                    items(cals) { cal ->
                        val isSelected = selectedCalendarId == cal.id
                        ListItem(
                            headlineContent = { Text(cal.displayName) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(if (isSelected) Modifier.background(Color(0x11000000)) else Modifier)
                                .clickable {
                                    // select this calendar (or deselect if already selected)
                                    selectedCalendarId = if (isSelected) null else cal.id
                                },
                            supportingContent = { Text(text = "ID: ${cal.id}") },
                            leadingContent = {
                                // colored circle showing calendar color; clickable to open color picker
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .background(Color(cal.color))
                                        .border(
                                            width = 1.dp,
                                            color = Color.Black.copy(alpha = 0.12f),
                                            shape = CircleShape
                                        )
                                        .then(if (cal.canModify) Modifier.clickable {
                                            editingCalendarId = cal.id
                                            tempColor = cal.color
                                        } else Modifier)
                                )
                            },
                            trailingContent = {
                                val isChecked = visibility[cal.id] ?: true
                                Checkbox(checked = isChecked, onCheckedChange = { checked -> viewModel.setCalendarVisibility(cal.id, checked) })
                            },
                            colors = ListItemDefaults.colors(
                                containerColor = if(selectedCalendarId == cal.id) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface
                            )
                        )
                        HorizontalDivider()
                    }
                }
                item {
                    Spacer(modifier = Modifier.height(64.dp))
                }
            }

            // Color picker dialog for existing calendar
            if (editingCalendarId != null) {
                val cal = calendars.find { it.id == editingCalendarId }
                if (cal != null) {
                    // predefined swatches (ARGB ints)
                    val swatches = listOf(
                        0xFFF44336.toInt(), // red
                        0xFFE91E63.toInt(), // pink
                        0xFF9C27B0.toInt(), // purple
                        0xFF3F51B5.toInt(), // indigo
                        0xFF2196F3.toInt(), // blue
                        0xFF009688.toInt(), // teal
                        0xFF4CAF50.toInt(), // green
                        0xFFFFC107.toInt(), // amber
                        0xFFFF9800.toInt(), // orange
                        0xFF795548.toInt(), // brown
                        0xFF607D8B.toInt()  // blue grey
                    )

                    AlertDialog(
                        onDismissRequest = { editingCalendarId = null },
                        title = { Text(text = "Change color for \"${cal.displayName}\"") },
                        text = {
                            Column {
                                // swatches row
                                androidx.compose.foundation.lazy.LazyRow {
                                    items(swatches.size) { idx ->
                                        val c = swatches[idx]
                                        val selected = (tempColor == c)
                                        Box(
                                            modifier = Modifier
                                                .padding(6.dp)
                                                .size(40.dp)
                                                .clip(CircleShape)
                                                .background(Color(c))
                                                .border(
                                                    width = if (selected) 3.dp else 1.dp,
                                                    color = if (selected) Color.Black else Color.Black.copy(alpha = 0.12f),
                                                    shape = CircleShape
                                                )
                                                .clickable {
                                                    tempColor = c
                                                }
                                        )
                                    }
                                }
                            }
                        },
                        confirmButton = {
                            Button(onClick = {
                                // persist color change via ViewModel
                                viewModel.setCalendarColor(cal.id, tempColor)
                                editingCalendarId = null
                            }) {
                                Text("Change color")
                            }
                        },
                        dismissButton = {
                            Button(onClick = { editingCalendarId = null }) {
                                Text("Cancel")
                            }
                        }
                    )
                } else {
                    // if calendar no longer exists, close dialog
                    editingCalendarId = null
                }
            }

            // Add-calendar dialog
            if (showAddDialog) {
                // swatches for choosing color
                val swatches = listOf(
                    0xFFF44336.toInt(), // red
                    0xFFE91E63.toInt(), // pink
                    0xFF9C27B0.toInt(), // purple
                    0xFF3F51B5.toInt(), // indigo
                    0xFF2196F3.toInt(), // blue
                    0xFF009688.toInt(), // teal
                    0xFF4CAF50.toInt(), // green
                    0xFFFFC107.toInt(), // amber
                    0xFFFF9800.toInt(), // orange
                    0xFF795548.toInt(), // brown
                    0xFF607D8B.toInt()  // blue grey
                )

                AlertDialog(
                    onDismissRequest = { showAddDialog = false },
                    title = { Text(text = "New local calendar") },
                    text = {
                        Column {
                            OutlinedTextField(
                                value = newDisplayName,
                                onValueChange = { newDisplayName = it },
                                label = { Text("Calendar name") },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.size(8.dp))
                            Text("Choose color:")
                            androidx.compose.foundation.lazy.LazyRow {
                                items(swatches.size) { idx ->
                                    val c = swatches[idx]
                                    val selected = (newColor == c)
                                    Box(
                                        modifier = Modifier
                                            .padding(6.dp)
                                            .size(40.dp)
                                            .clip(CircleShape)
                                            .background(Color(c))
                                            .border(
                                                width = if (selected) 3.dp else 1.dp,
                                                color = if (selected) Color.Black else Color.Black.copy(alpha = 0.12f),
                                                shape = CircleShape
                                            )
                                            .clickable { newColor = c }
                                    )
                                }
                            }
                        }
                    },
                    confirmButton = {
                        Button(enabled = newDisplayName.isNotBlank() && newColor != 0, onClick = {
                            // create calendar as visible and editor-access
                            val accessLevel = android.provider.CalendarContract.Calendars.CAL_ACCESS_EDITOR
                            // always create under the "Offline Calendar" account
                            viewModel.createLocalCalendar("Offline Calendar", newDisplayName.ifEmpty { "New Calendar" }, newColor, true, accessLevel)
                            showAddDialog = false
                            // reset fields optionally
                            newDisplayName = ""
                            newColor = 0xFF2196F3.toInt()
                        }) {
                            Text("Create")
                        }
                    },
                    dismissButton = {
                        Button(onClick = { showAddDialog = false }) {
                            Text("Cancel")
                        }
                    }
                )
            }

            // Rename dialog
            if (showRenameDialog && selectedCalendarId != null) {
                AlertDialog(
                    onDismissRequest = { showRenameDialog = false },
                    title = { Text("Rename calendar") },
                    text = {
                        Column {
                            OutlinedTextField(
                                value = renameText,
                                onValueChange = { renameText = it },
                                label = { Text("New name") },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    },
                    confirmButton = {
                        Button(enabled = renameText.isNotBlank(), onClick = {
                            val id = selectedCalendarId
                            if (id != null) {
                                viewModel.renameCalendar(id, renameText)
                            }
                            showRenameDialog = false
                            selectedCalendarId = null
                        }) {
                            Text("Rename")
                        }
                    },
                    dismissButton = {
                        Button(onClick = { showRenameDialog = false }) { Text("Cancel") }
                    }
                )
            }

            // Delete confirm dialog
            if (showDeleteConfirm && selectedCalendarId != null) {
                val cal = calendars.find { it.id == selectedCalendarId }
                AlertDialog(
                    onDismissRequest = { showDeleteConfirm = false },
                    title = { Text("Delete calendar") },
                    text = { Text(text = "Are you sure you want to delete \"${cal?.displayName ?: "this calendar"}\"? This will remove all events in the calendar.") },
                    confirmButton = {
                        Button(onClick = {
                            val id = selectedCalendarId
                            if (id != null) {
                                viewModel.deleteCalendar(id)
                            }
                            showDeleteConfirm = false
                            selectedCalendarId = null
                        }) { Text("Delete") }
                    },
                    dismissButton = {
                        Button(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
                    }
                )
            }
        }
    }
}
