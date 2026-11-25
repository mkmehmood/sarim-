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
  await assertFails(unauth.collection("users").doc(ALICE_UID).collection("production").get());
  await assertFails(unauth.collection("users").doc(ALICE_UID).collection("sales").get());
  await assertFails(unauth.collection("users").doc(ALICE_UID).collection("rep").get());
  await assertFails(
    unauth.collection("users").doc(ALICE_UID).collection("stores").doc("list").set({
      stores: [],
      stores_timestamp: Date.now(),
    })
  );
});

test("User profile: owner self-bootstrap succeeds, privilege escalation and unauthorized workspace join rejected", async () => {
  const alice = testEnv.authenticatedContext(ALICE_UID, { email: ALICE_EMAIL, email_verified: true }).firestore();
  const bob = testEnv.authenticatedContext(BOB_UID, { email: BOB_EMAIL, email_verified: true }).firestore();
  const now = pastDate();

  // Valid owner self-bootstrap (Google Sign-In new user)
  await assertSucceeds(
    alice.collection("users").doc(ALICE_UID).set({
      uid: ALICE_UID,
      email: ALICE_EMAIL,
      displayName: "Alice Admin",
      role: "admin",
      roles: ["admin"],
      approved: true,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Valid harmless update by owner
  await assertSucceeds(
    alice.collection("users").doc(ALICE_UID).update({
      displayName: "Alice Owner",
      lastWrite: Date.now(),
      updatedAt: pastDate(500),
    })
  );

  // Reject stranger joining Alice's workspace without an invite
  await assertFails(
    bob.collection("users").doc(BOB_UID).set({
      uid: BOB_UID,
      email: BOB_EMAIL,
      displayName: "Bob Intruder",
      ownerUid: ALICE_UID,
      role: "user",
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

  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().collection("users").doc(ALICE_UID).set({
      uid: ALICE_UID,
      email: ALICE_EMAIL,
      displayName: "Alice Admin",
      role: "admin",
      roles: ["admin"],
      approved: true,
      createdAt: now,
      updatedAt: now,
    });
  });

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

  await assertFails(
    bobUnverified.collection("workspace_invites").where("memberEmail", "==", BOB_EMAIL).get()
  );
  await assertFails(
    charlie.collection("workspace_invites").where("memberEmail", "==", BOB_EMAIL).get()
  );
  await assertFails(charlie.collection("workspace_invites").get());

  await assertSucceeds(
    bobVerified.collection("workspace_invites").where("memberEmail", "==", BOB_EMAIL).get()
  );
  // Escalation to admin rejected
  await assertFails(
    bobVerified.collection("users").doc(BOB_UID).set({
      uid: BOB_UID,
      email: BOB_EMAIL,
      displayName: "Bob Sales",
      ownerUid: ALICE_UID,
      role: "admin",
      roles: ["admin"],
      approved: true,
      inviteId: "inv_bob_1",
      createdAt: now,
      updatedAt: now,
    })
  );
  // Valid member onboarding
  await assertSucceeds(
    bobVerified.collection("users").doc(BOB_UID).set({
      uid: BOB_UID,
      email: BOB_EMAIL,
      displayName: "Bob Sales",
      ownerUid: ALICE_UID,
      role: "user",
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

test("Web-parity collections & singletons: admin writes, member reads (blocked on settings/accounts), outsider denied", async () => {
  const alice = testEnv.authenticatedContext(ALICE_UID, { email: ALICE_EMAIL, email_verified: true }).firestore();
  const bob = testEnv.authenticatedContext(BOB_UID, { email: BOB_EMAIL, email_verified: true }).firestore();
  const charlie = testEnv.authenticatedContext(CHARLIE_UID, { email: CHARLIE_EMAIL, email_verified: true }).firestore();
  const now = pastDate();

  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await db.collection("users").doc(ALICE_UID).set({
      uid: ALICE_UID, email: ALICE_EMAIL, displayName: "Alice Admin",
      role: "admin", roles: ["admin"], approved: true, createdAt: now, updatedAt: now,
    });
    await db.collection("users").doc(BOB_UID).set({
      uid: BOB_UID, email: BOB_EMAIL, displayName: "Bob Member",
      ownerUid: ALICE_UID, role: "user", roles: ["sales", "rep"], approved: true, createdAt: now, updatedAt: now,
    });
  });

  // Admin writes web-parity singleton docs & sync collections
  await assertSucceeds(
    alice.collection("users").doc(ALICE_UID).collection("stores").doc("list").set({
      stores: [{ key: "zubair", name: "Zubair", salePrice: 1200, formulaId: "f_1" }],
      stores_timestamp: Date.now(),
    })
  );
  await assertSucceeds(
    alice.collection("users").doc(ALICE_UID).collection("settings").doc("accounts").set({
      accounts: [{ uid: BOB_UID, email: BOB_EMAIL, roles: ["sales", "rep"], approved: true }],
      updatedAt: Date.now(),
    })
  );
  await assertSucceeds(
    alice.collection("users").doc(ALICE_UID).collection("production").doc("prod_1").set({
      id: "prod_1",
      date: "2026-11-24",
      time: "10:15",
      store: "Zubair",
      grossWt: 52.5,
      contWt: 2.5,
      net: 50.0,
      cp: 800,
      sp: 1200,
      totalCost: 40000,
      totalSale: 60000,
      profit: 20000,
      formulaUnits: 2,
      formulaId: "f_1",
      formulaName: "Special",
      timestamp: Date.now(),
      updatedAt: now,
    })
  );
  await assertSucceeds(
    alice.collection("users").doc(ALICE_UID).collection("rep").doc("rep_1").set({
      id: "rep_1",
      date: "2026-11-24",
      customerName: "Khan Traders",
      quantity: 10,
      supplyStore: "Zubair",
      paymentType: "CREDIT",
      salesRep: "Bob Member",
      unitPrice: 1200,
      totalValue: 12000,
      totalCost: 8000,
      profit: 4000,
      creditReceived: false,
      updatedAt: now,
    })
  );
  await assertSucceeds(
    alice.collection("users").doc(ALICE_UID).collection("daily_ledger").doc("entries").set({
      daily_ledger: [],
      daily_ledger_timestamp: Date.now(),
    })
  );

  // Member Bob can read shared collections & stores/list, CANNOT read settings/accounts, CANNOT write directly
  await assertSucceeds(bob.collection("users").doc(ALICE_UID).collection("production").get());
  await assertSucceeds(bob.collection("users").doc(ALICE_UID).collection("rep").get());
  await assertSucceeds(bob.collection("users").doc(ALICE_UID).collection("stores").doc("list").get());
  await assertFails(bob.collection("users").doc(ALICE_UID).collection("settings").doc("accounts").get());
  await assertFails(
    bob.collection("users").doc(ALICE_UID).collection("production").doc("prod_bob").set({
      id: "prod_bob", date: "2026-11-24", store: "Zubair", net: 20,
    })
  );

  // Member Bob CAN ping owner lastWrite field, CANNOT mutate other fields on owner doc
  await assertSucceeds(
    bob.collection("users").doc(ALICE_UID).update({ lastWrite: Date.now() })
  );
  await assertFails(
    bob.collection("users").doc(ALICE_UID).update({ displayName: "Hacked" })
  );

  // Outsider Charlie denied everywhere
  await assertFails(charlie.collection("users").doc(ALICE_UID).collection("production").get());
  await assertFails(charlie.collection("users").doc(ALICE_UID).collection("rep").get());
  await assertFails(charlie.collection("users").doc(ALICE_UID).collection("stores").doc("list").get());
});

test("Pending approvals (web parity): member submits col__docId, cannot self-approve; admin approves", async () => {
  const alice = testEnv.authenticatedContext(ALICE_UID, { email: ALICE_EMAIL, email_verified: true }).firestore();
  const bob = testEnv.authenticatedContext(BOB_UID, { email: BOB_EMAIL, email_verified: true }).firestore();
  const now = pastDate();

  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await db.collection("users").doc(ALICE_UID).set({
      uid: ALICE_UID, email: ALICE_EMAIL, displayName: "Alice",
      role: "admin", roles: ["admin"], approved: true, createdAt: now, updatedAt: now,
    });
    await db.collection("users").doc(BOB_UID).set({
      uid: BOB_UID, email: BOB_EMAIL, displayName: "Bob",
      ownerUid: ALICE_UID, role: "user", roles: ["sales", "rep"], approved: true, createdAt: now, updatedAt: now,
    });
  });

  const pid = "rep__rep_bob_1";
  await assertSucceeds(
    bob.collection("users").doc(ALICE_UID).collection("pending").doc(pid).set({
      id: pid,
      collection: "rep",
      docId: "rep_bob_1",
      action: "set",
      data: { id: "rep_bob_1", customerName: "Khan Traders", quantity: 10, totalValue: 12000 },
      isEdit: false,
      status: "pending",
      submittedBy: { uid: BOB_UID, name: "Bob", email: BOB_EMAIL },
      submittedAt: Date.now(),
      summary: { title: "New sale", body: "Khan Traders — 12,000" },
    })
  );

  await assertSucceeds(
    bob.collection("users").doc(ALICE_UID).collection("pending")
      .where("submittedBy.uid", "==", BOB_UID)
      .get()
  );

  // Member cannot self-approve
  await assertFails(
    bob.collection("users").doc(ALICE_UID).collection("pending").doc(pid).update({
      status: "approved",
    })
  );

  // Admin approves
  await assertSucceeds(
    alice.collection("users").doc(ALICE_UID).collection("pending").doc(pid).update({
      status: "approved",
      decidedBy: { uid: ALICE_UID, name: "Alice" },
      decidedAt: Date.now(),
    })
  );
});

test("Photos (web parity): members read/write photo docs, outsiders and bad shapes rejected", async () => {
  const now = pastDate();
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await db.collection("users").doc(ALICE_UID).set({
      uid: ALICE_UID, email: ALICE_EMAIL, displayName: "Alice",
      role: "admin", roles: ["admin"], approved: true, createdAt: now, updatedAt: now,
    });
    await db.collection("users").doc(BOB_UID).set({
      uid: BOB_UID, email: BOB_EMAIL, displayName: "Bob",
      ownerUid: ALICE_UID, role: "user", roles: ["sales"], approved: true, createdAt: now, updatedAt: now,
    });
  });
  const alice = testEnv.authenticatedContext(ALICE_UID, { email: ALICE_EMAIL, email_verified: true }).firestore();
  const bob = testEnv.authenticatedContext(BOB_UID, { email: BOB_EMAIL, email_verified: true }).firestore();
  const charlie = testEnv.authenticatedContext(CHARLIE_UID, { email: CHARLIE_EMAIL, email_verified: true }).firestore();
  const photos = (db) => db.collection("users").doc(ALICE_UID).collection("photos");

  await assertSucceeds(photos(bob).doc("ZXhwZW5zZTpleHBfMQ").set({ key: "expense:exp_1", data: "data:image/jpeg;base64,AAAA", updatedAt: pastDate(500) }));
  await assertSucceeds(photos(alice).doc("ZXhwZW5zZTpleHBfMQ").get());
  await assertSucceeds(photos(bob).get());
  await assertFails(photos(charlie).doc("ZXhwZW5zZTpleHBfMQ").get());
  await assertFails(photos(charlie).doc("b3V0c2lkZXI").set({ key: "x", data: "y", updatedAt: pastDate(500) }));
  await assertSucceeds(photos(bob).doc("ZXhwZW5zZTpleHBfMQ").set({ key: "expense:exp_1", data: null, deleted: true, updatedAt: pastDate(300) }));
  await assertFails(photos(bob).doc("c3RyYXk").set({ key: "stray", data: "y", extra: 1, updatedAt: pastDate(500) }));
  await assertFails(photos(bob).doc("bm9rZXk").set({ data: "y", updatedAt: pastDate(500) }));
  await assertFails(photos(bob).doc("ZXhwZW5zZTpleHBfMQ").delete());
  await assertSucceeds(photos(alice).doc("ZXhwZW5zZTpleHBfMQ").delete());
});

test("FCM tokens (web parity): admin manages only their own, members and others denied", async () => {
  const now = pastDate();
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await db.collection("users").doc(ALICE_UID).set({
      uid: ALICE_UID, email: ALICE_EMAIL, displayName: "Alice",
      role: "admin", roles: ["admin"], approved: true, createdAt: now, updatedAt: now,
    });
    await db.collection("users").doc(BOB_UID).set({
      uid: BOB_UID, email: BOB_EMAIL, displayName: "Bob",
      ownerUid: ALICE_UID, role: "user", roles: ["sales"], approved: true, createdAt: now, updatedAt: now,
    });
  });
  const alice = testEnv.authenticatedContext(ALICE_UID, { email: ALICE_EMAIL, email_verified: true }).firestore();
  const bob = testEnv.authenticatedContext(BOB_UID, { email: BOB_EMAIL, email_verified: true }).firestore();
  const tokens = (db) => db.collection("users").doc(ALICE_UID).collection("fcm_tokens");
  const mine = { uid: ALICE_UID, token: "tok_abc", platform: "android", updatedAt: pastDate(500) };

  await assertSucceeds(tokens(alice).doc(ALICE_UID + "_device1").set(mine));
  await assertSucceeds(tokens(alice).doc(ALICE_UID + "_device1").get());
  await assertFails(tokens(alice).doc("other_device1").set(mine));
  await assertFails(tokens(alice).doc(ALICE_UID + "_device2").set({ ...mine, uid: BOB_UID }));
  await assertFails(tokens(alice).doc(ALICE_UID + "_device3").set({ ...mine, extra: true }));
  await assertFails(tokens(bob).doc(ALICE_UID + "_device1").get());
  await assertFails(tokens(bob).doc(BOB_UID + "_device1").set({ ...mine, uid: BOB_UID }));
  await assertSucceeds(tokens(alice).doc(ALICE_UID + "_device1").delete());
});
