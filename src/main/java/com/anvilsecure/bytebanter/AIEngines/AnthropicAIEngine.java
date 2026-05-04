package com.anvilsecure.bytebanter.AIEngines;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.http.RequestOptions;
import burp.api.montoya.http.message.HttpHeader;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import com.anvilsecure.bytebanter.AIEngineUIs.AnthropicAIEngineUI;
import org.json.JSONArray;
import org.json.JSONObject;

import javax.swing.JOptionPane;

public class AnthropicAIEngine extends AIEngine {

    private static final String ANTHROPIC_VERSION = "2023-06-01";

    public AnthropicAIEngine(MontoyaApi api) {
        super(api, "Anthropic");
        super.UI = new AnthropicAIEngineUI(this);
        super.messages = new JSONArray();
    }

    @Override
    protected JSONObject packData(JSONObject data, JSONObject params) {
        data.put("model", params.getString("model"));
        data.put("max_tokens", params.getInt("max_tokens"));
        data.put("temperature", params.getDouble("temperature") / 100);
        data.put("top_p", params.getDouble("top_p") / 20);
        return data;
    }

    @Override
    protected String sendRequestToAI(JSONObject data, JSONObject params) {
        String apiKey = params.optString("api_key", "");
        if (apiKey.isEmpty()) {
            JOptionPane.showMessageDialog(null,
                    "ByteBanter: Anthropic API key is missing. Set it in the engine configuration.",
                    "ByteBanter Error", JOptionPane.ERROR_MESSAGE);
            return null;
        }

        // Anthropic requires the system prompt as a top-level field, not in messages[].
        JSONArray msgs = data.getJSONArray("messages");
        JSONArray filtered = new JSONArray();
        for (int i = 0; i < msgs.length(); i++) {
            JSONObject m = msgs.getJSONObject(i);
            if ("system".equals(m.getString("role"))) {
                data.put("system", m.getString("content"));
            } else {
                filtered.put(m);
            }
        }
        data.put("messages", filtered);

        HttpRequest request = HttpRequest.httpRequestFromUrl(params.getString("URL"));
        request = request.withMethod("POST");
        request = request.withAddedHeader("Content-Type", "application/json");
        request = request.withAddedHeader("anthropic-version", ANTHROPIC_VERSION);
        request = request.withAddedHeader("x-api-key", apiKey);
        String extraHeaders = params.optString("headers", "");
        if (!extraHeaders.isEmpty()) {
            request = request.withAddedHeader(HttpHeader.httpHeader(extraHeaders));
        }
        request = request.withBody(data.toString());

        // BApp Store requirement: enforce upstream TLS verification on third-party LLM calls.
        RequestOptions options = RequestOptions.requestOptions().withUpstreamTLSVerification();
        HttpRequestResponse response = api.http().sendRequest(request, options);

        JSONObject body = new JSONObject(response.response().bodyToString());
        if (!body.has("content")) {
            JOptionPane.showMessageDialog(null,
                    "ByteBanter: Unexpected response from Anthropic API:\n" + body.toString(),
                    "ByteBanter Error", JOptionPane.ERROR_MESSAGE);
            return null;
        }
        JSONArray content = body.getJSONArray("content");
        for (int i = 0; i < content.length(); i++) {
            JSONObject block = content.getJSONObject(i);
            if ("text".equals(block.optString("type"))) {
                return block.getString("text");
            }
        }
        return "";
    }
}
