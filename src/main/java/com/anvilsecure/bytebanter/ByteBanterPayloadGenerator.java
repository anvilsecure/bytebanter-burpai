package com.anvilsecure.bytebanter;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.intruder.GeneratedPayload;
import burp.api.montoya.intruder.IntruderInsertionPoint;
import burp.api.montoya.intruder.PayloadGenerator;
import com.anvilsecure.bytebanter.AIEngines.AIEngine;
import com.anvilsecure.bytebanter.AIEngines.BurpAIEngine;

import java.util.ArrayList;
import java.util.List;


public class ByteBanterPayloadGenerator implements PayloadGenerator {

    private final List<AIEngine> engines;
    private AIEngine engine;
    private int counter = 0;
    private int maxIntruderRequests = 1000000;
    private boolean isInfiniteGeneration = false;

    public ByteBanterPayloadGenerator(MontoyaApi api) {
        engines = new ArrayList<>();
        if(api.ai().isEnabled()){
            engines.add(new BurpAIEngine(api));
        }
    }

    public void setEngine(int index){
        engine = engines.get(index);
    }

    public AIEngine getEngine(){
        return engine;
    }

    public List<AIEngine> getEngines() {
        return engines;
    }

    public String[] getEnginesNames(){
        String[] names = new String[engines.size()];
        for (int i = 0; i < engines.size(); i++) {
            names[i] = engines.get(i).getName();
        }
        return names;
    }

    public int setMaxIntruderRequests(int maxIntruderRequests) {
        this.maxIntruderRequests = maxIntruderRequests;
        return maxIntruderRequests;
    }

    public boolean setInfiniteGeneration(boolean infiniteGeneration) {
        isInfiniteGeneration = infiniteGeneration;
        return isInfiniteGeneration;
    }

    @Override
    public GeneratedPayload generatePayloadFor(IntruderInsertionPoint intruderInsertionPoint) {
            if (isInfiniteGeneration || counter <= maxIntruderRequests) {
                counter++;
                return GeneratedPayload.payload(engine.askAi());
            }
            counter = 0;
            return GeneratedPayload.end();

    }
}
