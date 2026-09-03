package com.kalazacare.app.ui.approval

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kalazacare.app.data.model.ApprovalRequest
import com.kalazacare.app.data.model.ApprovalStatus
import com.kalazacare.app.ui.ApprovalViewModel
import com.kalazacare.app.ui.components.ConfirmDialog
import com.kalazacare.app.ui.components.EmptyState
import com.kalazacare.app.ui.components.KalazaTopBar
import com.kalazacare.app.ui.components.ApprovalStatusBadge
import com.kalazacare.app.ui.theme.KalazaRed
import com.kalazacare.app.util.timeAgo
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApprovalQueueScreen(
    viewModel: ApprovalViewModel,
    onBack: () -> Unit,
    onLogout: () -> Unit
) {
    val requests by viewModel.requests.collectAsState()
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val tabs = listOf("Pending", "Approved", "Rejected")

    Scaffold(
        topBar = {
            KalazaTopBar(
                title = "Approval Queue",
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
                contentColor = KalazaRed,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex]),
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

            val filteredRequests = when (selectedTabIndex) {
                0 -> requests.filter { it.status == ApprovalStatus.PENDING }
                1 -> requests.filter { it.status == ApprovalStatus.APPROVED }
                else -> requests.filter { it.status == ApprovalStatus.REJECTED }
            }

            if (filteredRequests.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    EmptyState(
                        title = "No Requests",
                        message = "No ${tabs[selectedTabIndex].lowercase()} requests",
                        icon = if (selectedTabIndex == 1) Icons.Default.CheckCircle else Icons.Default.Inbox
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(filteredRequests) { request ->
                        ApprovalRequestCard(
                            request = request,
                            onApprove = { viewModel.approve(request.id) },
                            onReject = { reason -> viewModel.reject(request.id, reason) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ApprovalRequestCard(
    request: ApprovalRequest,
    onApprove: () -> Unit,
    onReject: (String) -> Unit
) {
    var showApproveConfirm by remember { mutableStateOf(false) }
    var showRejectDialog by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = request.patientName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                ApprovalStatusBadge(status = request.status)
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "Requested by ${request.requestedByName} • ${request.timestamp.timeAgo()}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))

            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "Field Changed: ${request.fieldChanged}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${request.oldValue.ifBlank { "None" }}  →  ${request.newValue.ifBlank { "None" }}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = KalazaRed
                    )
                }
            }

            if (request.status == ApprovalStatus.REJECTED && request.rejectionReason.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Reason: ${request.rejectionReason}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            if (request.status == ApprovalStatus.PENDING) {
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = { showRejectDialog = true },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Reject")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { showApproveConfirm = true },
                        colors = ButtonDefaults.buttonColors(containerColor = KalazaRed)
                    ) {
                        Text("Approve")
                    }
                }
            }
        }
    }

    if (showApproveConfirm) {
        ConfirmDialog(
            title = "Approve Request",
            message = "Are you sure you want to approve this change to ${request.patientName}'s record?",
            onConfirm = {
                onApprove()
                showApproveConfirm = false
            },
            onDismiss = { showApproveConfirm = false }
        )
    }

    if (showRejectDialog) {
        RejectDialog(
            onReject = { reason ->
                onReject(reason)
                showRejectDialog = false
            },
            onDismiss = { showRejectDialog = false }
        )
    }
}

@Composable
private fun RejectDialog(
    onReject: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var reason by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Reject Request") },
        text = {
            OutlinedTextField(
                value = reason,
                onValueChange = { reason = it },
                label = { Text("Reason for rejection") },
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(
                onClick = { onReject(reason) },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                enabled = reason.isNotBlank()
            ) {
                Text("Reject")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
