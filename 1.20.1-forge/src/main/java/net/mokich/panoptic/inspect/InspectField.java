package net.mokich.panoptic.inspect;

public final class InspectField {
    public String label;
    public String value;
    public boolean copyable = true;

    public InspectField() {}

    public InspectField(String label, String value, boolean copyable) {
        this.label = label;
        this.value = value;
        this.copyable = copyable;
    }

    public static InspectField of(String label, String value) {
        return new InspectField(label, value, true);
    }
}