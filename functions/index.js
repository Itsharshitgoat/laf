const functions = require("firebase-functions");
const admin = require("firebase-admin");

admin.initializeApp();

/**
 * Automatically triggers whenever a new notification document is created
 * in Firestore and delivers a real background push notification via FCM.
 */
exports.sendPushNotificationOnCreate = functions.firestore
  .document("notifications/{notificationId}")
  .onCreate(async (snapshot, context) => {
    const notificationData = snapshot.data();
    if (!notificationData) return null;

    const { userId, title, message, itemId, type } = notificationData;
    if (!userId) {
      console.log("No recipient userId specified; skipping push notification.");
      return null;
    }

    try {
      const userDoc = await admin.firestore().collection("users").doc(userId).get();
      if (!userDoc.exists) {
        console.log(`User document ${userId} not found.`);
        return null;
      }

      const fcmToken = userDoc.data().fcmToken;
      if (!fcmToken || typeof fcmToken !== "string" || fcmToken.trim() === "") {
        console.log(`User ${userId} does not have an active FCM token registered.`);
        return null;
      }

      const payload = {
        token: fcmToken.trim(),
        notification: {
          title: title || "Lost & Found Alert",
          body: message || "You have a new update on your report."
        },
        data: {
          itemId: itemId ? String(itemId) : "",
          type: type ? String(type) : "general",
          notificationId: context.params.notificationId
        },
        android: {
          priority: "high",
          notification: {
            channelId: "laf_notifications_channel",
            sound: "default",
            clickAction: "FLUTTER_NOTIFICATION_CLICK"
          }
        }
      };

      const response = await admin.messaging().send(payload);
      console.log(`Successfully sent FCM push to user ${userId}:`, response);
      return response;
    } catch (error) {
      console.error(`Error sending push notification to user ${userId}:`, error);

      // Clean up stale or unregistered tokens
      if (
        error.code === "messaging/invalid-registration-token" ||
        error.code === "messaging/registration-token-not-registered"
      ) {
        console.log(`Removing invalid FCM token for user ${userId}`);
        await admin.firestore().collection("users").doc(userId).update({
          fcmToken: admin.firestore.FieldValue.delete()
        });
      }
      return null;
    }
  });
