import { chmod, mkdir, readFile, rename, rm, writeFile } from "node:fs/promises";
import { homedir } from "node:os";
import { dirname, join } from "node:path";
import { randomBytes, randomUUID } from "node:crypto";

export function companionHome(env = process.env) {
  return env.JEAN_CALCUL_COMPANION_HOME || join(homedir(), ".config", "jean-calcul-companion");
}

export async function ensurePrivateDirectory(path) {
  await mkdir(path, { recursive: true, mode: 0o700 });
  await chmod(path, 0o700).catch(() => {});
}

export async function readJson(path, fallback = null) {
  try {
    return JSON.parse(await readFile(path, "utf8"));
  } catch (error) {
    if (error?.code === "ENOENT") return fallback;
    throw error;
  }
}

export async function writePrivateJson(path, value) {
  await ensurePrivateDirectory(dirname(path));
  const temp = `${path}.${process.pid}.${randomBytes(6).toString("hex")}.tmp`;
  await writeFile(temp, JSON.stringify(value, null, 2) + "\n", { mode: 0o600 });
  await chmod(temp, 0o600).catch(() => {});
  await rename(temp, path);
  await chmod(path, 0o600).catch(() => {});
}

export async function removeFile(path) {
  await rm(path, { force: true });
}

export async function loadOrCreateHostId(home) {
  const path = join(home, "host.json");
  const existing = await readJson(path);
  if (existing?.ext_agent_host_id) return existing.ext_agent_host_id;
  const id = `urn:uuid:${randomUUID()}`;
  await writePrivateJson(path, { version: 1, ext_agent_host_id: id });
  return id;
}

export function credentialPath(home) {
  return join(home, "chatgpt-accounts.json");
}

export function pairingPath(home) {
  return join(home, "pairing.json");
}

export function tlsKeyPath(home) {
  return join(home, "tls-key.pem");
}

export function tlsCertPath(home) {
  return join(home, "tls-cert.pem");
}
