package com.anvilsecure.bytebanter.AIEngineUIs;

import com.anvilsecure.bytebanter.AIEngines.AIEngine;
import org.json.JSONObject;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;

public class AnthropicAIEngineUI extends AIEngineUI {

    private static final String DEFAULT_URL = "https://api.anthropic.com/v1/messages";
    private static final String[] DEFAULT_MODELS = {
            "claude-sonnet-4-6",
            "claude-opus-4-7",
            "claude-haiku-4-5-20251001",
            "claude-3-5-sonnet-latest",
            "claude-3-5-haiku-latest"
    };

    private JComboBox<String> modelCombo;
    private JPasswordField apiKeyField;

    public AnthropicAIEngineUI(AIEngine engine) {
        super(engine);
    }

    @Override
    public JPanel getAIConfPanel() {
        JPanel configPanel = super.getAIConfPanel();
        urlField.setText(DEFAULT_URL);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(2, 2, 2, 2);
        gbc.gridx = 0;
        gbc.gridwidth = 2;
        gbc.weightx = 1.0;

        apiKeyField = new JPasswordField(40);
        JPanel apiKeyPanel = new JPanel(new BorderLayout());
        apiKeyPanel.setBorder(new TitledBorder("API Key (x-api-key):"));
        apiKeyPanel.add(apiKeyField, BorderLayout.CENTER);
        gbc.gridy = 3;
        configPanel.add(apiKeyPanel, gbc);

        modelCombo = new JComboBox<>(DEFAULT_MODELS);
        modelCombo.setEditable(true);
        JPanel modelPanel = new JPanel(new BorderLayout());
        modelPanel.setBorder(new TitledBorder("Model:"));
        modelPanel.add(modelCombo, BorderLayout.CENTER);
        gbc.gridy = 4;
        configPanel.add(modelPanel, gbc);

        return configPanel;
    }

    @Override
    public JPanel getParamPanel() {
        JPanel paramPanel = new JPanel(new GridLayout(0, 1));

        // Max Tokens (required by Anthropic)
        maxTokensSpinner = new JSpinner(new SpinnerNumberModel(1024, 1, 64000, 1));
        JPanel maxTokensPanel = new JPanel(new BorderLayout());
        maxTokensPanel.add(new JLabel("Max Tokens:"), BorderLayout.WEST);
        maxTokensPanel.add(maxTokensSpinner, BorderLayout.CENTER);
        paramPanel.add(maxTokensPanel);

        // Temperature
        temperatureSlider = new JSlider(0, 100, 70);
        JLabel tempLabel = new JLabel("Temperature: " + String.format("%.2f", temperatureSlider.getValue() / 100.0));
        temperatureSlider.addChangeListener(
                e -> tempLabel.setText("Temperature: " + String.format("%.2f", temperatureSlider.getValue() / 100.0)));
        JPanel tempPanel = new JPanel(new BorderLayout());
        tempPanel.add(tempLabel, BorderLayout.NORTH);
        tempPanel.add(temperatureSlider, BorderLayout.CENTER);
        paramPanel.add(tempPanel);

        // Top P
        topPSlider = new JSlider(0, 20, 20);
        JLabel topPLabel = new JLabel("Top P: " + String.format("%.2f", topPSlider.getValue() / 20.0));
        topPSlider.addChangeListener(
                e -> topPLabel.setText("Top P: " + String.format("%.2f", topPSlider.getValue() / 20.0)));
        JPanel topPPanel = new JPanel(new BorderLayout());
        topPPanel.add(topPLabel, BorderLayout.NORTH);
        topPPanel.add(topPSlider, BorderLayout.CENTER);
        paramPanel.add(topPPanel);

        return paramPanel;
    }

    @Override
    public JSONObject getParams() {
        JSONObject params = super.getParams();
        params.put("model", String.valueOf(modelCombo.getSelectedItem()));
        params.put("api_key", new String(apiKeyField.getPassword()));
        return params;
    }

    @Override
    public void loadParams(JSONObject params) {
        super.loadParams(params);
        modelCombo.setSelectedItem(params.optString("model", DEFAULT_MODELS[0]));
        apiKeyField.setText(params.optString("api_key", ""));
    }

    @Override
    public JSONObject getPersistableParams() {
        JSONObject params = super.getPersistableParams();
        if (persistSensitiveCheck != null && !persistSensitiveCheck.isSelected()) {
            params.remove("api_key");
        }
        return params;
    }
}
