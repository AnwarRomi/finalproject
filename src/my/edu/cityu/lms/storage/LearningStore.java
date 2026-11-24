package my.edu.cityu.lms.storage;

import my.edu.cityu.lms.core.*;
import my.edu.cityu.lms.core.LearningActivity.Difficulty;
import my.edu.cityu.lms.core.LearningActivity.Type;
import my.edu.cityu.lms.core.LearningService.EnrollmentProgress;
import my.edu.cityu.lms.core.Student.ActivityProgress;

import java.io.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/** Stores the classroom state in a versioned local file. */
public final class LearningStore {
    private static final String FORMAT = "SmartLMS/1";
    private final Path file;

    public LearningStore(Path file) {
        this.file = file.toAbsolutePath();
    }

    public void save(LearningService service) throws IOException {
        Files.createDirectories(file.getParent());
        Path staging = Files.createTempFile(file.getParent(), "smartlms-", ".tmp");
        try {
            try (var out = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(staging)))) {
                write(out, service);
            }
            try {
                Files.move(staging, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(staging, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(staging);
        }
    }

    public LearningService load() throws IOException {
        try (var in = new DataInputStream(new BufferedInputStream(Files.newInputStream(file)))) {
            if (!FORMAT.equals(in.readUTF())) {
                throw new IOException("Unrecognized classroom file format.");
            }
            var service = new LearningService();
            for (int remaining = count(in); remaining > 0; remaining--) {
                var role = User.Role.valueOf(in.readUTF());
                String id = in.readUTF();
                String username = in.readUTF();
                String name = in.readUTF();
                service.registerUser(role == User.Role.STUDENT
                        ? new Student(id, username, name) : new Teacher(id, username, name));
            }
            for (int remaining = count(in); remaining > 0; remaining--) {
                Course course = service.addCourse(in.readUTF(), in.readUTF(), in.readUTF(), in.readUTF());
                for (int activities = count(in); activities > 0; activities--) {
                    String id = in.readUTF();
                    String title = in.readUTF();
                    var difficulty = Difficulty.valueOf(in.readUTF());
                    var type = Type.valueOf(in.readUTF());
                    LearningActivity activity = type == Type.QUIZ
                            ? new Quiz(id, title, difficulty, in.readInt())
                            : new StudyMaterial(id, title, difficulty, type, in.readUTF());
                    service.addActivity(course.getTeacherId(), course.getId(), activity);
                }
            }
            for (int remaining = count(in); remaining > 0; remaining--) {
                String student = in.readUTF();
                String course = in.readUTF();
                Instant lastAccess = in.readBoolean() ? readInstant(in) : null;
                Map<String, ActivityProgress> results = new HashMap<>();
                for (int activities = count(in); activities > 0; activities--) {
                    String id = in.readUTF();
                    int attempts = in.readInt();
                    boolean complete = in.readBoolean();
                    int score = in.readInt();
                    if (score < -1 || results.putIfAbsent(id,
                            new ActivityProgress(attempts, complete, score == -1 ? null : score,
                                    readInstant(in))) != null) {
                        throw new IOException("Invalid or duplicate activity result.");
                    }
                }
                service.restoreEnrollment(new EnrollmentProgress(student, course, results, lastAccess));
            }
            if (in.read() != -1) {
                throw new IOException("Unexpected data after the classroom records.");
            }
            return service;
        } catch (EOFException ex) {
            throw new IOException("The classroom file is incomplete.", ex);
        } catch (RuntimeException ex) {
            throw new IOException("Invalid classroom data: " + ex.getMessage(), ex);
        }
    }

    private static void write(DataOutputStream out, LearningService service) throws IOException {
        out.writeUTF(FORMAT);
        var users = service.getUsers();
        out.writeInt(users.size());
        for (User user : users) {
            out.writeUTF(user.getRole().name());
            out.writeUTF(user.getId());
            out.writeUTF(user.getUsername());
            out.writeUTF(user.getName());
        }
        var courses = service.getAllCourses();
        out.writeInt(courses.size());
        for (Course course : courses) {
            out.writeUTF(course.getTeacherId());
            out.writeUTF(course.getId());
            out.writeUTF(course.getTitle());
            out.writeUTF(course.getDescription());
            out.writeInt(course.getActivities().size());
            for (LearningActivity activity : course.getActivities()) {
                out.writeUTF(activity.getId());
                out.writeUTF(activity.getTitle());
                out.writeUTF(activity.getDifficulty().name());
                out.writeUTF(activity.getType().name());
                if (activity instanceof Quiz quiz) {
                    out.writeInt(quiz.getPassingScore());
                } else {
                    out.writeUTF(((StudyMaterial) activity).getResource());
                }
            }
        }
        var enrollments = service.enrollmentSnapshots();
        out.writeInt(enrollments.size());
        for (var enrollment : enrollments) {
            out.writeUTF(enrollment.studentId());
            out.writeUTF(enrollment.courseId());
            out.writeBoolean(enrollment.lastAccessedAt() != null);
            if (enrollment.lastAccessedAt() != null) {
                writeInstant(out, enrollment.lastAccessedAt());
            }
            out.writeInt(enrollment.activityResults().size());
            for (var entry : enrollment.activityResults().entrySet()) {
                var result = entry.getValue();
                out.writeUTF(entry.getKey());
                out.writeInt(result.attempts());
                out.writeBoolean(result.completed());
                out.writeInt(result.bestQuizScore() == null ? -1 : result.bestQuizScore());
                writeInstant(out, result.lastAttemptAt());
            }
        }
    }

    private static int count(DataInputStream in) throws IOException {
        int value = in.readInt();
        if (value < 0) {
            throw new IOException("A classroom record count is invalid.");
        }
        return value;
    }

    private static Instant readInstant(DataInputStream in) throws IOException {
        return Instant.ofEpochSecond(in.readLong(), in.readInt());
    }

    private static void writeInstant(DataOutputStream out, Instant value) throws IOException {
        out.writeLong(value.getEpochSecond());
        out.writeInt(value.getNano());
    }
}
