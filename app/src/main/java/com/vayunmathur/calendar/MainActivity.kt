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
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.scene.DialogSceneStrategy
import androidx.navigation3.ui.NavDisplay
import com.vayunmathur.calendar.ui.CalendarScreen
import com.vayunmathur.calendar.ui.CalendarSetDateDialog
import com.vayunmathur.calendar.ui.EditEventScreen
import com.vayunmathur.calendar.ui.EventScreen
import com.vayunmathur.calendar.ui.SettingsScreen
import com.vayunmathur.calendar.ui.theme.CalendarTheme
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import androidx.compose.runtime.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow

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

sealed interface Route: NavKey {
    @Serializable
    data object Calendar: Route {
        @Serializable
        data class GotoDialog(val dateViewing: Long): Route
    }

    @Serializable
    data object Settings: Route

    @Serializable
    data class Event(val id: Long): Route

    @Serializable
    data class EditEvent(val id: Long?): Route
}

// The Registry that holds the events
class NavResultRegistry {
    private val _results = Channel<Pair<String, Any>>(Channel.BUFFERED)
    val results = _results.receiveAsFlow()

    fun dispatchResult(key: String, result: Any) {
        _results.trySend(key to result)
    }
}

// The Composable helper (The "ResultEffect" you saw)
@Composable
inline fun <reified T> ResultEffect(key: String, crossinline onResult: (T) -> Unit) {
    val registry = LocalNavResultRegistry.current
    LaunchedEffect(registry) {
        registry.results.collect { (k, result) ->
            if (k == key && result is T) {
                onResult(result)
            }
        }
    }
}

// Make it available everywhere via CompositionLocal
val LocalNavResultRegistry = staticCompositionLocalOf<NavResultRegistry> {
    error("No NavResultRegistry provided")
}

@Composable
fun Navigation(id: Long?) {
    val viewModel: ContactViewModel = viewModel()
    val resultRegistry = remember { NavResultRegistry() }
    val backStack = rememberNavBackStack(*(if(id == null) arrayOf(Route.Calendar) else arrayOf(Route.Calendar, Route.Event(id)))) as NavBackStack<Route>
    LaunchedEffect(id) {
        if(id != null) {
            while(backStack.size > 1) {
                backStack.removeAt(backStack.lastIndex)
            }
            backStack.add(Route.Event(id))
        } else {
            while(backStack.size > 1) {
                backStack.removeAt(backStack.lastIndex)
            }
        }
    }
    Scaffold(contentWindowInsets = WindowInsets.displayCutout
    ) { paddingValues ->
        CompositionLocalProvider(LocalNavResultRegistry provides resultRegistry) {
            NavDisplay(
                modifier = Modifier.padding(paddingValues).consumeWindowInsets(paddingValues),
                sceneStrategy = DialogSceneStrategy(),
                backStack = backStack, entryProvider = entryProvider {
                    entry<Route.Calendar> {
                        CalendarScreen(viewModel, backStack)
                    }
                    entry<Route.Event> { key ->
                        EventScreen(viewModel, key.id, backStack)
                    }
                    entry<Route.Settings> {
                        SettingsScreen(viewModel, backStack)
                    }
                    entry<Route.EditEvent> { key ->
                        EditEventScreen(viewModel, key.id, backStack)
                    }
                    entry<Route.Calendar.GotoDialog>(metadata = DialogSceneStrategy.dialog()) { key ->
                        CalendarSetDateDialog(backStack, LocalDate.fromEpochDays(key.dateViewing))
                    }
                })
        }
    }
}