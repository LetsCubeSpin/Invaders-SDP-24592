package records;

import event.AllEndingsSeenEvent;
import event.GameplayEventPublisher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import story.Ending;
import story.EndingCollection;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class AllEndingsAchievementTest {

    @TempDir Path temp;

    @Test
    void seeingEveryEndingUnlocksTheAchievementThroughTheEvent() {
        RecordsService records = new RecordsService(temp.resolve("profile.properties"));
        try {
            GameplayEventPublisher events = new GameplayEventPublisher();
            events.subscribe(AllEndingsSeenEvent.class, e -> records.allEndingsSeen());
            EndingCollection endings = new EndingCollection(events);

            Ending[] all = Ending.values();
            for (int i = 0; i < all.length - 1; i++)
                endings.see(all[i]);
            assertNull(records.getUnlockTime(Achievement.ALL_ENDINGS),
                    "locked until the last ending");

            endings.see(all[all.length - 1]);
            assertNotNull(records.getUnlockTime(Achievement.ALL_ENDINGS));
            assertEquals(new AchievementUnlock(Achievement.ALL_ENDINGS, 1), records.pollNotification());
        } finally {
            records.close();
        }
    }

    @Test
    void unlockingTwiceKeepsTheFirstTimeAndNotifiesOnce() {
        RecordsService records = new RecordsService(temp.resolve("profile.properties"));
        try {
            records.allEndingsSeen();
            Long first = records.getUnlockTime(Achievement.ALL_ENDINGS);
            records.allEndingsSeen();

            assertEquals(first, records.getUnlockTime(Achievement.ALL_ENDINGS));
            assertEquals(new AchievementUnlock(Achievement.ALL_ENDINGS, 1), records.pollNotification());
            assertNull(records.pollNotification());
        } finally {
            records.close();
        }
    }

    @Test
    void theUnlockIsSavedAndLoadedAgain() {
        Path profile = temp.resolve("profile.properties");
        RecordsService records = new RecordsService(profile);
        records.allEndingsSeen();
        Long unlocked = records.getUnlockTime(Achievement.ALL_ENDINGS);
        records.close();

        RecordsService reloaded = new RecordsService(profile);
        try {
            assertEquals(unlocked, reloaded.getUnlockTime(Achievement.ALL_ENDINGS));
        } finally {
            reloaded.close();
        }
    }

    @Test
    void aClosedServiceIgnoresTheCall() {
        RecordsService records = new RecordsService(temp.resolve("profile.properties"));
        records.close();

        records.allEndingsSeen();

        assertNull(records.getUnlockTime(Achievement.ALL_ENDINGS));
    }
}
