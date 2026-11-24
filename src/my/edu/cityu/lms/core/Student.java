package my.edu.cityu.lms.core;

import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Stores individual enrollment and activity outcomes. */
public final class Student extends User {
    private final Map<String, Enrollment> enrollments = new LinkedHashMap<>();

    public Student(String id, String username, String name) {
        super(id, username, name);
    }

    @Override
    public Role getRole() {
        return Role.STUDENT;
    }

    public record ActivityProgress(int attempts, boolean completed, Integer bestQuizScore, Instant lastAttemptAt) {
        public ActivityProgress {
            if (attempts < 1 || (bestQuizScore != null && (bestQuizScore < 0 || bestQuizScore > 100))) {
                throw new IllegalArgumentException("Invalid activity progress.");
            }
            Objects.requireNonNull(lastAttemptAt, "Last attempt time");
        }
    }

    private static final class Enrollment {
        private final Map<String, ActivityProgress> results = new HashMap<>();
        private Instant lastAccessedAt;
    }

    boolean enroll(String courseId) {
        return enrollments.putIfAbsent(courseId, new Enrollment()) == null;
    }

    boolean isEnrolled(String courseId) {
        return enrollments.containsKey(courseId);
    }

    List<String> courseIds() {
        return List.copyOf(enrollments.keySet());
    }

    Instant lastAccess(String courseId) {
        return enrollment(courseId).lastAccessedAt;
    }

    void access(String courseId, Instant at) {
        Enrollment enrollment = enrollment(courseId);
        if (enrollment.lastAccessedAt == null || at.isAfter(enrollment.lastAccessedAt)) {
            enrollment.lastAccessedAt = at;
        }
    }

    Map<String, ActivityProgress> results(String courseId) {
        return Map.copyOf(enrollment(courseId).results);
    }

    ActivityProgress record(String courseId, String activityId, boolean passed, Integer score, Instant at) {
        Enrollment enrollment = enrollment(courseId);
        ActivityProgress prior = enrollment.results.get(activityId);
        if (prior != null && score == null) {
            access(courseId, at);
            return prior;
        }
        Integer best = score;
        boolean completed = passed;
        int attempts = 1;
        if (prior != null) {
            attempts = Math.incrementExact(prior.attempts());
            completed |= prior.completed();
            if (score != null) {
                best = Math.max(score, prior.bestQuizScore());
            }
            if (at.isBefore(prior.lastAttemptAt())) {
                at = prior.lastAttemptAt();
            }
        }
        ActivityProgress next = new ActivityProgress(attempts, completed, best, at);
        enrollment.results.put(activityId, next);
        access(courseId, at);
        return next;
    }

    void restore(String courseId, Map<String, ActivityProgress> results, Instant lastAccessedAt) {
        Enrollment enrollment = new Enrollment();
        enrollment.results.putAll(results);
        enrollment.lastAccessedAt = lastAccessedAt;
        if (enrollments.putIfAbsent(courseId, enrollment) != null) {
            throw new IllegalArgumentException("Enrollment already exists for " + courseId + ".");
        }
    }

    private Enrollment enrollment(String courseId) {
        Enrollment enrollment = enrollments.get(courseId);
        if (enrollment == null) {
            throw new IllegalArgumentException("Student is not enrolled in " + courseId + ".");
        }
        return enrollment;
    }
}
