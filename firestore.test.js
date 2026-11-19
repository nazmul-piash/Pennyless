const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_uid_123";
const BOB_UID = "bob_uid_456";

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

test("Unauthenticated user cannot read or write any user collection", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("transactions").get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("accounts").doc("acc_1").set({
      userId: ALICE_UID,
      name: "Cash",
      accountType: "CASH",
      balance: 100.0,
      creditLimit: 0.0,
      dueDayOfMonth: 0,
      colorHex: "#3B82F6",
      lastFourDigits: "",
      createdAt: new Date(Date.now() - 1000),
      updatedAt: new Date(Date.now() - 1000),
    })
  );
});

test("Authenticated owner can create and partially update UserProfile, rejects ghost fields and cross-user access", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const now = new Date(Date.now() - 5000);

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Alice",
      email: "alice@example.com",
      currency: "EUR",
      monthlyBudget: 2500.0,
      seededDemoData: true,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Partial update allowed
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      currency: "USD",
      monthlyBudget: 3000.0,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Shadow update with ghost field rejected
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      isAdmin: true,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Immutable createdAt mutation rejected
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      createdAt: new Date(Date.now() - 500),
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Cross-user read/write rejected
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).update({ displayName: "Hacked", updatedAt: new Date(Date.now() - 500) }));
});

test("Authenticated owner can list, create, update, delete accounts, categories, transactions, and recurringBills", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const ts = new Date(Date.now() - 5000);

  // Empty subcollection list queries must succeed for owner
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("accounts").get());
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("transactions").get());

  // Account create & balance update
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("accounts").doc("acc_tf_gold").set({
      userId: ALICE_UID,
      name: "TF Bank Mastercard Gold",
      accountType: "CREDIT_CARD",
      balance: -420.5,
      creditLimit: 3000.0,
      dueDayOfMonth: 25,
      colorHex: "#D97706",
      lastFourDigits: "4821",
      createdAt: ts,
      updatedAt: ts,
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("accounts").doc("acc_tf_gold").update({
      balance: -452.9,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Category create
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("categories").doc("cat_food").set({
      userId: ALICE_UID,
      name: "Food & Dining",
      txType: "EXPENSE",
      iconKey: "restaurant",
      colorHex: "#FF5A5F",
      sortOrder: 1,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Transaction create with AI receipt metadata & lineItems (NO id field)
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("transactions").doc("tx_1").set({
      userId: ALICE_UID,
      txType: "EXPENSE",
      amount: 32.4,
      currency: "EUR",
      dateIso: "2026-11-19",
      merchant: "REWE Markt",
      categoryName: "Food & Dining",
      subcategory: "Supermarket",
      accountId: "acc_tf_gold",
      accountName: "TF Bank Mastercard Gold",
      note: "Weekly grocery and coffee",
      needsReview: false,
      aiConfidence: 0.96,
      lineItems: [
        { itemName: "Organic Milk", amount: 2.4, suggestedCategory: "Food & Dining" },
        { itemName: "Espresso Beans", amount: 30.0, suggestedCategory: "Food & Dining" },
      ],
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Reject transaction with @DocumentId `id` inside payload
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("transactions").doc("tx_2").set({
      id: "tx_2",
      userId: ALICE_UID,
      txType: "EXPENSE",
      amount: 10.0,
      currency: "EUR",
      dateIso: "2026-11-19",
      merchant: "Cafe",
      categoryName: "Food & Dining",
      subcategory: "Coffee",
      accountId: "acc_tf_gold",
      accountName: "TF Bank Mastercard Gold",
      note: "Morning latte",
      needsReview: false,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Reject future tampered timestamp
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("transactions").doc("tx_future").set({
      userId: ALICE_UID,
      txType: "EXPENSE",
      amount: 10.0,
      currency: "EUR",
      dateIso: "2026-11-19",
      merchant: "Cafe",
      categoryName: "Food & Dining",
      subcategory: "Coffee",
      accountId: "acc_tf_gold",
      accountName: "TF Bank Mastercard Gold",
      note: "Future attack",
      needsReview: false,
      createdAt: new Date(Date.now() + 60000),
      updatedAt: new Date(Date.now() + 60000),
    })
  );

  // RecurringBill create
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("recurringBills").doc("bill_1").set({
      userId: ALICE_UID,
      title: "Apartment Rent",
      amount: 1150.0,
      categoryName: "Bills & Utilities",
      accountId: "acc_tf_gold",
      accountName: "TF Bank Mastercard Gold",
      dayOfMonth: 1,
      autoPay: true,
      lastPaidMonth: "2026-11",
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Cross-user subcollection isolation
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("transactions").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("transactions").doc("tx_1").delete());
});
