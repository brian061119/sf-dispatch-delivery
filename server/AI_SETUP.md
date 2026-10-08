# Gemini delivery integration

Both POST /api/ai/parse ({text}) and POST /api/ai/chat ({message,history}) require the existing login JWT.
Credentials are read only from the backend environment. The default model is gemini-3.8-flash;
set GEMINI_MODEL to a different model available to your Google project if necessary.

In the same macOS zsh terminal that launches the backend:

```sh
read -s "GEMINI_API_KEY?Paste API key and press Enter: "
echo
export GEMINI_API_KEY
mvn spring-boot:run
```

Do not put the key in VITE_* variables, commit it, or send it in screenshots.
Environment variables set in another terminal will not be inherited by this process.
No new Maven dependencies are required: the client uses Java 11 HttpClient and Jackson.

The model returns structured intent/package/address text. It never supplies authoritative
prices, coordinates or order status. /parse returns {prefill,missingFields,mode}; coordinates
are confirmed in the existing order wizard. /chat returns {reply,cards,prefill?}.
Only read-only business services are available to the assistant. Orders and payments still
require the user's explicit action in the existing wizard.

Quotes require weight and exact street-number addresses. The backend geocodes with public
Nominatim, bounded to San Francisco, using a small cache and at most one request per 1.1s
per process. If addresses are ambiguous, outside the bounds, or lookup is unavailable, the
assistant asks for confirmed locations and offers the order wizard. For production, replace
this single-process public lookup with a dedicated geocoding provider and shared rate limiter.
Missing dimensions use the recommendation service's existing default volume; the wizard
must confirm dimensions before final payment. Prices and availability can change.

Weight-only questions such as "How much for a 2kg package?" ask for exact addresses locally,
without a model request, unless earlier user turns contain route context. Unknown numeric
fields returned as null/0 are omitted; negative values are rejected. Transient HTTP 5xx
responses are retried at most twice with short exponential backoff and jitter.

Gemini errors (missing key, invalid permissions, quota, unavailable model, timeout) return
HTTP 503 with a sanitized message. There is no silent mock fallback. Provider bodies and
API keys are never included in errors or logs. History is bounded and cleared on login/logout.

Validation: backend AI unit/integration tests use a fake provider HTTP server and mocked
model extraction, never real credentials or billable requests. They do not prove that your
Google project has access to the default model. Restart the backend with your configured key
and test from the logged-in browser to confirm that separately.

## Product knowledge and conversational help
`src/main/resources/delivery-knowledge.md` is a curated product knowledge resource reviewed
against source, not an upload of source code or credentials. It is sent in system instructions
on each model request; update it alongside business/UI changes and restart the backend.
HELP answers now display the model's specific knowledge-grounded reply. Questions about
cancellation are HELP; explicit execution requests are WRITE and remain gated to manual UI.
QUOTE/TRACK continue to dispatch to the existing authoritative backend services. This uses
structured intent routing rather than provider-native function calls. The small knowledge
base fits in request context; there is no vector database or permanent model training.
Regression tests verify the request includes knowledge and help answers are not overwritten.
They mock model responses and cannot guarantee model factuality on every unseen question;
actual bilingual phrasing and provider availability still require live testing.
