package com.kalazacare.app.util

import com.kalazacare.app.data.model.Staff
import com.kalazacare.app.data.model.UserRole

object SessionManager {
    private var currentStaff: Staff? = null

    fun setCurrentStaff(staff: Staff) { currentStaff = staff }
    fun getCurrentStaff(): Staff? = currentStaff
    fun isLoggedIn(): Boolean = currentStaff != null
    fun isAdmin(): Boolean = currentStaff?.role == UserRole.ADMIN
    fun isSupervisor(): Boolean = currentStaff?.role == UserRole.SUPERVISOR
    fun getCurrentStaffName(): String = currentStaff?.name ?: "Unknown"
    fun getCurrentStaffId(): String = currentStaff?.id ?: ""
    fun logout() { currentStaff = null }
}
