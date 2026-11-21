// 타자기 효과가 시간에 맞춰 글자를 드러내고 건너뛰기를 지원하는지 확인하는 테스트
package story;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TypewriterTest {

    @Test
    void showsNothingBeforeItStarts() {
        Typewriter typewriter = new Typewriter("HELLO", 10);
        assertEquals(0, typewriter.visibleLength(5000));
        assertEquals("", typewriter.visibleText(5000));
        assertFalse(typewriter.isDone(5000));
    }

    @Test
    void revealsCharactersAtTheGivenSpeed() {
        Typewriter typewriter = new Typewriter("HELLO", 10);
        typewriter.start(1000);
        assertEquals(0, typewriter.visibleLength(1000));
        assertEquals(1, typewriter.visibleLength(1100));
        assertEquals("HEL", typewriter.visibleText(1300));
        assertFalse(typewriter.isDone(1400));
        assertTrue(typewriter.isDone(1500));
    }

    @Test
    void neverShowsMoreThanTheText() {
        Typewriter typewriter = new Typewriter("HI", 10);
        typewriter.start(0);
        assertEquals("HI", typewriter.visibleText(60000));
    }

    @Test
    void startingAgainKeepsTheFirstStartTime() {
        Typewriter typewriter = new Typewriter("HELLO", 10);
        typewriter.start(1000);
        typewriter.start(1400);
        assertEquals(3, typewriter.visibleLength(1300));
    }

    @Test
    void finishShowsEverythingAtOnce() {
        Typewriter typewriter = new Typewriter("HELLO", 10);
        typewriter.start(1000);
        typewriter.finish();
        assertEquals("HELLO", typewriter.visibleText(1000));
        assertTrue(typewriter.isDone(1000));
    }

    @Test
    void rejectsBadArguments() {
        assertThrows(IllegalArgumentException.class,
                () -> new Typewriter(null, 10));
        assertThrows(IllegalArgumentException.class,
                () -> new Typewriter("HI", 0));
    }
}
