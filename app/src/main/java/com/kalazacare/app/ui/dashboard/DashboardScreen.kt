package com.kalazacare.app.ui.dashboard

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kalazacare.app.ui.DashboardViewModel
import com.kalazacare.app.ui.components.EmptyState
import com.kalazacare.app.ui.components.KalazaTopBar
import com.kalazacare.app.ui.components.NotificationBell
import com.kalazacare.app.ui.components.PatientCard
import com.kalazacare.app.ui.theme.KalazaRed
import com.kalazacare.app.util.SessionManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    onPatientClick: (String) -> Unit,
    onAddPatient: () -> Unit,
    onLogout: () -> Unit
) {
    val patients by viewModel.patients.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val pendingMeds by viewModel.pendingMeds.collectAsState()
    val pendingApprovals by viewModel.pendingApprovals.collectAsState()
    val totalPatients by viewModel.totalPatients.collectAsState()
    val showArchived by viewModel.showArchived.collectAsState()

    Scaffold(
        topBar = {
            KalazaTopBar(
                title = "Dashboard",
                onLogout = onLogout,
                actions = {
                    NotificationBell(count = pendingApprovals + pendingMeds)
                }
            )
        },
        floatingActionButton = {
            if (SessionManager.isAdmin()) {
                FloatingActionButton(
                    onClick = onAddPatient,
                    containerColor = KalazaRed,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ) {
                    Icon(Icons.Default.Add, "Add Patient")
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Good morning, ${SessionManager.getCurrentStaffName()}",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Quick Stats Row
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                item { StatCard("Total Patients", totalPatients.toString()) }
                item { StatCard("Pending Meds", pendingMeds.toString(), isAlert = pendingMeds > 0) }
                item { StatCard("Pending Approvals", pendingApprovals.toString(), isAlert = pendingApprovals > 0) }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.search(it) },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search patients...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                singleLine = true,
                shape = MaterialTheme.shapes.medium
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (SessionManager.isAdmin()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Show Archived",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Switch(
                        checked = showArchived,
                        onCheckedChange = { viewModel.setShowArchived(it) },
                        colors = SwitchDefaults.colors(checkedTrackColor = KalazaRed)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Patient List
            if (patients.isEmpty()) {
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    EmptyState(
                        title = "No Patients Found",
                        message = if (searchQuery.isBlank()) "No patients found." else "No patients match your search.",
                        icon = Icons.Default.Search
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(bottom = 80.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(patients) { patient ->
                        PatientCard(
                            patient = patient,
                            onClick = { onPatientClick(patient.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatCard(title: String, value: String, isAlert: Boolean = false) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isAlert) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant
        ),
        modifier = Modifier.widthIn(min = 120.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = if (isAlert) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = if (isAlert) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
