package io.github.xingyingyuzhui.modbusmobile.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import io.github.xingyingyuzhui.modbusmobile.application.SessionManager
import io.github.xingyingyuzhui.modbusmobile.ui.connection.ConnectionScreen
import io.github.xingyingyuzhui.modbusmobile.ui.monitor.MonitorScreen
import io.github.xingyingyuzhui.modbusmobile.ui.project.ProjectScreen
import io.github.xingyingyuzhui.modbusmobile.ui.traffic.TrafficScreen
import io.github.xingyingyuzhui.modbusmobile.ui.workbench.WorkbenchScreen

val LocalSession = staticCompositionLocalOf<SessionManager> {
    error("SessionManager 还没有提供")
}

private enum class Tab(val route: String, val label: String) {
    Connection("connection", "连接"),
    Workbench("workbench", "工作台"),
    Monitor("monitor", "监控"),
    Traffic("traffic", "通信日志"),
    Project("project", "工程"),
}

@Composable
fun ModbusApp() {
    val nav = rememberNavController()
    val current = nav.currentBackStackEntryAsState().value?.destination?.route
    Scaffold(
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = current == tab.route,
                        onClick = {
                            nav.navigate(tab.route) {
                                popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        label = { Text(tab.label) },
                        icon = { Text(tab.label.take(1)) },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = Tab.Connection.route,
            modifier = Modifier.padding(padding),
        ) {
            composable(Tab.Connection.route) { ConnectionScreen() }
            composable(Tab.Workbench.route) { WorkbenchScreen() }
            composable(Tab.Monitor.route) { MonitorScreen() }
            composable(Tab.Traffic.route) { TrafficScreen() }
            composable(Tab.Project.route) { ProjectScreen() }
        }
    }
}
