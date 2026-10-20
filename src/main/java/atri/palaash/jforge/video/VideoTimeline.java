package atri.palaash.jforge.video;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class VideoTimeline {
    public record Clip(String id, String sourcePath, double startSec, double durationSec, double fps) {}
    private final List<Clip> clips = new ArrayList<>();
    private double durationSec = 0;

    public void addClip(Clip clip) { clips.add(clip); durationSec += clip.durationSec(); }
    public void removeClip(String id) { clips.removeIf(c -> c.id().equals(id)); recalc(); }
    private void recalc() { durationSec = clips.stream().mapToDouble(Clip::durationSec).sum(); }
    public List<Clip> clips() { return Collections.unmodifiableList(clips); }
    public double durationSec() { return durationSec; }
    public int clipCount() { return clips.size(); }
    public void clear() { clips.clear(); durationSec = 0; }
}
