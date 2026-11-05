// Rooktest van de web-app: Firebase wordt vervangen door vaste testdata,
// daarna laden we het tabblad Spellen en klikken we de filters aan.
const { test, expect } = require("@playwright/test");

const DATA = {
  event: { name: "Testweekend", joinCode: "1234" },
  members: [{ id: "u1", name: "Anna", status: "BESCHIKBAAR", wants: [] }],
  games: [
    { id: "g1", name: "Catan", owner: "Anna", minPlayers: 3, maxPlayers: 4, weight: "MIDDEL" },
    { id: "g2", name: "Azul", owner: "Bram", minPlayers: 2, maxPlayers: 4, weight: "LICHT" },
    { id: "g3", name: "Brass", owner: "", wishlist: true, minPlayers: 2, maxPlayers: 4, weight: "ZWAAR" },
  ],
};

const FB = "https://www.gstatic.com/firebasejs/10.12.2/";
const stubs = {
  "firebase-app.js": `export const initializeApp = () => ({});`,
  "firebase-auth.js": `export const getAuth = () => ({});
    export const signInAnonymously = async () => {};
    export const onAuthStateChanged = (a, cb) => setTimeout(() => cb({ uid: "u1" }), 0);`,
  "firebase-firestore.js": `const D = ${JSON.stringify(DATA)};
    const noop = async () => ({ exists: () => false, docs: [], data: () => ({}) });
    export const getFirestore = () => ({});
    export const collection = (db, ...p) => ({ doc: false, name: p[2] });
    export const doc = (db, ...p) => ({ doc: true, name: p[2] });
    export const onSnapshot = (ref, cb) => {
      if (ref.doc && !ref.name) cb({ exists: () => true, id: "e1", data: () => D.event });
      else if (ref.doc) cb({ exists: () => true, id: "x", data: () => ({}) });
      else cb({ docs: (D[ref.name] || []).map(({ id, ...r }) => ({ id, data: () => r })) });
      return () => {};
    };
    export const getDoc = noop, getDocs = noop, setDoc = noop, addDoc = noop, updateDoc = noop, deleteDoc = noop;
    export const query = (r) => r, where = () => 0, limit = () => 0, arrayUnion = () => 0, arrayRemove = () => 0;
    export const writeBatch = () => ({ set() {}, update() {}, delete() {}, commit: noop });`,
};

test.beforeEach(async ({ page }) => {
  await page.route("**/config.js", (r) => r.fulfill({ contentType: "text/javascript", body: `window.FIREBASE_CONFIG={apiKey:"x",projectId:"p"};` }));
  await page.route(FB + "*", (r) => {
    const f = r.request().url().slice(FB.length);
    return r.fulfill({ contentType: "text/javascript", body: stubs[f] });
  });
  await page.addInitScript(() => {
    localStorage.setItem("app_eventId", "e1");
    localStorage.setItem("app_tab", "spellen");
  });
  await page.goto("/app/index.html");
  await expect(page.locator("[data-gedit]")).toHaveCount(3);
});

const titles = (page) => page.locator("[data-gedit]").allInnerTexts().then((t) => t.map((x) => x.replace("✎", "").trim()));

test("Gaat mee en Wenslijst sluiten elkaar uit", async ({ page }) => {
  await page.getByRole("button", { name: "Gaat mee (2)" }).click();
  expect(await titles(page)).toEqual(["Azul", "Catan"]);
  await page.getByRole("button", { name: "Wenslijst (1)" }).click();
  expect(await titles(page)).toEqual(["Brass"]);
  await expect(page.getByRole("button", { name: "Gaat mee (2)" })).not.toHaveClass(/on/);
});

test("Neem ik mee filtert op de eigen naam", async ({ page }) => {
  await page.getByRole("button", { name: "Neem ik mee (1)" }).click();
  expect(await titles(page)).toEqual(["Catan"]);
  await page.getByRole("button", { name: "Neem ik mee (1)" }).click();
  expect(await titles(page)).toHaveLength(3);
});

test("Iedereen in het eigenaarfilter verbergt de wenslijst", async ({ page }) => {
  await page.getByRole("button", { name: /^Filters/ }).click();
  await page.locator("#gOwner").selectOption({ label: "Iedereen (meegebracht)" });
  expect(await titles(page)).toEqual(["Azul", "Catan"]);
  await page.getByRole("button", { name: "Filters wissen" }).click();
  expect(await titles(page)).toHaveLength(3);
});
