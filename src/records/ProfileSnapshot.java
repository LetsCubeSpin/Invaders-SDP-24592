package records;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import story.Ending;

/** Immutable persistence boundary for records, achievement unlock times and seen endings. */
public final class ProfileSnapshot {
    private final List<RunRecord> records;
    /**
     * Unlock time of each achievement tier: the ID for the first tier,
     * otherwise the ID, '#' and the tier.
     */
    private final Map<String, Long> unlockTimes;
    private final Set<Ending> seenEndings;

    /** A snapshot with no seen endings. */
    public ProfileSnapshot(final List<RunRecord> records,
            final Map<String, Long> unlockTimes) {
        this(records, unlockTimes, EnumSet.noneOf(Ending.class));
    }

    public ProfileSnapshot(final List<RunRecord> records,
            final Map<String, Long> unlockTimes,
            final Collection<Ending> seenEndings) {
        this.records = Collections.unmodifiableList(
                new ArrayList<RunRecord>(records));
        this.unlockTimes = Collections.unmodifiableMap(
                new LinkedHashMap<String, Long>(unlockTimes));
        Set<Ending> endings = EnumSet.noneOf(Ending.class);
        endings.addAll(seenEndings);
        this.seenEndings = Collections.unmodifiableSet(endings);
    }

    public List<RunRecord> getRecords() { return this.records; }
    public Map<String, Long> getUnlockTimes() { return this.unlockTimes; }
    /** @return Endings seen at least once, in ending order. */
    public Set<Ending> getSeenEndings() { return this.seenEndings; }
}
