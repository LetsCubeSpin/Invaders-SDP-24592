// 스토리 질문 한 개의 문장과 두 선택지(제목, 설명)를 담는 불변 데이터 클래스
package story;

/**
 * One story question: the text shown first, and a title and a short
 * description for each of the two choices.
 */
public final class StoryQuestion {

    private final String text;
    private final String[] titles;
    private final String[] details;

    /**
     * @param text
     *            Text of the question.
     * @param titles
     *            Titles of choice 0 and choice 1.
     * @param details
     *            Descriptions of choice 0 and choice 1.
     */
    public StoryQuestion(final String text, final String[] titles,
            final String[] details) {
        if (text == null || titles == null || details == null
                || titles.length != 2 || details.length != 2)
            throw new IllegalArgumentException(
                    "A question needs a text and two titles and details.");
        this.text = text;
        this.titles = titles.clone();
        this.details = details.clone();
    }

    /** @return Text of the question. */
    public String getText() {
        return this.text;
    }

    /**
     * @param choice
     *            0 or 1.
     * @return Title of the choice.
     */
    public String getTitle(final int choice) {
        return this.titles[checkChoice(choice)];
    }

    /**
     * @param choice
     *            0 or 1.
     * @return Short description of the choice.
     */
    public String getDetail(final int choice) {
        return this.details[checkChoice(choice)];
    }

    private static int checkChoice(final int choice) {
        if (choice < 0 || choice > 1)
            throw new IllegalArgumentException("choice must be 0 or 1.");
        return choice;
    }
}
