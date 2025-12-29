package com.vayunmathur.calendar

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.vayunmathur.calendar.ui.CalendarScreen
import com.vayunmathur.calendar.ui.EditEventScreen
import com.vayunmathur.calendar.ui.EventScreen
import com.vayunmathur.calendar.ui.SettingsScreen
import com.vayunmathur.calendar.ui.theme.CalendarTheme
import kotlinx.serialization.Serializable

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val permissions = arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)
            var hasPermissions by remember { mutableStateOf(permissions.all { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }) }
            CalendarTheme {
                if (!hasPermissions) {
                    NoPermissionsScreen(permissions) { hasPermissions = it }
                } else {
                    if(intent.hasExtra("id")) {
                        Navigation(intent.getLongExtra("id", 0))
                    } else {
                        Navigation(null)
                    }
                }
            }
        }
    }
}

@Composable
fun NoPermissionsScreen(permissions: Array<String>, setHasPermissions: (Boolean) -> Unit) {
    val permissionRequestor = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissionsResult ->
        setHasPermissions(permissionsResult.values.all { it })
    }
    LaunchedEffect(Unit) {
        permissionRequestor.launch(permissions)
    }
    Scaffold {
        Box(
            modifier = Modifier
                .padding(it)
                .fillMaxSize()
        ) {
            Button(
                {
                    permissionRequestor.launch(permissions)
                }, Modifier.align(Alignment.Center)
            ) {
                Text(text = "Please grant calendar permission")
            }
        }
    }
}

@Serializable
data object CalendarPage: NavKey

@Serializable
data object SettingsPage: NavKey

@Serializable
data class EventPage(val id: Long): NavKey

@Serializable
data class EditEventPage(val id: Long?): NavKey

@Composable
fun Navigation(id: Long?) {
    val viewModel: ContactViewModel = viewModel()
    val backStack = rememberNavBackStack(*(if(id == null) arrayOf(CalendarPage) else arrayOf(CalendarPage, EventPage(id))))
    LaunchedEffect(id) {
        if(id != null) {
            while(backStack.size > 1) {
                backStack.removeAt(backStack.lastIndex)
            }
            backStack.add(EventPage(id))
        } else {
            while(backStack.size > 1) {
                backStack.removeAt(backStack.lastIndex)
            }
        }
    }
    Scaffold(contentWindowInsets = WindowInsets.displayCutout
    ) { paddingValues ->
        NavDisplay(modifier = Modifier.padding(paddingValues).consumeWindowInsets(paddingValues),
            backStack = backStack, entryProvider = entryProvider {
                entry<CalendarPage> {
                    CalendarScreen(viewModel, backStack)
                }
                entry<EventPage> { key ->
                    EventScreen(viewModel, key.id, backStack)
                }
                entry<SettingsPage> {
                    SettingsScreen(viewModel, backStack)
                }
                entry<EditEventPage> { key ->
                    EditEventScreen(viewModel, key.id, backStack)
                }
            })
    }
}