import http from "node:http";
import { fileURLToPath } from "node:url";

const PROVIDER_URL = "https://v3.football.api-sports.io/fixtures";
const MOSCOW_TIMEZONE = "Europe/Moscow";
const CACHE_TTL_MILLIS = 5 * 60 * 1000;
const RATE_WINDOW_MILLIS = 60 * 1000;
const RATE_LIMIT = 30;
const MAX_RESPONSE_BYTES = 2 * 1024 * 1024;

function moscowDate(now) {
  const parts = new Intl.DateTimeFormat("en-GB", {
    timeZone: MOSCOW_TIMEZONE,
    year: "numeric",
    month: "2-digit",
    day: "2-digit"
  }).formatToParts(now);
  const value = Object.fromEntries(
    parts.filter((part) => part.type !== "literal")
      .map((part) => [part.type, part.value])
  );
  return `${value.year}-${value.month}-${value.day}`;
}

function addUtcDays(date, days) {
  const next = new Date(`${date}T00:00:00Z`);
  next.setUTCDate(next.getUTCDate() + days);
  return next.toISOString().slice(0, 10);
}

function allowedDate(date, now) {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(date)) return false;
  const parsed = new Date(`${date}T00:00:00Z`);
  if (Number.isNaN(parsed.getTime()) || parsed.toISOString().slice(0, 10) !== date) {
    return false;
  }
  const today = moscowDate(now);
  return date >= today && date <= addUtcDays(today, 2);
}

function text(value, maxLength) {
  return typeof value === "string" ? value.slice(0, maxLength) : "";
}

function integer(value) {
  return Number.isSafeInteger(value) ? value : null;
}

function sanitizeFixture(item) {
  const id = integer(item?.fixture?.id);
  const timestamp = integer(item?.fixture?.timestamp);
  const country = text(item?.league?.country, 80);
  const league = text(item?.league?.name, 80);
  const home = text(item?.teams?.home?.name, 90);
  const away = text(item?.teams?.away?.name, 90);
  if (!id || !timestamp || !country || !league || !home || !away) return null;
  return {
    fixture: {
      id,
      timestamp,
      status: {
        short: text(item?.fixture?.status?.short, 12),
        long: text(item?.fixture?.status?.long, 60)
      }
    },
    league: { name: league, country },
    teams: { home: { name: home }, away: { name: away } },
    goals: {
      home: integer(item?.goals?.home),
      away: integer(item?.goals?.away)
    }
  };
}

function json(response, status, payload, cacheControl = "no-store") {
  const body = JSON.stringify(payload);
  response.writeHead(status, {
    "Content-Type": "application/json; charset=utf-8",
    "Content-Length": Buffer.byteLength(body),
    "Cache-Control": cacheControl,
    "X-Content-Type-Options": "nosniff",
    "Referrer-Policy": "no-referrer"
  });
  response.end(body);
}

export function createScheduleGateway({
  apiKey,
  fetchImpl = globalThis.fetch,
  clock = () => new Date()
}) {
  if (!apiKey) throw new Error("API_SPORTS_KEY is required on the server");
  if (typeof fetchImpl !== "function") throw new Error("fetch is required");

  const cache = new Map();
  const rateWindows = new Map();

  return async function scheduleGateway(request, response) {
    try {
      const requestUrl = new URL(request.url, "http://localhost");
      if (request.method !== "GET" || requestUrl.pathname !== "/fixtures") {
        json(response, 404, { error: "Not found" });
        return;
      }

      const now = clock();
      const ip = request.socket.remoteAddress || "unknown";
      const currentWindow = rateWindows.get(ip);
      if (!currentWindow || now.getTime() - currentWindow.startedAt >= RATE_WINDOW_MILLIS) {
        rateWindows.set(ip, { startedAt: now.getTime(), count: 1 });
      } else {
        currentWindow.count += 1;
        if (currentWindow.count > RATE_LIMIT) {
          json(response, 429, { error: "Temporarily unavailable" });
          return;
        }
      }

      const date = requestUrl.searchParams.get("date") || "";
      const timezone = requestUrl.searchParams.get("timezone") || "";
      if (!allowedDate(date, now) || timezone !== MOSCOW_TIMEZONE) {
        json(response, 400, { error: "Invalid schedule window" });
        return;
      }

      const cached = cache.get(date);
      if (cached && now.getTime() - cached.storedAt < CACHE_TTL_MILLIS) {
        json(response, 200, cached.payload, "public, max-age=300");
        return;
      }

      const upstream = new URL(PROVIDER_URL);
      upstream.searchParams.set("date", date);
      upstream.searchParams.set("timezone", MOSCOW_TIMEZONE);
      const providerResponse = await fetchImpl(upstream, {
        headers: {
          Accept: "application/json",
          "x-apisports-key": apiKey
        },
        signal: AbortSignal.timeout(12_000)
      });
      const body = await providerResponse.text();
      if (!providerResponse.ok || Buffer.byteLength(body) > MAX_RESPONSE_BYTES) {
        json(response, 502, { error: "Schedule source unavailable" });
        return;
      }

      const providerPayload = JSON.parse(body);
      const providerErrors = providerPayload?.errors;
      const hasErrors = Array.isArray(providerErrors)
        ? providerErrors.length > 0
        : providerErrors && Object.keys(providerErrors).length > 0;
      if (hasErrors || !Array.isArray(providerPayload?.response)) {
        json(response, 502, { error: "Schedule source unavailable" });
        return;
      }

      const payload = {
        parameters: { date, timezone: MOSCOW_TIMEZONE },
        errors: [],
        response: providerPayload.response
          .slice(0, 500)
          .map(sanitizeFixture)
          .filter(Boolean)
      };
      cache.set(date, { storedAt: now.getTime(), payload });
      json(response, 200, payload, "public, max-age=300");
    } catch {
      json(response, 502, { error: "Schedule source unavailable" });
    }
  };
}

if (process.argv[1] === fileURLToPath(import.meta.url)) {
  const port = Number.parseInt(process.env.PORT || "8787", 10);
  const handler = createScheduleGateway({
    apiKey: process.env.API_SPORTS_KEY
  });
  http.createServer(handler).listen(port, "0.0.0.0", () => {
    process.stdout.write(`Schedule gateway listening on ${port}\n`);
  });
}
