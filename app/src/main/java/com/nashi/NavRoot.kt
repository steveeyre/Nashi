package com.nashi

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.nashi.feature.item.ItemDetailScreen
import com.nashi.feature.item.ItemEditScreen
import com.nashi.feature.item.ItemListScreen
import com.nashi.feature.fields.CategoryFieldsScreen
import com.nashi.feature.history.StockHistoryScreen
import com.nashi.feature.location.LocationScreen
import com.nashi.feature.more.MoreScreen
import com.nashi.feature.place.PlaceScreen
import com.nashi.feature.scan.ScanScreen
import com.nashi.feature.space.SpaceScreen
import com.nashi.feature.stats.StatsScreen
import com.nashi.feature.trash.TrashScreen

private const val NAV_FADE_MS = 200

private data class Tab(val route: String, val label: String, val icon: ImageVector)

@Composable
fun NashiRoot() {
    NotificationPermissionGate()
    val tabs = listOf(
        Tab("items", "物品", Icons.Default.Home),
        Tab("location", "位置", Icons.Default.Place),
        Tab("stats", "统计", Icons.Default.BarChart),
        Tab("space", "空间", Icons.Default.ViewModule),
        Tab("more", "更多", Icons.Default.Settings),
    )
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route ?: "items"
    Scaffold(bottomBar = {
        Column {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f))
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 0.dp,
            ) {
                tabs.forEach { t ->
                    NavigationBarItem(
                        selected = route.startsWith(t.route),
                        onClick = { nav.navigate(t.route) { popUpTo("items"); launchSingleTop = true } },
                        label = { Text(t.label, style = MaterialTheme.typography.labelSmall) },
                        icon = { Icon(t.icon, t.label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                    )
                }
            }
        }
    }, contentWindowInsets = WindowInsets(0, 0, 0, 0)) { pad ->
        // 所有页签(含「空间」)统一淡入淡出, 不再依赖 Navigation 的默认过渡
        NavHost(
            nav,
            startDestination = "items",
            modifier = Modifier.padding(pad),
            enterTransition = { fadeIn(tween(NAV_FADE_MS)) },
            exitTransition = { fadeOut(tween(NAV_FADE_MS)) },
            popEnterTransition = { fadeIn(tween(NAV_FADE_MS)) },
            popExitTransition = { fadeOut(tween(NAV_FADE_MS)) },
        ) {
            composable("items") {
                ItemListScreen(
                    onAdd = { nav.navigate("edit") },
                    onOpenItem = { nav.navigate("item?id=" + it) },
                    onScan = { nav.navigate("scan") },
                )
            }
            composable(
                "item?id={id}",
                arguments = listOf(navArgument("id") { type = NavType.StringType; defaultValue = "" }),
            ) {
                ItemDetailScreen(
                    onBack = { nav.popBackStack() },
                    onEdit = { nav.navigate("edit?id=" + it) },
                    onOpenHistory = { nav.navigate("history?item=" + it) },
                )
            }
            composable(
                "edit?id={id}&barcode={barcode}",
                arguments = listOf(
                    navArgument("id") { type = NavType.StringType; defaultValue = "" },
                    navArgument("barcode") { type = NavType.StringType; defaultValue = "" },
                ),
            ) {
                ItemEditScreen(
                    itemId = it.arguments?.getString("id") ?: "",
                    barcode = it.arguments?.getString("barcode") ?: "",
                    onSaved = { nav.popBackStack() },
                )
            }
            composable("scan") {
                ScanScreen(onNewItem = { b -> nav.navigate("edit?barcode=$b") }, onDone = { nav.popBackStack() })
            }
            composable("location") {
                LocationScreen(
                    onManage = { nav.navigate("places") },
                    onItemClick = { nav.navigate("item?id=" + it) },
                )
            }
            composable("places") { PlaceScreen(onBack = { nav.popBackStack() }) }
            composable("fields") { CategoryFieldsScreen(onBack = { nav.popBackStack() }) }
            composable(
                "history?item={item}",
                arguments = listOf(navArgument("item") { type = NavType.StringType; defaultValue = "" }),
            ) { StockHistoryScreen(onBack = { nav.popBackStack() }) }
            composable("trash") { TrashScreen(onBack = { nav.popBackStack() }) }
            composable("stats") { StatsScreen() }
            composable("space") { SpaceScreen() }
            composable("more") {
                MoreScreen(
                    onOpenFields = { nav.navigate("fields") },
                    onOpenHistory = { nav.navigate("history") },
                    onOpenTrash = { nav.navigate("trash") },
                )
            }
        }
    }
}

@Composable
private fun NotificationPermissionGate() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val ctx = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
