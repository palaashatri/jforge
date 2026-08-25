package atri.palaash.jforge.control;

import atri.palaash.jforge.api.ControlInput;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ControlSpec {
    private final List<ControlInput> controls = new ArrayList<>();

    public void add(ControlInput c) {
        validate(c);
        controls.add(c);
    }

    public void remove(String type) { controls.removeIf(c -> c.type().equals(type)); }
    public void setEnabled(String type, boolean enabled) {
        for (int i = 0; i < controls.size(); i++) {
            ControlInput c = controls.get(i);
            if (c.type().equals(type)) controls.set(i, new ControlInput(c.type(), c.source(), c.preprocessor(), c.strength(), c.startPercent(), c.endPercent(), enabled));
        }
    }
    public List<ControlInput> all() { return Collections.unmodifiableList(controls); }
    public List<ControlInput> enabled() { return controls.stream().filter(ControlInput::enabled).toList(); }
    public int size() { return controls.size(); }
    public void clear() { controls.clear(); }

    public static void validate(ControlInput c) {
        if (c.type().isBlank()) throw new IllegalArgumentException("control type must not be blank");
        if (c.strength() < 0 || c.strength() > 1) throw new IllegalArgumentException("strength must be in [0,1]");
        if (c.startPercent() < 0 || c.startPercent() > 1 || c.endPercent() < 0 || c.endPercent() > 1)
            throw new IllegalArgumentException("start/end percent must be in [0,1]");
        if (c.startPercent() >= c.endPercent()) throw new IllegalArgumentException("startPercent must be < endPercent");
        Preprocessor p = Preprocessor.fromId(c.preprocessor());
        if (p == Preprocessor.NONE && !c.preprocessor().isBlank()) {
            // unknown preprocessor is allowed but logged as NONE
        }
    }
}
