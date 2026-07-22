package com.kalazacare.app.data.repository

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.kalazacare.app.data.model.Staff
import com.kalazacare.app.data.model.UserRole
import kotlinx.coroutines.tasks.await
import java.time.LocalDate
import java.util.UUID

private const val STAFF_COLLECTION = "staff"

/**
 * Firestore doesn't have a built-in java.time.LocalDate mapper, and Staff carries
 * one (joinedDate) — so this file reads/writes plain field maps instead of relying
 * on automatic POJO (de)serialization, converting LocalDate <-> ISO string by hand.
 */
private fun Staff.toFirestoreMap(): Map<String, Any> = mapOf(
    "name" to name,
    "nameLower" to name.trim().lowercase(),   // for case-insensitive lookup by name
    "email" to email,
    "role" to role.name,
    "phone" to phone,
    "isActive" to isActive,
    "joinedDate" to joinedDate.toString(),
    "authEmail" to authEmail,
)

private fun staffFromDocument(id: String, data: Map<String, Any?>): Staff = Staff(
    id = id,
    name = data["name"] as? String ?: "",
    email = data["email"] as? String ?: "",
    role = (data["role"] as? String)?.let { runCatching { UserRole.valueOf(it) }.getOrNull() } ?: UserRole.STAFF,
    phone = data["phone"] as? String ?: "",
    isActive = data["isActive"] as? Boolean ?: true,
    joinedDate = (data["joinedDate"] as? String)?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: LocalDate.now(),
    authEmail = data["authEmail"] as? String ?: "",
)

/**
 * Firebase Auth's password provider needs an email — login here is still strictly
 * by staff Name + password, but under the hood each staff member gets a synthetic,
 * never-shown email combining their name with a random UUID for uniqueness.
 */
private fun synthesizeAuthEmail(name: String): String {
    val slug = name.trim().lowercase().replace(Regex("[^a-z0-9]+"), ".").trim('.').ifBlank { "staff" }
    return "$slug.${UUID.randomUUID()}@staff.kalazacare.internal"
}

/**
 * A second, independent FirebaseAuth instance used only for creating new staff
 * accounts. Firebase's client SDK signs the caller in as whichever user
 * createUserWithEmailAndPassword just created — on the *default* FirebaseAuth
 * instance, that would silently hijack the Super Admin's own session onto the
 * brand-new account. Running account creation against this separate instance
 * keeps the Super Admin signed in on the default one throughout.
 */
private fun secondaryAuth(context: Context): FirebaseAuth {
    val name = "StaffCreation"
    val app = FirebaseApp.getApps(context).firstOrNull { it.name == name }
        ?: FirebaseApp.initializeApp(context, FirebaseApp.getInstance().options, name)
    return FirebaseAuth.getInstance(app)
}

class FirebaseAuthRepository(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
) : AuthRepository {
    private var loggedIn: Staff? = null

    override suspend fun login(name: String, password: String): Staff? {
        val trimmedName = name.trim()
        if (trimmedName.isBlank() || password.isBlank()) return null

        val snapshot = firestore.collection(STAFF_COLLECTION)
            .whereEqualTo("nameLower", trimmedName.lowercase())
            .limit(1)
            .get().await()
        val doc = snapshot.documents.firstOrNull() ?: return null
        val data = doc.data ?: return null
        val staff = staffFromDocument(doc.id, data)
        if (!staff.isActive || staff.authEmail.isBlank()) return null

        return try {
            auth.signInWithEmailAndPassword(staff.authEmail, password).await()
            loggedIn = staff
            staff
        } catch (_: Exception) {
            null
        }
    }

    override fun logout() {
        auth.signOut()
        loggedIn = null
    }

    override fun currentStaff(): Staff? = loggedIn
}

class FirestoreStaffRepository(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val appContext: Context,
) : StaffRepository {
    private val staffCollection = firestore.collection(STAFF_COLLECTION)

    override suspend fun getAllStaff(): List<Staff> {
        val snapshot = staffCollection.get().await()
        return snapshot.documents
            .mapNotNull { doc -> doc.data?.let { staffFromDocument(doc.id, it) } }
            .sortedBy { it.name }
    }

    override suspend fun addStaff(name: String, email: String, phone: String, role: UserRole, password: String): Staff {
        val trimmedName = name.trim()
        val existing = staffCollection.whereEqualTo("nameLower", trimmedName.lowercase()).limit(1).get().await()
        if (!existing.isEmpty) throw DuplicateStaffNameException(trimmedName)

        val authEmail = synthesizeAuthEmail(trimmedName)

        // Creates the actual login credential on the secondary auth instance (see
        // secondaryAuth doc) — this is where the Super Admin's assigned password
        // ends up, hashed and stored by Firebase Auth itself. The resulting UID
        // becomes the Firestore document ID too, so security rules can look a
        // caller's own staff doc up directly via request.auth.uid.
        val worker = secondaryAuth(appContext)
        val result = worker.createUserWithEmailAndPassword(authEmail, password).await()
        val uid = result.user?.uid ?: error("Firebase did not return a UID for the new account")
        worker.signOut()

        val staff = Staff(
            id = uid,
            name = trimmedName,
            email = email,
            role = role,
            phone = phone,
            isActive = true,
            joinedDate = LocalDate.now(),
            authEmail = authEmail,
        )
        staffCollection.document(uid).set(staff.toFirestoreMap()).await()
        return staff
    }

    override suspend fun revokeStaff(id: String) {
        staffCollection.document(id).update("isActive", false).await()
    }

    override suspend fun unrevokeStaff(id: String) {
        staffCollection.document(id).update("isActive", true).await()
    }

    override suspend fun deleteStaff(id: String) {
        // Removes the staff profile. The linked Firebase Auth account can't be
        // deleted from here — the client SDK can only delete the *currently signed-in*
        // user, not an arbitrary one; that needs the Admin SDK (a Cloud Function).
        // It's left orphaned but harmless: with no Firestore doc, login() above can
        // never resolve it back to a Staff, so the account can't sign in again.
        staffCollection.document(id).delete().await()
    }

    override suspend fun updateStaff(staff: Staff) {
        staffCollection.document(staff.id).set(staff.toFirestoreMap()).await()
    }
}
