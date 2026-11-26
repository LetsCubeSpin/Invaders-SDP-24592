package engine;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Font;
import java.lang.reflect.Method;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import screen.Screen;

class DrawManagerFontTest {

    private LanguageManager.Language previousLanguage;
    private DrawManager drawManager;
    private Screen screen;
    private boolean drawingOpen;

    @BeforeEach
    void prepare() throws Exception {
        previousLanguage = LanguageManager.getCurrentLanguage();
        drawManager = DrawManager.getInstance();
        screen = new Screen(448, 520, 60);
        setCachedFont("fontKeyHelp", null);
        setCachedFont("fontSmall", null);
    }

    @AfterEach
    void restoreLanguage() throws Exception {
        if (drawingOpen)
            finishDrawing();
        setCachedFont("fontKeyHelp", null);
        setCachedFont("fontSmall", null);
        LanguageManager.setCurrentLanguage(previousLanguage);
        startDrawing();
        finishDrawing();
    }

    @Test
    void refreshesRecordsFontsAfterLanguageChanges() throws Exception {
        LanguageManager.setCurrentLanguage(LanguageManager.Language.ENGLISH);
        startDrawing();
        Font englishKeyHelp = cachedFont("keyHelpFont");
        Font englishAchievementCondition = cachedFont("smallFont");
        assertFalse(englishKeyHelp.canDisplay('한'));
        assertFalse(englishAchievementCondition.canDisplay('한'));
        finishDrawing();

        LanguageManager.setCurrentLanguage(LanguageManager.Language.KOREAN);
        startDrawing();
        Font koreanKeyHelp = cachedFont("keyHelpFont");
        Font koreanAchievementCondition = cachedFont("smallFont");

        assertTrue(koreanKeyHelp.canDisplay('한'));
        assertTrue(koreanAchievementCondition.canDisplay('한'));
        finishDrawing();
    }

    private Font cachedFont(final String methodName) throws Exception {
        Method method = DrawManager.class.getDeclaredMethod(methodName);
        method.setAccessible(true);
        return (Font) method.invoke(drawManager);
    }

    private void setCachedFont(final String fieldName, final Font font)
            throws Exception {
        java.lang.reflect.Field field = DrawManager.class
                .getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(null, font);
    }

    private void startDrawing() {
        drawManager.initDrawing(screen);
        drawingOpen = true;
    }

    private void finishDrawing() {
        drawManager.completeDrawing(screen);
        drawingOpen = false;
    }
}