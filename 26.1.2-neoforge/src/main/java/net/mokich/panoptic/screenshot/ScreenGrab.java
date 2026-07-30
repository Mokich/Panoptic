package net.mokich.panoptic.screenshot;

import java.util.ArrayList;
import java.util.List;

public final class ScreenGrab {
    public long id;
    public String source;
    public String screenTitle;
    public String customName;
    public boolean favorite;
    public String bg;
    public int bgW;
    public int bgH;
    public int regionW;
    public int regionH;
    public List<GrabOp> ops = new ArrayList<>();

    public String displayTitle() {
        if (customName != null && !customName.isEmpty()) {
            return customName;
        }
        return screenTitle;
    }

    public int itemCount() {
        int n = 0;
        for (GrabOp o : ops) {
            if ("i".equals(o.t)) {
                n++;
            }
        }
        return n;
    }
}