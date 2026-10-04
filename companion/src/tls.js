import { X509Certificate, createHash } from "node:crypto";
import { access, chmod, readFile } from "node:fs/promises";
import { execFile } from "node:child_process";
import { promisify } from "node:util";
import { tlsCertPath, tlsKeyPath, ensurePrivateDirectory } from "./storage.js";

const execFileAsync = promisify(execFile);

async function exists(path) {
  try {
    await access(path);
    return true;
  } catch {
    return false;
  }
}

export async function ensureTlsMaterial(home) {
  await ensurePrivateDirectory(home);
  const keyPath = tlsKeyPath(home);
  const certPath = tlsCertPath(home);
  if (!(await exists(keyPath)) || !(await exists(certPath))) {
    try {
      await execFileAsync("openssl", [
        "req", "-x509", "-newkey", "rsa:2048", "-sha256", "-nodes",
        "-days", "3650", "-subj", "/CN=Jean Calcul Companion",
        "-keyout", keyPath, "-out", certPath,
      ]);
    } catch (error) {
      throw new Error(
        "Impossible de générer le certificat TLS. Installez openssl puis relancez le compagnon.",
        { cause: error },
      );
    }
    await chmod(keyPath, 0o600).catch(() => {});
    await chmod(certPath, 0o644).catch(() => {});
  }
  const [key, cert] = await Promise.all([readFile(keyPath), readFile(certPath)]);
  return { key, cert, pin: certificatePin(cert) };
}

export function certificatePin(pem) {
  const certificate = new X509Certificate(pem);
  const spki = certificate.publicKey.export({ type: "spki", format: "der" });
  return `sha256/${createHash("sha256").update(spki).digest("base64")}`;
}
