import assert from "node:assert/strict";
import http from "node:http";
import test from "node:test";
import { createScheduleGateway } from "./server.mjs";

const FIXED_NOW = new Date("2026-08-07T09:00:00Z");

async function withServer(fetchImpl, action) {
  const handler = createScheduleGateway({
    apiKey: "server-only-key",
    fetchImpl,
    clock: () => FIXED_NOW
  });
  const server = http.createServer(handler);
  await new Promise((resolve) => server.listen(0, "127.0.0.1", resolve));
  try {
    const { port } = server.address();
    await action(`http://127.0.0.1:${port}`);
  } finally {
    await new Promise((resolve) => server.close(resolve));
  }
}

function providerResponse() {
  return new Response(JSON.stringify({
    errors: [],
    response: [{
      fixture: {
        id: 42,
        timestamp: 1786096800,
        status: { short: "NS", long: "Not Started", leaked: "drop" }
      },
      league: { name: "Premier League", country: "Russia", logo: "drop" },
      teams: {
        home: { name: "Home", logo: "drop" },
        away: { name: "Away", logo: "drop" }
      },
      goals: { home: null, away: null },
      private: "drop"
    }]
  }), { status: 200 });
}

test("forwards the provider key only upstream and sanitizes the response", async () => {
  let upstreamHeaders;
  await withServer(async (_url, options) => {
    upstreamHeaders = options.headers;
    return providerResponse();
  }, async (baseUrl) => {
    const response = await fetch(
      `${baseUrl}/fixtures?date=2026-08-07&timezone=Europe%2FMoscow`
    );
    const body = await response.json();

    assert.equal(response.status, 200);
    assert.equal(upstreamHeaders["x-apisports-key"], "server-only-key");
    assert.equal(response.headers.get("x-ratelimit-requests-remaining"), null);
    assert.deepEqual(Object.keys(body.response[0]).sort(), [
      "fixture", "goals", "league", "teams"
    ]);
    assert.equal(JSON.stringify(body).includes("server-only-key"), false);
    assert.equal(JSON.stringify(body).includes("drop"), false);
  });
});

test("rejects unsupported dates before contacting the provider", async () => {
  let calls = 0;
  await withServer(async () => {
    calls += 1;
    return providerResponse();
  }, async (baseUrl) => {
    const response = await fetch(
      `${baseUrl}/fixtures?date=2026-08-20&timezone=Europe%2FMoscow`
    );

    assert.equal(response.status, 400);
    assert.equal(calls, 0);
  });
});

test("caches a date instead of spending provider quota twice", async () => {
  let calls = 0;
  await withServer(async () => {
    calls += 1;
    return providerResponse();
  }, async (baseUrl) => {
    const url = `${baseUrl}/fixtures?date=2026-08-08&timezone=Europe%2FMoscow`;
    assert.equal((await fetch(url)).status, 200);
    assert.equal((await fetch(url)).status, 200);
    assert.equal(calls, 1);
  });
});
