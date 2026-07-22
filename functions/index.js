// Fires the actual push notification whenever the app writes a new document to
// `notifications` (edit requests, approvals, allotment flags, etc.) — this is
// the piece the client can't safely do itself, since sending to an arbitrary
// device token requires Admin SDK credentials.
const { onDocumentCreated } = require("firebase-functions/v2/firestore");
const { initializeApp } = require("firebase-admin/app");
const { getFirestore } = require("firebase-admin/firestore");
const { getMessaging } = require("firebase-admin/messaging");

initializeApp();
const db = getFirestore();
const messaging = getMessaging();

exports.sendNotificationPush = onDocumentCreated("notifications/{notificationId}", async (event) => {
  const data = event.data?.data();
  if (!data) return;

  const tokens = new Set();

  if (data.recipientStaffId) {
    const doc = await db.collection("staff").doc(data.recipientStaffId).get();
    const token = doc.data()?.fcmToken;
    if (token) tokens.add(token);
  }

  if (data.recipientRole) {
    const snap = await db.collection("staff")
      .where("role", "==", data.recipientRole)
      .where("isActive", "==", true)
      .get();
    snap.forEach((doc) => {
      const token = doc.data().fcmToken;
      if (token) tokens.add(token);
    });
  }

  if (tokens.size === 0) {
    console.log(`No push tokens to notify for ${event.params.notificationId}`);
    return;
  }

  // Data-only payload (no `notification` block) — the app's own
  // FirebaseMessagingService decides how to display it, consistently across
  // foreground/background/killed states.
  const message = {
    data: {
      title: data.title || "Kalaza Care",
      message: data.message || "",
      targetRoute: data.targetRoute || "",
    },
    tokens: Array.from(tokens),
  };

  const response = await messaging.sendEachForMulticast(message);
  console.log(`Sent ${response.successCount}/${tokens.size} push(es) for notification ${event.params.notificationId}`);
});
