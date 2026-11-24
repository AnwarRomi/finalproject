package my.edu.cityu.lms.core;

import java.util.Objects;

/** The shared contract for PDF, video and quiz activities. */
public abstract class LearningActivity {
    public enum Difficulty { EASY, MEDIUM, HARD }
    public enum Type { PDF, VIDEO, QUIZ }

    private final String id;
    private final String title;
    private final Difficulty difficulty;

    LearningActivity(String id, String title, Difficulty difficulty) {
        this.id = User.required(id, "Activity ID");
        this.title = User.required(title, "Activity title");
        this.difficulty = Objects.requireNonNull(difficulty, "Difficulty");
    }

    public final String getId() { return id; }
    public final String getTitle() { return title; }
    public final Difficulty getDifficulty() { return difficulty; }
    public abstract Type getType();

    /** Materials take no score. Quizzes take a percentage from 0 to 100. */
    public abstract boolean meetsCompletionRule(Integer quizScore);
}
