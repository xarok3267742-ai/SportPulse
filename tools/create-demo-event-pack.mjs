import { readFileSync } from "node:fs";
import { createSign } from "node:crypto";

const now = Date.now();
const minute = 60 * 1000;
const hour = 60 * minute;
const anchor = Math.floor(now / hour) * hour;
const envelopeVersion = 1;
const keyId = argumentValue("--key-id")
  ?? "sport-pulse-local-dev-2026";
const algorithm = "SHA256withRSA";
const domain = "SPORT_PULSE_EVENT_PACK_ENVELOPE_V1";
const revision = process.argv.includes("--update")
  ? 2
  : process.argv.includes("--rollback")
    ? 0
    : 1;
const generatedAt = anchor - hour + revision * minute;

const football = {
  id: "preview_football",
  sport: "Футбол",
  tournament: "Премьер-лига",
  region: "Россия",
  match: "Север — Столица",
  startAt: anchor + (revision >= 2 ? 4 : 3) * hour,
  focus: "Составы, темп и стандартные положения",
  note: revision >= 2
    ? "Стартовый состав уточнен в новой версии пакета."
    : "Демонстрационные данные: проверьте официальные заявки.",
  tags: revision >= 2
    ? ["футбол", "состав подтвержден"]
    : ["футбол", "вечер"],
  assessment: {
    form: 68,
    lineup: revision >= 2 ? 64 : 44,
    load: 57,
    context: 72,
    sources: revision >= 2 ? 72 : 51
  }
};

const hockey = {
  id: "preview_hockey",
  sport: "Хоккей",
  tournament: "Континентальная лига",
  region: "Россия и СНГ",
  match: "Метеор — Барс",
  startAt: anchor + 5 * hour,
  focus: "Вратари, спецбригады и календарная нагрузка",
  note: "Демонстрационные данные: перепроверьте стартовые составы.",
  tags: ["хоккей", "составы"],
  assessment: {
    form: 61,
    lineup: 52,
    load: 46,
    context: 66,
    sources: 58
  }
};

const basketball = {
  id: "preview_basketball",
  sport: "Баскетбол",
  tournament: "Единая лига",
  region: "СНГ",
  match: "Алатау — Волга",
  startAt: anchor + 27 * hour,
  focus: "Ротация, темп и выездная серия",
  note: "Демонстрационные данные: сверьте травмы и протокол.",
  tags: ["баскетбол", "ротация"],
  assessment: {
    form: 56,
    lineup: 63,
    load: 49,
    context: 60,
    sources: 47
  }
};

const volleyball = {
  id: "preview_volleyball",
  sport: "Волейбол",
  tournament: "Суперлига",
  region: "Россия",
  match: "Нева — Урал",
  startAt: anchor + 30 * hour,
  focus: "Состав, прием и плотность календаря",
  note: "Новое демонстрационное событие второй версии.",
  tags: ["волейбол", "новое событие"],
  assessment: {
    form: 58,
    lineup: 61,
    load: 54,
    context: 67,
    sources: 55
  }
};

const events = revision >= 2
  ? [football, basketball, volleyball]
  : [football, hockey, basketball];

const eventPack = {
  schemaVersion: 1,
  packageId: `local_preview_${Math.floor(anchor / 1000)}_r${revision}`,
  source: "Локальный тестовый пакет",
  generatedAt,
  validUntil: anchor + 24 * hour,
  events
};

const payload = `${JSON.stringify(eventPack, null, 2)}\n`;

if (process.argv.includes("--signed")) {
  const privateKeyPath = argumentValue("--private-key")
    ?? process.env.SPORT_PULSE_EVENT_PACK_PRIVATE_KEY;
  if (!privateKeyPath) {
    throw new Error(
      "Для --signed укажите --private-key <PEM> или "
      + "SPORT_PULSE_EVENT_PACK_PRIVATE_KEY."
    );
  }
  const signer = createSign("RSA-SHA256");
  signer.update(`${domain}\n${keyId}\n${algorithm}\n`, "utf8");
  signer.update(payload, "utf8");
  const signature = signer.sign(
    readFileSync(privateKeyPath),
    "base64"
  );
  const envelope = {
    envelopeVersion,
    keyId,
    algorithm,
    payloadEncoding: "base64",
    payload: Buffer.from(payload, "utf8").toString("base64"),
    signature
  };
  process.stdout.write(`${JSON.stringify(envelope, null, 2)}\n`);
} else {
  process.stdout.write(payload);
}

function argumentValue(name) {
  const index = process.argv.indexOf(name);
  if (index < 0) return null;
  const value = process.argv[index + 1];
  if (!value || value.startsWith("--")) {
    throw new Error(`После ${name} требуется значение.`);
  }
  return value;
}
