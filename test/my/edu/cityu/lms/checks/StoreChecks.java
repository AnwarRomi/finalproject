package my.edu.cityu.lms.checks;

import my.edu.cityu.lms.core.LearningActivity.Difficulty;
import my.edu.cityu.lms.core.LearningActivity.Type;
import my.edu.cityu.lms.core.StudyMaterial;
import my.edu.cityu.lms.demo.SmartLmsCoreDemo;
import my.edu.cityu.lms.storage.LearningStore;

import java.io.IOException;
import java.nio.file.Files;

public final class StoreChecks {
    private StoreChecks() { }

    public static void main(String[] args) throws IOException {
        var directory = Files.createTempDirectory("smartlms-check-");
        var file = directory.resolve("classroom.dat");
        try {
            var original = SmartLmsCoreDemo.createDemo();
            original.submitQuiz("S1001", "BIT1123", "J03", 60);
            original.submitQuiz("S1001", "BIT1123", "J03", 80);
            original.addCourse("T1001", "OOP2", "Object Design", "Practice design");
            original.addActivity("T1001", "OOP2",
                    new StudyMaterial("R1", "Design Notes", Difficulty.MEDIUM, Type.PDF, "notes.pdf"));
            original.enrollStudent("T1001", "OOP2", "S1002");
            var store = new LearningStore(file);
            store.save(original);
            var restored = store.load();
            var quiz = restored.activityResults("S1001", "BIT1123").get("J03");
            if (!quiz.completed() || quiz.bestQuizScore() != 80 || quiz.attempts() != 2
                    || !original.enrollmentSnapshots().equals(restored.enrollmentSnapshots())
                    || restored.courseProgress("S1001", "BIT1123").percent() != 67
                    || restored.openCourse("S1002", "OOP2").getActivities().size() != 1
                    || !restored.recommendations("S1001", 5).stream()
                    .map(item -> item.activity().getId()).toList().equals(original.recommendations("S1001", 5)
                            .stream().map(item -> item.activity().getId()).toList())) {
                throw new AssertionError("Restored classroom differs from the saved one.");
            }
            restored.markMaterialComplete("S1002", "OOP2", "R1");
            store.save(restored);
            if (store.load().courseProgress("S1002", "OOP2").percent() != 100) {
                throw new AssertionError("Second save did not persist the new completion.");
            }
            System.out.println("T06 PASS | Disk round trip and replacement preserve classroom progress");
            Files.write(file, new byte[]{0, 25});
            try {
                store.load();
                throw new AssertionError("An incomplete file should fail to load.");
            } catch (IOException expected) {
                System.out.println("T07 PASS | Truncated classroom file is rejected");
            }
        } finally {
            Files.deleteIfExists(file);
            Files.delete(directory);
        }
    }
}
