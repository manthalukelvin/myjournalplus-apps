package com.celmatech.myjournalplus.ui

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.celmatech.myjournalplus.ui.auth.AuthViewModel
import com.celmatech.myjournalplus.ui.auth.LoginScreen
import com.celmatech.myjournalplus.ui.auth.SignUpScreen
import com.celmatech.myjournalplus.ui.edit.EditEntryScreen
import com.celmatech.myjournalplus.ui.home.HomeScreen
import com.celmatech.myjournalplus.ui.insights.AiInsightsScreen
import com.celmatech.myjournalplus.ui.mood.MoodScreen
import com.celmatech.myjournalplus.ui.settings.SettingsScreen
import com.celmatech.myjournalplus.data.repository.DeviceRepository
import com.celmatech.myjournalplus.data.local.UserPrefs
import com.celmatech.myjournalplus.util.ReminderScheduler
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await

sealed class Screen(val route: String) {
    data object Login : Screen("login")
    data object SignUp : Screen("signup")
    data object Home : Screen("home")
    data object Edit : Screen("edit?entryId={entryId}") {
        fun create(entryId: String? = null) =
            if (entryId.isNullOrBlank()) "edit?entryId=" else "edit?entryId=$entryId"
    }
    data object Mood : Screen("mood")
    data object Insights : Screen("insights")
    data object Settings : Screen("settings")
}

@Composable
fun NavGraph(
    initialOpen: String? = null,
    authVm: AuthViewModel = viewModel()
) {
    val navController = rememberNavController()
    val user by authVm.currentUser.collectAsState()
    val profile by authVm.userProfile.collectAsState()
    // Start on Home if already signed in so Google / session restore never stuck on Login
    val startDest = if (user != null) Screen.Home.route else Screen.Login.route

    // Handle notification deep links once logged in
    LaunchedEffect(user, initialOpen) {
        if (user != null && initialOpen != null) {
            when (initialOpen) {
                "edit" -> navController.navigate(Screen.Edit.create()) {
                    popUpTo(Screen.Home.route)
                }
                "mood" -> navController.navigate(Screen.Mood.route) {
                    popUpTo(Screen.Home.route)
                }
            }
        }
    }

    // Anytime auth becomes non-null while on Login, go Home (covers Google sign-in)
    LaunchedEffect(user) {
        if (user != null) {
            val route = navController.currentBackStackEntry?.destination?.route
            if (route == Screen.Login.route || route == Screen.SignUp.route) {
                navController.navigate(Screen.Home.route) {
                    popUpTo(0) { inclusive = true }
                }
            }
        }
    }

    NavHost(navController = navController, startDestination = startDest) {
        composable(Screen.Login.route) {
            LaunchedEffect(user) {
                if (user != null) {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                }
            }
            LoginScreen(
                onLoginSuccess = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                },
                onNavigateSignUp = { navController.navigate(Screen.SignUp.route) },
                viewModel = authVm
            )
        }
        composable(Screen.SignUp.route) {
            SignUpScreen(
                onSignUpSuccess = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                },
                onBack = { navController.popBackStack() },
                viewModel = authVm
            )
        }
        composable(Screen.Home.route) {
            val context = LocalContext.current
            LaunchedEffect(user) {
                val currentUser = user
                if (currentUser == null) {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(0) { inclusive = true }
                    }
                } else {
                    try {
                        DeviceRepository().registerOrTouch(context, currentUser.uid)
                    } catch (_: Exception) { }
                    try {
                        val prefs = UserPrefs(context)
                        val enabled = prefs.reminderEnabled.first()
                        if (enabled) {
                            ReminderScheduler.scheduleDaily(
                                context,
                                prefs.reminderHour.first(),
                                prefs.reminderMinute.first()
                            )
                        }
                    } catch (_: Exception) { }
                    // Remote logout from another device
                    try {
                        val deviceId = DeviceRepository().currentDeviceId(context)
                        val snap = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                            .collection("users").document(currentUser.uid).get().await()
                        val forced = snap.get("forceLogoutDevices") as? List<*> ?: emptyList<Any>()
                        if (deviceId in forced.map { it.toString() }) {
                            com.google.firebase.firestore.FirebaseFirestore.getInstance()
                                .collection("users").document(currentUser.uid)
                                .update(
                                    "forceLogoutDevices",
                                    com.google.firebase.firestore.FieldValue.arrayRemove(deviceId)
                                )
                            authVm.logout()
                        }
                    } catch (_: Exception) { }
                }
            }
            HomeScreen(
                profile = profile,
                onNewEntry = { navController.navigate(Screen.Edit.create()) },
                onEditEntry = { id -> navController.navigate(Screen.Edit.create(id)) },
                onMood = { navController.navigate(Screen.Mood.route) },
                onInsights = { navController.navigate(Screen.Insights.route) },
                onSettings = { navController.navigate(Screen.Settings.route) },
                onLogout = {
                    authVm.logout()
                    navController.navigate(Screen.Login.route) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }
        composable(
            route = "edit?entryId={entryId}",
            arguments = listOf(navArgument("entryId") {
                type = NavType.StringType
                nullable = true
                defaultValue = null
            })
        ) { backStack ->
            EditEntryScreen(
                entryId = backStack.arguments?.getString("entryId"),
                isPremium = profile?.isPremium == true,
                onSaved = { navController.popBackStack() },
                onBack = { navController.popBackStack() }
            )
        }
        composable(Screen.Mood.route) {
            MoodScreen(
                isPremium = profile?.isPremium == true,
                onBack = { navController.popBackStack() },
                onUpgrade = { navController.navigate(Screen.Settings.route) }
            )
        }
        composable(Screen.Insights.route) {
            AiInsightsScreen(
                isPremium = profile?.isPremium == true,
                onBack = { navController.popBackStack() }
            )
        }
        composable(Screen.Settings.route) {
            SettingsScreen(
                profile = profile,
                onBack = { navController.popBackStack() },
                onLogout = {
                    authVm.logout()
                    navController.navigate(Screen.Login.route) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }
    }
}
