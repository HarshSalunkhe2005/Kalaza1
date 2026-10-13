package com.kalazacare.app.util

import com.kalazacare.app.data.model.Staff
import com.kalazacare.app.data.model.UserRole

object SessionManager {
    private var currentStaff: Staff? = null

    // A new session (login, bypass login, restore after process death) re-anchors the trusted clock to the server.
    fun setCurrentStaff(staff: Staff) { currentStaff = staff; TrustedClock.syncAsync() }
    fun getCurrentStaff(): Staff? = currentStaff
    fun isLoggedIn(): Boolean = currentStaff != null
    // isAdmin() is true for Super Admin AND Admin — they share every privilege.
    fun isAdmin(): Boolean = currentStaff?.role.let { it == UserRole.SUPER_ADMIN || it == UserRole.ADMIN }
    // Only the Super Admin can create/remove Admins.
    fun isSuperAdmin(): Boolean = currentStaff?.role == UserRole.SUPER_ADMIN
    fun isSupervisor(): Boolean = currentStaff?.role == UserRole.SUPERVISOR
    fun getCurrentStaffName(): String = currentStaff?.name ?: "Unknown"
    fun getCurrentStaffId(): String = currentStaff?.id ?: ""
    fun logout() { currentStaff = null }
}
