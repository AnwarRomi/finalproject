package my.edu.cityu.lms.core;

/** A teacher owns courses and manages their learning activities. */
public final class Teacher extends User {
    public Teacher(String id, String username, String name) {
        super(id, username, name);
    }

    @Override
    public Role getRole() {
        return Role.TEACHER;
    }
}
