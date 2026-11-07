package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.ScreenDestination
import com.example.ui.XauPilotViewModel
import com.example.ui.components.EmergencyStopDialog
import com.example.ui.screens.accounts.AccountsScreen
import com.example.ui.screens.admin.AdminConsoleScreen
import com.example.ui.screens.admin.AdminSupportScreen
import com.example.ui.screens.support.SupportScreen
import com.example.ui.screens.home.HomeScreen
import com.example.ui.screens.marketplace.MarketplaceScreen
import com.example.ui.screens.positions.PositionsScreen
import com.example.ui.screens.profile.BridgeGuideScreen
import com.example.ui.screens.profile.ProfileScreen
import com.example.ui.screens.profile.PublisherWorkspaceScreen
import com.example.ui.screens.risk.RiskManagementScreen
import com.example.ui.screens.settings_ea.EaSettingsScreen
import com.example.ui.screens.vps.VpsManagementScreen
import com.example.ui.theme.*

class MainActivity : ComponentActivity() {

    private val viewModel: XauPilotViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        com.example.core.network.ApiClient.initialize(applicationContext)
        com.example.core.data.XauPilotRepository.initialize(applicationContext)
        enableEdgeToEdge()

        setContent {
            XauPilotTheme(darkTheme = true) {
                MainAppContent(viewModel = viewModel)
            }
        }
    }
}

data class NavTabItem(
    val title: String,
    val icon: ImageVector,
    val destination: ScreenDestination,
    val testTag: String
)

@Composable
fun MainAppContent(viewModel: XauPilotViewModel) {
    val currentScreen by viewModel.currentScreen.collectAsState()
    val authSession by viewModel.authSession.collectAsState()
    val sessionRestoreComplete by viewModel.sessionRestoreComplete.collectAsState()
    val isGuestMode by viewModel.isGuestMode.collectAsState()
    val userMessage by viewModel.userMessage.collectAsState()
    val emergencyDialogVisible by viewModel.emergencyDialogVisible.collectAsState()
    val selectedEmergencyPolicy by viewModel.selectedEmergencyPolicy.collectAsState()
    val selectedAccountId by viewModel.selectedAccountId.collectAsState()
    val signedInRole = authSession?.user?.role?.name.orEmpty()

    val snackbarHostState = remember { SnackbarHostState() }

    if (authSession == null && !isGuestMode) {
        if (!sessionRestoreComplete) {
            Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = androidx.compose.ui.Alignment.Center) {
                CircularProgressIndicator(color = GoldPrimary)
            }
        } else {
            WelcomeAuthScreen(viewModel = viewModel)
        }
        return
    }

    // Handle user messages in Snackbar
    LaunchedEffect(userMessage) {
        userMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.dismissUserMessage()
        }
    }

    // Hardware/System Back button handling
    BackHandler(enabled = currentScreen != ScreenDestination.Home) {
        if (isGuestMode) viewModel.navigateTo(ScreenDestination.Home) else viewModel.navigateBack()
    }

    val allNavTabs = listOf(
        NavTabItem("Home", Icons.Default.Dashboard, ScreenDestination.Home, "nav_home_tab"),
        NavTabItem("Market", Icons.Default.Storefront, ScreenDestination.Marketplace, "nav_market_tab"),
        NavTabItem("Accounts", Icons.Default.AccountBalance, ScreenDestination.Accounts, "nav_accounts_tab"),
        NavTabItem("Positions", Icons.Default.ShowChart, ScreenDestination.Positions, "nav_positions_tab"),
        NavTabItem("Profile", Icons.Default.Person, ScreenDestination.Profile, "nav_profile_tab")
    )
    val roleTabs = if (authSession != null && (signedInRole == "SUPER_ADMINISTRATOR" || signedInRole == "ADMINISTRATOR")) allNavTabs + listOf(
        NavTabItem("Support", Icons.Default.SupportAgent, ScreenDestination.Support, "nav_support_tab"),
        NavTabItem("Admin", Icons.Default.AdminPanelSettings, ScreenDestination.AdminConsole, "nav_admin_tab")
    ) else if (authSession != null) allNavTabs + NavTabItem("Support", Icons.Default.SupportAgent, ScreenDestination.Support, "nav_support_tab") else allNavTabs
    val navTabs = if (isGuestMode) allNavTabs.take(2) else roleTabs

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = CharcoalBackground,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            NavigationBar(
                containerColor = CharcoalSurface,
                tonalElevation = 8.dp,
                // Keep the navigation bar anchored at the bottom and let Material 3
                // apply the system navigation-bar inset (gesture/3-button area).
                // Do not force a fixed height: it can clip labels and icons when
                // edge-to-edge is enabled.
                windowInsets = NavigationBarDefaults.windowInsets,
                modifier = Modifier.fillMaxWidth()
            ) {
                navTabs.forEach { tab ->
                    val isSelected = when (currentScreen) {
                        tab.destination -> true
                        is ScreenDestination.EaSettings -> tab.destination == ScreenDestination.Accounts
                        is ScreenDestination.VpsManagement -> tab.destination == ScreenDestination.Accounts
                        is ScreenDestination.RiskManagement -> tab.destination == ScreenDestination.Accounts
                        is ScreenDestination.BridgeGuide -> tab.destination == ScreenDestination.Profile
                        is ScreenDestination.PublisherWorkspace -> tab.destination == ScreenDestination.Profile
                        is ScreenDestination.AdminConsole -> tab.destination == ScreenDestination.AdminConsole
                        is ScreenDestination.Support -> tab.destination == ScreenDestination.Support
                        else -> false
                    }

                    NavigationBarItem(
                        selected = isSelected,
                        onClick = { viewModel.navigateTo(tab.destination) },
                        icon = {
                            Icon(
                                imageVector = tab.icon,
                                contentDescription = tab.title,
                                modifier = Modifier.size(22.dp)
                            )
                        },
                        label = {
                            Text(
                                text = tab.title,
                                fontSize = 12.sp
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = CharcoalBackground,
                            selectedTextColor = GoldLight,
                            indicatorColor = GoldPrimary,
                            unselectedIconColor = TextSecondaryDark,
                            unselectedTextColor = TextSecondaryDark
                        ),
                        modifier = Modifier.testTag(tab.testTag)
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (val screen = if (isGuestMode && currentScreen !is ScreenDestination.Home && currentScreen !is ScreenDestination.Marketplace) ScreenDestination.Home else currentScreen) {
                is ScreenDestination.Home -> HomeScreen(viewModel = viewModel)
                is ScreenDestination.Marketplace -> MarketplaceScreen(viewModel = viewModel)
                is ScreenDestination.Accounts -> AccountsScreen(viewModel = viewModel)
                is ScreenDestination.Positions -> PositionsScreen(viewModel = viewModel)
                is ScreenDestination.EaSettings -> EaSettingsScreen(installationId = screen.installationId, viewModel = viewModel)
                is ScreenDestination.VpsManagement -> VpsManagementScreen(viewModel = viewModel)
                is ScreenDestination.RiskManagement -> RiskManagementScreen(viewModel = viewModel)
                is ScreenDestination.Profile -> ProfileScreen(viewModel = viewModel)
                is ScreenDestination.BridgeGuide -> BridgeGuideScreen(viewModel = viewModel)
                is ScreenDestination.PublisherWorkspace -> PublisherWorkspaceScreen(viewModel = viewModel)
                is ScreenDestination.AdminConsole -> AdminConsoleScreen(user = authSession?.user)
                is ScreenDestination.Support -> if (signedInRole == "SUPER_ADMINISTRATOR" || signedInRole == "ADMINISTRATOR") AdminSupportScreen(user = authSession?.user) else SupportScreen()
                else -> HomeScreen(viewModel = viewModel)
            }
        }

        // Global Emergency Stop Confirmation Dialog
        EmergencyStopDialog(
            visible = emergencyDialogVisible,
            selectedPolicy = selectedEmergencyPolicy,
            onSelectPolicy = { viewModel.setEmergencyPolicy(it) },
            onConfirm = {
                selectedAccountId?.let { accId ->
                    viewModel.executeEmergencyStop(accId)
                }
            },
            onDismiss = { viewModel.dismissEmergencyDialog() }
        )
    }
}
