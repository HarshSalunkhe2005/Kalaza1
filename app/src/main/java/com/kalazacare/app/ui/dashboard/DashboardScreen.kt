package com.kalazacare.app.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kalazacare.app.ui.DashboardViewModel
import com.kalazacare.app.ui.components.KalazaTopBar
import com.kalazacare.app.ui.components.NotificationBell
import com.kalazacare.app.ui.components.PatientCard
import com.kalazacare.app.ui.theme.KalazaRed
import com.kalazacare.app.ui.theme.Outline
import com.kalazacare.app.ui.theme.SurfaceVariant
import com.kalazacare.app.ui.theme.White

@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel = viewModel(),
    onPatientClick: (String) -> Unit,
    onAddPatient: () -> Unit
) {
    val patients by viewModel.patients.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val pendingMeds by viewModel.pendingMeds.collectAsState()
    val pendingApprovals by viewModel.pendingApprovals.collectAsState()

    Scaffold(
        topBar = {
            KalazaTopBar(
                title = "Dashboard",
                actions = {
                    NotificationBell(badgeCount = pendingApprovals + pendingMeds)
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddPatient,
                containerColor = KalazaRed,
                contentColor = White,
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(imageVector = Icons.Filled.Add, contentDescription = "Add Patient")
            }
        },
        containerColor = SurfaceVariant
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Search Bar
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = White,
                shadowElevation = 2.dp
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { viewModel.search(it) },
                    placeholder = { Text("Search patients by name or room...") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = "Search") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedBorderColor = Outline,
                        focusedBorderColor = KalazaRed
                    ),
                    singleLine = true
                )
            }

            // Patient List
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                contentPadding = PaddingValues(top = 16.dp, bottom = 80.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
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
