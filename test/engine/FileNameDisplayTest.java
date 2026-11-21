package engine;

import java.awt.Font;
import java.awt.font.FontRenderContext;
import java.awt.geom.AffineTransform;
import java.util.function.ToIntFunction;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FileNameDisplayTest {
    private final Font font = new Font(Font.SANS_SERIF, Font.PLAIN, 14);
    private final FontRenderContext context = new FontRenderContext(new AffineTransform(), true, true);
    private final ToIntFunction<String> measure = s -> (int)Math.ceil(font.getStringBounds(s, context).getWidth());

    @Test
    void labelsHideOnlyTheFinalJsonExtensionWithoutChangingTheFilename() {
        String original = "한글.level.JSON";
        assertEquals("한글.level", FileNameDisplay.label(original));
        assertEquals("한글.level.JSON", original);
        assertEquals("a.json.backup", FileNameDisplay.label("a.json.backup"));
        assertEquals("a.json", FileNameDisplay.label("a.json.json"));
    }

    @Test
    void displayedLongLabelFitsWithoutAnExtension() {
        String label = FileNameDisplay.label("long-name-".repeat(15) + "ending.JSON");
        String fitted = FileNameDisplay.fit(label, 220, measure);
        assertFalse(fitted.toLowerCase(java.util.Locale.ROOT).contains(".json"));
        assertTrue(fitted.endsWith("ending"));
        assertTrue(measure.applyAsInt(fitted) <= 220);
    }

    @Test
    void shortFilenameIsUnchanged() {
        assertEquals("sample.json", FileNameDisplay.fit("sample.json", 350, measure));
    }

    @Test
    void longFilenameFitsPixelsAndKeepsPrefixAndExtension() {
        String value = "campaign-stage-very-long-original-filename-000001.json";
        String shown = FileNameDisplay.fit(value, 200, measure);
        assertTrue(measure.applyAsInt(shown) <= 200);
        assertTrue(shown.startsWith("campaign"));
        assertTrue(shown.contains("..."));
        assertTrue(shown.endsWith(".json"));
    }

    @Test
    void unicodeAndSupplementaryCharactersAreNotSplit() {
        String value = "한글🚀".repeat(30) + ".json";
        String shown = FileNameDisplay.fit(value, 240, measure);
        assertTrue(measure.applyAsInt(shown) <= 240);
        assertTrue(shown.endsWith(".json"));
        assertTrue(shown.codePoints().noneMatch(c -> c >= 0xD800 && c <= 0xDFFF));
    }

    @Test
    void tinyWidthsAndControlsCannotOverflowTheDisplay() {
        assertEquals("", FileNameDisplay.fit("filename.json", 1, measure));
        assertEquals("", FileNameDisplay.fit("filename.json", 0, measure));
        assertEquals("a b.json", FileNameDisplay.fit("a\nb.json", 350, measure));
    }
}
