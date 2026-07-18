package com.kalazacare.app.util

import com.kalazacare.app.data.model.Staff
import com.kalazacare.app.data.model.UserRole

/**
 * Manages the currently logged-in session.
 * Will be replaced with Firebase Auth when backend is wired.
 */
object SessionManager {
    private var currentStaff: Staff? = null

    fun setCurrentStaff(staff: Staff) { currentStaff = staff }

    fun getCurrentStaff(): Staff? = currentStaff

    fun isLoggedIn(): Boolean = currentStaff != null

    fun isAdmin(): Boolean = currentStaff?.role == UserRole.ADMIN

    fun isMedicineStaff(): Boolean = currentStaff?.role == UserRole.MEDICINE_STAFF

    fun getCurrentStaffName(): String = currentStaff?.name ?: "Unknown"

    fun getCurrentStaffId(): String = currentStaff?.id ?: ""

    fun logout() { currentStaff = null }
}
