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
    val userMessage by viewModel.userMessage.collectAsState()
    val emergencyDialogVisible by viewModel.emergencyDialogVisible.collectAsState()
    val selectedEmergencyPolicy by viewModel.selectedEmergencyPolicy.collectAsState()
    val selectedAccountId by viewModel.selectedAccountId.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }

    // Handle user messages in Snackbar
    LaunchedEffect(userMessage) {
        userMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.dismissUserMessage()
        }
    }

    // Hardware/System Back button handling
    BackHandler(enabled = currentScreen != ScreenDestination.Home) {
        viewModel.navigateBack()
    }

    val navTabs = listOf(
        NavTabItem("Home", Icons.Default.Dashboard, ScreenDestination.Home, "nav_home_tab"),
        NavTabItem("Market", Icons.Default.Storefront, ScreenDestination.Marketplace, "nav_market_tab"),
        NavTabItem("Accounts", Icons.Default.AccountBalance, ScreenDestination.Accounts, "nav_accounts_tab"),
        NavTabItem("Positions", Icons.Default.ShowChart, ScreenDestination.Positions, "nav_positions_tab"),
        NavTabItem("Profile", Icons.Default.Person, ScreenDestination.Profile, "nav_profile_tab")
    )

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = CharcoalBackground,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            NavigationBar(
                containerColor = CharcoalSurface,
                tonalElevation = 8.dp,
                modifier = Modifier.height(72.dp)
            ) {
                navTabs.forEach { tab ->
                    val isSelected = when (currentScreen) {
                        tab.destination -> true
                        is ScreenDestination.EaSettings -> tab.destination == ScreenDestination.Accounts
                        is ScreenDestination.VpsManagement -> tab.destination == ScreenDestination.Accounts
                        is ScreenDestination.RiskManagement -> tab.destination == ScreenDestination.Accounts
                        is ScreenDestination.BridgeGuide -> tab.destination == ScreenDestination.Profile
                        is ScreenDestination.PublisherWorkspace -> tab.destination == ScreenDestination.Profile
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
                                fontSize = 11.sp
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
            when (val screen = currentScreen) {
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
