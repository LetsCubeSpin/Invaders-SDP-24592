package engine;

import engine.LanguageManager.Language;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.prefs.Preferences;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LanguageManagerTest {

    private Language previousLanguage;

    @BeforeEach
    void rememberLanguage() {
        previousLanguage = LanguageManager.getCurrentLanguage();
    }

    @AfterEach
    void restoreLanguage() {
        LanguageManager.setCurrentLanguage(previousLanguage);
    }

    @Test
    void returnsTranslationsForTheSelectedLanguage() {
        LanguageManager.setCurrentLanguage(Language.ENGLISH);
        assertEquals("Settings", LanguageManager.get("settings.title"));

        LanguageManager.setCurrentLanguage(Language.KOREAN);
        assertEquals("설정", LanguageManager.get("settings.title"));
        assertEquals("게임 시작", LanguageManager.get("menu.play"));
        assertEquals("사용자 지정", LanguageManager.get("color.custom"));
        assertEquals("완벽한 클리어",
                LanguageManager.get("achievement.clean_sweep.name"));
        assertEquals("목숨을 잃지 않고 스테이지 {target}개를 클리어하세요.",
                LanguageManager.get("achievement.clean_sweep.condition"));
    }

    @Test
    void fallsBackToTheKeyWhenNoTranslationExists() {
        LanguageManager.setCurrentLanguage(Language.KOREAN);
        assertEquals("missing.translation", LanguageManager.get("missing.translation"));
    }

    @Test
    void persistsTheSelectedLanguage() {
        LanguageManager.setCurrentLanguage(Language.KOREAN);
        assertEquals(Language.KOREAN.name(), Preferences.userNodeForPackage(
                LanguageManager.class).get("language", ""));
    }

    @Test
    void resetToEnglishPersistsTheDefaultLanguage() {
        LanguageManager.setCurrentLanguage(Language.KOREAN);
        LanguageManager.resetToEnglish();

        assertEquals(Language.ENGLISH, LanguageManager.getCurrentLanguage());
        assertEquals(Language.ENGLISH.name(), Preferences.userNodeForPackage(
                LanguageManager.class).get("language", ""));
    }

    @Test
    void reportsTheCurrentAndAvailableLanguageNames() {
        LanguageManager.setCurrentLanguage(Language.ENGLISH);
        assertEquals("English", LanguageManager.getCurrentLanguageName());
        assertEquals("한국어", LanguageManager.getOtherLanguageName());

        LanguageManager.setCurrentLanguage(Language.KOREAN);
        assertEquals("한국어", LanguageManager.getCurrentLanguageName());
        assertEquals("English", LanguageManager.getOtherLanguageName());
    }
}