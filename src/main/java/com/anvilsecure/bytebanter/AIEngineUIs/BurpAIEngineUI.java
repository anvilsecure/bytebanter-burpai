package com.anvilsecure.bytebanter.AIEngineUIs;

import com.anvilsecure.bytebanter.AIEngines.AIEngine;
import org.json.JSONObject;

import javax.swing.*;
import javax.swing.text.NumberFormatter;
import java.awt.*;
import java.text.NumberFormat;

public class BurpAIEngineUI extends AIEngineUI {

    private JCheckBox infiniteRequestCheck;
    private JFormattedTextField requestLimitField;

    public BurpAIEngineUI(AIEngine engine) {
        super(engine);
    }

    @Override
    public JPanel getAIConfPanel() {
        JPanel confPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(5, 5, 5, 5);

        // pannel title
        gbc.gridx = 0; gbc.gridy = 0; gbc.gridwidth = 2;
        confPanel.add(new JLabel("Requests Limit Configuration:"), gbc);

        // Checkbox for infinite requests
        infiniteRequestCheck = new JCheckBox("Infinite Requests");
        gbc.gridy = 1; gbc.gridwidth = 1;
        confPanel.add(infiniteRequestCheck, gbc);

        // numeric text field (1 - 1.000.000)
        NumberFormat format = NumberFormat.getIntegerInstance();
        format.setGroupingUsed(false); // remove separator to avoid parsing errors
        NumberFormatter formatter = new NumberFormatter(format);
        formatter.setValueClass(Integer.class);
        formatter.setMinimum(1);
        formatter.setMaximum(1000000);
        formatter.setAllowsInvalid(false); // avoid invalid charactes
        formatter.setCommitsOnValidEdit(true); // update vlues

        requestLimitField = new JFormattedTextField(formatter);
        requestLimitField.setValue(1000000); // defailt value
        requestLimitField.setColumns(10);

        gbc.gridx = 1;
        confPanel.add(requestLimitField, gbc);

        // --- Logic ---

        infiniteRequestCheck.addActionListener(e -> {
            boolean isInfinite = infiniteRequestCheck.isSelected();
            // Disable input if infinite is checked
            requestLimitField.setEnabled(!isInfinite);
        });

        return confPanel;
    }

    // Placeholder per le tue funzioni di logica
    private void handleInfiniteCheckChanged(boolean isInfinite) {
        System.out.println("Infinite requests: " + isInfinite);
        // engine.setInfinite(isInfinite);
    }

    private void handleLimitValueChanged(int newValue) {
        System.out.println("Nuovo limite richieste: " + newValue);
        // engine.setRequestLimit(newValue);
    }

    @Override
    public JPanel getParamPanel() {
        JPanel paramPanel = new JPanel(new GridLayout(6, 1));
        temperatureSlider = new JSlider(1, 200, 50);
        paramPanel.add(new JLabel("Temperature:"));
        paramPanel.add(temperatureSlider);
        return paramPanel;
    }

    @Override
    public JSONObject getParams(){
        JSONObject params = super.getParams();
        params.put("isInfiniteRequests", infiniteRequestCheck.isSelected());
        params.put("requestsLimit", requestLimitField.getValue());
        return params;
    }
}