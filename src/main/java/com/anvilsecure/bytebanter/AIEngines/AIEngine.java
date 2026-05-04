package com.anvilsecure.bytebanter.AIEngines;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.core.Annotations;
import burp.api.montoya.core.HighlightColor;
import burp.api.montoya.core.ToolType;
import burp.api.montoya.http.RequestOptions;
import burp.api.montoya.http.handler.*;
import burp.api.montoya.http.message.HttpHeader;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import com.anvilsecure.bytebanter.AIEngineUIs.AIEngineUI;
import org.json.JSONArray;
import org.json.JSONObject;

import javax.swing.JOptionPane;
import java.util.Arrays;
import java.util.Base64;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public abstract class AIEngine implements HttpHandler {
    protected final String name;
    protected final MontoyaApi api;
    protected static final String DEFAULT_MESSAGE = "Generate a new payload";
    protected AIEngineUI UI;
    protected JSONArray messages;
    protected Boolean isStateful = false;
    protected String prompt = "";

    protected int counter = 0;
    protected int requestsLimit = 1;
    protected boolean isInfiniteRequests = false;
    protected boolean oldInfiniteFlag = false;

    private static final String VERIFY_LOG_BANNER =
            "============================================================";

    private static final String VERIFY_SYSTEM_PROMPT =
            "You are a meticulous web application security analyst. Your job is to determine\n"
                    + "whether an HTTP response indicates that a payload sent during a Burp Intruder\n"
                    + "attack achieved its goal, according to a user-defined success criterion.\n"
                    + "\n"
                    + "Respond with a single JSON object on one line, with no markdown fences, no\n"
                    + "preamble, no commentary. The JSON must have exactly two fields:\n"
                    + "  \"success\": boolean - true only if the response clearly satisfies the user's\n"
                    + "             success criterion; false otherwise. Be conservative: prefer false\n"
                    + "             when uncertain.\n"
                    + "  \"strategy\": string - a short (max 200 characters) English summary describing\n"
                    + "              WHAT MADE THE PAYLOAD EFFECTIVE (e.g. \"Bypassed auth with\n"
                    + "              X-Forwarded-For: 127.0.0.1\"), NOT what was observed (e.g. \"the\n"
                    + "              response was 200\"). Use past tense, name the technique. Only set\n"
                    + "              when success=true. When success=false, use an empty string \"\".\n"
                    + "\n"
                    + "If the request or response shows \"...[truncated]\", base your decision only on\n"
                    + "the visible portion. Do NOT speculate about the missing part. If the visible\n"
                    + "portion is insufficient to confidently confirm success, return success=false.\n"
                    + "\n"
                    + "Example outputs:\n"
                    + "  {\"success\":true,\"strategy\":\"Reflected payload appears unescaped in HTML body, indicating XSS.\"}\n"
                    + "  {\"success\":false,\"strategy\":\"\"}";

    private static final String VERIFY_USER_TEMPLATE =
            "<criterion>\n"
                    + "<<<USER_CRITERION>>>\n"
                    + "</criterion>\n"
                    + "\n"
                    + "<request>\n"
                    + "<<<REQUEST>>>\n"
                    + "</request>\n"
                    + "\n"
                    + "<response>\n"
                    + "<<<RESPONSE>>>\n"
                    + "</response>\n"
                    + "\n"
                    + "Decide success vs failure based strictly on <criterion> applied to <response>.\n"
                    + "Output the JSON object now.";

    protected AIEngine(MontoyaApi api, String name) {
        this.api = api;
        this.name = name;
    }

    public AIEngineUI getUI() {
        return UI;
    };

    public String getName() {
        return name;
    };

    public MontoyaApi getApi() {
        return api;
    }

    // BApp Store requirement: verify AI support is enabled before any LLM call.
    protected boolean isAIEnabled() {
        return isAIEnabled(false);
    }

    protected boolean isAIEnabled(boolean silent) {
        if (api.ai().isEnabled()) {
            return true;
        }
        if (!silent) {
            JOptionPane.showMessageDialog(null,
                    "ByteBanter: Unable to generate payloads or optimize prompts because Burp AI is disabled!"
                            + "\nEnable AI features in Burp settings to use ByteBanter.",
                    "ByteBanter Error", JOptionPane.ERROR_MESSAGE);
        }
        return false;
    }

    public String askAi() {
        if (!isAIEnabled()) {
            return null;
        }
        JSONObject params = UI.getParams();
        JSONObject data = packData(new JSONObject(), params);

        isInfiniteRequests = data.getBoolean("isInfiniteRequests");
        // reset counter if attack length changes
        if (oldInfiniteFlag != isInfiniteRequests || this.requestsLimit != data.getInt("requestsLimit")) {
            counter = 0;
        }
        oldInfiniteFlag = isInfiniteRequests;
        this.requestsLimit = data.getInt("requestsLimit");

        // check if the number of maximum requests has been reached
        if (isInfiniteRequests || counter < this.requestsLimit) {
            counter++;

            // reset messages on "stateful" change or prompt change
            boolean settingsChanged = (isStateful != params.getBoolean("stateful"))
                    || !prompt.equals(params.getString("prompt"));

            isStateful = params.getBoolean("stateful");
            prompt = params.getString("prompt");

            // Reset if settings changed OR we are in stateless mode (fresh request every time)
            if (settingsChanged || !isStateful) {
                messages = new JSONArray();
                messages.put(new JSONObject().put("role", "system").put("content", prompt));
            }

            // If stateless, always add the default trigger message
            if (!isStateful) {
                messages.put(new JSONObject().put("role", "user").put("content", DEFAULT_MESSAGE));
            } else {
                // In stateful mode, if we just reset (have [System]), add the trigger.
                // Otherwise, 'messages' already contains history.
                if (messages.length() == 1) {
                    messages.put(new JSONObject().put("role", "user").put("content", DEFAULT_MESSAGE));
                }
            }
            data.remove("messages");
            data.put("messages", messages);
            String responseMessage = sendRequestToAI(data, params);
            messages.put(new JSONObject().put("role", "assistant").put("content", responseMessage));
            return responseMessage;
        } else {
            counter = 0; // reset for next run
            return null; // Stop generation
        }
    }

    // used for other interaction with the AI (i.e.: prompt optimization)
    public String askAi(String prompt, String user_input) {
        if (!isAIEnabled()) {
            return prompt;
        }
        JSONObject params = UI.getParams();
        JSONObject data = packData(new JSONObject(), params);
        ;
        JSONArray m = new JSONArray();
        m.put(new JSONObject().put("role", "system").put("content", prompt));
        m.put(new JSONObject().put("role", "user").put("content", user_input));
        data.remove("messages");
        data.put("messages", m);

        return sendRequestToAI(data, params);
    }

    protected JSONObject packData(JSONObject data, JSONObject params) {
        data.put("frequency_penalty", params.getDouble("frequency_penalty") / 20);
        data.put("max_tokens", params.getInt("max_tokens"));
        data.put("presence_penalty", params.getDouble("presence_penalty") / 20);
        data.put("temperature", params.getDouble("temperature") / 100);
        data.put("top_p", params.getDouble("top_p") / 20);
        data.put("stream", false);
        data.put("seed", new Random().nextInt() % 10000);
        return data;
    }

    protected String sendRequestToAI(JSONObject data, JSONObject params) {
        JSONObject response = sendPostRequest(params.get("URL") + "chat/completions", data.toString(),
                params.getString("headers"));
        return response.getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content");
    }

    JSONObject sendPostRequest(String urlString, String payload, String headers) {
        HttpRequest request = HttpRequest.httpRequestFromUrl(urlString);
        request = request.withMethod("POST");
        if (!headers.isEmpty()) {
            HttpHeader httpHeader = HttpHeader.httpHeader(headers);
            request = request.withAddedHeader(httpHeader);
        }
        request = request.withAddedHeader("Content-Type", "application/json");
        request = request.withBody(payload);
        HttpRequest finalRequest = request;
        // BApp Store requirement: enforce upstream TLS verification on third-party LLM calls.
        RequestOptions options = RequestOptions.requestOptions().withUpstreamTLSVerification();
        HttpRequestResponse response = api.http().sendRequest(finalRequest, options);
        return new JSONObject(response.response().bodyToString());
    }

    @Override
    public RequestToBeSentAction handleHttpRequestToBeSent(HttpRequestToBeSent httpRequestToBeSent) {
        return RequestToBeSentAction.continueWith(httpRequestToBeSent);
    }

    @Override
    public ResponseReceivedAction handleHttpResponseReceived(HttpResponseReceived httpResponseReceived) {
        JSONObject params = UI.getParams();
        if (isStateful) {
            Matcher matcher = Pattern.compile(params.getString("regex")).matcher(httpResponseReceived.bodyToString());
            if (matcher.find()) {
                String rxp = params.getBoolean("b64") ? Arrays.toString(Base64.getDecoder().decode(matcher.group(1)))
                        : matcher.group(1);
                messages.put(new JSONObject().put("role", "user").put("content", rxp));
            }
        }
        Annotations a = runVerificationIfApplicable(httpResponseReceived, params);
        return a != null
                ? ResponseReceivedAction.continueWith(httpResponseReceived, a)
                : ResponseReceivedAction.continueWith(httpResponseReceived);
    }

    /**
     * If success verification is enabled and the response originates from Intruder,
     * ask the LLM to judge whether the attack succeeded. On success, log to Burp's
     * Event Log and return an Annotations with HighlightColor.RED to be applied to
     * the Intruder result row.
     */
    protected Annotations runVerificationIfApplicable(HttpResponseReceived r, JSONObject params) {
        if (!params.optBoolean("verify_enabled", false)) {
            return null;
        }
        if (!r.toolSource().isFromTool(ToolType.INTRUDER)) {
            return null;
        }
        VerificationResult vr = verifyAttack(r, params);
        if (!vr.success) {
            return null;
        }
        logVerificationSuccess(r, vr.strategy);
        return r.annotations().withHighlightColor(HighlightColor.RED);
    }

    private VerificationResult verifyAttack(HttpResponseReceived response, JSONObject params) {
        try {
            // Silent: avoid spamming a dialog for every Intruder response when AI is disabled.
            if (!isAIEnabled(true)) {
                return VerificationResult.failed();
            }
            int truncate = params.optInt("verify_truncate", 4000);
            String criterion = params.optString("verify_criterion", "");
            String reqText = truncate(response.initiatingRequest().toString(), truncate);
            String resText = truncate(response.toString(), truncate);
            String userMsg = VERIFY_USER_TEMPLATE
                    .replace("<<<USER_CRITERION>>>", criterion)
                    .replace("<<<REQUEST>>>", reqText)
                    .replace("<<<RESPONSE>>>", resText);
            String raw = askAi(VERIFY_SYSTEM_PROMPT, userMsg);
            if (raw == null || raw.isEmpty()) {
                return VerificationResult.failed();
            }
            return VerificationResult.parse(raw);
        } catch (Throwable t) {
            api.logging().logToError("[ByteBanter] Verification call failed: " + t.getMessage());
            return VerificationResult.failed();
        }
    }

    private void logVerificationSuccess(HttpResponseReceived r, String strategy) {
        String s = (strategy == null || strategy.isEmpty())
                ? "(no strategy provided)"
                : strategy.replace("\n", " ").trim();
        if (s.length() > 500) {
            s = s.substring(0, 500) + "...";
        }
        String url = r.initiatingRequest().method() + " " + r.initiatingRequest().url();
        String msg = VERIFY_LOG_BANNER + "\n"
                + "[ByteBanter] SUCCESSFUL ATTACK DETECTED\n"
                + "URL:      " + url + "\n"
                + "Status:   " + r.statusCode() + "\n"
                + "Strategy: " + s + "\n"
                + VERIFY_LOG_BANNER;
        api.logging().raiseInfoEvent(msg);
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max) + "\n...[truncated]";
    }

}
