package my.edu.cityu.lms.core;

import my.edu.cityu.lms.core.LearningActivity.Difficulty;
import my.edu.cityu.lms.core.Student.ActivityProgress;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Application logic used by the student and teacher screens. */
public final class LearningService {
    private final Clock clock;
    private final Map<String, User> users = new LinkedHashMap<>();
    private final Map<String, User> usersByUsername = new HashMap<>();
    private final Map<String, Course> courses = new LinkedHashMap<>();

    public LearningService() {
        this(Clock.systemUTC());
    }

    public LearningService(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "Clock");
    }

    public record CourseProgress(String studentId, String studentName, String courseId, String courseTitle,
                                 int completedActivities, int totalActivities, Instant lastAccessedAt) {
        public int percent() {
            return roundedPercent(completedActivities, totalActivities);
        }
    }

    public record TeacherDashboard(int courseCount, int enrolledStudents, int averageProgressPercent,
                                   List<CourseProgress> rows) {
        public TeacherDashboard {
            rows = List.copyOf(rows);
        }
    }

    public record Recommendation(String courseId, String courseTitle, LearningActivity activity,
                                 int courseProgressPercent, String reason) { }

    /** Persistence adapter data for a single enrollment, including attempts and timestamps. */
    public record EnrollmentProgress(String studentId, String courseId, Map<String, ActivityProgress> activityResults,
                                     Instant lastAccessedAt) {
        public EnrollmentProgress {
            studentId = User.required(studentId, "Student ID");
            courseId = User.required(courseId, "Course ID");
            activityResults = Map.copyOf(activityResults);
        }
    }

    public void registerUser(User user) {
        Objects.requireNonNull(user, "User");
        if (users.containsKey(user.getId()) || usersByUsername.containsKey(user.getUsername())) {
            throw new IllegalArgumentException("User ID and username must be unique.");
        }
        User registered = switch (user.getRole()) {
            case STUDENT -> new Student(user.getId(), user.getUsername(), user.getName());
            case TEACHER -> new Teacher(user.getId(), user.getUsername(), user.getName());
        };
        users.put(registered.getId(), registered);
        usersByUsername.put(registered.getUsername(), registered);
    }

    /** Matches a demo account and role. GUI navigation can use the returned account ID. */
    public Optional<User> findDemoUser(String username, User.Role role) {
        Objects.requireNonNull(role, "Role");
        if (username == null || username.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(usersByUsername.get(User.normalizeUsername(username)))
                .filter(user -> user.getRole() == role);
    }

    public List<User> getUsers() {
        return List.copyOf(users.values());
    }

    public List<Course> getAllCourses() {
        return List.copyOf(courses.values());
    }

    public Course addCourse(String teacherId, String courseId, String title, String description) {
        Teacher teacher = requireTeacher(teacherId);
        Course course = new Course(courseId, teacher.getId(), title, description);
        if (courses.putIfAbsent(course.getId(), course) != null) {
            throw new IllegalArgumentException("Course ID already exists: " + course.getId() + ".");
        }
        return course;
    }

    public void updateCourse(String teacherId, String courseId, String title, String description) {
        Course course = requireOwnedCourse(teacherId, courseId);
        String updatedTitle = User.required(title, "Course title");
        course.setDescription(description);
        course.setTitle(updatedTitle);
    }

    public void addActivity(String teacherId, String courseId, LearningActivity activity) {
        requireOwnedCourse(teacherId, courseId).addActivity(activity);
    }

    /** Returns true only when this call creates the enrollment. */
    public boolean enrollStudent(String teacherId, String courseId, String studentId) {
        Course course = requireOwnedCourse(teacherId, courseId);
        return requireStudent(studentId).enroll(course.getId());
    }

    public List<Course> studentCourses(String studentId) {
        return requireStudent(studentId).courseIds().stream().map(courses::get).toList();
    }

    public List<Course> teacherCourses(String teacherId) {
        Teacher teacher = requireTeacher(teacherId);
        return courses.values().stream().filter(course -> course.getTeacherId().equals(teacher.getId())).toList();
    }

    public Course openCourse(String studentId, String courseId) {
        Student student = requireStudent(studentId);
        Course course = requireCourse(courseId);
        student.access(course.getId(), clock.instant());
        return course;
    }

    public ActivityProgress markMaterialComplete(String studentId, String courseId, String activityId) {
        return recordActivity(studentId, courseId, activityId, null);
    }

    public ActivityProgress submitQuiz(String studentId, String courseId, String activityId, int quizScore) {
        return recordActivity(studentId, courseId, activityId, quizScore);
    }

    private ActivityProgress recordActivity(String studentId, String courseId, String activityId, Integer quizScore) {
        Student student = requireStudent(studentId);
        Course course = requireCourse(courseId);
        LearningActivity activity = course.requireActivity(activityId);

        // The same base-type reference dispatches to the material or quiz implementation.
        boolean passed = activity.meetsCompletionRule(quizScore);
        return student.record(course.getId(), activity.getId(), passed, quizScore, clock.instant());
    }

    public CourseProgress courseProgress(String studentId, String courseId) {
        return progress(requireStudent(studentId), requireCourse(courseId));
    }

    public List<CourseProgress> studentProgress(String studentId) {
        Student student = requireStudent(studentId);
        return student.courseIds().stream().map(id -> progress(student, courses.get(id))).toList();
    }

    public int overallStudentProgressPercent(String studentId) {
        return combinedPercent(studentProgress(studentId));
    }

    public Map<String, ActivityProgress> activityResults(String studentId, String courseId) {
        return requireStudent(studentId).results(requireCourse(courseId).getId());
    }

    public TeacherDashboard teacherDashboard(String teacherId) {
        List<Course> owned = teacherCourses(teacherId);
        List<CourseProgress> rows = new ArrayList<>();
        for (User user : users.values()) {
            if (user instanceof Student student) {
                for (Course course : owned) {
                    if (student.isEnrolled(course.getId())) {
                        rows.add(progress(student, course));
                    }
                }
            }
        }
        int studentCount = (int) rows.stream().map(CourseProgress::studentId).distinct().count();
        return new TeacherDashboard(owned.size(), studentCount, combinedPercent(rows), rows);
    }

    /**
     * Considers unfinished activities in enrolled courses. It prioritizes courses with
     * lower progress, then activities closest to the learner's current difficulty band.
     */
    public List<Recommendation> recommendations(String studentId, int limit) {
        if (limit < 1) {
            throw new IllegalArgumentException("Recommendation limit must be positive.");
        }
        Student student = requireStudent(studentId);
        List<Recommendation> candidates = new ArrayList<>();
        for (String courseId : student.courseIds()) {
            Course course = courses.get(courseId);
            Map<String, ActivityProgress> results = student.results(courseId);
            int percent = progress(student, course, results).percent();
            for (LearningActivity activity : course.getActivities()) {
                ActivityProgress result = results.get(activity.getId());
                if (result == null || !result.completed()) {
                    candidates.add(new Recommendation(courseId, course.getTitle(), activity, percent,
                            reason(activity, result, percent)));
                }
            }
        }
        candidates.sort(Comparator.comparingInt(Recommendation::courseProgressPercent)
                .thenComparingInt(r -> Math.abs(r.activity().getDifficulty().ordinal()
                        - preferredDifficulty(r.courseProgressPercent()).ordinal()))
                .thenComparing(Recommendation::courseId)
                .thenComparing(r -> r.activity().getId()));
        return List.copyOf(candidates.subList(0, Math.min(limit, candidates.size())));
    }

    private static Difficulty preferredDifficulty(int percent) {
        if (percent < 40) {
            return Difficulty.EASY;
        }
        return percent < 75 ? Difficulty.MEDIUM : Difficulty.HARD;
    }

    private static String reason(LearningActivity activity, ActivityProgress result, int percent) {
        if (activity instanceof Quiz quiz && result != null) {
            return "Retry available. Best score " + result.bestQuizScore()
                    + "%, pass mark " + quiz.getPassingScore() + "%.";
        }
        String level = activity.getDifficulty().name().toLowerCase(java.util.Locale.ROOT);
        return "Unfinished " + level + " activity in a course at " + percent + "% completion.";
    }

    public List<EnrollmentProgress> enrollmentSnapshots() {
        List<EnrollmentProgress> snapshots = new ArrayList<>();
        for (User user : users.values()) {
            if (user instanceof Student student) {
                for (String courseId : student.courseIds()) {
                    snapshots.add(new EnrollmentProgress(student.getId(), courseId,
                            student.results(courseId), student.lastAccess(courseId)));
                }
            }
        }
        return List.copyOf(snapshots);
    }

    /** Restores one enrollment after the referenced users, courses and activities have been loaded. */
    public void restoreEnrollment(EnrollmentProgress snapshot) {
        Objects.requireNonNull(snapshot, "Enrollment snapshot");
        Student student = requireStudent(snapshot.studentId());
        Course course = requireCourse(snapshot.courseId());
        for (var entry : snapshot.activityResults().entrySet()) {
            LearningActivity activity = course.requireActivity(entry.getKey());
            ActivityProgress result = entry.getValue();
            if (activity.meetsCompletionRule(result.bestQuizScore()) != result.completed()
                    || (result.bestQuizScore() == null && result.attempts() != 1)
                    || snapshot.lastAccessedAt() == null
                    || snapshot.lastAccessedAt().isBefore(result.lastAttemptAt())) {
                throw new IllegalArgumentException("Invalid saved progress for activity " + activity.getId() + ".");
            }
        }
        student.restore(course.getId(), snapshot.activityResults(), snapshot.lastAccessedAt());
    }

    private static CourseProgress progress(Student student, Course course) {
        return progress(student, course, student.results(course.getId()));
    }

    private static CourseProgress progress(Student student, Course course, Map<String, ActivityProgress> results) {
        int completed = (int) results.values().stream().filter(ActivityProgress::completed).count();
        return new CourseProgress(student.getId(), student.getName(), course.getId(), course.getTitle(),
                completed, course.getActivities().size(), student.lastAccess(course.getId()));
    }

    private static int combinedPercent(List<CourseProgress> rows) {
        long completed = rows.stream().mapToLong(CourseProgress::completedActivities).sum();
        long total = rows.stream().mapToLong(CourseProgress::totalActivities).sum();
        return roundedPercent(completed, total);
    }

    private static int roundedPercent(long completed, long total) {
        return total == 0 ? 0 : (int) Math.round(completed * 100.0 / total);
    }

    private User requireUser(String userId) {
        String id = User.required(userId, "User ID");
        User user = users.get(id);
        if (user == null) {
            throw new IllegalArgumentException("Unknown user " + id + ".");
        }
        return user;
    }

    private Student requireStudent(String id) {
        if (requireUser(id) instanceof Student student) {
            return student;
        }
        throw new IllegalArgumentException("Choose a student account.");
    }

    private Teacher requireTeacher(String id) {
        if (requireUser(id) instanceof Teacher teacher) {
            return teacher;
        }
        throw new IllegalArgumentException("Choose a teacher account.");
    }

    private Course requireCourse(String courseId) {
        String id = User.required(courseId, "Course ID");
        Course course = courses.get(id);
        if (course == null) {
            throw new IllegalArgumentException("Unknown course " + id + ".");
        }
        return course;
    }

    private Course requireOwnedCourse(String teacherId, String courseId) {
        Teacher teacher = requireTeacher(teacherId);
        Course course = requireCourse(courseId);
        if (!course.getTeacherId().equals(teacher.getId())) {
            throw new IllegalArgumentException("This teacher does not own " + course.getId() + ".");
        }
        return course;
    }
}
