# ByteBanter — PR Notes

This update extends the BurpAI-only ByteBanter currently published in the BApp
Store to support multiple LLM providers — Burp AI (default), Ollama, any
OpenAI-compatible Chat Completions endpoint, and the Anthropic Messages API —
under PortSwigger's policy for extensions that use third-party LLMs. It also
introduces an opt-in Success Verification feature for Burp Intruder, an opt-in
for persisting sensitive data, and a number of UX and prompt-engineering
fixes.

> **Heads-up for the reviewer:** the source tree contains an additional engine,
> `ClaudeCodeEngine`, that delegates prompts to a local Claude Code CLI
> subprocess so that users can route through their existing Anthropic
> subscription instead of an API key. This engine bypasses the Montoya
> networking API (the subprocess makes its own HTTP calls) and therefore does
> not satisfy the third-party LLM policy in the same way the four engines
> above do. We are happy to **remove the registration of this engine before
> the BApp Store build** if PortSwigger prefers; the README documents the
> exact one-line change in `ByteBanterPayloadGenerator`. The compliance
> claims below describe Burp AI, Ollama, the OpenAI-compatible engine, and
> the Anthropic Messages API.

## Compliance with the third-party LLM policy

The four criteria PortSwigger requires for extensions that talk to third-party
LLMs are addressed as follows:

- The extension declares `EnhancedCapability.AI_FEATURES` and verifies that
  Burp AI is enabled before every LLM call. The verification path runs the
  check silently to avoid dialog spam during background work; user-initiated
  actions surface a dialog when AI is unavailable.
- Burp AI is the default provider. It is registered first in the engine list
  and is pre-selected on first load.
- All requests to third-party LLM providers use the Montoya networking API
  (`api.http().sendRequest`). No third-party HTTP client is bundled.
- All requests to third-party LLM providers are sent with
  `RequestOptions.withUpstreamTLSVerification()` to enforce upstream TLS
  validation, including the model-discovery call against Ollama.

## New features

### Multi-provider support

A combo box at the top of the ByteBanter tab lets the user pick the engine.
Settings are stored per engine.

- Ollama via its native `/api/chat` endpoint, with auto-discovery of installed
  models from the configured URL (the model dropdown populates as soon as the
  URL is valid).
- OpenAI-compatible Chat Completions endpoint, working out of the box with
  OpenAI itself and any compatible runtime such as Oobabooga, LM Studio, or
  vLLM.
- Anthropic Messages API with a dedicated API-key field, an editable model
  selector pre-populated with the current Claude model IDs, and the
  `anthropic-version` header sent automatically. The system prompt is hoisted
  to the top-level `system` field as required by Anthropic, and the response
  is parsed from `content[0].text`.

### Success Verification (opt-in)

After each Intruder response, the selected LLM judges the response against a
user-defined success criterion. The feature is off by default and is exposed
through a dedicated panel in the configuration column.

- Matches highlight the Intruder result row in red via Annotations with
  `HighlightColor.RED` and write a multi-line banner-formatted entry to Burp's
  Event Log under the header `[ByteBanter] SUCCESSFUL ATTACK DETECTED`,
  including URL, status code, and a short English summary of the winning
  strategy.
- A `ToolType.INTRUDER` filter ensures that Repeater, Proxy, and other tools
  are not affected and never trigger an extra LLM call.
- A "Generate from prompt!" button (same sparkler styling as the existing
  Optimize button) derives a starting criterion from the current
  payload-generation prompt using a one-shot example.
- A truncation spinner caps how many characters of the request and response
  are sent to the verification model, bounding token usage on large bodies.
- The JSON parser tolerates markdown fences, leading/trailing prose, and
  malformed output; on parse failure the response is silently treated as
  unsuccessful.

### Sensitive-data persistence opt-in

A new "Sensitive Data" panel sits directly above Request Limits, adjacent to
the headers field, with a right-aligned checkbox.

- The checkbox controls whether the API key and custom headers are persisted
  across Burp sessions. It is unchecked by default.
- An inline italic warning informs the user that Burp's extension data is
  stored in plaintext on disk and that the option should only be enabled on
  trusted machines.
- When unchecked, sensitive fields live only in memory and must be re-entered
  each session. The checkbox state itself is always persisted so the user's
  choice is remembered.

## UX and quality improvements

- The "Optimize!" prompt has been rewritten with stronger guardrails: the
  rewriter is required to preserve the attack goal, all concrete details
  (target names, parameter names, URLs, secret keywords, encodings, regex
  patterns), and explicit user constraints; preamble and markdown are
  forbidden in the output.
- The verification meta-prompt uses XML-tag delimiters around criterion,
  request, and response; defaults to failure on truncated or ambiguous
  evidence; and constrains the `strategy` field to describe what made the
  payload effective rather than what was observed.
- The prompt-tracking fix originally requested by PortSwigger for the BApp
  Store version now also covers the third-party engines: changing the prompt
  mid-attack is detected and the conversation is reset so the new prompt
  takes effect on the next payload.
- Excessive per-request logging has been removed across all engines. The
  extension only emits setup logs and the verification banner.
- The Anthropic model selector renders cleanly: the TitledBorder is now on a
  wrapper panel rather than directly on the JComboBox/JPasswordField, fixing
  the visual misalignment of the internal separator that was visible on some
  Look-and-Feels.
- The default verification criterion shown in the UI has been replaced with a
  concrete starting-point template (leaked secrets, database error fragments,
  stack traces) instead of a circular placeholder.

## Compatibility

- The build flow is unchanged: a single uber JAR produced by
  `mvn clean package`.
- Settings persisted from the BurpAI-only version load without errors. Any
  new parameter falls back to a safe default when not present in stored
  settings.
- No new bundled dependencies beyond the Montoya API and `org.json`.

## Verification performed

- The extension was built and loaded in Burp Suite Professional. All four
  engines are selectable from the combo box and each one runs an end-to-end
  payload-generation cycle.
- Each criterion of the third-party LLM policy was exercised manually: the
  AI-disabled state is handled silently for verification and via dialog for
  user-initiated actions; Burp AI is selected by default; outbound requests
  to third-party providers go through the Montoya API and carry upstream TLS
  verification.
- Intruder attacks were run with Success Verification enabled against a local
  target. Matching responses are highlighted red and produce
  `[ByteBanter] SUCCESSFUL ATTACK DETECTED` entries in the Event Log; non-
  matching responses are left untouched.
- With verification enabled, sending requests from Repeater does not trigger
  any extra LLM call, confirming the Intruder filter.
- Settings persist correctly across Burp restarts; the sensitive-data opt-in
  is honoured on both write and read paths.
