package my.edu.cityu.lms.core;

/** A scored activity that counts as completed once the student passes it. */
public final class Quiz extends LearningActivity {
    private final int passingScore;

    public Quiz(String id, String title, Difficulty difficulty, int passingScore) {
        super(id, title, difficulty);
        if (passingScore < 1 || passingScore > 100) {
            throw new IllegalArgumentException("Passing score must be between 1 and 100.");
        }
        this.passingScore = passingScore;
    }

    @Override
    public Type getType() {
        return Type.QUIZ;
    }

    public int getPassingScore() {
        return passingScore;
    }

    @Override
    public boolean meetsCompletionRule(Integer quizScore) {
        if (quizScore == null || quizScore < 0 || quizScore > 100) {
            throw new IllegalArgumentException("Enter a quiz score between 0 and 100.");
        }
        return quizScore >= passingScore;
    }
}
