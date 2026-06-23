package com.larateam.sshmanager.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.larateam.sshmanager.ui.connections.ConnectionEditScreen
import com.larateam.sshmanager.ui.connections.ConnectionEditViewModel
import com.larateam.sshmanager.ui.connections.ConnectionsListScreen
import com.larateam.sshmanager.ui.dashboard.DashboardScreen
import com.larateam.sshmanager.ui.debug.SshDebugScreen
import com.larateam.sshmanager.ui.home.HomeScreen
import com.larateam.sshmanager.ui.sessions.SessionsScreen
import com.larateam.sshmanager.ui.settings.SettingsScreen
import com.larateam.sshmanager.ui.sftp.SftpScreen
import com.larateam.sshmanager.ui.terminal.StaticTerminalDemoScreen
import com.larateam.sshmanager.ui.terminal.TerminalTabsScreen

/** Navigation routes. Grows one entry per feature screen as later phases land. */
object Routes {
    const val HOME = "home"
    const val CONNECTIONS = "connections"
    const val CONNECTION_EDIT = "connection_edit"
    const val SSH_DEBUG = "ssh_debug"
    const val TERMINAL_DEMO = "terminal_demo"
    const val TERMINALS = "terminals"
    const val DASHBOARD = "dashboard"
    const val SFTP = "sftp"
    const val SESSIONS = "sessions"
    const val SETTINGS = "settings"
    const val ARG_CONNECTION_ID = "connectionId"

    /** Edit an existing connection, or add a new one when [id] is the new-id sentinel. */
    fun connectionEdit(id: Long = ConnectionEditViewModel.NEW_ID): String = "$CONNECTION_EDIT/$id"

    /** Per-connection dashboard. */
    fun dashboard(id: Long): String = "$DASHBOARD/$id"

    /** Per-connection SFTP file manager. */
    fun sftp(id: Long): String = "$SFTP/$id"
}

@Composable
fun AppNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = Routes.HOME,
        modifier = modifier,
    ) {
        composable(Routes.HOME) {
            HomeScreen(
                onOpenConnections = { navController.navigate(Routes.CONNECTIONS) },
                onOpenDebug = { navController.navigate(Routes.SSH_DEBUG) },
                onOpenTerminalDemo = { navController.navigate(Routes.TERMINAL_DEMO) },
                onOpenTerminals = { navController.navigate(Routes.TERMINALS) },
                onOpenSessions = { navController.navigate(Routes.SESSIONS) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(onBack = { navController.popBackStack() })
        }

        composable(Routes.SESSIONS) {
            SessionsScreen(
                onBack = { navController.popBackStack() },
                onOpenTerminals = { navController.navigate(Routes.TERMINALS) },
                onOpenDashboard = { id -> navController.navigate(Routes.dashboard(id)) },
                onOpenSftp = { id -> navController.navigate(Routes.sftp(id)) },
            )
        }

        composable(Routes.TERMINALS) {
            TerminalTabsScreen(
                onAllClosed = {
                    navController.navigate(Routes.CONNECTIONS) {
                        popUpTo(Routes.TERMINALS) { inclusive = true }
                    }
                },
            )
        }

        composable(Routes.SSH_DEBUG) {
            SshDebugScreen(onBack = { navController.popBackStack() })
        }

        composable(Routes.TERMINAL_DEMO) {
            StaticTerminalDemoScreen()
        }

        composable(Routes.CONNECTIONS) {
            ConnectionsListScreen(
                onAddConnection = { navController.navigate(Routes.connectionEdit()) },
                onEditConnection = { id -> navController.navigate(Routes.connectionEdit(id)) },
                onOpenDashboard = { id -> navController.navigate(Routes.dashboard(id)) },
                onBack = { navController.popBackStack() },
            )
        }

        composable(
            route = "${Routes.DASHBOARD}/{${Routes.ARG_CONNECTION_ID}}",
            arguments = listOf(navArgument(Routes.ARG_CONNECTION_ID) { type = NavType.LongType }),
        ) { entry ->
            val id = entry.arguments?.getLong(Routes.ARG_CONNECTION_ID) ?: -1L
            DashboardScreen(
                onBack = { navController.popBackStack() },
                onOpenTerminals = { navController.navigate(Routes.TERMINALS) },
                onOpenSftp = { navController.navigate(Routes.sftp(id)) },
            )
        }

        composable(
            route = "${Routes.SFTP}/{${Routes.ARG_CONNECTION_ID}}",
            arguments = listOf(navArgument(Routes.ARG_CONNECTION_ID) { type = NavType.LongType }),
        ) {
            SftpScreen(onBack = { navController.popBackStack() })
        }

        composable(
            route = "${Routes.CONNECTION_EDIT}/{${Routes.ARG_CONNECTION_ID}}",
            arguments = listOf(
                navArgument(Routes.ARG_CONNECTION_ID) { type = NavType.LongType },
            ),
        ) {
            ConnectionEditScreen(onDone = { navController.popBackStack() })
        }
    }
}
