package com.kalazacare.app.data.repository

import com.kalazacare.app.data.model.Staff
import com.kalazacare.app.data.model.UserRole
import com.kalazacare.app.data.remote.SupabaseClients
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.util.UUID

private const val STAFF_TABLE = "staff"

@Serializable
private data class LoginLookupRow(
    val id: String,
    @SerialName("auth_email") val authEmail: String,
    @SerialName("is_active") val isActive: Boolean,
)
@Serializable
private data class NameLowerParam(@SerialName("p_name_lower") val pNameLower: String)

@Serializable
data class StaffRow(
    val id: String,
    val name: String,
    @SerialName("name_lower") val nameLower: String = "",
    val email: String = "",
    val role: String = "STAFF",
    val phone: String = "",
    @SerialName("is_active") val isActive: Boolean = true,
    @SerialName("joined_date") val joinedDate: String = LocalDate.now().toString(),
    @SerialName("auth_email") val authEmail: String = "",
    @SerialName("fcm_token") val fcmToken: String = "",
)

fun StaffRow.toDomain() = Staff(
    id = id, name = name, email = email,
    role = runCatching { UserRole.valueOf(role) }.getOrDefault(UserRole.STAFF),
    phone = phone, isActive = isActive,
    joinedDate = runCatching { LocalDate.parse(joinedDate) }.getOrDefault(LocalDate.now()),
    authEmail = authEmail, fcmToken = fcmToken,
)

fun Staff.toRow() = StaffRow(
    id = id, name = name, nameLower = name.trim().lowercase(), email = email, role = role.name,
    phone = phone, isActive = isActive, joinedDate = joinedDate.toString(),
    authEmail = authEmail, fcmToken = fcmToken,
)

/**
 * Supabase Auth's password provider needs an email — login here is still strictly
 * by staff Name + password, but under the hood each staff member gets a synthetic,
 * never-shown email combining their name with a random UUID for uniqueness.
 */
private fun synthesizeAuthEmail(name: String): String {
    val slug = name.trim().lowercase().replace(Regex("[^a-z0-9]+"), ".").trim('.').ifBlank { "staff" }
    return "$slug.${UUID.randomUUID()}@staff.kalazacare.internal"
}

class SupabaseAuthRepository(private val client: SupabaseClient) : AuthRepository {
    private var loggedIn: Staff? = null
    private val logoutScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override suspend fun login(name: String, password: String): Staff? {
        val trimmedName = name.trim()
        if (trimmedName.isBlank() || password.isBlank()) return null

        // Before sign-in there's no auth.uid() yet, so the normal RLS-guarded
        // `staff` table read (own row only) can't resolve name -> auth_email.
        // This narrow SECURITY DEFINER function exposes only the 3 fields
        // needed for that lookup instead of opening the whole table to anon.
        val lookup = client.postgrest.rpc("staff_login_lookup", NameLowerParam(trimmedName.lowercase()))
            .decodeSingleOrNull<LoginLookupRow>() ?: return null
        if (!lookup.isActive || lookup.authEmail.isBlank()) return null

        return try {
            client.auth.signInWith(Email) {
                email = lookup.authEmail
                this.password = password
            }
            // Now signed in, auth.uid() == lookup.id, so the full row is readable.
            val row = client.postgrest.from(STAFF_TABLE)
                .select { filter { eq("id", lookup.id) } }
                .decodeSingleOrNull<StaffRow>() ?: return null
            val staff = row.toDomain()
            loggedIn = staff
            staff
        } catch (_: Exception) {
            null
        }
    }

    override fun logout() {
        logoutScope.launch { runCatching { client.auth.signOut() } }
        loggedIn = null
    }

    override fun currentStaff(): Staff? = loggedIn
}

class SupabaseStaffRepository(private val client: SupabaseClient) : StaffRepository {

    override suspend fun getAllStaff(): List<Staff> =
        client.postgrest.from(STAFF_TABLE).select().decodeList<StaffRow>()
            .map { it.toDomain() }.sortedBy { it.name }

    override suspend fun addStaff(name: String, email: String, phone: String, role: UserRole, password: String): Staff {
        val trimmedName = name.trim()
        val existing = client.postgrest.from(STAFF_TABLE)
            .select { filter { eq("name_lower", trimmedName.lowercase()) } }
            .decodeSingleOrNull<StaffRow>()
        if (existing != null) throw DuplicateStaffNameException(trimmedName)

        val authEmail = synthesizeAuthEmail(trimmedName)

        // Creates the actual login credential on the secondary client (see
        // SupabaseClients.staffCreation doc) — this is where the Super Admin's
        // assigned password ends up, hashed and stored by Supabase Auth itself.
        // The resulting user id becomes the staff row's id too, so RLS policies
        // can look a caller's own staff row up directly via auth.uid().
        val worker = SupabaseClients.staffCreation
        worker.auth.signUpWith(Email) {
            this.email = authEmail
            this.password = password
        }
        val uid = worker.auth.currentUserOrNull()?.id
            ?: error("Supabase did not return a user id for the new account")
        worker.auth.signOut()

        val staff = Staff(
            id = uid, name = trimmedName, email = email, role = role, phone = phone,
            isActive = true, joinedDate = LocalDate.now(), authEmail = authEmail,
        )
        client.postgrest.from(STAFF_TABLE).insert(staff.toRow())
        return staff
    }

    override suspend fun revokeStaff(id: String) {
        client.postgrest.from(STAFF_TABLE).update(mapOf("is_active" to false)) { filter { eq("id", id) } }
    }

    override suspend fun unrevokeStaff(id: String) {
        client.postgrest.from(STAFF_TABLE).update(mapOf("is_active" to true)) { filter { eq("id", id) } }
    }

    override suspend fun deleteStaff(id: String) {
        // Removes the staff profile. The linked Auth account can't be deleted from
        // here — the client SDK can only act on the currently-signed-in user, not
        // an arbitrary one; that needs the Auth Admin API (service_role key). It's
        // left orphaned but harmless: with no staff row, login() above can never
        // resolve it back to a Staff, so the account can't sign in again.
        client.postgrest.from(STAFF_TABLE).delete { filter { eq("id", id) } }
    }

    override suspend fun updateStaff(staff: Staff) {
        client.postgrest.from(STAFF_TABLE).update(staff.toRow()) { filter { eq("id", staff.id) } }
    }

    override suspend fun updateFcmToken(staffId: String, token: String) {
        client.postgrest.from(STAFF_TABLE).update(mapOf("fcm_token" to token)) { filter { eq("id", staffId) } }
    }
}
