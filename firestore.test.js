const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");
const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_owner_123";
const ALICE_EMAIL = "alice@gullzubair.test";
const BOB_UID = "bob_member_456";
const BOB_EMAIL = "bob@gullzubair.test";
const CHARLIE_UID = "charlie_outsider_789";
const CHARLIE_EMAIL = "charlie@outsider.test";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function pastDate(msOffset = 2000) {
  return new Date(Date.now() - msOffset);
}

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

test("Unauthenticated user: denied across all root and workspace collections", async () => {
  const unauth = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauth.collection("users").doc(ALICE_UID).get());
  await assertFails(unauth.collection("workspace_invites").get());
  await assertFails(unauth.collection("users").doc(ALICE_UID).collection("stores").get());
  await assertFails(unauth.collection("users").doc(ALICE_UID).collection("sales").get());
  await assertFails(
    unauth.collection("users").doc(ALICE_UID).collection("stores").doc("store_1").set({
      ownerUid: ALICE_UID,
      memberUids: [ALICE_UID],
      storeKey: "STORE_1",
      name: "STORE 1",
      formulaId: "",
      salePrice: 1200,
      active: true,
      createdAt: pastDate(),
      updatedAt: pastDate(),
    })
  );
});

test("User profile: owner bootstrap succeeds, privilege escalation and @DocumentId 'id' rejected", async () => {
  const alice = testEnv.authenticatedContext(ALICE_UID, { email: ALICE_EMAIL, email_verified: true }).firestore();
  const now = pastDate();

  // Valid owner self-bootstrap
  await assertSucceeds(
    alice.collection("users").doc(ALICE_UID).set({
      uid: ALICE_UID,
      email: ALICE_EMAIL,
      displayName: "Alice Admin",
      workspaceOwnerUid: ALICE_UID,
      role: "admin",
      roles: ["admin"],
      approved: true,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Valid harmless displayName update
  await assertSucceeds(
    alice.collection("users").doc(ALICE_UID).update({
      displayName: "Alice Owner",
      updatedAt: pastDate(500),
    })
  );

  // Reject ghost field / @DocumentId `id`
  const bob = testEnv.authenticatedContext(BOB_UID, { email: BOB_EMAIL, email_verified: true }).firestore();
  await assertFails(
    bob.collection("users").doc(BOB_UID).set({
      id: BOB_UID,
      uid: BOB_UID,
      email: BOB_EMAIL,
      displayName: "Bob",
      workspaceOwnerUid: BOB_UID,
      role: "admin",
      roles: ["admin"],
      approved: true,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Reject joining Alice's workspace without a valid invite
  await assertFails(
    bob.collection("users").doc(BOB_UID).set({
      uid: BOB_UID,
      email: BOB_EMAIL,
      displayName: "Bob Intruder",
      workspaceOwnerUid: ALICE_UID,
      role: "member",
      roles: ["sales"],
      approved: true,
      createdAt: now,
      updatedAt: now,
    })
  );
});

test("Workspace invites & verified-email member onboarding", async () => {
  const alice = testEnv.authenticatedContext(ALICE_UID, { email: ALICE_EMAIL, email_verified: true }).firestore();
  const bobVerified = testEnv.authenticatedContext(BOB_UID, { email: BOB_EMAIL, email_verified: true }).firestore();
  const bobUnverified = testEnv.authenticatedContext(BOB_UID, { email: BOB_EMAIL, email_verified: false }).firestore();
  const charlie = testEnv.authenticatedContext(CHARLIE_UID, { email: CHARLIE_EMAIL, email_verified: true }).firestore();
  const now = pastDate();

  await assertSucceeds(
    alice.collection("workspace_invites").doc("inv_bob_1").set({
      ownerUid: ALICE_UID,
      ownerName: "Alice Admin",
      memberEmail: BOB_EMAIL,
      displayName: "Bob Sales",
      roles: ["sales", "rep"],
      approved: true,
      status: "pending",
      createdAt: now,
      updatedAt: now,
    })
  );

  // Unverified email cannot list/read invite
  await assertFails(
    bobUnverified.collection("workspace_invites").where("memberEmail", "==", BOB_EMAIL).get()
  );

  // Outsider Charlie cannot list/read Bob's invite
  await assertFails(
    charlie.collection("workspace_invites").where("memberEmail", "==", BOB_EMAIL).get()
  );
  await assertFails(charlie.collection("workspace_invites").get());

  // Verified Bob can list his own invite and join workspace as member (not admin)
  await assertSucceeds(
    bobVerified.collection("workspace_invites").where("memberEmail", "==", BOB_EMAIL).get()
  );
  await assertFails(
    bobVerified.collection("users").doc(BOB_UID).set({
      uid: BOB_UID,
      email: BOB_EMAIL,
      displayName: "Bob Sales",
      workspaceOwnerUid: ALICE_UID,
      role: "admin", // Escalation rejected
      roles: ["admin"],
      approved: true,
      inviteId: "inv_bob_1",
      createdAt: now,
      updatedAt: now,
    })
  );
  await assertSucceeds(
    bobVerified.collection("users").doc(BOB_UID).set({
      uid: BOB_UID,
      email: BOB_EMAIL,
      displayName: "Bob Sales",
      workspaceOwnerUid: ALICE_UID,
      role: "member",
      roles: ["sales", "rep"],
      approved: true,
      inviteId: "inv_bob_1",
      createdAt: now,
      updatedAt: now,
    })
  );
  await assertSucceeds(
    bobVerified.collection("workspace_invites").doc("inv_bob_1").update({
      status: "accepted",
      acceptedByUid: BOB_UID,
      updatedAt: pastDate(500),
    })
  );
});

test("Workspace business collections: Kotlin payloads, owner unfiltered list, member scoped query, outsider denied", async () => {
  const alice = testEnv.authenticatedContext(ALICE_UID, { email: ALICE_EMAIL, email_verified: true }).firestore();
  const bob = testEnv.authenticatedContext(BOB_UID, { email: BOB_EMAIL, email_verified: true }).firestore();
  const charlie = testEnv.authenticatedContext(CHARLIE_UID, { email: CHARLIE_EMAIL, email_verified: true }).firestore();
  const now = pastDate();

  // Seed user profiles
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await db.collection("users").doc(ALICE_UID).set({
      uid: ALICE_UID,
      email: ALICE_EMAIL,
      displayName: "Alice Admin",
      workspaceOwnerUid: ALICE_UID,
      role: "admin",
      roles: ["admin"],
      approved: true,
      createdAt: now,
      updatedAt: now,
    });
    await db.collection("users").doc(BOB_UID).set({
      uid: BOB_UID,
      email: BOB_EMAIL,
      displayName: "Bob Member",
      workspaceOwnerUid: ALICE_UID,
      role: "member",
      roles: ["sales"],
      approved: true,
      createdAt: now,
      updatedAt: now,
    });
  });

  // Unfiltered empty list query succeeds for owner
  await assertSucceeds(alice.collection("users").doc(ALICE_UID).collection("stores").get());
  await assertSucceeds(alice.collection("users").doc(ALICE_UID).collection("production").get());

  // Create StoreConfig (Kotlin-aligned payload, no id field)
  await assertSucceeds(
    alice.collection("users").doc(ALICE_UID).collection("stores").doc("store_main").set({
      ownerUid: ALICE_UID,
      memberUids: [ALICE_UID, BOB_UID],
      storeKey: "GULL_SPECIAL",
      name: "GULL SPECIAL",
      formulaId: "formula_1",
      salePrice: 1600,
      active: true,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Create ProductionRecord + SalesRecord + LedgerEntry
  await assertSucceeds(
    alice.collection("users").doc(ALICE_UID).collection("production").doc("prod_1").set({
      ownerUid: ALICE_UID,
      memberUids: [ALICE_UID, BOB_UID],
      date: "2026-11-24",
      storeKey: "GULL_SPECIAL",
      storeName: "GULL SPECIAL",
      formulaId: "formula_1",
      formulaUnits: 2,
      grossWeight: 52.5,
      containerWeight: 2.5,
      netWeight: 50.0,
      costPerKg: 1100.0,
      salePrice: 1600.0,
      totalCost: 55000.0,
      totalRevenue: 80000.0,
      profit: 25000.0,
      transfer: false,
      transferType: "none",
      notes: "Morning batch",
      recycled: false,
      recycledAtEpoch: 0,
      createdByUid: ALICE_UID,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Reject future timestamp
  await assertFails(
    alice.collection("users").doc(ALICE_UID).collection("sales").doc("sale_bad_ts").set({
      ownerUid: ALICE_UID,
      memberUids: [ALICE_UID],
      date: "2026-11-24",
      mode: "sale",
      customerName: "Khan Traders",
      storeKey: "GULL_SPECIAL",
      repName: "NONE",
      qtyKg: 10,
      ratePerKg: 1600,
      totalAmount: 16000,
      cashReceived: 10000,
      creditAmount: 6000,
      expiredKg: 0,
      choraValue: 0,
      addToLedger: true,
      recycled: false,
      recycledAtEpoch: 0,
      createdByUid: ALICE_UID,
      createdAt: new Date(Date.now() + 60000),
      updatedAt: now,
    })
  );

  // Partial update on production preserving createdAt succeeds; mutating createdAt fails
  await assertSucceeds(
    alice.collection("users").doc(ALICE_UID).collection("production").doc("prod_1").update({
      notes: "Updated batch note",
      updatedAt: pastDate(500),
    })
  );
  await assertFails(
    alice.collection("users").doc(ALICE_UID).collection("production").doc("prod_1").update({
      createdAt: pastDate(100),
      updatedAt: pastDate(100),
    })
  );

  // Member Bob can query with memberUids filter, cannot write directly to production
  await assertSucceeds(
    bob.collection("users").doc(ALICE_UID).collection("production")
      .where("memberUids", "array-contains", BOB_UID)
      .get()
  );
  await assertFails(
    bob.collection("users").doc(ALICE_UID).collection("production").doc("prod_bob").set({
      ownerUid: ALICE_UID,
      memberUids: [ALICE_UID, BOB_UID],
      date: "2026-11-24",
      storeKey: "GULL_SPECIAL",
      storeName: "GULL SPECIAL",
      formulaUnits: 1,
      grossWeight: 20,
      containerWeight: 0,
      netWeight: 20,
      costPerKg: 1000,
      salePrice: 1500,
      totalCost: 20000,
      totalRevenue: 30000,
      profit: 10000,
      transfer: false,
      transferType: "none",
      recycled: false,
      recycledAtEpoch: 0,
      createdByUid: BOB_UID,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Outsider Charlie cannot list unfiltered, query Bob's docs, or read Alice's doc
  await assertFails(
    charlie.collection("users").doc(ALICE_UID).collection("production").get()
  );
  await assertFails(
    charlie.collection("users").doc(ALICE_UID).collection("production")
      .where("memberUids", "array-contains", BOB_UID)
      .get()
  );
  await assertFails(charlie.collection("users").doc(ALICE_UID).collection("production").doc("prod_1").get());
});

test("Pending approvals workflow: member submits pending, cannot self-approve; admin approves", async () => {
  const alice = testEnv.authenticatedContext(ALICE_UID, { email: ALICE_EMAIL, email_verified: true }).firestore();
  const bob = testEnv.authenticatedContext(BOB_UID, { email: BOB_EMAIL, email_verified: true }).firestore();
  const now = pastDate();

  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await db.collection("users").doc(ALICE_UID).set({
      uid: ALICE_UID,
      email: ALICE_EMAIL,
      displayName: "Alice",
      workspaceOwnerUid: ALICE_UID,
      role: "admin",
      roles: ["admin"],
      approved: true,
      createdAt: now,
      updatedAt: now,
    });
    await db.collection("users").doc(BOB_UID).set({
      uid: BOB_UID,
      email: BOB_EMAIL,
      displayName: "Bob",
      workspaceOwnerUid: ALICE_UID,
      role: "member",
      roles: ["sales"],
      approved: true,
      createdAt: now,
      updatedAt: now,
    });
  });

  // Member submits pending approval
  await assertSucceeds(
    bob.collection("users").doc(ALICE_UID).collection("pending").doc("pend_1").set({
      ownerUid: ALICE_UID,
      memberUids: [ALICE_UID, BOB_UID],
      submittedByUid: BOB_UID,
      submittedByName: "Bob",
      submittedByEmail: BOB_EMAIL,
      action: "set",
      targetCollection: "sales",
      targetDocId: "sale_bob_1",
      summaryTitle: "Sale: Khan Traders (10 kg)",
      summaryDetail: "Cash Rs 16,000",
      recordDate: "2026-11-24",
      payloadData: {
        customerName: "Khan Traders",
        qtyKg: 10,
        totalAmount: 16000,
      },
      status: "pending",
      createdAt: now,
      updatedAt: now,
    })
  );

  // Member lists own pending
  await assertSucceeds(
    bob.collection("users").doc(ALICE_UID).collection("pending")
      .where("submittedByUid", "==", BOB_UID)
      .get()
  );

  // Member cannot self-approve
  await assertFails(
    bob.collection("users").doc(ALICE_UID).collection("pending").doc("pend_1").update({
      status: "approved",
      updatedAt: pastDate(500),
    })
  );

  // Admin approves
  await assertSucceeds(
    alice.collection("users").doc(ALICE_UID).collection("pending").doc("pend_1").update({
      status: "approved",
      reviewedByUid: ALICE_UID,
      updatedAt: pastDate(500),
    })
  );
});

test("Photos (web parity): members read/write photo docs, outsiders and bad shapes rejected", async () => {
  const now = pastDate();
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await db.collection("users").doc(ALICE_UID).set({
      uid: ALICE_UID, email: ALICE_EMAIL, displayName: "Alice", workspaceOwnerUid: ALICE_UID,
      role: "admin", roles: ["admin"], approved: true, createdAt: now, updatedAt: now,
    });
    await db.collection("users").doc(BOB_UID).set({
      uid: BOB_UID, email: BOB_EMAIL, displayName: "Bob", workspaceOwnerUid: ALICE_UID,
      role: "member", roles: ["sales"], approved: true, createdAt: now, updatedAt: now,
    });
  });
  const alice = testEnv.authenticatedContext(ALICE_UID, { email: ALICE_EMAIL, email_verified: true }).firestore();
  const bob = testEnv.authenticatedContext(BOB_UID, { email: BOB_EMAIL, email_verified: true }).firestore();
  const charlie = testEnv.authenticatedContext(CHARLIE_UID, { email: CHARLIE_EMAIL, email_verified: true }).firestore();
  const photos = (db) => db.collection("users").doc(ALICE_UID).collection("photos");

  // A member can add a photo in the web app's document shape.
  await assertSucceeds(photos(bob).doc("ZXhwZW5zZTpleHBfMQ").set({ key: "expense:exp_1", data: "data:image/jpeg;base64,AAAA", updatedAt: pastDate(500) }));
  // Owner and member can read it; an outsider cannot.
  await assertSucceeds(photos(alice).doc("ZXhwZW5zZTpleHBfMQ").get());
  await assertSucceeds(photos(bob).get());
  await assertFails(photos(charlie).doc("ZXhwZW5zZTpleHBfMQ").get());
  await assertFails(photos(charlie).doc("b3V0c2lkZXI").set({ key: "x", data: "y", updatedAt: pastDate(500) }));
  // A deletion tombstone is allowed; a photo with a stray field or no key is not.
  await assertSucceeds(photos(bob).doc("ZXhwZW5zZTpleHBfMQ").set({ key: "expense:exp_1", data: null, deleted: true, updatedAt: pastDate(300) }));
  await assertFails(photos(bob).doc("c3RyYXk").set({ key: "stray", data: "y", extra: 1, updatedAt: pastDate(500) }));
  await assertFails(photos(bob).doc("bm9rZXk").set({ data: "y", updatedAt: pastDate(500) }));
  // Only an admin may hard-delete.
  await assertFails(photos(bob).doc("ZXhwZW5zZTpleHBfMQ").delete());
  await assertSucceeds(photos(alice).doc("ZXhwZW5zZTpleHBfMQ").delete());
});

test("FCM tokens (web parity): admin manages only their own, members and others denied", async () => {
  const now = pastDate();
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await db.collection("users").doc(ALICE_UID).set({
      uid: ALICE_UID, email: ALICE_EMAIL, displayName: "Alice", workspaceOwnerUid: ALICE_UID,
      role: "admin", roles: ["admin"], approved: true, createdAt: now, updatedAt: now,
    });
    await db.collection("users").doc(BOB_UID).set({
      uid: BOB_UID, email: BOB_EMAIL, displayName: "Bob", workspaceOwnerUid: ALICE_UID,
      role: "member", roles: ["sales"], approved: true, createdAt: now, updatedAt: now,
    });
  });
  const alice = testEnv.authenticatedContext(ALICE_UID, { email: ALICE_EMAIL, email_verified: true }).firestore();
  const bob = testEnv.authenticatedContext(BOB_UID, { email: BOB_EMAIL, email_verified: true }).firestore();
  const tokens = (db) => db.collection("users").doc(ALICE_UID).collection("fcm_tokens");
  const mine = { uid: ALICE_UID, token: "tok_abc", platform: "android", updatedAt: pastDate(500) };

  await assertSucceeds(tokens(alice).doc(ALICE_UID + "_device1").set(mine));
  await assertSucceeds(tokens(alice).doc(ALICE_UID + "_device1").get());
  // Wrong id prefix, someone else's uid, or extra fields are rejected.
  await assertFails(tokens(alice).doc("other_device1").set(mine));
  await assertFails(tokens(alice).doc(ALICE_UID + "_device2").set({ ...mine, uid: BOB_UID }));
  await assertFails(tokens(alice).doc(ALICE_UID + "_device3").set({ ...mine, extra: true }));
  // Members cannot read or write tokens.
  await assertFails(tokens(bob).doc(ALICE_UID + "_device1").get());
  await assertFails(tokens(bob).doc(BOB_UID + "_device1").set({ ...mine, uid: BOB_UID }));
  await assertSucceeds(tokens(alice).doc(ALICE_UID + "_device1").delete());
});
