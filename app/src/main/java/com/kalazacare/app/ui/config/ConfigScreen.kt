package com.kalazacare.app.ui.config

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kalazacare.app.ui.ConfigViewModel
import com.kalazacare.app.ui.components.KalazaTopBar
import com.kalazacare.app.ui.sync.SyncConflictsTab
import com.kalazacare.app.ui.theme.KalazaRed

@Composable
fun ConfigScreen(
    viewModel: ConfigViewModel,
    onBack: () -> Unit,
    onLogout: () -> Unit
) {
    val staffList by viewModel.staffList.collectAsState()
    val utilItems by viewModel.utilItems.collectAsState()
    val syncConflicts by viewModel.syncConflicts.collectAsState()

    var selectedTabIndex by remember { mutableStateOf(0) }
    val roundWindows by viewModel.roundWindows.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current
    val tabs = listOf("Staff", "Utility Items", "Round Times", "Sync" + if (syncConflicts.isNotEmpty()) " (${syncConflicts.size})" else "")

    Scaffold(
        topBar = {
            KalazaTopBar(
                title = "Admin Configuration",
                onBack = onBack,
                onLogout = onLogout,
                onRefresh = { viewModel.load() }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            TabRow(
                selectedTabIndex = selectedTabIndex,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = KalazaRed,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex]),
                        color = KalazaRed,
                        height = 3.dp
                    )
                }
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        text = {
                            Text(
                                text = title,
                                fontWeight = if (selectedTabIndex == index) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        selectedContentColor = KalazaRed,
                        unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            when (selectedTabIndex) {
                0 -> StaffEditor(
                    staffList = staffList,
                    onAddStaff = { name, email, phone, role, password, onResult ->
                        viewModel.addStaff(name, email, phone, role, password, onResult)
                    },
                    onRevokeStaff = { viewModel.revokeStaff(it) },
                    onUnrevokeStaff = { viewModel.unrevokeStaff(it) },
                    onDeleteStaff = { viewModel.deleteStaff(it) },
                    onResetPassword = { staffId, newPassword, onResult ->
                        viewModel.resetPassword(staffId, newPassword, onResult)
                    }
                )
                1 -> UtilItemsEditor(
                    items = utilItems,
                    onAddItem = { viewModel.addUtilityItem(it) },
                    onUpdateItem = { viewModel.updateUtilityItem(it) },
                    onDeleteItem = { viewModel.deleteUtilityItem(it) }
                )
                2 -> RoundTimesEditor(
                    windows = roundWindows,
                    onSave = { tag, start, end, onResult ->
                        viewModel.saveRoundWindow(tag, start, end) { ok, msg ->
                            android.widget.Toast.makeText(context, if (ok) "Round times saved" else msg, android.widget.Toast.LENGTH_LONG).show()
                            onResult(ok, msg)
                        }
                    }
                )
                3 -> SyncConflictsTab(
                    conflicts = syncConflicts,
                    onDismiss = { viewModel.dismissConflict(it) }
                )
            }
        }
    }
}
