package my.edu.cityu.lms.core;

import java.util.Locale;

/** Shared identity for the two roles in the learning prototype. */
public abstract class User {
    public enum Role { STUDENT, TEACHER }

    private final String id;
    private final String username;
    private final String name;

    User(String id, String username, String name) {
        this.id = required(id, "User ID");
        this.username = normalizeUsername(username);
        this.name = required(name, "Name");
    }

    public final String getId() { return id; }
    public final String getUsername() { return username; }
    public final String getName() { return name; }
    public abstract Role getRole();

    static String normalizeUsername(String username) {
        return required(username, "Username").toLowerCase(Locale.ROOT);
    }

    static String required(String text, String label) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException(label + " is required.");
        }
        return text.strip();
    }
}
