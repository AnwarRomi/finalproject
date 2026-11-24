package my.edu.cityu.lms.checks;

import my.edu.cityu.lms.core.Course;
import my.edu.cityu.lms.core.LearningActivity.Difficulty;
import my.edu.cityu.lms.core.LearningActivity.Type;
import my.edu.cityu.lms.core.LearningService;
import my.edu.cityu.lms.core.LearningService.EnrollmentProgress;
import my.edu.cityu.lms.core.Quiz;
import my.edu.cityu.lms.core.Student;
import my.edu.cityu.lms.core.Student.ActivityProgress;
import my.edu.cityu.lms.core.StudyMaterial;
import my.edu.cityu.lms.core.Teacher;
import my.edu.cityu.lms.core.User;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

public final class CoreChecks {
    private static final Instant NOW = Instant.parse("2026-11-24T08:00:00Z");

    private CoreChecks() { }

    public static void main(String[] args) {
        accountAndCourseRules();
        activityCompletionRules();
        recommendationOrder();
        dashboardsAndAccessTime();
        persistenceHandoff();
        System.out.println("All 5 core checks passed.");
    }

    private static LearningService catalog() {
        LearningService lms = new LearningService(Clock.fixed(NOW, ZoneOffset.UTC));
        lms.registerUser(new Teacher("T1", "teacher", "Teacher One"));
        lms.registerUser(new Teacher("T2", "otherteacher", "Teacher Two"));
        lms.registerUser(new Student("S1", "student", "Student One"));
        lms.registerUser(new Student("S2", "otherstudent", "Student Two"));
        lms.addCourse("T1", "A", "Java", "Java foundations");
        lms.addCourse("T1", "B", "Maths", "Maths foundations");
        lms.addCourse("T2", "C", "SQL", "SQL foundations");
        lms.addActivity("T1", "A", new StudyMaterial("PDF", "Java Notes", Difficulty.EASY, Type.PDF, "handout"));
        lms.addActivity("T1", "A", new Quiz("QUIZ", "Java Quiz", Difficulty.MEDIUM, 70));
        lms.addActivity("T1", "B", new StudyMaterial("HARD", "Maths Challenge", Difficulty.HARD, Type.PDF, "handout"));
        lms.addActivity("T1", "B", new StudyMaterial("EASY", "Maths Introduction", Difficulty.EASY, Type.VIDEO, "video"));
        lms.addActivity("T2", "C", new Quiz("SQL", "SQL Quiz", Difficulty.EASY, 70));
        return lms;
    }

    private static LearningService scenario() {
        LearningService lms = catalog();
        lms.enrollStudent("T1", "A", "S1");
        lms.enrollStudent("T1", "B", "S1");
        lms.enrollStudent("T1", "A", "S2");
        lms.enrollStudent("T2", "C", "S2");
        return lms;
    }

    private static void accountAndCourseRules() {
        LearningService lms = scenario();
        require(lms.findDemoUser(" STUDENT ", User.Role.STUDENT).orElseThrow().getId().equals("S1"),
                "lookup should normalize the username");
        require(lms.findDemoUser("student", User.Role.TEACHER).isEmpty(), "role mismatch should fail");
        reject(() -> lms.registerUser(new Student("S3", "Student", "Duplicate username")));
        reject(() -> lms.registerUser(new Student("S1", "new", "Duplicate ID")));
        reject(() -> lms.addCourse("S1", "D", "Course", "Description"));
        reject(() -> lms.updateCourse("T2", "A", "Other title", "Other description"));
        reject(() -> lms.updateCourse("T1", "A", "Other title", ""));
        require(lms.teacherCourses("T1").get(0).getTitle().equals("Java"), "invalid edit changed the title");
        lms.updateCourse("T1", "A", "Java Programming", "OOP and practice");
        require(lms.teacherCourses("T1").get(0).getTitle().equals("Java Programming"), "valid edit was not applied");
        reject(() -> lms.addActivity("T1", "A", new Quiz("PDF", "Duplicate activity", Difficulty.EASY, 70)));
        require(!lms.enrollStudent("T1", "A", "S1"), "duplicate enrollment should remain a single row");
        pass("T01", "Role lookup, teacher ownership, editing and duplicate checks");
    }

    private static void activityCompletionRules() {
        LearningService lms = scenario();
        reject(() -> lms.markMaterialComplete("S1", "C", "SQL"));
        reject(() -> lms.submitQuiz("S1", "A", "PDF", 90));
        reject(() -> lms.markMaterialComplete("S1", "A", "QUIZ"));
        reject(() -> lms.submitQuiz("S1", "A", "QUIZ", 101));
        require(lms.activityResults("S1", "A").isEmpty(), "invalid actions should leave the record empty");
        var fail = lms.submitQuiz("S1", "A", "QUIZ", 69);
        require(!fail.completed() && fail.bestQuizScore() == 69, "a below-threshold quiz must remain unfinished");
        require(lms.courseProgress("S1", "A").percent() == 0, "a failed attempt must not add progress");
        var pass = lms.submitQuiz("S1", "A", "QUIZ", 70);
        require(pass.completed(), "the exact passing score should count");
        var retry = lms.submitQuiz("S1", "A", "QUIZ", 40);
        require(retry.attempts() == 3 && retry.completed() && retry.bestQuizScore() == 70,
                "a later lower score must not erase completion");
        lms.markMaterialComplete("S1", "A", "PDF");
        require(lms.markMaterialComplete("S1", "A", "PDF").attempts() == 1,
                "duplicate completion must be idempotent");
        require(lms.courseProgress("S1", "A").percent() == 100, "all activities completed");
        pass("T02", "Runtime dispatch, score boundary, best score and material completion");
    }

    private static void recommendationOrder() {
        LearningService lms = scenario();
        lms.markMaterialComplete("S1", "A", "PDF");
        lms.submitQuiz("S1", "A", "QUIZ", 55);
        var recommendations = lms.recommendations("S1", 10);
        var ids = recommendations.stream().map(r -> r.activity().getId()).toList();
        require(ids.equals(List.of("EASY", "HARD", "QUIZ")),
                "unfinished enrolled courses must be ranked by progress and difficulty");
        require(recommendations.get(2).reason().contains("55%"), "retry reason should explain the score");
        require(lms.recommendations("S1", 1).size() == 1, "recommendation limit");
        lms.submitQuiz("S1", "A", "QUIZ", 75);
        require(lms.recommendations("S1", 9).stream().noneMatch(r -> r.activity().getId().equals("QUIZ")),
                "passed quiz remains in recommendations");
        reject(() -> lms.recommendations("S1", 0));
        pass("T03", "Ranked recommendations exclude completed and unenrolled activities");
    }

    private static void dashboardsAndAccessTime() {
        LearningService lms = scenario();
        require(lms.courseProgress("S1", "B").lastAccessedAt() == null, "unopened course date");
        lms.openCourse("S1", "B");
        require(NOW.equals(lms.courseProgress("S1", "B").lastAccessedAt()), "opening a course records the time");
        reject(() -> lms.openCourse("S1", "C"));
        lms.markMaterialComplete("S1", "A", "PDF");
        require(lms.overallStudentProgressPercent("S1") == 25, "one completed activity out of four");
        var teacher = lms.teacherDashboard("T1");
        require(teacher.courseCount() == 2 && teacher.enrolledStudents() == 2 && teacher.rows().size() == 3,
                "teacher dashboard should count distinct students across owned enrollments");
        require(teacher.averageProgressPercent() == 17, "one out of six rounds to 17");
        lms.addCourse("T1", "D", "Empty", "No materials yet");
        lms.enrollStudent("T1", "D", "S1");
        require(lms.courseProgress("S1", "D").percent() == 0, "empty-course progress");
        pass("T04", "Student and teacher percentages, distinct count, and last-access time");
    }

    private static void persistenceHandoff() {
        LearningService original = scenario();
        original.submitQuiz("S1", "A", "QUIZ", 59);
        original.markMaterialComplete("S1", "A", "PDF");
        original.openCourse("S1", "B");

        LearningService restored = new LearningService();
        original.getUsers().forEach(restored::registerUser);
        for (Course course : original.getAllCourses()) {
            restored.addCourse(course.getTeacherId(), course.getId(), course.getTitle(), course.getDescription());
            course.getActivities().forEach(activity -> restored.addActivity(course.getTeacherId(), course.getId(), activity));
        }
        var inconsistent = new EnrollmentProgress("S1", "A",
                Map.of("QUIZ", new ActivityProgress(1, true, 59, NOW)), NOW);
        reject(() -> restored.restoreEnrollment(inconsistent));
        require(restored.studentCourses("S1").isEmpty(), "invalid restore must be atomic");
        original.enrollmentSnapshots().forEach(restored::restoreEnrollment);
        require(restored.enrollmentSnapshots().equals(original.enrollmentSnapshots()), "snapshot data did not round-trip");
        require(restored.studentProgress("S1").equals(original.studentProgress("S1")), "restored dashboard differs");
        reject(() -> restored.restoreEnrollment(original.enrollmentSnapshots().get(0)));
        restored.submitQuiz("S1", "A", "QUIZ", 100);
        require(original.activityResults("S1", "A").get("QUIZ").bestQuizScore() == 59,
                "restored state must not change source instances");
        pass("T05", "Persistence adapter validates, restores and isolates state");
    }

    private static void reject(Runnable operation) {
        try {
            operation.run();
        } catch (IllegalArgumentException expected) {
            return;
        }
        throw new AssertionError("Expected invalid operation to be rejected.");
    }

    private static void require(boolean result, String reason) {
        if (!result) {
            throw new AssertionError(reason);
        }
    }

    private static void pass(String check, String description) {
        System.out.printf("%s PASS | %s%n", check, description);
    }
}
