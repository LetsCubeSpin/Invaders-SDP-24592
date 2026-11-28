// 스토리 질문과 보스 이전 대화에서 위쪽 대화창으로 말하는 인물(관제탑 여성, 세라)과 그 이름을 정의한 열거형
package story;

import engine.LanguageManager;

/**
 * The characters who ask the questions of the story as a conversation at the
 * top of the board, each with a portrait of her own.
 */
public enum StorySpeaker {

    /** The woman at the control tower. */
    CONTROL("story.controlName"),
    /** Sera, the Swarm scientist who was rescued. */
    SERA("story.seraName");

    private final String nameKey;

    StorySpeaker(final String nameKey) {
        this.nameKey = nameKey;
    }

    /** @return Name shown above her lines, in the selected language. */
    public String displayName() {
        return LanguageManager.get(this.nameKey);
    }
}
