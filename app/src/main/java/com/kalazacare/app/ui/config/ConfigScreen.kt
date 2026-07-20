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
import com.kalazacare.app.ui.theme.KalazaRed

@Composable
fun ConfigScreen(
    viewModel: ConfigViewModel,
    onBack: () -> Unit,
    onLogout: () -> Unit
) {
    val staffList by viewModel.staffList.collectAsState()
    val utilItems by viewModel.utilItems.collectAsState()

    var selectedTabIndex by remember { mutableStateOf(0) }
    val tabs = listOf("Staff Management", "Utility Items")

    Scaffold(
        topBar = {
            KalazaTopBar(
                title = "Admin Configuration",
                onBack = onBack,
                onLogout = onLogout
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
                    onDeleteStaff = { viewModel.deleteStaff(it) }
                )
                1 -> UtilItemsEditor(
                    items = utilItems,
                    onAddItem = { viewModel.addUtilityItem(it) },
                    onDeleteItem = { viewModel.deleteUtilityItem(it) }
                )
            }
        }
    }
}
