package com.kalazacare.app.util

import com.kalazacare.app.data.model.Staff
import com.kalazacare.app.data.model.UserRole

object SessionManager {
    private var currentStaff: Staff? = null

    fun setCurrentStaff(staff: Staff) { currentStaff = staff }
    fun getCurrentStaff(): Staff? = currentStaff
    fun isLoggedIn(): Boolean = currentStaff != null
    // isAdmin() means SuperAdmin — the old, fully-privileged Admin role. Every
    // existing isAdmin()-gated feature keeps working exactly as before; only the
    // enum name changed underneath it.
    fun isAdmin(): Boolean = currentStaff?.role == UserRole.SUPER_ADMIN
    fun isSupervisor(): Boolean = currentStaff?.role == UserRole.SUPERVISOR
    // The new, restricted Admin role — photo-audit only, no other access.
    fun isPhotoAdmin(): Boolean = currentStaff?.role == UserRole.ADMIN
    fun getCurrentStaffName(): String = currentStaff?.name ?: "Unknown"
    fun getCurrentStaffId(): String = currentStaff?.id ?: ""
    fun logout() { currentStaff = null }
}
