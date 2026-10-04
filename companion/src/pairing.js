import { createHash, randomBytes, timingSafeEqual } from "node:crypto";
import { pairingPath, readJson, writePrivateJson } from "./storage.js";

function digest(token) {
  return createHash("sha256").update(token, "utf8").digest();
}

export async function resetPairingToken(home) {
  const token = randomBytes(32).toString("base64url");
  await writePrivateJson(pairingPath(home), {
    version: 1,
    token_sha256: digest(token).toString("hex"),
    rotated_at: new Date().toISOString(),
  });
  return token;
}

export async function ensurePairingToken(home) {
  const record = await readJson(pairingPath(home));
  if (record?.token_sha256) return null;
  return resetPairingToken(home);
}

export async function verifyPairingToken(home, candidate) {
  if (typeof candidate !== "string" || candidate.length < 32 || candidate.length > 256) return false;
  const record = await readJson(pairingPath(home));
  if (!record?.token_sha256) return false;
  const expected = Buffer.from(record.token_sha256, "hex");
  const actual = digest(candidate);
  return expected.length === actual.length && timingSafeEqual(expected, actual);
}

export function bearerToken(authorization) {
  if (typeof authorization !== "string") return null;
  const match = /^Bearer\s+([^\s]+)$/i.exec(authorization.trim());
  return match?.[1] ?? null;
}
