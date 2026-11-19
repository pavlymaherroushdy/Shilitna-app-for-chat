const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ADMIN_UID = "admin_pavly";
const FRIEND_UID = "friend_omar";
const STRANGER_UID = "stranger_999";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated users are rejected on rooms, members, and messages", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("rooms").doc("room_1").get());
  await assertFails(unauthDb.collection("rooms").get());
  await assertFails(
    unauthDb.collection("rooms").doc("room_1").collection("messages").get()
  );
});

test("Admin can create room + admin member in atomic batch and list own rooms", async () => {
  const adminDb = testEnv.authenticatedContext(ADMIN_UID, {
    email: "pavlymaherroushdy@gmail.com",
    email_verified: true,
  }).firestore();
  const now = new Date(Date.now() - 5000);

  const batch = adminDb.batch();
  batch.set(adminDb.collection("rooms").doc("room_1"), {
    name: "شلة السهرة",
    adminUid: ADMIN_UID,
    adminName: "Pavly",
    pin: "4829",
    memberUids: [ADMIN_UID],
    createdAt: now,
    updatedAt: now,
  });
  batch.set(adminDb.collection("rooms").doc("room_1").collection("members").doc(ADMIN_UID), {
    uid: ADMIN_UID,
    displayName: "Pavly",
    role: "admin",
    online: true,
    adminUid: ADMIN_UID,
    memberUids: [ADMIN_UID],
    lastSeen: now,
    joinedAt: now,
  });
  await assertSucceeds(batch.commit());

  await assertSucceeds(
    adminDb.collection("rooms").where("memberUids", "array-contains", ADMIN_UID).get()
  );
  // Unfiltered list fails
  await assertFails(
    testEnv.authenticatedContext(STRANGER_UID).firestore().collection("rooms").get()
  );
});

test("Friend can join room once, cannot escalate to admin, and cannot change PIN", async () => {
  const now = new Date(Date.now() - 10000);
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await db.collection("rooms").doc("room_1").set({
      name: "شلة السهرة",
      adminUid: ADMIN_UID,
      adminName: "Pavly",
      pin: "4829",
      memberUids: [ADMIN_UID],
      createdAt: now,
      updatedAt: now,
    });
    await db.collection("rooms").doc("room_1").collection("members").doc(ADMIN_UID).set({
      uid: ADMIN_UID,
      displayName: "Pavly",
      role: "admin",
      online: true,
      adminUid: ADMIN_UID,
      memberUids: [ADMIN_UID],
      lastSeen: now,
      joinedAt: now,
    });
  });

  const friendDb = testEnv.authenticatedContext(FRIEND_UID).firestore();
  const joinTime = new Date(Date.now() - 2000);

  // Friend cannot self-assign admin role
  const badBatch = friendDb.batch();
  badBatch.update(friendDb.collection("rooms").doc("room_1"), {
    memberUids: [ADMIN_UID, FRIEND_UID],
    updatedAt: joinTime,
  });
  badBatch.set(friendDb.collection("rooms").doc("room_1").collection("members").doc(FRIEND_UID), {
    uid: FRIEND_UID,
    displayName: "Omar",
    role: "admin",
    online: true,
    adminUid: ADMIN_UID,
    memberUids: [ADMIN_UID, FRIEND_UID],
    lastSeen: joinTime,
    joinedAt: joinTime,
  });
  await assertFails(badBatch.commit());

  // Valid member join succeeds atomically
  const okBatch = friendDb.batch();
  okBatch.update(friendDb.collection("rooms").doc("room_1"), {
    memberUids: [ADMIN_UID, FRIEND_UID],
    updatedAt: joinTime,
  });
  okBatch.update(friendDb.collection("rooms").doc("room_1").collection("members").doc(ADMIN_UID), {
    memberUids: [ADMIN_UID, FRIEND_UID],
  });
  okBatch.set(friendDb.collection("rooms").doc("room_1").collection("members").doc(FRIEND_UID), {
    uid: FRIEND_UID,
    displayName: "Omar",
    role: "member",
    online: true,
    adminUid: ADMIN_UID,
    memberUids: [ADMIN_UID, FRIEND_UID],
    lastSeen: joinTime,
    joinedAt: joinTime,
  });
  await assertSucceeds(okBatch.commit());

  // Friend cannot change room PIN
  await assertFails(
    friendDb.collection("rooms").doc("room_1").update({
      pin: "0000",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Room admin CAN update PIN
  const adminDb = testEnv.authenticatedContext(ADMIN_UID).firestore();
  await assertSucceeds(
    adminDb.collection("rooms").doc("room_1").update({
      pin: "9911",
      updatedAt: new Date(Date.now() - 1000),
    })
  );
});

test("Messages enforce membership, sender identity, and optional media fields", async () => {
  const now = new Date(Date.now() - 10000);
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await db.collection("rooms").doc("room_1").set({
      name: "شلة السهرة",
      adminUid: ADMIN_UID,
      adminName: "Pavly",
      pin: "4829",
      memberUids: [ADMIN_UID, FRIEND_UID],
      createdAt: now,
      updatedAt: now,
    });
  });

  const friendDb = testEnv.authenticatedContext(FRIEND_UID).firestore();
  const strangerDb = testEnv.authenticatedContext(STRANGER_UID).firestore();

  // Member sends text message
  await assertSucceeds(
    friendDb.collection("rooms").doc("room_1").collection("messages").doc("msg_1").set({
      senderId: FRIEND_UID,
      senderName: "Omar",
      text: "أهلاً بالشلة!",
      mediaType: "text",
      adminUid: ADMIN_UID,
      memberUids: [ADMIN_UID, FRIEND_UID],
      createdAt: new Date(Date.now() - 1000),
    })
  );

  // Member sends compressed image message (null optional fields omitted)
  await assertSucceeds(
    friendDb.collection("rooms").doc("room_1").collection("messages").doc("msg_2").set({
      senderId: FRIEND_UID,
      senderName: "Omar",
      text: "",
      mediaType: "image",
      mediaBase64: "AAAA",
      mediaName: "photo.jpg",
      adminUid: ADMIN_UID,
      memberUids: [ADMIN_UID, FRIEND_UID],
      createdAt: new Date(Date.now() - 1000),
    })
  );

  // Stranger cannot read messages (unfiltered or targeting another member) or send messages
  await assertFails(
    strangerDb.collection("rooms").doc("room_1").collection("messages").get()
  );
  await assertFails(
    strangerDb.collection("rooms").doc("room_1").collection("messages")
      .where("memberUids", "array-contains", FRIEND_UID).get()
  );
  await assertFails(
    strangerDb.collection("rooms").doc("room_1").collection("messages").doc("msg_3").set({
      senderId: STRANGER_UID,
      senderName: "Hacker",
      text: "Hello",
      mediaType: "text",
      adminUid: ADMIN_UID,
      memberUids: [ADMIN_UID, FRIEND_UID],
      createdAt: new Date(Date.now() - 1000),
    })
  );

  // Member cannot spoof another senderId or inject ghost field
  await assertFails(
    friendDb.collection("rooms").doc("room_1").collection("messages").doc("msg_4").set({
      senderId: ADMIN_UID,
      senderName: "Pavly",
      text: "Spoof",
      mediaType: "text",
      adminUid: ADMIN_UID,
      memberUids: [ADMIN_UID, FRIEND_UID],
      createdAt: new Date(Date.now() - 1000),
    })
  );
  await assertFails(
    friendDb.collection("rooms").doc("room_1").collection("messages").doc("msg_5").set({
      senderId: FRIEND_UID,
      senderName: "Omar",
      text: "Ghost",
      mediaType: "text",
      isVerified: true,
      adminUid: ADMIN_UID,
      memberUids: [ADMIN_UID, FRIEND_UID],
      createdAt: new Date(Date.now() - 1000),
    })
  );
});

test("Email spoofing test: unverified platform admin email is denied", async () => {
  const now = new Date(Date.now() - 10000);
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().collection("rooms").doc("room_1").set({
      name: "شلة السهرة",
      adminUid: ADMIN_UID,
      adminName: "Pavly",
      pin: "4829",
      memberUids: [ADMIN_UID],
      createdAt: now,
      updatedAt: now,
    });
  });

  const spoofedDb = testEnv.authenticatedContext(STRANGER_UID, {
    email: "pavlymaherroushdy@gmail.com",
    email_verified: false,
  }).firestore();

  await assertFails(
    spoofedDb.collection("rooms").doc("room_1").update({
      pin: "1234",
      updatedAt: new Date(Date.now() - 1000),
    })
  );
});
