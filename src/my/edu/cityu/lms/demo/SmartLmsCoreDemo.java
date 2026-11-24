package my.edu.cityu.lms.demo;

import my.edu.cityu.lms.core.LearningActivity.Difficulty;
import my.edu.cityu.lms.core.LearningActivity.Type;
import my.edu.cityu.lms.core.LearningService;
import my.edu.cityu.lms.core.Quiz;
import my.edu.cityu.lms.core.Student;
import my.edu.cityu.lms.core.StudyMaterial;
import my.edu.cityu.lms.core.Teacher;

import java.io.PrintStream;

/** Runs a fictional classroom scenario using the same API as the GUI. */
public final class SmartLmsCoreDemo {
    private SmartLmsCoreDemo() { }

    public static LearningService createDemo() {
        LearningService lms = new LearningService();
        lms.registerUser(new Teacher("T1001", "teacher", "Demo Teacher"));
        lms.registerUser(new Student("S1001", "aisha", "Aisha Rahman"));
        lms.registerUser(new Student("S1002", "daniel", "Daniel Lim"));
        lms.registerUser(new Student("S1003", "sara", "Sara Ahmad"));

        lms.addCourse("T1001", "BIT1063", "Discrete Mathematics", "Logic, sets and graphs");
        lms.addCourse("T1001", "BIT1123", "Java Programming", "Object-oriented programming fundamentals");
        lms.addCourse("T1001", "DB101", "Database Systems", "SQL and relational design");

        lms.addActivity("T1001", "BIT1063",
                new StudyMaterial("M01", "Propositional Logic Slides", Difficulty.EASY, Type.PDF, "Logic handout"));
        lms.addActivity("T1001", "BIT1063",
                new StudyMaterial("M02", "Set Theory Video", Difficulty.MEDIUM, Type.VIDEO, "Sets lesson"));
        lms.addActivity("T1001", "BIT1063", new Quiz("M03", "Graph Theory Exercises", Difficulty.HARD, 70));
        lms.addActivity("T1001", "BIT1063",
                new StudyMaterial("M04", "Graph Theory Notes", Difficulty.MEDIUM, Type.PDF, "Graph theory handout"));

        lms.addActivity("T1001", "BIT1123",
                new StudyMaterial("J01", "Java Getting Started", Difficulty.EASY, Type.PDF, "Java handout"));
        lms.addActivity("T1001", "BIT1123",
                new StudyMaterial("J02", "Java Classes Video", Difficulty.EASY, Type.VIDEO, "Classes lesson"));
        lms.addActivity("T1001", "BIT1123", new Quiz("J03", "OOP Fundamentals Quiz", Difficulty.MEDIUM, 70));

        lms.addActivity("T1001", "DB101",
                new StudyMaterial("D01", "SQL Introduction", Difficulty.EASY, Type.PDF, "SQL handout"));
        lms.addActivity("T1001", "DB101",
                new StudyMaterial("D02", "SQL Filters Video", Difficulty.EASY, Type.VIDEO, "Filtering lesson"));
        lms.addActivity("T1001", "DB101", new Quiz("D03", "SQL Joins Practice", Difficulty.HARD, 75));

        lms.enrollStudent("T1001", "BIT1063", "S1001");
        lms.enrollStudent("T1001", "BIT1123", "S1001");
        lms.enrollStudent("T1001", "DB101", "S1001");
        lms.enrollStudent("T1001", "BIT1123", "S1002");
        lms.enrollStudent("T1001", "DB101", "S1003");
        lms.markMaterialComplete("S1001", "BIT1063", "M01");
        lms.markMaterialComplete("S1001", "BIT1063", "M02");
        lms.submitQuiz("S1001", "BIT1063", "M03", 84);
        lms.markMaterialComplete("S1001", "BIT1123", "J01");
        lms.markMaterialComplete("S1001", "DB101", "D01");
        lms.markMaterialComplete("S1002", "BIT1123", "J01");
        lms.markMaterialComplete("S1003", "DB101", "D01");
        return lms;
    }

    public static void main(String[] args) {
        runDemo(System.out);
    }

    private static void runDemo(PrintStream out) {
        LearningService lms = createDemo();
        String studentId = "S1001";
        String javaCourseId = "BIT1123";
        String quizId = "J03";

        out.println("Smart Learning Management System | SDG 4 Quality Education");
        out.println();
        out.println("Student dashboard - Aisha Rahman");
        for (var progress : lms.studentProgress(studentId)) {
            out.printf("  %s | %d/%d complete (%d%%)%n", progress.courseTitle(),
                    progress.completedActivities(), progress.totalActivities(), progress.percent());
        }
        out.printf("  Overall: %d%%%n%n", lms.overallStudentProgressPercent(studentId));

        out.println("Recommended next activities");
        for (var recommendation : lms.recommendations(studentId, 4)) {
            out.printf("  %s | %s | %s%n", recommendation.courseTitle(),
                    recommendation.activity().getTitle(), recommendation.reason());
        }

        out.println();
        out.println("Pass mark is 70%. Both attempts use the same quiz.");
        for (int score : new int[]{60, 80}) {
            var attempt = lms.submitQuiz(studentId, javaCourseId, quizId, score);
            out.printf("  Score %d%% | %s | Java progress %d%%%n", score,
                    attempt.completed() ? "completed" : "retry available",
                    lms.courseProgress(studentId, javaCourseId).percent());
        }

        var teacher = lms.teacherDashboard("T1001");
        out.printf("%nTeacher dashboard: %d courses, %d distinct enrolled students, %d%% activity completion.%n",
                teacher.courseCount(), teacher.enrolledStudents(), teacher.averageProgressPercent());
    }
}
