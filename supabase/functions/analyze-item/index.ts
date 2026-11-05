// Supabase Edge Function: analyze-item
//
// The Android client posts { image, mimeType, model } where `image` is a
// base64 encoded JPEG. The vision provider key is read from the function's
// environment and never leaves the server.
//
// Required environment variables:
//   OLLAMA_API_KEY   (secret)
// Optional:
//   OLLAMA_BASE_URL  (default: https://ollama.com)
//   OLLAMA_MODEL     (default: gemma3:27b)
//
// Deploy with JWT verification disabled (the mobile app does not carry a
// Supabase session), or wire the app to send a Supabase anon key:
//   supabase functions deploy analyze-item --no-verify-jwt

const corsHeaders: Record<string, string> = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers":
    "authorization, x-client-info, apikey, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};

const MAX_BASE64_LENGTH = 14 * 1024 * 1024; // ~10 MB JPEG

function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { ...corsHeaders, "Content-Type": "application/json" },
  });
}

const PROMPT = `You are an assistant for a lost-and-found app.
Look at the item in the photo and return ONLY a JSON object (no markdown) with:
{
  "itemType": "short human readable item name",
  "primaryColor": "dominant color",
  "secondaryColors": ["other visible colors"],
  "material": "likely material or empty string",
  "brand": "visible brand or empty string",
  "suggestedCategory": "one of: Phones, Wallets, Cards, Keys, Bags, Other",
  "detectedFeatures": ["short identifying features"],
  "tags": ["search tags"],
  "keywords": ["extra search keywords"],
  "confidence": 0
}
"confidence" must be a number between 0 and 100. Never invent a brand or
material when it is not visible; use an empty string instead.`;

interface AnalysisPayload {
  itemType?: unknown;
  primaryColor?: unknown;
  primary_color?: unknown;
  color?: unknown;
  secondaryColors?: unknown;
  suggestedCategory?: unknown;
  suggested_category?: unknown;
  category?: unknown;
  material?: unknown;
  brand?: unknown;
  detectedFeatures?: unknown;
  detected_features?: unknown;
  features?: unknown;
  tags?: unknown;
  keywords?: unknown;
  confidence?: unknown;
}

function text(value: unknown): string {
  return typeof value === "string" ? value.trim() : "";
}

function list(value: unknown): string[] {
  if (Array.isArray(value)) {
    return value
      .map((entry) => (typeof entry === "string" ? entry.trim() : ""))
      .filter((entry) => entry.length > 0);
  }
  if (typeof value === "string") {
    return value
      .split(",")
      .map((entry) => entry.trim())
      .filter((entry) => entry.length > 0);
  }
  return [];
}

function normalise(payload: AnalysisPayload, fallbackModel: string) {
  const itemType = text(payload.itemType ?? payload.detectedFeatures ?? "");
  const primaryColor = text(
    payload.primaryColor ?? payload.primary_color ?? payload.color ?? "",
  );
  const category = text(
    payload.suggestedCategory ?? payload.suggested_category ?? payload.category ?? "",
  );

  let confidence = Number(payload.confidence ?? 0);
  if (!Number.isFinite(confidence)) confidence = 0;
  if (confidence > 0 && confidence <= 1) confidence *= 100;
  confidence = Math.max(0, Math.min(100, confidence));

  const features = list(
    payload.detectedFeatures ?? payload.detected_features ?? payload.features,
  );
  const tags = list(payload.tags);

  return {
    itemType,
    primaryColor: primaryColor || "Unknown",
    suggestedCategory: category,
    material: text(payload.material),
    brand: text(payload.brand),
    detectedFeatures: features,
    secondaryColors: list(payload.secondaryColors),
    tags: tags.length > 0 ? tags : features,
    keywords: list(payload.keywords),
    confidence: Math.round(confidence),
    model: fallbackModel,
  };
}

Deno.serve(async (request: Request): Promise<Response> => {
  if (request.method === "OPTIONS") {
    return new Response("ok", { headers: corsHeaders });
  }
  if (request.method !== "POST") {
    return json({ error: "Method not allowed" }, 405);
  }

  // Security: Enforce client secret authentication
  const expectedSecret = Deno.env.get("AI_CLIENT_SECRET") || "laf-campus-vision-token-v1";
  const authHeader = request.headers.get("Authorization") ?? request.headers.get("apikey") ?? "";
  const token = authHeader.replace(/^Bearer\s+/i, "").trim();
  if (!token || token !== expectedSecret) {
    return json({ error: "Unauthorized: valid AI_CLIENT_SECRET required" }, 401);
  }

  const apiKey = Deno.env.get("OLLAMA_API_KEY");
  if (!apiKey) {
    console.error("OLLAMA_API_KEY is not configured");
    return json({ error: "AI service is not configured" }, 500);
  }

  let payload: { image?: unknown; mimeType?: unknown; model?: unknown };
  try {
    payload = await request.json();
  } catch {
    return json({ error: "Invalid JSON body" }, 400);
  }

  const image = typeof payload.image === "string" ? payload.image.trim() : "";
  if (!image) {
    return json({ error: "Missing image" }, 400);
  }
  if (image.length > MAX_BASE64_LENGTH) {
    return json({ error: "Image is too large" }, 413);
  }

  const baseUrl = (Deno.env.get("OLLAMA_BASE_URL") ?? "https://ollama.com")
    .replace(/\/+$/, "");

  // Model pinning: only allow supported vision models
  const allowedModels = new Set(["gemma3:27b", "gemma4:31b", "llava:13b"]);
  const requestedModel = text(payload.model);
  const model = allowedModels.has(requestedModel)
    ? requestedModel
    : (Deno.env.get("OLLAMA_MODEL") || "gemma3:27b");

  try {
    const upstream = await fetch(`${baseUrl}/api/chat`, {
      method: "POST",
      headers: {
        "Authorization": `Bearer ${apiKey}`,
        "Content-Type": "application/json",
      },
      body: JSON.stringify({
        model,
        stream: false,
        format: "json",
        options: { temperature: 0.2 },
        messages: [
          {
            role: "user",
            content: PROMPT,
            images: [image],
          },
        ],
      }),
    });

    const raw = await upstream.text();
    if (!upstream.ok) {
      console.error("Vision provider error", upstream.status, raw.slice(0, 500));
      return json({ error: "AI provider request failed" }, 502);
    }

    let content = "";
    try {
      const parsed = JSON.parse(raw);
      content = parsed?.message?.content ?? parsed?.response ?? "";
    } catch {
      return json({ error: "AI provider returned invalid JSON" }, 502);
    }

    let analysis: AnalysisPayload;
    try {
      analysis = typeof content === "string" ? JSON.parse(content) : content;
    } catch {
      console.error("Unable to parse model content", content.slice(0, 500));
      return json({ error: "AI provider returned an unexpected response" }, 502);
    }

    const result = normalise(analysis ?? {}, model);
    if (!result.itemType) {
      return json({ error: "AI could not identify the item" }, 502);
    }

    return json({ data: result }, 200);
  } catch (error) {
    console.error("analyze-item failed", error);
    return json({ error: "AI analysis is temporarily unavailable" }, 500);
  }
});
