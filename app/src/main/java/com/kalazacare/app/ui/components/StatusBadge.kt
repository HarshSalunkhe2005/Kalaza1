package com.kalazacare.app.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kalazacare.app.data.model.ApprovalStatus
import com.kalazacare.app.data.model.MedStatus
import com.kalazacare.app.data.model.UserRole
import com.kalazacare.app.data.model.displayLabel
import com.kalazacare.app.ui.theme.*

@Composable
fun StatusBadge(text: String, containerColor: Color, contentColor: Color) {
    Surface(
        color  = containerColor,
        shape  = RoundedCornerShape(50.dp),
    ) {
        Text(
            text     = text,
            style    = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
            color    = contentColor,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

@Composable
fun ApprovalStatusBadge(status: ApprovalStatus) {
    when (status) {
        ApprovalStatus.PENDING  -> StatusBadge("Pending",  Color(0xFFFFF3CD), Color(0xFF856404))
        ApprovalStatus.APPROVED -> StatusBadge("Approved", Color(0xFFD4EDDA), Color(0xFF155724))
        ApprovalStatus.REJECTED -> StatusBadge("Rejected", Color(0xFFF8D7DA), Color(0xFF721C24))
    }
}

@Composable
fun MedStatusBadge(status: MedStatus) {
    when (status) {
        MedStatus.ADMINISTERED -> StatusBadge("Given ✓",  Color(0xFFD4EDDA), Color(0xFF155724))
        MedStatus.PENDING      -> StatusBadge("Pending",  Color(0xFFFFF3CD), Color(0xFF856404))
        MedStatus.OVERDUE      -> StatusBadge("Overdue!", Color(0xFFF8D7DA), Color(0xFF721C24))
    }
}

@Composable
fun RoleBadge(role: UserRole) {
    when (role) {
        UserRole.ADMIN          -> StatusBadge(role.displayLabel(), KalazaRed, White)
        UserRole.MEDICINE_STAFF -> StatusBadge(role.displayLabel(), Color(0xFFD1E7DD), Color(0xFF0F5132))
        UserRole.STAFF          -> StatusBadge(role.displayLabel(), SurfaceVariant, OnSurfaceVariant)
    }
}
