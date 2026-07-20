package com.kalazacare.app.ui.config

import android.util.Patterns
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.kalazacare.app.data.model.Staff
import com.kalazacare.app.data.model.UserRole
import com.kalazacare.app.data.model.displayLabel
import com.kalazacare.app.ui.components.RoleBadge
import com.kalazacare.app.ui.components.StatusBadge
import com.kalazacare.app.ui.theme.KalazaRed
import java.time.format.DateTimeFormatter

@Composable
fun StaffEditor(
    staffList: List<Staff>,
    onAddStaff: (name: String, email: String, phone: String, role: UserRole, password: String, onResult: (Boolean, String) -> Unit) -> Unit,
    onRevokeStaff: (String) -> Unit,
    onUnrevokeStaff: (String) -> Unit,
    onDeleteStaff: (String) -> Unit
) {
    val context = LocalContext.current
    val currentStaffId = com.kalazacare.app.util.SessionManager.getCurrentStaffId()
    var showAddDialog by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(staffList) { staff ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        // ── Header: name + role badge always get the full width,
                        // never compressed by whatever actions appear below. ──
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = staff.name,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            RoleBadge(role = staff.role)
                            if (!staff.isActive) {
                                Spacer(modifier = Modifier.width(8.dp))
                                StatusBadge(
                                    "Revoked",
                                    MaterialTheme.colorScheme.errorContainer,
                                    MaterialTheme.colorScheme.onErrorContainer,
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = staff.email,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Joined: ${staff.joinedDate.format(DateTimeFormatter.ofPattern("dd MMM yyyy"))}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        // ── Footer: actions, laid out on their own row so they
                        // never fight the header for space. ──
                        if (staff.isActive) {
                            if (staff.id != currentStaffId) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                    TextButton(onClick = { onRevokeStaff(staff.id) }) {
                                        Text("Revoke", color = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        } else {
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                TextButton(onClick = { onUnrevokeStaff(staff.id) }) {
                                    Text("Activate", color = KalazaRed)
                                }
                                TextButton(onClick = { onDeleteStaff(staff.id) }) {
                                    Text("Delete", color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }
            item { Spacer(modifier = Modifier.height(80.dp)) }
        }

        FloatingActionButton(
            onClick = { showAddDialog = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            containerColor = KalazaRed,
            contentColor = MaterialTheme.colorScheme.onPrimary
        ) {
            Icon(Icons.Filled.Add, contentDescription = "Add Staff")
        }

        if (showAddDialog) {
            AddStaffDialog(
                onDismiss = { showAddDialog = false },
                onAddStaff = { name, email, phone, role, password ->
                    onAddStaff(name, email, phone, role, password) { success, message ->
                        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                        if (success) showAddDialog = false
                    }
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddStaffDialog(
    onDismiss: () -> Unit,
    onAddStaff: (name: String, email: String, phone: String, role: UserRole, password: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }
    var selectedRole by remember { mutableStateOf(UserRole.STAFF) }
    val isPasswordValid = password.length >= 8

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Staff") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Full Name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                val isEmailValid = email.isBlank() || Patterns.EMAIL_ADDRESS.matcher(email).matches()
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it.trim() },
                    label = { Text("Email") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = !isEmailValid,
                    supportingText = if (!isEmailValid) { { Text("Enter a valid email address") } } else null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
                )
                val isPhoneValid = phone.isBlank() || phone.length == 10
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it.filter { c -> c.isDigit() }.take(10) },
                    label = { Text("Phone") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = !isPhoneValid,
                    supportingText = if (!isPhoneValid) { { Text("Must be 10 digits") } } else null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
                )

                // Assigned here, at creation time — there's no separate invite/setup
                // step; this is what the staff member logs in with, alongside their name.
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = password.isNotEmpty() && !isPasswordValid,
                    supportingText = { Text("At least 8 characters") },
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                contentDescription = if (passwordVisible) "Hide password" else "Show password"
                            )
                        }
                    }
                )

                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded }
                ) {
                    OutlinedTextField(
                        value = selectedRole.displayLabel(),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Role") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        // SuperAdmin can assign any of the three operational roles —
                        // SUPER_ADMIN accounts aren't created through this dialog.
                        UserRole.entries.filter { it != UserRole.SUPER_ADMIN }.forEach { role ->
                            DropdownMenuItem(
                                text = { Text(role.displayLabel()) },
                                onClick = {
                                    selectedRole = role
                                    expanded = false
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onAddStaff(name.trim(), email, phone, selectedRole, password) },
                colors = ButtonDefaults.buttonColors(containerColor = KalazaRed),
                enabled = name.isNotBlank() &&
                    email.isNotBlank() && Patterns.EMAIL_ADDRESS.matcher(email).matches() &&
                    phone.length == 10 && isPasswordValid
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
