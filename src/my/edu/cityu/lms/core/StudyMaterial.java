package my.edu.cityu.lms.core;

import java.util.Objects;

/** A reading or video that the student explicitly marks complete. */
public final class StudyMaterial extends LearningActivity {
    private final Type type;
    private final String resource;

    public StudyMaterial(String id, String title, Difficulty difficulty, Type type, String resource) {
        super(id, title, difficulty);
        this.type = Objects.requireNonNull(type, "Material type");
        if (type == Type.QUIZ) {
            throw new IllegalArgumentException("Use Quiz for a scored activity.");
        }
        this.resource = User.required(resource, "Resource");
    }

    @Override
    public Type getType() {
        return type;
    }

    /** Resource label, link or file reference supplied by the teacher. */
    public String getResource() {
        return resource;
    }

    @Override
    public boolean meetsCompletionRule(Integer quizScore) {
        if (quizScore != null) {
            throw new IllegalArgumentException("A study material does not take a quiz score.");
        }
        return true;
    }
}
