#!/usr/bin/env node
/**
 * Idempotent n8n bootstrap: owner, Mailpit SMTP credential, outbox webhook workflow.
 * n8n only notifies; Spring Boot remains the source of business truth.
 */
const fs = require("fs");

const BASE = process.env.N8N_BASE_URL || "http://n8n:5678";
const EMAIL = process.env.N8N_OWNER_EMAIL || "n8n@konselyavisa.local";
const PASSWORD = process.env.N8N_OWNER_PASSWORD || "N8nDev!23";
const WORKFLOW_NAME = "KonselyaVisa outbox notify";
const CREDENTIAL_NAME = "Mailpit SMTP";
const BROWSER_ID = "konselya-n8n-bootstrap";

let cookie = "";

async function waitForN8n() {
  for (let i = 0; i < 60; i++) {
    try {
      const response = await fetch(`${BASE}/healthz`);
      if (response.ok) {
        return;
      }
    } catch {
      // still starting
    }
    await new Promise((resolve) => setTimeout(resolve, 2000));
  }
  throw new Error("n8n did not become healthy");
}

async function request(method, path, body) {
  const headers = {
    "browser-id": BROWSER_ID,
    accept: "application/json",
  };
  if (cookie) {
    headers.cookie = cookie;
  }
  if (body !== undefined) {
    headers["content-type"] = "application/json";
  }
  const response = await fetch(`${BASE}${path}`, {
    method,
    headers,
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  const setCookie = response.headers.getSetCookie?.() || [];
  if (setCookie.length > 0) {
    cookie = setCookie.map((value) => value.split(";")[0]).join("; ");
  } else {
    const single = response.headers.get("set-cookie");
    if (single) {
      cookie = single.split(";").map((part) => part.trim()).filter((part) => part.includes("=") && !part.toLowerCase().startsWith("path") && !part.toLowerCase().startsWith("http") && !part.toLowerCase().startsWith("samesite") && !part.toLowerCase().startsWith("expires") && !part.toLowerCase().startsWith("max-age")).join("; ");
    }
  }
  const text = await response.text();
  let json = null;
  try {
    json = text ? JSON.parse(text) : null;
  } catch {
    json = { raw: text };
  }
  return { status: response.status, json };
}

async function ensureOwner() {
  const setup = await request("POST", "/rest/owner/setup", {
    email: EMAIL,
    firstName: "Konselya",
    lastName: "Dev",
    password: PASSWORD,
  });
  if (setup.status === 200 || setup.status === 204) {
    console.log("n8n owner created");
    return;
  }
  console.log(`owner setup status ${setup.status} (already initialized is OK)`);
}

async function login() {
  let result;
  for (let attempt = 0; attempt < 6; attempt++) {
    result = await request("POST", "/rest/login", {
      emailOrLdapLoginId: EMAIL,
      password: PASSWORD,
    });
    if (result.status === 429) {
      await new Promise((resolve) => setTimeout(resolve, 15000 * (attempt + 1)));
      continue;
    }
    break;
  }
  if (result.status >= 400) {
    throw new Error(`n8n login failed: ${result.status} ${JSON.stringify(result.json)}`);
  }
  console.log("n8n login OK");
}

async function ensureSmtpCredential() {
  const list = await request("GET", "/rest/credentials");
  const payload = list.json?.data;
  const items = Array.isArray(payload) ? payload : payload || [];
  const existing = Array.isArray(items) ? items.find((item) => item.name === CREDENTIAL_NAME) : null;
  if (existing) {
    console.log(`SMTP credential exists ${existing.id}`);
    return existing.id;
  }
  const created = await request("POST", "/rest/credentials", {
    name: CREDENTIAL_NAME,
    type: "smtp",
    data: {
      user: "",
      password: "",
      host: process.env.MAILPIT_HOST || "mailpit",
      port: Number(process.env.MAILPIT_SMTP_PORT || 1025),
      secure: false,
      disableStartTls: true,
    },
  });
  if (created.status >= 400) {
    throw new Error(`create credential failed: ${created.status} ${JSON.stringify(created.json)}`);
  }
  const id = created.json?.data?.id || created.json?.id;
  console.log(`SMTP credential created ${id}`);
  return id;
}

function workflowItems(json) {
  const candidates = [json?.data?.data, json?.data?.workflows, json?.data, json?.workflows, json];
  for (const candidate of candidates) {
    if (Array.isArray(candidate)) {
      return candidate;
    }
  }
  return [];
}

async function ensureWorkflow(smtpId) {
  const list = await request("GET", "/rest/workflows");
  const items = workflowItems(list.json);
  const template = JSON.parse(fs.readFileSync("/import/konselyavisa-outbox-notify.json", "utf8"));
  template.nodes.forEach((node) => {
    if (node.credentials?.smtp) {
      node.credentials.smtp.id = smtpId;
    }
  });
  template.active = false;
  const existing = items.find((item) => item.name === WORKFLOW_NAME);
  let workflowId = existing?.id;
  if (!existing) {
    const created = await request("POST", "/rest/workflows", template);
    if (created.status >= 400) {
      throw new Error(`create workflow failed: ${created.status} ${JSON.stringify(created.json)}`);
    }
    workflowId = created.json?.data?.id || created.json?.id;
    console.log(`workflow created ${workflowId}`);
  } else {
    const current = await request("GET", `/rest/workflows/${workflowId}`);
    const body = current.json?.data || current.json;
    body.nodes = template.nodes;
    body.connections = template.connections;
    body.settings = template.settings;
    const updated = await request("PATCH", `/rest/workflows/${workflowId}`, body);
    if (updated.status >= 400) {
      throw new Error(`update workflow failed: ${updated.status} ${JSON.stringify(updated.json)}`);
    }
    console.log(`workflow updated ${workflowId}`);
  }
  const activated = await request("PATCH", `/rest/workflows/${workflowId}`, { active: true });
  if (activated.status >= 400) {
    throw new Error(`activate workflow failed: ${activated.status} ${JSON.stringify(activated.json)}`);
  }
  console.log("workflow active");
}

async function main() {
  await waitForN8n();
  await ensureOwner();
  await login();
  const smtpId = await ensureSmtpCredential();
  await ensureWorkflow(smtpId);
}

main().catch((error) => {
  console.error(error);
  process.exit(1);
});
