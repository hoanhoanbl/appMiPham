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
  "products",
  "brands",
  "spa_packages",
  "appointments",
  "treatment_plans",
  "treatment_sessions",
  "treatment_progress_photos",
  "consultation_chat_threads",
  "consultation_chat_messages",
  "orders",
];

const stringFieldAllowList = new Set([
  "name",
  "description",
  "shortDescription",
  "category",
  "brandName",
  "origin",
  "packageName",
  "spaPackageName",
  "photoGuide",
  "benefits",
  "steps",
  "suitableFor",
  "note",
  "consultationNote",
  "recommendationNote",
  "internalNote",
  "cancelReason",
  "noShowNote",
  "rescheduleReason",
  "photoSkipReason",
  "hiddenReason",
  "lastMessage",
  "message",
  "address",
]);

const technicalFields = new Set([
  "id",
  "uid",
  "userId",
  "consultantId",
  "spaPackageId",
  "appointmentId",
  "treatmentPlanId",
  "treatmentSessionId",
  "email",
  "userEmail",
  "consultantEmail",
  "phoneNumber",
  "imageUrl",
  "packageImageUrl",
  "status",
  "role",
  "packageType",
  "photoPolicy",
  "photoType",
  "senderRole",
  "uploadedBy",
  "paymentMethod",
  "timeSlotLabel",
  "dateLabel",
]);

const replacements = [
  ["Bo SP lam dep", "Bộ SP làm đẹp"],
  ["Bo SP làm đẹp", "Bộ SP làm đẹp"],
  ["San pham", "Sản phẩm"],
  ["san pham", "sản phẩm"],
  ["gia tot", "giá tốt"],
  ["gia re", "giá rẻ"],
  ["phu hop", "phù hợp"],
  ["vs moij ng", "với mọi người"],
  ["lam dep", "làm đẹp"],
  ["Lam sach", "Làm sạch"],
  ["Cham soc", "Chăm sóc"],
  ["chat Luong", "chất lượng"],
  ["chat luong", "chất lượng"],
  ["Duoc my pham", "Dược mỹ phẩm"],
  ["sua rua mat", "sữa rửa mặt"],
  ["Sua rua mat", "Sữa rửa mặt"],
  ["Tay trang", "Tẩy trang"],
  ["Kem duong", "Kem dưỡng"],
  ["Kem chong nang", "Kem chống nắng"],
  ["Mat na", "Mặt nạ"],
  ["Tri mun", "Trị mụn"],
  ["Tay te bao chet", "Tẩy tế bào chết"],
  ["Chong lao hoa", "Chống lão hóa"],
  ["Duong mat", "Dưỡng mắt"],
  ["Duong moi", "Dưỡng môi"],
  ["Phap", "Pháp"],
  ["thuong hieu", "thương hiệu"],
  ["Thu gian", "Thư giãn"],
  ["thư giản", "thư giãn"],
  ["Thư giản", "Thư giãn"],
  ["Giam stress", "Giảm stress"],
  ["giam stress", "giảm stress"],
  ["Giam", "Giảm"],
  ["giam", "giảm"],
  ["Matxa", "Mát xa"],
  ["matxa", "mát xa"],
  ["cổ vài gáy", "cổ vai gáy"],
  ["co vai gay", "cổ vai gáy"],
  ["Goi dau", "Gội đầu"],
  ["goi dau", "gội đầu"],
  ["k de lai", "không để lại"],
  ["k để lại", "không để lại"],
  ["de lai", "để lại"],
  ["sẹo", "sẹo"],
  ["Da kho", "Da khô"],
  ["Da dau", "Da dầu"],
  ["Da mun", "Da mụn"],
  ["Da thieu suc song", "Da thiếu sức sống"],
  ["Da nhay cam", "Da nhạy cảm"],
  ["chăm sóc da", "Chăm sóc da"],
  ["xin chao ban", "xin chào bạn"],
  ["xin chao", "xin chào"],
  ["alo", "alô"],
  ["Customer cancelled", "Khách đã hủy"],
  ["Khach chon lich", "Khách chọn lịch"],
  ["Khach", "Khách"],
  ["khach", "khách"],
  ["tu van", "tư vấn"],
  ["Tu van", "Tư vấn"],
  ["lich", "lịch"],
  ["Lich", "Lịch"],
  ["buoi", "buổi"],
  ["Buoi", "Buổi"],
  ["dieu tri", "điều trị"],
  ["Dieu tri", "Điều trị"],
  ["khong", "không"],
  ["Khong", "Không"],
];

const addressReplacements = [
  ["ttinh", "tỉnh"],
  ["tinhh", "tỉnh"],
  ["Tinh", "Tỉnh"],
  ["tinh", "tỉnh"],
  ["Xom", "Xóm"],
  ["xom", "xóm"],
  ["xa ", "xã "],
  ["duong", "đường"],
  ["Da Nang", "Đà Nẵng"],
  ["da nang", "Đà Nẵng"],
  ["Dien Ban", "Điện Bàn"],
  ["Quang Nam", "Quảng Nam"],
  ["Nghe An", "Nghệ An"],
  ["Dong Nai", "Đồng Nai"],
  ["Hoa Quy", "Hòa Quý"],
  ["Ngu Hanh Son", "Ngũ Hành Sơn"],
  ["Lao Cai", "Lào Cai"],
  ["lao cai", "Lào Cai"],
  ["cong Thanh", "Công Thành"],
  ["dai Minh", "Đại Minh"],
];

function shouldProcessField(path) {
  const key = path.split(".").pop()?.replace(/\[\d+\]$/, "") || "";
  if (technicalFields.has(key) || key.endsWith("Id") || key.endsWith("Url")) return false;
  return stringFieldAllowList.has(key);
}

function convertText(input, path) {
  if (!shouldProcessField(path)) return input;
  let value = input;
  for (const [from, to] of replacements) {
    value = value.split(from).join(to);
  }
  if (path.split(".").pop()?.replace(/\[\d+\]$/, "") === "address") {
    for (const [from, to] of addressReplacements) {
      value = value.split(from).join(to);
    }
  }
  return value;
}

function decodeValue(value) {
  if ("stringValue" in value) return value.stringValue;
  if ("integerValue" in value) return Number(value.integerValue);
  if ("doubleValue" in value) return Number(value.doubleValue);
  if ("booleanValue" in value) return value.booleanValue;
  if ("nullValue" in value) return null;
  if ("timestampValue" in value) return value.timestampValue;
  if ("arrayValue" in value) return (value.arrayValue.values || []).map(decodeValue);
  if ("mapValue" in value) {
    return Object.fromEntries(
      Object.entries(value.mapValue.fields || {}).map(([key, child]) => [key, decodeValue(child)]),
    );
  }
  return undefined;
}

function encodeValue(value) {
  if (typeof value === "string") return { stringValue: value };
  if (typeof value === "number") {
    return Number.isInteger(value) ? { integerValue: String(value) } : { doubleValue: value };
  }
  if (typeof value === "boolean") return { booleanValue: value };
  if (value === null || value === undefined) return { nullValue: null };
  if (Array.isArray(value)) return { arrayValue: { values: value.map(encodeValue) } };
  if (typeof value === "object") {
    return {
      mapValue: {
        fields: Object.fromEntries(Object.entries(value).map(([key, child]) => [key, encodeValue(child)])),
      },
    };
  }
  return { stringValue: String(value) };
}

function convertObject(value, path = "") {
  if (typeof value === "string") {
    const next = convertText(value, path);
    return { value: next, changes: next === value ? [] : [{ path, before: value, after: next }] };
  }
  if (Array.isArray(value)) {
    const next = [];
    const changes = [];
    value.forEach((item, index) => {
      const result = convertObject(item, `${path}[${index}]`);
      next.push(result.value);
      changes.push(...result.changes);
    });
    return { value: next, changes };
  }
  if (value && typeof value === "object") {
    const next = {};
    const changes = [];
    for (const [key, child] of Object.entries(value)) {
      const childPath = path ? `${path}.${key}` : key;
      const result = convertObject(child, childPath);
      next[key] = result.value;
      changes.push(...result.changes);
    }
    return { value: next, changes };
  }
  return { value, changes: [] };
}

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

async function patchDocument(docName, nextData, changedPaths, headers) {
  const url = new URL(`${baseUrl}/${docName.split("/documents/")[1]}`);
  const rootFields = [...new Set(changedPaths.map((path) => path.split(/[.[\]]/)[0]).filter(Boolean))];
  for (const path of rootFields) {
    url.searchParams.append("updateMask.fieldPaths", path);
  }
  const fields = Object.fromEntries(Object.entries(nextData).map(([key, value]) => [key, encodeValue(value)]));
  const response = await fetch(url, {
    method: "PATCH",
    headers: { ...headers, "content-type": "application/json" },
    body: JSON.stringify({ fields }),
  });
  const text = await response.text();
  if (!response.ok) throw new Error(`${response.status} ${text}`);
}

async function main() {
  const token = await signIn();
  const headers = { authorization: `Bearer ${token}` };
  let changedDocCount = 0;
  let changedValueCount = 0;
  let failedWrites = 0;

  console.log(writeMode ? "WRITE MODE" : "DRY RUN");
  for (const collection of collections) {
    const docs = await listDocuments(collection, headers);
    for (const doc of docs) {
      const id = doc.name.split("/").pop();
      const data = Object.fromEntries(Object.entries(doc.fields || {}).map(([key, value]) => [key, decodeValue(value)]));
      const result = convertObject(data);
      if (result.changes.length === 0) continue;

      changedDocCount += 1;
      changedValueCount += result.changes.length;
      console.log(`\n${collection}/${id}`);
      for (const change of result.changes) {
        console.log(`  ${change.path}: "${change.before}" -> "${change.after}"`);
      }

      if (writeMode) {
        try {
          await patchDocument(doc.name, result.value, result.changes.map((change) => change.path), headers);
        } catch (error) {
          failedWrites += 1;
          console.warn(`  WRITE FAILED: ${error.message.slice(0, 240)}`);
        }
      }
    }
  }

  console.log(`\nSummary: ${changedDocCount} docs, ${changedValueCount} values${writeMode ? `, ${failedWrites} failed writes` : ""}.`);
  if (!writeMode) console.log("Run with --write to apply these changes.");
}

main().catch((error) => {
  console.error(error);
  process.exit(1);
});
