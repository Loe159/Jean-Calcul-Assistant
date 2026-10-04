import assert from "node:assert/strict";
import { mkdtemp, rm } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";
import test from "node:test";
import { resetPairingToken, verifyPairingToken } from "../src/pairing.js";

test("pairing token is hashed, verifiable and revocable", async () => {
  const home = await mkdtemp(join(tmpdir(), "jean-pairing-"));
  try {
    const first = await resetPairingToken(home);
    assert.equal(await verifyPairingToken(home, first), true);
    assert.equal(await verifyPairingToken(home, "wrong"), false);

    const second = await resetPairingToken(home);
    assert.notEqual(first, second);
    assert.equal(await verifyPairingToken(home, first), false);
    assert.equal(await verifyPairingToken(home, second), true);
  } finally {
    await rm(home, { recursive: true, force: true });
  }
});
