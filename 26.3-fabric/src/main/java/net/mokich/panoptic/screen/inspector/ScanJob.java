package net.mokich.panoptic.screen.inspector;

import net.mokich.panoptic.inspect.InspectEntry;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public final class ScanJob {
    private final List<Supplier<InspectEntry>> builders;
    private final List<InspectEntry> results = new ArrayList<>();
    private int index;

    public ScanJob(List<Supplier<InspectEntry>> builders) {
        this.builders = builders;
    }

    public void step(long budgetNanos) {
        long start = System.nanoTime();
        while (index < builders.size()) {
            try {
                InspectEntry entry = builders.get(index).get();
                if (entry != null) {
                    results.add(entry);
                }
            } catch (Throwable ignored) {
            }
            index++;
            if (System.nanoTime() - start > budgetNanos) {
                break;
            }
        }
    }
    public boolean done() {
        return index >= builders.size();
    }
    public int processed() {
        return index;
    }
    public int total() {
        return builders.size();
    }
    public float progress() {
        return builders.isEmpty() ? 1.0F : (float) index / builders.size();
    }
    public List<InspectEntry> results() {
        return results;
    }
}