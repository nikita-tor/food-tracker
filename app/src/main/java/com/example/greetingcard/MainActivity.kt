package com.example.greetingcard

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import com.example.greetingcard.data.Meal
import com.example.greetingcard.ui.AddFoodScreen
import com.example.greetingcard.ui.FoodFormScreen
import com.example.greetingcard.ui.HistoryScreen
import com.example.greetingcard.ui.RestoreDialog
import com.example.greetingcard.ui.SavedScreen
import com.example.greetingcard.ui.SettingsScreen
import com.example.greetingcard.ui.TodayScreen
import com.example.greetingcard.ui.TodayTopBar
import com.example.greetingcard.ui.WelcomeScreen
import com.example.greetingcard.ui.rememberBackupPicker
import com.example.greetingcard.ui.theme.GreetingCardTheme
import java.time.LocalTime

class MainActivity : ComponentActivity() {
    private val vm: FoodViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            GreetingCardTheme {
                FoodTrackerApp(vm)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        vm.onResume()
    }

    override fun onStop() {
        super.onStop()
        vm.flushPendingSave()
    }
}

private enum class Tab(val screen: Screen, val label: String, val title: String, @param:DrawableRes val icon: Int) {
    TODAY(Screen.Today, "Today", "Today", R.drawable.ic_today),
    HISTORY(Screen.History, "History", "History", R.drawable.ic_history),
    SAVED(Screen.Saved, "My foods", "My foods & meals", R.drawable.ic_star),
    SETTINGS(Screen.Settings, "Goals", "Goals & settings", R.drawable.ic_tune),
}

@Composable
fun FoodTrackerApp(vm: FoodViewModel, modifier: Modifier = Modifier) {
    val snackbarState = remember { SnackbarHostState() }
    val snackbarHost: @Composable () -> Unit = { SnackbarHost(snackbarState) }

    LaunchedEffect(vm.message?.id) {
        val message = vm.message ?: return@LaunchedEffect
        snackbarState.currentSnackbarData?.dismiss()
        val result = snackbarState.showSnackbar(
            message.text,
            actionLabel = if (message.undo != null) "Undo" else null,
            duration = SnackbarDuration.Short,
        )
        if (result == SnackbarResult.ActionPerformed) message.undo?.invoke()
    }

    BackHandler(enabled = vm.backStack.size > 1) { vm.back() }

    if (!vm.loaded) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }

    // Shown over whatever screen the backup was picked from: the Goals tab, or the welcome screen.
    vm.pendingRestore?.let { backup ->
        RestoreDialog(backup, onConfirm = vm::confirmRestore, onDismiss = vm::cancelRestore)
    }

    if (vm.needsName) {
        val pickBackup = rememberBackupPicker(onPick = vm::openBackup)
        WelcomeScreen(onContinue = vm::setName, onRestoreBackup = pickBackup, modifier = modifier)
        return
    }

    // Key on the stack depth so each screen instance gets fresh form state.
    key(vm.backStack.size, vm.screen) {
        when (val screen = vm.screen) {
            is Screen.AddFood -> AddFoodScreen(vm, screen.meal, snackbarHost, modifier)
            is Screen.FoodForm -> FoodFormScreen(vm, screen.meal, screen.draft, snackbarHost, modifier)
            Screen.Today -> TabScaffold(vm, Tab.TODAY, snackbarHost, modifier)
            Screen.History -> TabScaffold(vm, Tab.HISTORY, snackbarHost, modifier)
            Screen.Saved -> TabScaffold(vm, Tab.SAVED, snackbarHost, modifier)
            Screen.Settings -> TabScaffold(vm, Tab.SETTINGS, snackbarHost, modifier)
        }
    }
}

/** One of the main tabs, with the bottom navigation bar. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TabScaffold(
    vm: FoodViewModel,
    current: Tab,
    snackbarHost: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            if (current == Tab.TODAY) TodayTopBar(vm) else TopAppBar(title = { Text(current.title) })
        },
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = tab == current,
                        onClick = { vm.switchTab(tab.screen) },
                        icon = { Icon(painterResource(tab.icon), contentDescription = null) },
                        label = { Text(tab.label) },
                    )
                }
            }
        },
        floatingActionButton = {
            if (current == Tab.TODAY) {
                // One tap from anywhere on Today, to the meal that fits the time; Add food can switch it.
                val meal = Meal.forHour(LocalTime.now().hour)
                ExtendedFloatingActionButton(
                    onClick = { vm.openAddFood(meal) },
                    icon = { Icon(painterResource(R.drawable.ic_add), contentDescription = null) },
                    text = { Text("Add to ${meal.label.lowercase()}") },
                )
            }
            if (current == Tab.SAVED) {
                FloatingActionButton(onClick = { vm.navigate(Screen.FoodForm(meal = null, draft = FoodDraft())) }) {
                    Icon(painterResource(R.drawable.ic_add), contentDescription = "New saved food")
                }
            }
        },
        snackbarHost = snackbarHost,
    ) { padding ->
        when (current) {
            Tab.TODAY -> TodayScreen(vm, padding)
            Tab.HISTORY -> HistoryScreen(vm, padding)
            Tab.SAVED -> SavedScreen(vm, padding)
            Tab.SETTINGS -> SettingsScreen(vm, padding)
        }
    }
}
