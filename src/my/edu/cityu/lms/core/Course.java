package my.edu.cityu.lms.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Teacher-owned course and its ordered list of learning activities. */
public final class Course {
    private final String id;
    private final String teacherId;
    private String title;
    private String description;
    private final List<LearningActivity> activities = new ArrayList<>();

    Course(String id, String teacherId, String title, String description) {
        this.id = User.required(id, "Course ID");
        this.teacherId = User.required(teacherId, "Teacher ID");
        setTitle(title);
        setDescription(description);
    }

    public String getId() { return id; }
    public String getTeacherId() { return teacherId; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public List<LearningActivity> getActivities() { return List.copyOf(activities); }

    void setTitle(String title) {
        this.title = User.required(title, "Course title");
    }

    void setDescription(String description) {
        this.description = User.required(description, "Course description");
    }

    void addActivity(LearningActivity activity) {
        Objects.requireNonNull(activity, "Activity");
        if (activities.stream().anyMatch(existing -> existing.getId().equals(activity.getId()))) {
            throw new IllegalArgumentException("Activity ID already exists in " + id + ".");
        }
        activities.add(activity);
    }

    LearningActivity requireActivity(String activityId) {
        String id = User.required(activityId, "Activity ID");
        return activities.stream().filter(activity -> activity.getId().equals(id)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown activity " + id + " in " + this.id + "."));
    }
}
