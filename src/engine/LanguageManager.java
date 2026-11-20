package engine;

import java.util.HashMap;
import java.util.Map;




/**
 * Manages the current UI language and provides translated strings.
 * Supports English (en) and Korean (ko).
 */
public final class LanguageManager {





    /** Supported language codes. */
    public enum Language {
        ENGLISH("en", "ENGLISH"),
        KOREAN("ko", "한국어");

        private final String code;
        private final String displayName;

        Language(final String code, final String displayName) {
            this.code = code;
            this.displayName = displayName;
        }

        public String getCode() {
            return code;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    private static Language currentLanguage = Language.ENGLISH;

    private static final Map<String, String> translations = new HashMap<>();

    static {
        loadTranslations();
    }

    /**
     * Returns the current language.
     */
    public static Language getCurrentLanguage() {
        return currentLanguage;
    }

    /**
     * Switches to the specified language.
     */
    public static void setCurrentLanguage(final Language language) {
        currentLanguage = language;
    }

    /**
     * Cycles to the next supported language.
     */
    public static void cycleLanguage() {
        Language[] languages = Language.values();
        int index = currentLanguage.ordinal();
        currentLanguage = languages[(index + 1) % languages.length];
    }

    /**
     * Returns the translated string for the given key.
     * Falls back to the key itself if no translation is found.
     */
    public static String get(final String key) {
        String prefix = currentLanguage.getCode() + ".";
        String fullKey = prefix + key;
        String value = translations.get(fullKey);
        if (value == null) {
            // Fallback to English
            value = translations.get("en." + key);
        }
        return value != null ? value : key;
    }

    /**
     * Returns the translated string for the given key with format arguments
     * applied via {@link String#format(String, Object...)}.
     * Falls back to the key itself if no translation is found.
     *
     * @param key  the translation key
     * @param args arguments to substitute into {@code {0}}, {@code {1}}, etc.
     * @return the formatted translated string
     */
    public static String get(final String key, final Object... args) {
        return String.format(get(key), args);
    }

    /**
     * Returns the display name of the current language.
     */
    public static String getCurrentLanguageName() {
        return currentLanguage.getDisplayName();
    }

    /**
     * Returns the display name of a language.
     */
    public static String getLanguageName(final Language language) {
        return language.getDisplayName();
    }

    private static void loadTranslations() {
        // === Main Menu ===
        put("menu.play", "PLAY");
        put("menu.shop", "SHOP");
        put("menu.hangar", "HANGAR");
        put("menu.highScores", "HIGH SCORES");
        put("menu.records", "RECORDS & ACHIEVEMENTS");
        put("menu.settings", "SETTINGS");
        put("menu.exit", "EXIT");

        // === Title Screen ===
        put("title.gameName", "INVADERS");
        put("title.instructions", "SELECT WITH W+S / ARROWS, CONFIRM WITH SPACE");

        // === Game Mode Selection ===
        put("mode.endless", "ENDLESS MODE");
        put("mode.campaign", "CAMPAIGN MODE");
        put("mode.story", "STORY MODE");
        put("mode.back", "BACK");
        put("mode.selectMode", "SELECT MODE");

        // === Player Selection ===
        put("player.one", "1 PLAYER");
        put("player.two", "2 PLAYERS (CO-OP)");
        put("player.selectPlayers", "SELECT PLAYERS");

        // === Settings Menu ===
        put("settings.title", "SETTINGS");
        put("settings.audio", "AUDIO");
        put("settings.controls", "CONTROLS");
        put("settings.gameplay", "GAMEPLAY");
        put("settings.language", "LANGUAGE");
        put("settings.back", "BACK");
        put("settings.nav", "UP/DOWN: SELECT  SPACE: CONFIRM");

        // === Audio Settings ===
        put("audio.title", "AUDIO SETTINGS");
        put("audio.select", "UP/DOWN: SELECT");
        put("audio.adjust", "LEFT/RIGHT: ADJUST  M: MUTE");
        put("audio.return", "PRESS ESC TO RETURN");
        put("audio.bgmVolume", "BGM VOLUME");
        put("audio.sfxVolume", "SFX VOLUME");
        put("audio.mute", "MUTE");
        put("audio.on", "ON");
        put("audio.off", "OFF");

        // === Controls Settings ===
        put("controls.title", "CONTROLS");
        put("controls.moveLeft", "MOVE LEFT");
        put("controls.moveRight", "MOVE RIGHT");
        put("controls.fire", "FIRE");
        put("controls.back", "BACK");
        put("controls.nav", "UP/DOWN: SELECT  SPACE: CHANGE");
        put("controls.pressKey", "PRESS ANY KEY...");

        // === Gameplay Settings ===
        put("gameplay.title", "GAMEPLAY");
        put("gameplay.easy", "EASY");
        put("gameplay.normal", "NORMAL");
        put("gameplay.hard", "HARD");
        put("gameplay.nav", "UP/DOWN: SELECT  SPACE: CONFIRM");

        // === Game Over / Score Screen ===
        put("gameOver.gameClear", "GAME CLEAR");
        put("gameOver.gameOver", "GAME OVER");
        put("gameOver.playAgain", "PRESS SPACE TO PLAY AGAIN, ESC TO EXIT");
        put("gameOver.stageFailed", "STAGE %d FAILED");
        put("gameOver.newRecord", "NEW RECORD!");
        put("gameOver.playAgainEsc", "SPACE: PLAY AGAIN   ESC: MENU");
        put("score.score", "SCORE %d");
        put("score.livesRemaining", "LIVES REMAINING %d");
        put("score.enemiesDestroyed", "ENEMIES DESTROYED %d");
        put("score.accuracy", "ACCURACY %s%%");
        put("score.infiniteMode", "INFINITE MODE");
        put("score.mode", "MODE: %s");

        // === High Scores ===
        put("highScores.title", "HIGH SCORES");
        put("highScores.return", "PRESS SPACE OR ESC TO RETURN");

        // === Pause ===
        put("pause.paused", "PAUSED");

        // === Countdown ===
        put("countdown.level", "LEVEL %d");
        put("countdown.bonusLife", " - BONUS LIFE!");
        put("countdown.go", "GO!");

        // === Shop ===
        put("shop.title", "SHOP");
        put("shop.credits", "CREDITS: %d");
        put("shop.upgrades", "UPGRADES");
        put("shop.unlocks", "UNLOCKS");
        put("shop.noUpgrades", "NO UPGRADES AVAILABLE");
        put("shop.noUnlocks", "NO UNLOCKS AVAILABLE");
        put("shop.back", "BACK (ESC)");
        put("shop.retrySave", "RETRY SAVE (R)");
        put("shop.available", "AVAILABLE");
        put("shop.owned", "OWNED");
        put("shop.unlocked", "UNLOCKED");
        put("shop.saveFailed", "SAVE FAILED. PROGRESS KEPT THIS SESSION.");
        put("shop.saved", "PROGRESS SAVED.");
        put("shop.nav1", "UP/DOWN: SECTION   LEFT/RIGHT: PRODUCT");
        put("shop.nav2", "WASD ALSO WORKS   SPACE/ENTER: CONFIRM");

        // === Hangar ===
        put("hangar.title", "HANGAR");
        put("hangar.chooseShip", "CHOOSE SHIP");
        put("hangar.chooseColor", "CHOOSE COLOR");
        put("hangar.back", "BACK");
        put("hangar.selectShip", "SELECT SHIP");
        put("hangar.select", "SPACE: SELECT   ESC: BACK");
        put("hangar.locked", "UNLOCK IN SHOP   ESC: BACK");
        put("hangar.lockedBadge", "LOCKED");
        put("hangar.speed", "SPEED: %d");
        put("hangar.hp", "HP: %d");
        put("hangar.reload", "RELOAD: %dMS");
        put("hangar.color", "COLOR: %s");
        put("hangar.editColor", "LEFT/RIGHT OR DRAG: ADJUST COLOR");
        put("hangar.editColorEnter", "ENTER OR DRAG: EDIT CUSTOM COLOR");
        put("hangar.done", "ENTER: DONE");
        put("hangar.chooseColorNav", "LEFT/RIGHT: CHOOSE COLOR");
        put("hangar.returnMenu", "ESC: RETURN TO MENU");

        // === Name Input ===
        put("nameInput.title", "INTRODUCE NAME: ");

        // === Records & Achievements ===
        put("records.title", "RECORDS & ACHIEVEMENTS");
        put("records.runs", "RUN RECORDS");
        put("records.achievements", "ACHIEVEMENTS");
        put("records.locked", "[LOCKED]");
        put("records.achievementUnlocked", "ACHIEVEMENT UNLOCKED: %s");
        put("records.newAchievements", "NEW ACHIEVEMENTS:");
        put("records.nav", "LEFT/RIGHT: SWITCH   ESC/SPACE: RETURN");
        put("records.personalBests", "PERSONAL BESTS");
        put("records.noRuns", "NO COMPLETED RUNS YET.");
        put("records.highestScore", "HIGHEST SCORE: ");
        put("records.mostEnemies", "MOST ENEMIES: ");
        put("records.longestSurvival", "LONGEST SURVIVAL: ");
        put("records.topRuns", "TOP COMPLETED RUNS");
        put("records.runEntry", "%2d. %d pts  %d enemies  %.1fs");
        put("records.profileReady", "PROFILE READY");
        put("records.profileRestored", "PROFILE RESTORED FROM BACKUP");
        put("records.savingProfile", "SAVING PROFILE...");
        put("records.profileError", "PROFILE ERROR - CHECK GAME LOG");

        // === HUD ===
        put("hud.grenade", "GRENADE [G] X%d");

        // === Core Dialogs ===
        put("core.loadError", "UNABLE TO LOAD PROGRESS");
        put("core.loadErrorMessage", "YOUR SAVED PROGRESS COULD NOT BE LOADED.\nYOUR SAVE HAS NOT BEEN CHANGED. RETRY OR QUIT?");
        put("core.retry", "RETRY");
        put("core.quit", "QUIT");
        put("core.saveRewardsError", "UNABLE TO SAVE REWARDS");
        put("core.balanceFull", "YOUR BALANCE IS FULL. EARNED CREDITS REMAIN PENDING IN THIS SESSION.\nSPEND CREDITS IN THE SHOP TO MAKE ROOM.");
        put("core.saveFailed", "YOUR CREDITS ARE AVAILABLE IN THIS SESSION, BUT PROGRESS COULD NOT BE SAVED.");
        put("core.exitWarning", "\nEXITING NOW WILL LOSE UNSAVED PROGRESS OR PENDING CREDITS.");
        put("core.returnToMenu", "RETURN TO MENU");
        put("core.exitWithoutSaving", "EXIT WITHOUT SAVING");
        put("core.continue", "CONTINUE");

        // === Achievements ===
        put("achievement.first_run.name", "First Flight");
        put("achievement.first_run.condition", "Complete your first run.");
        put("achievement.first_enemy.name", "First Contact");
        put("achievement.first_enemy.condition", "Defeat your first enemy.");
        put("achievement.twenty_enemies.name", "Ace Pilot");
        put("achievement.twenty_enemies.condition", "Defeat 20 enemies in one run.");
        put("achievement.sixty_seconds.name", "Still Flying");
        put("achievement.sixty_seconds.condition", "Survive 60 active seconds in one run.");
        put("achievement.beat_high_score.name", "Top That");
        put("achievement.beat_high_score.condition", "Beat a previous run's high score.");
        put("achievement.all_endings.name", "Every Ending");
        put("achievement.all_endings.condition", "See all four story endings.");

        // === Ship Types ===
        put("ship.standard", "Standard Ship");
        put("ship.speedster", "Speedster");
        put("ship.tank", "Heavy Tank");

        // === Language Setting ===
        put("language.current", "LANGUAGE: ENGLISH");

        // === Color Names ===
        put("color.green", "Green");
        put("color.cyan", "Cyan");
        put("color.magenta", "Magenta");
        put("color.yellow", "Yellow");
        put("color.orange", "Orange");
        put("color.blue", "Blue");
        put("color.custom", "Custom");

        // === Shop Upgrades ===
        put("upgrade.hull.name", "Hull");
        put("upgrade.engine.name", "Engine");
        put("upgrade.reloader.name", "Reloader");
        put("upgrade.max", "MAX");
        put("upgrade.hull.description", "+%d max lives. Campaign.");
        put("upgrade.engine.description", "+%d move speed. Campaign.");
        put("upgrade.reloader.description", "Shot interval -%d%%. Campaign.");
        put("shop.ship.selectHangar", "Select in Hangar.");
        put("shop.ship.unlockHangar", "Unlock for Hangar.");

        // === Level Display ===
        put("hud.level", "LEVEL %d/%d");

        // === Shop Purchase Messages ===
        put("shop.purchase.available", "Available in Hangar. No purchase needed.");
        put("shop.purchase.success", "Purchase complete.");
        put("shop.purchase.insufficientFunds", "Not enough credits.");
        put("shop.purchase.alreadyUnlocked", "Already owned. No credits spent.");
        put("shop.purchase.invalidItemId", "Invalid product. Nothing purchased.");
        put("shop.purchase.categoryMismatch", "Unknown product or wrong category.");
        put("shop.purchase.itemNotAvailable", "This product is unavailable.");
        put("shop.purchase.default", "Purchase could not be completed.");

        // ---
    }

    private static void put(final String key, final String value) {
        // Store for both languages initially; Korean overrides below
        translations.put("en." + key, value);
        translations.put("ko." + key, value);
    }

    static {
        // Korean translations — override the ko.* entries
        ko("menu.play", "플레이");
        ko("menu.shop", "상점");
        ko("menu.hangar", "격납고");
        ko("menu.highScores", "최고 점수");
        ko("menu.records", "기록 & 업적");
        ko("menu.settings", "설정");
        ko("menu.exit", "나가기");

        ko("title.gameName", "인베이더스");
        ko("title.instructions", "W+S / 방향키로 선택, 스페이스 바로 확인");

        ko("mode.endless", "무한 모드");
        ko("mode.campaign", "캠페인 모드");
        ko("mode.story", "스토리 모드");
        ko("mode.back", "뒤로");
        ko("mode.selectMode", "모드 선택");

        ko("player.one", "1인 플레이");
        ko("player.two", "2인 협동 플레이");
        ko("player.selectPlayers", "플레이어 수 선택");

        ko("settings.title", "설정");
        ko("settings.audio", "오디오");
        ko("settings.controls", "조작");
        ko("settings.gameplay", "게임플레이");
        ko("settings.language", "언어");
        ko("settings.back", "뒤로");
        ko("settings.nav", "위/아래: 선택  스페이스: 확인");

        ko("audio.title", "오디오 설정");
        ko("audio.select", "위/아래: 선택");
        ko("audio.adjust", "좌/우: 조절  M: 음소거");
        ko("audio.return", "ESC 키를 눌러 돌아가기");
        ko("audio.bgmVolume", "BGM 볼륨");
        ko("audio.sfxVolume", "SFX 볼륨");
        ko("audio.mute", "음소거");
        ko("audio.on", "켬");
        ko("audio.off", "끔");

        ko("controls.title", "조작 설정");
        ko("controls.moveLeft", "왼쪽 이동");
        ko("controls.moveRight", "오른쪽 이동");
        ko("controls.fire", "발사");
        ko("controls.back", "뒤로");
        ko("controls.nav", "위/아래: 선택  스페이스 키: 변경");
        ko("controls.pressKey", "아무 키나 누르세요...");

        ko("gameplay.title", "게임플레이");
        ko("gameplay.easy", "쉬움");
        ko("gameplay.normal", "보통");
        ko("gameplay.hard", "어려움");
        ko("gameplay.nav", "위/아래: 선택  스페이스: 확인");

        ko("gameOver.gameClear", "게임 클리어");
        ko("gameOver.gameOver", "게임 오버");
        ko("gameOver.playAgain", "스페이스: 재시작, ESC: 나가기");
        ko("gameOver.stageFailed", "스테이지 %d 실패");
        ko("gameOver.newRecord", "신기록!");
        ko("gameOver.playAgainEsc", "스페이스: 재시작   ESC: 메뉴");
        ko("score.score", "점수 %d");
        ko("score.livesRemaining", "남은 목숨 %d");
        ko("score.enemiesDestroyed", "파괴한 적 %d");
        ko("score.accuracy", "정확도 %s%%");
        ko("score.infiniteMode", "무한 모드");
        ko("score.mode", "모드: %s");

        ko("highScores.title", "최고 점수");
        ko("highScores.return", "스페이스 키 또는 ESC 키를 눌러 돌아가기");

        ko("pause.paused", "일시 정지");

        ko("countdown.level", "레벨 %d");
        ko("countdown.bonusLife", " - 보너스 목숨!");
        ko("countdown.go", "시작!");

        ko("shop.title", "상점");
        ko("shop.credits", "크레딧: %d");
        ko("shop.upgrades", "업그레이드");
        ko("shop.unlocks", "해금");
        ko("shop.noUpgrades", "사용 가능한 업그레이드 없음");
        ko("shop.noUnlocks", "사용 가능한 해금 항목 없음");
        ko("shop.back", "뒤로 (ESC)");
        ko("shop.retrySave", "저장 재시도 (R)");
        ko("shop.available", "사용 가능");
        ko("shop.owned", "보유 중");
        ko("shop.unlocked", "해금됨");
        ko("shop.saveFailed", "저장 실패. 이번 세션에서는 진행 상황 유지.");
        ko("shop.saved", "진행 상황 저장됨.");
        ko("shop.nav1", "위/아래: 섹션   좌/우: 상품");
        ko("shop.nav2", "WASD도 가능   스페이스/Enter: 확인");

        ko("hangar.title", "격납고");
        ko("hangar.chooseShip", "기체 선택");
        ko("hangar.chooseColor", "색상 선택");
        ko("hangar.back", "뒤로");
        ko("hangar.selectShip", "기체 선택");
        ko("hangar.select", "스페이스: 선택   ESC: 뒤로");
        ko("hangar.locked", "상점에서 해금   ESC: 뒤로");
        ko("hangar.lockedBadge", "잠김");
        ko("hangar.speed", "속도: %d");
        ko("hangar.hp", "HP: %d");
        ko("hangar.reload", "재장전: %dms");
        ko("hangar.color", "색상: %s");
        ko("hangar.editColor", "좌/우 또는 드래그: 색상 조절");
        ko("hangar.editColorEnter", "Enter 또는 드래그: 사용자 색상 편집");
        ko("hangar.done", "Enter: 완료");
        ko("hangar.chooseColorNav", "좌/우: 색상 선택");
        ko("hangar.returnMenu", "ESC: 메뉴로 돌아가기");

        ko("nameInput.title", "이름 입력");

        ko("records.title", "기록 & 업적");
        ko("records.runs", "런 기록");
        ko("records.achievements", "업적");
        ko("records.locked", "[잠김]");
        ko("records.achievementUnlocked", "업적 달성: %s");
        ko("records.newAchievements", "새 업적:");
        ko("records.nav", "좌/우: 탭   ESC: 뒤로");
        ko("records.personalBests", "개인 최고 기록");
        ko("records.noRuns", "완료된 런 없음.");
        ko("records.highestScore", "최고 점수: ");
        ko("records.mostEnemies", "최대 적 파괴: ");
        ko("records.longestSurvival", "최장 생존 기록: ");
        ko("records.topRuns", "최고 런 기록");
        ko("records.runEntry", "%2d. %d점  %d기  %.1f초");
        ko("records.profileReady", "프로필 준비 완료");
        ko("records.profileRestored", "프로필 백업에서 복원됨");
        ko("records.savingProfile", "프로필 저장 중...");
        ko("records.profileError", "프로필 오류 - 게임 로그 확인");

        ko("hud.grenade", "유탄 [G] x%d");

        ko("core.loadError", "진행 상황 로드 실패");
        ko("core.loadErrorMessage", "저장된 진행 상황을 로드할 수 없습니다.\n저장 파일은 변경되지 않았습니다. 재시도하거나 종료하시겠습니까?");
        ko("core.retry", "재시도");
        ko("core.quit", "나가기");
        ko("core.saveRewardsError", "보상 저장 실패");
        ko("core.balanceFull", "잔액이 가득 찼습니다. 이번 세션에서 획득한 크레딧은 대기 상태입니다.\n상점에서 크레딧을 사용해서 공간을 만드세요.");
        ko("core.saveFailed", "이번 세션에서 크레딧을 사용할 수 있지만, 진행 상황을 저장할 수 없습니다.");
        ko("core.exitWarning", "\n지금 종료하면 저장되지 않은 진행 상황이나 대기 중인 크레딧이 손실됩니다.");
        ko("core.returnToMenu", "메뉴로 돌아가기");
        ko("core.exitWithoutSaving", "저장하지 않고 종료");
        ko("core.continue", "계속");

        ko("language.current", "언어: 한국어 ");

        // === Achievements ===
        ko("achievement.first_run.name", "첫 비행");
        ko("achievement.first_run.condition", "첫 번째 런을 완료하세요.");
        ko("achievement.first_enemy.name", "첫 교전");
        ko("achievement.first_enemy.condition", "첫 번째 적을 격파하세요.");
        ko("achievement.twenty_enemies.name", "에이스 파일럿");
        ko("achievement.twenty_enemies.condition", "한 런에서 적 20기를 격파하세요.");
        ko("achievement.sixty_seconds.name", "아직 비행 중");
        ko("achievement.sixty_seconds.condition", "한 런에서 60초 생존하세요.");
        ko("achievement.beat_high_score.name", "더 높게");
        ko("achievement.beat_high_score.condition", "이전 런의 최고 점수를 넘으세요.");
        ko("achievement.all_endings.name", "모든 엔딩");
        ko("achievement.all_endings.condition", "스토리 엔딩 4개를 모두 확인하세요.");

        // === Ship Types ===
        ko("ship.standard", "스탠다드 쉽");
        ko("ship.speedster", "스피드스터");
        ko("ship.tank", "헤비 탱크");

        // === Color Names ===
        ko("color.green", "초록");
        ko("color.cyan", "청록");
        ko("color.magenta", "자홍");
        ko("color.yellow", "노랑");
        ko("color.orange", "주황");
        ko("color.blue", "파랑");
        ko("color.custom", "사용자 지정");

        // === Shop Upgrades ===
        ko("upgrade.hull.name", "기체");
        ko("upgrade.engine.name", "엔진");
        ko("upgrade.reloader.name", "재장전");
        ko("upgrade.max", "최대");
        ko("upgrade.hull.description", "최대 목숨 +%d. 캠페인.");
        ko("upgrade.engine.description", "이동 속도 +%d. 캠페인.");
        ko("upgrade.reloader.description", "발사 간격 -%d%%. 캠페인.");
        ko("shop.ship.selectHangar", "격납고에서 선택하세요.");
        ko("shop.ship.unlockHangar", "격납고에서 해금하세요.");

        // === Level Display ===
        ko("hud.level", "레벨 %d/%d");

        // === Shop Purchase Messages ===
        ko("shop.purchase.available", "격납고에서 사용 가능합니다. 구매 불필요.");
        ko("shop.purchase.success", "구매 완료.");
        ko("shop.purchase.insufficientFunds", "크레딧이 부족합니다.");
        ko("shop.purchase.alreadyUnlocked", "이미 보유하고 있습니다. 크레딧 차감 없음.");
        ko("shop.purchase.invalidItemId", "잘못된 상품입니다. 구매되어지지 않았습니다.");
        ko("shop.purchase.categoryMismatch", "알 수 없는 상품 또는 잘못된 카테고리입니다.");
        ko("shop.purchase.itemNotAvailable", "이 상품은 사용할 수 없습니다.");
        ko("shop.purchase.default", "구매를 완료할 수 없습니다.");
    }

    private static void ko(final String key, final String value) {
        translations.put("ko." + key, value);
    }
}
