import fs from "node:fs";

const args = new Set(process.argv.slice(2));
const writeMode = args.has("--write");

const email = process.env.FIREBASE_EMAIL || "";
const password = process.env.FIREBASE_PASSWORD || "";
const idTokenFromEnv = process.env.FIREBASE_ID_TOKEN || "";
const config = JSON.parse(fs.readFileSync("app/google-services.json", "utf8"));
const apiKey = config.client[0].api_key[0].current_key;
const projectId = config.project_info.project_id;
const baseUrl = `https://firestore.googleapis.com/v1/projects/${projectId}/databases/(default)/documents`;

const collections = [
  "appointments",
  "treatment_plans",
  "treatment_sessions",
  "treatment_progress_photos",
  "consultation_chat_threads",
  "consultation_chat_messages",
  "appointment_capacity_blocks",
  "appointment_slots",
];

async function signIn() {
  if (idTokenFromEnv) return idTokenFromEnv;
  if (!email || !password) {
    throw new Error("Set FIREBASE_EMAIL and FIREBASE_PASSWORD, or provide FIREBASE_ID_TOKEN.");
  }
  const response = await fetch(`https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=${apiKey}`, {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify({ email, password, returnSecureToken: true }),
  });
  const payload = await response.json();
  if (!response.ok) throw new Error(`Cannot sign in: ${JSON.stringify(payload)}`);
  return payload.idToken;
}

async function listDocuments(collection, headers) {
  const docs = [];
  let pageToken = "";
  do {
    const url = new URL(`${baseUrl}/${collection}`);
    url.searchParams.set("pageSize", "100");
    if (pageToken) url.searchParams.set("pageToken", pageToken);
    const response = await fetch(url, { headers });
    const text = await response.text();
    if (!response.ok) {
      console.warn(`Skip ${collection}: ${response.status} ${text.slice(0, 180)}`);
      return docs;
    }
    const payload = JSON.parse(text);
    docs.push(...(payload.documents || []));
    pageToken = payload.nextPageToken || "";
  } while (pageToken);
  return docs;
}

async function deleteDocument(docName, headers) {
  const path = docName.split("/documents/")[1];
  const response = await fetch(`${baseUrl}/${path}`, {
    method: "DELETE",
    headers,
  });
  const text = await response.text();
  if (!response.ok && response.status !== 404) {
    throw new Error(`${response.status} ${text.slice(0, 240)}`);
  }
}

async function main() {
  const token = await signIn();
  const headers = { authorization: `Bearer ${token}` };
  let total = 0;
  let failed = 0;

  console.log(writeMode ? "WRITE MODE: deleting spa test data" : "DRY RUN: counting spa test data");
  for (const collection of collections) {
    const docs = await listDocuments(collection, headers);
    console.log(`${collection}: ${docs.length}`);
    total += docs.length;
    if (!writeMode) continue;

    for (const doc of docs) {
      const id = doc.name.split("/").pop();
      try {
        await deleteDocument(doc.name, headers);
        console.log(`  deleted ${collection}/${id}`);
      } catch (error) {
        failed += 1;
        console.warn(`  failed ${collection}/${id}: ${error.message}`);
      }
    }
  }

  console.log(writeMode ? `Deleted target docs. Total seen: ${total}, failed: ${failed}.` : `Total target docs: ${total}. Run with --write to delete.`);
  if (failed > 0) process.exitCode = 1;
}

main().catch((error) => {
  console.error(error);
  process.exit(1);
});
