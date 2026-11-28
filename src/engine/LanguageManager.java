package engine;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.prefs.Preferences;

/**
 * Manages UI text localization for Korean and English.
 * Provides translated strings for all UI elements displayed by DrawManager.
 */
public class LanguageManager {

    /** Supported UI languages. */
    public enum Language {
        /** English (default). */
        ENGLISH,
        /** Korean. */
        KOREAN
    }

    /** Key for the "Settings" title. */
    //public static final String KEY_SETTINGS = "settings.title";
    /** Key for the "Audio" option. */
    //public static final String KEY_AUDIO = "settings.audio";
    /** Key for the "Controls" option. */
    //public static final String KEY_CONTROLS = "settings.controls";
    /** Key for the "Color" option. */
    //public static final String KEY_COLOR = "settings.color";
    /** Key for the "Language" option. */
    //public static final String KEY_LANGUAGE = "settings.language";
    /** Key for the "Back" option. */
    //public static final String KEY_BACK = "settings.back";
    /** Key for the settings menu instruction text. */
    //public static final String KEY_SETTINGS_INSTRUCTION = "settings.instruction";

    /** Translations for English. */
    private static final Map<String, String> ENGLISH_TEXTS = new HashMap<>();
    /** Translations for Korean. */
    private static final Map<String, String> KOREAN_TEXTS = new HashMap<>();

    private static final Preferences PREFERENCES = getPreferences();
    private static final String LANGUAGE_PREFERENCE = "language";

    /** Current UI language. */
    private static Language currentLanguage = loadLanguage();

    static {


        addText("settings.title","Settings","설정");
        addText("settings.audio","Audio","오디오");
        addText("settings.controls", "Controls","조작");
        addText("settings.color", "Color","색상");
        addText("settings.changelanguage", "Change Language","언어 변경");
        addText("settings.back", "Back","뒤로");
        addText("settings.instruction", "Up/Down: select  Space: confirm / Click", "위/아래: 선택  스페이스: 확인 / 클릭");
        addText("settings.color.title","Ship Color","기체 색상");
        addText("settings.color.instruction.1stline","Change the %s ship's color(Up/Down)","%s 번째 기체의 색상 변경(위/아래)");
        addText("settings.color.instruction.2ndline.true","Enter: done","Enter 키: 완료");
        addText("settings.color.instruction.2ndline.false","Left/Right: choose color","왼쪽/오른쪽: 색상 선택");
        addText("settings.color.instruction.3rdline","Esc or click: back to settings","Esc 또는 클릭: 설정으로 복귀");



        addText("title.instructions", "Select with W/S or arrows; confirm with Space",
                "W/S 또는 방향키로 선택, 스페이스로 확인");
        addText("menu.play", "Play", "게임 시작");
        addText("menu.retry", "Retry (Space)", "재시도 (스페이스)");
        addText("menu.backAction", "Back (Esc)", "뒤로 (Esc)");
        addText("menu.highScores", "High scores", "최고 점수");
        addText("menu.records", "Records & Achievements", "기록 및 도전 과제");
        addText("menu.settings", "Settings", "설정");
        addText("menu.exit", "Exit", "종료");
        addText("mode.title", "Select Mode", "게임 모드 선택");
        addText("mode.endless", "Endless Mode", "무한 모드");
        addText("mode.campaign", "Campaign Mode", "캠페인 모드");
        addText("mode.story", "Story Mode", "스토리 모드");
        addText("mode.custom", "Custom Level", "사용자 레벨");
        addText("mode.timeAttack", "Time Attack", "타임 어택");
        addText("players.title", "Select Players", "플레이어 선택");
        addText("players.one", "1 Player", "1인 플레이");
        addText("players.two", "2 Players (Co-op)", "2인 협동 플레이");
        addText("ui.back", "Back", "뒤로");
        addText("settings.controlsTitle", "Controls", "조작 설정");
        addText("settings.controlsInstruction", "Up/Down: select  Space: change / Click",
                "위/아래: 선택  스페이스: 변경 / 클릭");
        addText("settings.moveLeft", "Move Left", "왼쪽 이동");
        addText("settings.moveRight", "Move Right", "오른쪽 이동");
        addText("settings.fire", "Fire", "발사");
        addText("settings.pressKey", "Press a key (Esc to cancel)",
                "키를 누르세요 (Esc: 취소)");
        addText("key.leftArrow", "Left Arrow", "왼쪽 방향키");
        addText("key.rightArrow", "Right Arrow", "오른쪽 방향키");
        addText("key.upArrow", "Up Arrow", "위쪽 방향키");
        addText("key.downArrow", "Down Arrow", "아래쪽 방향키");
        addText("key.space", "Space", "스페이스");
        addText("settings.audioTitle", "Audio Settings", "오디오 설정");
        addText("settings.audioSelect", "Up/Down: select", "위/아래: 선택");
        addText("settings.audioAdjust", "Left/Right: adjust  M: mute / Click bar",
                "왼쪽/오른쪽: 조절  M: 음소거 / 막대 클릭");
        addText("settings.audioReturn", "Press Esc or click to return",
                "Esc 또는 클릭: 돌아가기");
        addText("settings.bgmVolume", "BGM Volume", "배경 음악 음량");
        addText("settings.sfxVolume", "SFX Volume", "효과음 음량");
        addText("settings.mute", "Mute", "음소거");
        addText("settings.on", "ON", "켜짐");
        addText("settings.off", "OFF", "꺼짐");
        addText("settings.colorLabel", "Color: ", "색상: ");
        addText("settings.changeLanguageLabel", "Change Language to Korean", "영어로 언어 변경");
        addText("settings.colorCustom", "Custom", "사용자 지정");
        addText("settings.colorEdit", "Left/Right or drag: adjust color",
                "왼쪽/오른쪽 또는 드래그: 색상 조절");
        addText("settings.colorEnter", "Enter or drag: edit custom color",
                "Enter 또는 드래그: 사용자 색상 편집");
        addText("settings.colorDone", "Enter: done", "Enter 키: 완료");
        addText("settings.colorChoose", "Left/Right: choose color",
                "왼쪽/오른쪽: 색상 선택");
        addText("settings.colorPlayer", "%s ship color (Up/Down or click)",
                "%s 기체 색상 (위/아래 또는 클릭)");
        addText("settings.colorPlayerOne", "Player 1", "1번 플레이어");
        addText("settings.colorPlayerTwo", "Player 2", "2번 플레이어");
        addText("color.green", "Green", "초록색");
        addText("color.cyan", "Cyan", "청록색");
        addText("color.magenta", "Magenta", "자홍색");
        addText("color.yellow", "Yellow", "노란색");
        addText("color.orange", "Orange", "주황색");
        addText("color.blue", "Blue", "파란색");
        addText("color.custom", "Custom", "사용자 지정");
        addText("screen.paused", "Paused", "일시 정지");
        addText("screen.go", "GO!", "시작!");
        addText("screen.bonusLife", "Bonus life!", "보너스 목숨!");
        addText("screen.highScores", "High Scores", "최고 점수");
        addText("screen.highScoresHelp", "Press Space, Esc or click to return",
                "스페이스, Esc 또는 클릭: 돌아가기");
        addText("screen.newRecord", "New Record!", "신기록!");
        addText("screen.enterName", "Introduce name:", "이름을 입력하세요:");
        addText("screen.gameClear", "Game Clear", "게임 클리어");
        addText("screen.gameCleared", "Game Cleared!", "게임 클리어!");
        addText("screen.gameOver", "Game Over", "게임 오버");
        addText("screen.continueExit", "Press Space to play again, Escape to exit",
                "스페이스: 다시 하기, Esc: 나가기");
        addText("screen.stageFailed", "Stage %d Failed", "%d 스테이지 실패");
        addText("result.score", "Score %d", "점수 %d");
        addText("result.lives", "Lives remaining %d", "남은 목숨 %d");
        addText("result.enemies", "Enemies destroyed %d", "처치한 적 %d");
        addText("result.accuracy", "Accuracy %.2f%%", "명중률 %.2f%%");
        addText("result.newAchievements", "New achievements:", "새 도전 과제:");
        addText("result.achievementPage", "New achievements: %d/%d",
                "새 도전 과제: %d/%d");
        addText("result.controls", "Space: play again   Esc: menu   Click: choose",
                "스페이스: 다시 하기   Esc: 메뉴   클릭: 선택");
        addText("hud.level", "LEVEL", "레벨");
        addText("custom.importedLevels", "Imported Levels: %d", "가져온 레벨: %d");
        addText("custom.import", "Import JSON", "JSON 가져오기");
        addText("custom.title", "Custom Level", "사용자 레벨");
        addText("custom.playLevel", "Play Level", "레벨 플레이");
        addText("custom.exportJson", "Export JSON", "JSON 내보내기");
        addText("custom.controls", "Arrows / Space / Esc / Click",
                "방향키 / 스페이스 / Esc / 클릭");
        addText("timeAttack.clear", "TIME ATTACK CLEAR", "타임 어택 클리어");
        addText("timeAttack.gameOver", "GAME OVER", "게임 오버");
        addText("timeAttack.timeUp", "TIME UP", "시간 종료");
        addText("timeAttack.stagesCleared", "Stages cleared: %d/%d", "클리어한 스테이지: %d/%d");
        addText("timeAttack.score", "Score: %d", "점수: %d");
        addText("timeAttack.controls", "SPACE: Retry   ESC: Back   Click: choose",
                "스페이스: 재시도   Esc: 뒤로   클릭: 선택");
        addText("timeAttack.seconds", "%dS", "%d초");
        addText("timeAttack.secondsLeft", "%dS LEFT!", "%d초 남음!");
        addText("timeAttack.hud", "TIME %ds", "시간 %d초");
        addText("mode.infinite", "Infinite Mode", "무한 모드");
        addText("mode.easy","mode: Easy","모드: 쉬움");
        addText("story.pressSpace", "Press SPACE", "스페이스 키를 누르세요");
        addText("story.chooseSide", "UP ARROW: FLY TO A SIDE",
                "위쪽 방향키: 방향 선택");
        addText("story.pilotName", "PILOT", "조종사");
        addText("story.openingNarration",
                "Year 2189.\n"
                + "The defense line on Mars has fallen.\n"
                + "\n"
                + "The alien fleet, the Swarm,\n"
                + "is heading for Earth.\n"
                + "\n"
                + "Only one interceptor remains:\n"
                + "OCTO-1.\n"
                + "\n"
                + "\"Pilot, you are the last line of defense.\n"
                + "Launch.\"",
                "2189년.\n"
                + "화성의 방어선이 무너졌다.\n"
                + "\n"
                + "외계 함대 스웜이\n"
                + "지구를 향해 오고 있다.\n"
                + "\n"
                + "남은 요격기는 단 한 대:\n"
                + "OCTO-1.\n"
                + "\n"
                + "\"조종사, 네가 마지막 방어선이다.\n"
                + "출격하라.\"");
        addText("story.stage1Line", "Let's go wipe them all out.",
                "놈들을 전부 쓸어버리자.");
        addText("story.question.first.text",
                "Two signals at once. The position of the Swarm vanguard fleet, "
                + "and a distress call from the Moon base.",
                "두 개의 신호가 동시에 들어왔다. 스웜 선봉 함대의 위치, "
                + "그리고 달 기지의 조난 신호.");
        addText("story.question.first.assault.title", "ASSAULT", "공격");
        addText("story.question.first.assault.detail",
                "Strike the enemy commander first", "적 지휘관을 먼저 공격하기");
        addText("story.question.first.rescue.title", "RESCUE", "구조");
        addText("story.question.first.rescue.detail",
                "Save the Moon base survivors", "달 기지 생존자들을 구하기");
        addText("story.question.assault.text",
                "In the wreckage of ARGUS, we found an alien core, still beating.",
                "아르고스의 잔해에서 아직도 고동치는 외계 코어를 발견했다.");
        addText("story.question.assault.absorb.title", "ABSORB", "흡수");
        addText("story.question.assault.absorb.detail", "Absorb the core",
                "코어를 흡수하기");
        addText("story.question.assault.destroy.title", "DESTROY", "파괴");
        addText("story.question.assault.destroy.detail", "Destroy the core",
                "코어를 파괴하기");
        addText("story.question.rescue.lead",
                "Among the people we rescued was a Swarm scientist, Sera.",
                "구조한 사람들 중에는 스웜 과학자 세라가 있었다.");
        addText("story.question.rescue.text",
                "Trust me. I will show you the way to the mothership.",
                "날 믿어. 모선으로 가는 길을 알려줄게.");
        addText("story.controlName", "CONTROL", "관제탑");
        addText("story.seraName", "SERA", "세라");
        addText("story.boss.0.name", "ARGUS", "아르고스");
        addText("story.boss.0.line",
                "A worm of a puny planet...\nAnd you made it this far.",
                "하찮은 행성의 벌레가…\n여기까지 오다니.");
        addText("story.boss.1.name", "NOX", "녹스");
        addText("story.boss.1.line",
                "There are no survivors.\nI will leave none alive.",
                "생존자는 없다.\n남겨두지 않는다.");
        addText("story.question.rescue.escort.title", "ESCORT", "호위");
        addText("story.question.rescue.escort.detail", "Guard the flight home",
                "귀환 비행을 호위하기");
        addText("story.question.rescue.infiltrate.title", "INFILTRATE", "침투");
        addText("story.question.rescue.infiltrate.detail",
                "Infiltrate the mothership", "모선에 침투하기");
        addText("records.tabRuns", "Records", "기록");
        addText("records.tabAchievements", "Achievements", "도전 과제");
        addText("records.tabStats", "Stats", "통계");
        addText("records.profileReady", "Profile ready", "프로필 준비 완료");
        addText("records.profileRestored", "Profile restored from backup",
                "백업에서 프로필 복원 완료");
        addText("records.profileSaving", "Saving profile...", "프로필 저장 중...");
        addText("records.profileError", "Profile error - check game log",
                "프로필 오류 - 게임 로그를 확인하세요");
        addText("records.sort", "Sort", "정렬");
        addText("records.mode", "Mode", "모드");
        addText("records.select", "Select", "선택");
        addText("records.unlocked", "unlocked", "개 해제");
        addText("records.achievementSummary", "%d/%d unlocked", "%d/%d개 해제");
        addText("records.achievementScroll", "%d-%d   Up/Down: scroll",
                "%d-%d   위/아래: 스크롤");
        addText("records.changeTab", "Change tab", "탭 변경");
        addText("records.back", "Return", "돌아가기");
        addText("records.sortHelp", "Sort", "정렬");
        addText("records.modeHelp", "Mode", "모드");
        addText("records.order.score", "Score", "점수");
        addText("records.order.newest", "Newest", "최신순");
        addText("records.filter.all", "All", "전체");
        addText("records.filter.campaign", "Campaign", "캠페인");
        addText("records.filter.infinite", "Infinite", "무한 모드");
        addText("records.filter.story", "Story", "스토리");
        addText("records.column.score", "Score", "점수");
        addText("records.column.mode", "Mode", "모드");
        addText("records.column.date", "Date", "날짜");
        addText("records.noRuns", "No completed runs yet.", "완료한 게임 기록이 없습니다.");
        addText("records.noFilteredRuns", "No %s runs yet.", "%s 게임 기록이 없습니다.");
        addText("records.runSummary", "%d pts  %d enemies  %s",
                "%d점  적 %d기 격파  %s");
        addText("records.detail.mode", "Mode", "모드");
        addText("records.detail.stage", "Stage reached", "도달한 스테이지");
        addText("records.detail.waves", "Waves reached", "도달한 웨이브");
        addText("records.detail.shots", "Shots fired", "발사 횟수");
        addText("records.detail.hits", "Hits", "명중 횟수");
        addText("records.detail.accuracy", "Accuracy", "명중률");
        addText("records.detail.timesHit", "Times hit", "피격 횟수");
        addText("records.detail.bosses", "Bosses defeated", "처치한 보스");
        addText("records.detail.items", "Items used", "사용한 아이템");
        addText("records.personalBests", "Personal bests", "개인 최고 기록");
        addText("records.highestScore", "Highest score", "최고 점수");
        addText("records.mostEnemies", "Most enemies", "최다 처치");
        addText("records.longestSurvival", "Longest survival", "최장 생존 시간");
        addText("records.totals", "Totals", "누적 기록");
        addText("records.completedRuns", "Completed runs", "완료한 게임");
        addText("records.enemiesDefeated", "Enemies defeated", "처치한 적");
        addText("records.playTime", "Play time", "플레이 시간");
        addText("records.stagesCleared", "Stages cleared", "클리어한 스테이지");
        addText("records.endingsSeen", "Endings seen", "확인한 엔딩");
        addText("records.shotsFired", "Shots fired", "발사 횟수");
        addText("records.hits", "Hits", "명중 횟수");
        addText("records.timesHit", "Times hit", "피격 횟수");
        addText("records.bossesDefeated", "Bosses defeated", "처치한 보스");
        addText("records.itemsUsed", "Items used", "사용한 아이템");
        addText("time.seconds", "%.1fs", "%.1f초");
        addText("time.minutesSeconds", "%dm %02ds", "%d분 %02d초");
        addText("time.hoursMinutes", "%dh %02dm", "%d시간 %02d분");
        addText("achievement.tier.bronze", "Bronze", "동");
        addText("achievement.tier.silver", "Silver", "은");
        addText("achievement.tier.gold", "Gold", "금");



        addAchievement("first_run", "First Flight", "첫 비행",
                "Complete your first run.", "첫 게임을 완료하세요.");
        addAchievement("first_enemy", "First Contact", "첫 교전",
                "Defeat your first enemy.", "첫 적을 처치하세요.");
        addAchievement("twenty_enemies", "Ace Pilot", "에이스 파일럿",
                "Defeat {target} enemies in one run.", "한 게임에서 적 {target}기를 처치하세요.");
        addAchievement("sixty_seconds", "Still Flying", "계속 비행 중",
                "Survive {target} active seconds in one run.",
                "한 게임에서 {target}초 동안 생존하세요.");
        addAchievement("beat_high_score", "Top That", "기록을 넘어라",
                "Beat a previous run's high score.", "이전 게임의 최고 점수를 넘으세요.");
        addAchievement("all_endings", "Every Ending", "모든 엔딩",
                "See all four story endings.", "네 가지 스토리 엔딩을 모두 확인하세요.");
        addAchievement("exterminator", "Exterminator", "섬멸자",
                "Defeat {target} enemies in total.", "누적 적 {target}기를 처치하세요.");
        addAchievement("sharpshooter", "Sharpshooter", "명사수",
                "Finish a run with {target}% accuracy (50+ shots).",
                "한 게임에서 명중률 {target}% 이상 달성하세요 (50발 이상 발사).");
        addAchievement("untouchable", "Untouchable", "무적",
                "Clear a stage without losing a life.", "목숨을 잃지 않고 스테이지를 클리어하세요.");
        addAchievement("clean_sweep", "Clean Sweep", "완벽한 클리어",
                "Clear {target} stages without losing a life.",
                "목숨을 잃지 않고 스테이지 {target}개를 클리어하세요.");
    }



    private static void addText(final String key, final String english,
            final String korean) {
        ENGLISH_TEXTS.put(key, english);
        KOREAN_TEXTS.put(key, korean);
    }

    private static void addAchievement(final String id, final String englishName,
            final String koreanName, final String englishCondition,
            final String koreanCondition) {
        addText("achievement." + id + ".name", englishName, koreanName);
        addText("achievement." + id + ".condition", englishCondition,
                koreanCondition);
    }

    private static Language loadLanguage() {
        if (PREFERENCES == null)
            return Language.ENGLISH;
        try {
            return Language.valueOf(PREFERENCES.get(LANGUAGE_PREFERENCE,
                    Language.ENGLISH.name()));
        } catch (IllegalArgumentException | SecurityException exception) {
            return Language.ENGLISH;
        }
    }

    private static Preferences getPreferences() {
        try {
            return Preferences.userNodeForPackage(LanguageManager.class);
        } catch (SecurityException exception) {
            return null;
        }
    }

    /**
     * Returns the translated string for the given key in the current language.
     *
     * @param key the translation key
     * @return the translated string, or the key itself if no translation is found
     */
    public static String get(final String key) {
        Map<String, String> texts = currentLanguage == Language.KOREAN
                ? KOREAN_TEXTS : ENGLISH_TEXTS;
        String value = texts.get(key);
        return value != null ? value : key;
    }

    /**
     * @return the current UI language
     */
    public static Language getCurrentLanguage() {
        return currentLanguage;
    }

    /**
     * Switches the UI language.
     *
     * @param language the language to switch to
     */
    public static void setCurrentLanguage(final Language language) {
        currentLanguage = Objects.requireNonNull(language, "language");
        if (PREFERENCES == null)
            return;
        try {
            PREFERENCES.put(LANGUAGE_PREFERENCE, currentLanguage.name());
        } catch (SecurityException exception) {
            // Keep the language change for this session when preferences are unavailable.
        }
    }

    /** Resets the selected UI language to English for a new game session. */
    public static void resetToEnglish() {
        setCurrentLanguage(Language.ENGLISH);
    }

    /**
     * Toggles between English and Korean.
     */
    public static void toggleLanguage() {
        setCurrentLanguage(currentLanguage == Language.ENGLISH
                ? Language.KOREAN : Language.ENGLISH);
    }

    /**
     * Returns the display name of the current language.
     *
     * @return the localized name of the current language
     */
    public static String getCurrentLanguageName() {
        return currentLanguage == Language.KOREAN ? "한국어" : "English";
    }

    /**
     * Returns the display name of the other language (the one the user would switch to).
     *
     * @return the localized name of the other language
     */
    public static String getOtherLanguageName() {
        return currentLanguage == Language.KOREAN ? "English" : "한국어";
    }
}
