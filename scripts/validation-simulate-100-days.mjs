#!/usr/bin/env node

import { mkdir, writeFile } from "node:fs/promises";
import { createHash } from "node:crypto";

const args = process.argv.slice(2);
const value = (name, fallback = null) => {
  const index = args.indexOf(name);
  return index >= 0 && index + 1 < args.length ? args[index + 1] : fallback;
};
const has = name => args.includes(name);
const apiUrl = (value("--api-url", process.env.VALIDATION_API_BASE_URL || "http://localhost:18080/api")).replace(/\/+$/, "");
const sourceBatchId = value("--source-batch-id");
const days = Number(value("--days", "100"));
const seed = Number(value("--seed", "4112026"));
const profile = value("--profile", "realistic");
const dryRun = has("--dry-run");
const apply = has("--apply");
const verify = has("--verify") || apply;

if (!/^http:\/\/(localhost|127\.0\.0\.1)(:\d+)?\/api$/i.test(apiUrl)) throw new Error("Refusing non-local API URL: " + apiUrl);
if (!sourceBatchId) throw new Error("--source-batch-id is required");
if (!Number.isInteger(days) || days < 1 || days > 150) throw new Error("--days must be an integer from 1 to 150");
if (!Number.isInteger(seed)) throw new Error("--seed must be an integer");
if (!["realistic", "coverage", "dense"].includes(profile)) throw new Error("Invalid profile");
if (!dryRun && !apply) throw new Error("Choose --dry-run or --apply");
if (apply && value("--confirm") !== "SYNTHETIC-ONLY") throw new Error("Applying data requires --confirm SYNTHETIC-ONLY");

function manilaDate() {
  const parts = new Intl.DateTimeFormat("en", {
    timeZone: "Asia/Manila", year: "numeric", month: "2-digit", day: "2-digit"
  }).formatToParts(new Date());
  const result = Object.fromEntries(parts.filter(p => p.type !== "literal").map(p => [p.type, p.value]));
  return result.year + "-" + result.month + "-" + result.day;
}

function addDays(dateText, offset) {
  const date = new Date(dateText + "T00:00:00Z");
  date.setUTCDate(date.getUTCDate() + offset);
  return date.toISOString().slice(0, 10);
}

function stableUuid(label) {
  const hex = createHash("sha256").update(label).digest("hex").slice(0, 32).split("");
  hex[12] = "5";
  hex[16] = ["8", "9", "a", "b"][parseInt(hex[16], 16) % 4];
  return [hex.slice(0, 8), hex.slice(8, 12), hex.slice(12, 16), hex.slice(16, 20), hex.slice(20, 32)]
    .map(part => part.join("")).join("-");
}

async function request(path, options = {}) {
  const headers = { Accept: "application/json", ...(options.headers || {}) };
  if (options.body !== undefined) headers["Content-Type"] = "application/json";
  const response = await fetch(apiUrl + path, {
    method: options.method || "GET", headers,
    body: options.body === undefined ? undefined : JSON.stringify(options.body)
  });
  const text = await response.text();
  let body = null;
  if (text) {
    try { body = JSON.parse(text); } catch { body = text; }
  }
  if (!response.ok) throw new Error((options.method || "GET") + " " + path + " failed with " + response.status + ": " + String(body).slice(0, 800));
  return body;
}

async function downloadPdf(path, token) {
  const response = await fetch(apiUrl + path, { headers: { Accept: "application/pdf", Authorization: "Bearer " + token } });
  if (!response.ok) throw new Error("PDF request failed with " + response.status);
  return Buffer.from(await response.arrayBuffer());
}

async function login(email, password) {
  const result = await request("/auth/login", { method: "POST", body: { email, password } });
  if (!result?.token) throw new Error("Validation login did not return a token");
  return result;
}

const today = manilaDate();
const startDate = addDays(today, -days);
const runId = "100d-" + startDate.replaceAll("-", "") + "-" + seed + "-" + profile;
const artifactDir = value("--output-dir", "validation-artifacts") + "/" + runId;
await mkdir(artifactDir, { recursive: true });

const version = await request("/version");
if (version?.environment !== "validation") throw new Error("API is not validation: " + JSON.stringify(version));
const managerEmail = process.env.VALIDATION_MANAGER_EMAIL;
const managerPassword = process.env.VALIDATION_MANAGER_PASSWORD;
const handlerEmail = process.env.VALIDATION_HANDLER_EMAIL;
const handlerPassword = process.env.VALIDATION_HANDLER_PASSWORD;
if (!managerEmail || !managerPassword || !handlerEmail || !handlerPassword) throw new Error("Validation account variables are required");

const manager = await login(managerEmail, managerPassword);
const handler = await login(handlerEmail, handlerPassword);
if (manager.role !== "MANAGER" || handler.role !== "HANDLER") throw new Error("Validation accounts have incorrect roles");
const managerHeaders = { Authorization: "Bearer " + manager.token };
const handlerHeaders = { Authorization: "Bearer " + handler.token };
const source = await request("/batches/" + sourceBatchId, { headers: managerHeaders });
if (source.farmId !== manager.farmId) throw new Error("Source batch is outside the validation manager farm");
if (source.status === "ARCHIVED") throw new Error("Source batch is archived");
if (!Number.isInteger(source.initialPopulation) || source.initialPopulation < 20) throw new Error("Source batch needs at least 20 birds");

const handlers = await request("/handlers", { headers: managerHeaders });
const validationHandler = handlers.find(item => item.email === handlerEmail);
if (!validationHandler?.id) throw new Error("Validation handler is not visible to the manager");

const recordDays = profile === "realistic"
  ? [1, 10, 20, 30, 45, 60, 75, 90, days].filter((day, index, values) => day <= days && values.indexOf(day) === index)
  : Array.from({ length: days }, (_, index) => index + 1);
const checkpoints = [30, 60, days].filter((day, index, values) => day <= days && values.indexOf(day) === index);
const expected = {
  initialPopulation: source.initialPopulation,
  healthDeaths: 2,
  accidentalDeaths: 1,
  suspectedPredation: 1,
  confirmedPredation: 1,
  missing: 2,
  returned: 1,
  transferOut: 3,
  transferIn: 2,
  sales: 4,
  culling: 1,
  countCorrection: 2
};
expected.finalPopulation = expected.initialPopulation - 10;
const plan = {
  classification: "SYNTHETIC_VALIDATION", runId, sourceBatchId: Number(sourceBatchId),
  sourceBatchName: source.name, batchName: "[TEST COPY] " + source.name + " - " + runId,
  startDate, today, days, seed, profile, recordDays, checkpoints, expected
};
await writeFile(artifactDir + "/simulation-plan.json", JSON.stringify(plan, null, 2));
if (dryRun) {
  await writeFile(artifactDir + "/run-manifest.json", JSON.stringify({ ...plan, status: "DRY_RUN" }, null, 2));
  console.log(JSON.stringify({ status: "DRY_RUN", artifactDir, plan }, null, 2));
  process.exit(0);
}

const batches = await request("/batches", { headers: managerHeaders });
const batchName = plan.batchName;
if (batches.some(item => item.name === batchName)) throw new Error("This deterministic run already exists: " + batchName);
const createdBatch = await request("/batches", {
  method: "POST", headers: managerHeaders,
  body: {
    name: batchName, initialPopulation: source.initialPopulation, startDate,
    bloodline: source.bloodline || null,
    source: "SYNTHETIC_VALIDATION:" + runId + " derived from local copy " + source.name,
    handlerUserIds: [validationHandler.id]
  }
});
const batchId = createdBatch.id;

const eventDate = day => addDays(startDate, day - 1);
async function event(day, type, count, title, requestedDelta = null) {
  const body = {
    eventDate: eventDate(day), eventType: type, title, affectedCount: count,
    details: "Synthetic validation event; not a production record.",
    tags: "SYNTHETIC_VALIDATION:" + runId,
    operationId: stableUuid(runId + ":event:" + day + ":" + type + ":" + title)
  };
  if (requestedDelta !== null) body.populationDelta = requestedDelta;
  return request("/batches/" + batchId + "/events", { method: "POST", headers: handlerHeaders, body });
}
async function record(day) {
  return request("/batches/" + batchId + "/records", {
    method: "POST", headers: handlerHeaders,
    body: {
      recordDate: eventDate(day), temperatureC: null, mortalityCount: null,
      feedIntakeG: null, waterIntakeMl: null,
      behaviorNotes: "Synthetic observation day " + day + "; no measured feed, water, or temperature value.",
      temperatureQuality: "UNAVAILABLE", feedQuality: "UNAVAILABLE", waterQuality: "UNAVAILABLE"
    }
  });
}
async function input(day, productType, brandName, productName, purpose) {
  return request("/inputs", {
    method: "POST", headers: handlerHeaders,
    body: {
      batchId, recordedAt: eventDate(day) + "T08:00:00Z", productType, brandName, productName,
      quantity: 1, unit: productType === "FEED" ? "pack" : "sachet",
      route: productType === "MEDICINE" ? "SOLUBLE_IN_WATER" : null, purpose,
      notes: "Synthetic validation input; actual intake is not claimed."
    }
  });
}
async function finance(day, type, category, amount, description) {
  return request("/financial-transactions", {
    method: "POST", headers: managerHeaders,
    body: {
      batchId, transactionDate: eventDate(day), type, category, amount, currency: "PHP",
      counterparty: "Synthetic validation supplier", description
    }
  });
}

const events = [
  [3, "HEALTH_CONCERN", 1, "Health concern observed"],
  [7, "HEALTH_DEATH", expected.healthDeaths, "Health-related death"],
  [14, "ACCIDENTAL_DEATH", expected.accidentalDeaths, "Accidental death"],
  [20, "BEHAVIOR_OBSERVATION", 1, "Behavior observation"],
  [23, "SUSPECTED_PREDATION", expected.suspectedPredation, "Suspected predation"],
  [31, "CONFIRMED_PREDATION", expected.confirmedPredation, "Confirmed predation"],
  [45, "MISSING", expected.missing, "Missing birds"],
  [50, "VACCINE_MEDICINE", 1, "Medicine recorded"],
  [60, "FOUND_RETURNED", expected.returned, "Found and returned"],
  [70, "TRANSFER_OUT", expected.transferOut, "Transfer out"],
  [72, "TRANSFER_IN", expected.transferIn, "Transfer in"],
  [80, "SALE", expected.sales, "Sale recorded"],
  [90, "CULLING", expected.culling, "Culling recorded"],
  [95, "COUNT_CORRECTION", 1, "Count correction", expected.countCorrection]
].filter(item => item[0] <= days);
for (const item of events) await event(item[0], item[1], item[2], item[3], item[4] ?? null);
for (const day of recordDays) await record(day);
await input(Math.min(5, days), "FEED", "Baby Stag Booster", "Starter feed", "Routine feed record");
await input(Math.min(50, days), "MEDICINE", "Validation Vitamin Sachet", "Soluble vitamin", "Synthetic health intervention history");
await finance(Math.min(4, days), "EXPENSE", "Feed", 2500, "Synthetic batch feed expense");
await finance(Math.min(50, days), "EXPENSE", "Medicine", 450, "Synthetic batch medicine expense");
await finance(Math.min(80, days), "INCOME", "Sale", 1000, "Synthetic batch sale income");

const task = await request("/tasks", {
  method: "POST", headers: managerHeaders,
  body: {
    title: "Review synthetic batch history",
    instructions: "Check the generated history and report; this is a technical validation task.",
    batchId, assignedHandlerId: validationHandler.id,
    dueAt: new Date(Date.now() + 3600000).toISOString(), priority: "NORMAL"
  }
});
await request("/tasks/" + task.id + "/status", {
  method: "POST", headers: handlerHeaders,
  body: { status: "COMPLETED", note: "Synthetic validation dry run completed." }
});

for (const checkpoint of checkpoints) {
  const date = eventDate(checkpoint);
  await request("/batches/" + batchId + "/selection-reviews", {
    method: "POST", headers: managerHeaders,
    body: {
      periodStart: startDate, periodEnd: date, asOfDate: date,
      purpose: "SYNTHETIC_VALIDATION", snapshotNote: "Synthetic checkpoint day " + checkpoint,
      idempotencyKey: runId + ":review:" + checkpoint
    }
  });
}

const manifest = { ...plan, status: "APPLIED", testBatchId: batchId, completedAt: new Date().toISOString() };
await writeFile(artifactDir + "/run-manifest.json", JSON.stringify(manifest, null, 2));

if (verify) {
  const actualBatch = await request("/batches/" + batchId, { headers: managerHeaders });
  const actualEvents = await request("/batches/" + batchId + "/events?limit=200", { headers: managerHeaders });
  const actualRecords = await request("/batches/" + batchId + "/records?limit=200", { headers: managerHeaders });
  const actualInputs = await request("/inputs?batchId=" + batchId, { headers: managerHeaders });
  const allFinance = await request("/financial-transactions", { headers: managerHeaders });
  const allTasks = await request("/tasks", { headers: managerHeaders });
  const reviews = await request("/batches/" + batchId + "/selection-reviews", { headers: managerHeaders });
  const totals = {};
  for (const item of actualEvents) totals[item.eventType] = (totals[item.eventType] || 0) + Number(item.affectedCount || 0);
  const financeForBatch = allFinance.filter(item => Number(item.batchId) === Number(batchId));
  const checks = {
    initialPopulation: actualBatch.initialPopulation === expected.initialPopulation,
    finalPopulation: actualBatch.currentPopulation === expected.finalPopulation,
    healthDeaths: totals.HEALTH_DEATH === expected.healthDeaths,
    missing: totals.MISSING === expected.missing,
    returned: totals.FOUND_RETURNED === expected.returned,
    populationCategories: totals.TRANSFER_OUT === expected.transferOut && totals.SALE === expected.sales && totals.CULLING === expected.culling,
    products: actualInputs.length >= 2 && actualInputs.every(item => Number(item.batchId) === Number(batchId)),
    finance: financeForBatch.length >= 3,
    taskCompleted: allTasks.some(item => Number(item.batchId) === Number(batchId) && item.status === "COMPLETED"),
    reports: reviews.length === checkpoints.length,
    recordCoverage: actualRecords.length === recordDays.length
  };
  const failed = Object.entries(checks).filter(([, passed]) => !passed).map(([name]) => name);
  const actual = { batch: actualBatch, eventTotals: totals, eventCount: actualEvents.length,
    recordCount: actualRecords.length, inputCount: actualInputs.length, financeCount: financeForBatch.length,
    selectionReviewCount: reviews.length, checks, failed };
  await writeFile(artifactDir + "/actual-results.json", JSON.stringify(actual, null, 2));
  await writeFile(artifactDir + "/verification-report.md", "# Synthetic validation verification\n\n" +
    (failed.length ? "FAIL: " + failed.join(", ") : "PASS: all automated checks passed") + "\n");
  if (failed.length) throw new Error("Verification failed: " + failed.join(", "));
  const finalDate = eventDate(checkpoints[checkpoints.length - 1]);
  const finalReview = reviews.find(item => item.asOfDate === finalDate);
  if (finalReview?.id) await writeFile(artifactDir + "/selection-review-final.pdf",
    await downloadPdf("/batches/" + batchId + "/selection-reviews/" + finalReview.id + "/pdf", manager.token));
}

console.log(JSON.stringify({ status: "PASS", classification: "SYNTHETIC_VALIDATION", batchId,
  batchName, startDate, today, finalPopulation: expected.finalPopulation, artifactDir }, null, 2));
