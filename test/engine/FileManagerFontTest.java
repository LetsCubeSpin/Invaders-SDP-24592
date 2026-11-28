package engine;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Font;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FileManagerFontTest {

    private LanguageManager.Language previousLanguage;

    @BeforeEach
    void rememberLanguage() {
        previousLanguage = LanguageManager.getCurrentLanguage();
    }

    @AfterEach
    void restoreLanguage() {
        LanguageManager.setCurrentLanguage(previousLanguage);
    }

    @Test
    void loadsKoreanFontWhenKoreanIsSelected() throws Exception {
        LanguageManager.setCurrentLanguage(LanguageManager.Language.KOREAN);

        Font font = FileManager.getInstance().loadFont(20f);

        assertTrue(font.canDisplay('한'));
        assertTrue(font.canDisplay('글'));
    }
}
