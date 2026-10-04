#!/usr/bin/env node
import { companionHome } from "./storage.js";
import { authStatus, loginWithChatGpt, logoutChatGpt } from "./auth.js";
import { resetPairingToken } from "./pairing.js";
import { ChatGptPlanClient } from "./inference.js";
import { createCompanionServer } from "./server.js";

function valueOf(name, fallback) {
  const exact = process.argv.find((arg) => arg.startsWith(`--${name}=`));
  if (exact) return exact.slice(name.length + 3);
  const index = process.argv.indexOf(`--${name}`);
  return index >= 0 ? process.argv[index + 1] : fallback;
}

async function main() {
  const command = process.argv[2] || "serve";
  const home = companionHome();

  if (command === "login") {
    const result = await loginWithChatGpt(home, { newAccount: process.argv.includes("--new") });
    console.log(`Connexion ChatGPT validée pour ${result.label}.`);
    return;
  }

  if (command === "logout") {
    const result = await logoutChatGpt(home);
    console.log(result.revoked ? "Session ChatGPT révoquée et jetons locaux effacés." : "Jetons locaux effacés; révocation distante non confirmée.");
    return;
  }

  if (command === "status") {
    const status = await authStatus(home);
    console.log(status.authenticated ? `ChatGPT: connecté (${status.label})` : "ChatGPT: connexion requise");
    return;
  }

  if (command === "models") {
    const models = await new ChatGptPlanClient(home).listModels();
    if (!models.length) {
      console.log("Aucun modèle ChatGPT visible pour ce compte.");
    } else {
      for (const model of models) console.log(`${model.id}\t${model.display_name}`);
    }
    return;
  }

  if (command === "pairing-reset") {
    const token = await resetPairingToken(home);
    console.log("Nouveau jeton d'appairage local (affiché une seule fois):");
    console.log(token);
    console.log("L'ancien jeton est immédiatement révoqué.");
    return;
  }

  if (command !== "serve") throw new Error(`Commande inconnue: ${command}`);

  const host = valueOf("host", "127.0.0.1");
  const port = Number(valueOf("port", "43120"));
  if (!Number.isInteger(port) || port < 1 || port > 65535) throw new Error("Port invalide.");
  const companion = await createCompanionServer({ home, host, port });
  const address = await companion.start();
  console.log(`Jean Calcul Companion écoute sur https://${host}:${address.port}`);
  console.log(`Empreinte TLS à saisir dans Android: ${companion.pin}`);
  if (companion.firstPairingToken) {
    console.log("Premier jeton d'appairage local (affiché une seule fois):");
    console.log(companion.firstPairingToken);
  }
  const status = await authStatus(home);
  console.log(status.authenticated ? "ChatGPT: connecté" : "ChatGPT: connexion requise (npm run login)");

  const shutdown = async () => {
    await companion.close();
    process.exit(0);
  };
  process.once("SIGINT", shutdown);
  process.once("SIGTERM", shutdown);
}

main().catch((error) => {
  console.error(error?.message || "Erreur compagnon.");
  process.exitCode = 1;
});
