package my.edu.cityu.lms.ui;

import my.edu.cityu.lms.core.*;
import my.edu.cityu.lms.core.LearningActivity.Difficulty;
import my.edu.cityu.lms.demo.SmartLmsCoreDemo;
import my.edu.cityu.lms.storage.LearningStore;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.IOException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Supplier;

/** Swing integration demo for both classroom roles. */
public final class SmartLmsFrame extends JFrame {
    private final LearningStore store;
    private final LearningService service;
    private final JComboBox<User> accounts = new JComboBox<>();
    private final JComboBox<Course> courses = new JComboBox<>();
    private final JLabel summary = new JLabel();
    private final JLabel status = new JLabel(" ");
    private final JProgressBar overall = new JProgressBar(0, 100);
    private final JTabbedPane tabs = new JTabbedPane();
    private final JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT));
    private final DefaultTableModel activities = tableModel(
            "Activity", "Type", "Difficulty", "Resource / pass mark", "Status", "Attempts", "Best score");
    private final DefaultTableModel progress = tableModel("Student", "Course", "Completed", "Progress", "Last activity (UTC)");
    private final DefaultTableModel recommendations = tableModel("Course", "Next activity", "Type", "Reason");
    private final JTable activityTable = new JTable(activities);
    private final JButton openCourse = new JButton("Open course");
    private boolean changingCourses;

    private SmartLmsFrame(LearningService service, LearningStore store) {
        super("Smart Learning Management System");
        this.service = service;
        this.store = store;
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent event) {
                if (save() || JOptionPane.showConfirmDialog(SmartLmsFrame.this,
                        "Close without saving the current changes?", "Unsaved classroom",
                        JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
                    dispose();
                }
            }
        });
        setMinimumSize(new Dimension(790, 510));
        setSize(1070, 650);
        setLocationRelativeTo(null);

        JPanel content = new JPanel(new BorderLayout(0, 14));
        content.setBorder(new EmptyBorder(18, 20, 16, 20));
        setContentPane(content);

        JPanel top = new JPanel(new GridLayout(0, 1, 0, 5));
        JLabel heading = new JLabel("Smart Learning Management System");
        heading.setFont(heading.getFont().deriveFont(Font.BOLD, 24f));
        top.add(heading);
        top.add(new JLabel("SDG 4 • Quality Education"));
        JPanel picker = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        picker.add(new JLabel("Demo account"));
        picker.add(accounts);
        picker.add(new JLabel("Course"));
        picker.add(courses);
        picker.add(openCourse);
        top.add(picker);
        top.add(summary);
        overall.setStringPainted(true);
        top.add(overall);
        content.add(top, BorderLayout.NORTH);

        tabs.addTab("Learning activities", tableView(activityTable));
        tabs.addTab("Progress", tableView(new JTable(progress)));
        tabs.addTab("Recommendations", tableView(new JTable(recommendations)));
        content.add(tabs, BorderLayout.CENTER);
        JPanel footer = new JPanel(new BorderLayout());
        footer.add(actions, BorderLayout.NORTH);
        footer.add(status, BorderLayout.SOUTH);
        content.add(footer, BorderLayout.SOUTH);

        accounts.setRenderer((list, value, index, selected, focus) ->
                label(list, value == null ? "" : value.getName() + " (" + value.getRole() + ")",
                        index, selected, focus));
        courses.setRenderer((list, value, index, selected, focus) ->
                label(list, value == null ? "" : value.getId() + " · " + value.getTitle(),
                        index, selected, focus));
        service.getUsers().stream().filter(user -> user.getRole() == User.Role.STUDENT)
                .forEach(accounts::addItem);
        service.getUsers().stream().filter(user -> user.getRole() == User.Role.TEACHER)
                .forEach(accounts::addItem);
        accounts.addActionListener(event -> refreshAccount());
        courses.addActionListener(event -> {
            if (!changingCourses) {
                refreshTables();
            }
        });
        openCourse.addActionListener(event -> runAction(() -> {
            service.openCourse(actor().getId(), course().getId());
            tabs.setSelectedIndex(0);
            return "Opened " + course().getTitle() + ".";
        }));
        refreshAccount();
    }

    private void refreshAccount() {
        var user = actor();
        if (user == null) {
            return;
        }
        boolean student = user.getRole() == User.Role.STUDENT;
        actions.removeAll();
        if (student) {
            addButton("Complete material", () -> {
                service.markMaterialComplete(actor().getId(), course().getId(), activity().getId());
                return "Material completed.";
            });
            addButton("Submit quiz score", this::submitScore);
        } else {
            addButton("Add course", () -> editCourse(false));
            addButton("Edit course", () -> editCourse(true));
            addButton("Add activity", this::addActivity);
            addButton("Enroll student", this::enroll);
        }
        addButton("Save classroom", () -> "Classroom saved.");
        tabs.setEnabledAt(2, student);
        if (!student && tabs.getSelectedIndex() == 2) {
            tabs.setSelectedIndex(0);
        }
        actions.revalidate();
        actions.repaint();
        status.setText(" ");
        refreshCourses();
    }

    private void refreshCourses() {
        Course previous = (Course) courses.getSelectedItem();
        List<Course> available = actor().getRole() == User.Role.STUDENT
                ? service.studentCourses(actor().getId()) : service.teacherCourses(actor().getId());
        changingCourses = true;
        courses.setModel(new DefaultComboBoxModel<>(available.toArray(Course[]::new)));
        if (available.contains(previous)) {
            courses.setSelectedItem(previous);
        }
        changingCourses = false;
        refreshTables();
    }

    private void refreshTables() {
        var user = actor();
        boolean student = user.getRole() == User.Role.STUDENT;
        Course selected = (Course) courses.getSelectedItem();
        openCourse.setEnabled(student && selected != null);
        activities.setRowCount(0);
        progress.setRowCount(0);
        recommendations.setRowCount(0);
        if (selected != null) {
            java.util.Map<String, Student.ActivityProgress> results = student ? service.activityResults(user.getId(), selected.getId()) : java.util.Map.of();
            for (var activity : selected.getActivities()) {
                var outcome = results.get(activity.getId());
                String detail = activity instanceof Quiz quiz ? quiz.getPassingScore() + "%"
                        : ((StudyMaterial) activity).getResource();
                activities.addRow(new Object[]{activity.getTitle(), activity.getType(), activity.getDifficulty(),
                        detail, student ? activityStatus(outcome) : "", outcome == null ? "" : outcome.attempts(),
                        outcome == null || outcome.bestQuizScore() == null ? "" : outcome.bestQuizScore() + "%"});
            }
        }
        List<LearningService.CourseProgress> rows;
        if (student) {
            int percent = service.overallStudentProgressPercent(user.getId());
            summary.setText(selected == null ? "No enrolled courses."
                    : selected.getTitle() + " • Course progress "
                    + service.courseProgress(user.getId(), selected.getId()).percent() + "%");
            overall.setValue(percent);
            overall.setString("Overall learning progress " + percent + "%");
            for (var item : service.recommendations(user.getId(), 5)) {
                recommendations.addRow(new Object[]{item.courseTitle(), item.activity().getTitle(),
                        item.activity().getType(), item.reason()});
            }
            rows = service.studentProgress(user.getId());
        } else {
            var dashboard = service.teacherDashboard(user.getId());
            int percent = dashboard.averageProgressPercent();
            summary.setText(dashboard.courseCount() + " courses • " + dashboard.enrolledStudents()
                    + " enrolled students" + (selected == null ? "" : " • " + selected.getDescription()));
            overall.setValue(percent);
            overall.setString("Classroom activity completion " + percent + "%");
            rows = dashboard.rows();
        }
        for (var row : rows) {
            progress.addRow(new Object[]{row.studentName(), row.courseTitle(),
                    row.completedActivities() + " / " + row.totalActivities(), row.percent() + "%",
                    row.lastAccessedAt() == null ? "" : row.lastAccessedAt()});
        }
    }

    private static String activityStatus(Student.ActivityProgress outcome) {
        if (outcome == null) {
            return "Pending";
        }
        return outcome.completed() ? "Completed" : "Retry available";
    }

    private String submitScore() {
        Course selected = course();
        if (!(activity() instanceof Quiz quiz)) {
            throw new IllegalArgumentException("Select a quiz in Learning activities.");
        }
        String value = JOptionPane.showInputDialog(this,
                "Enter a practice score from 0 to 100. Pass mark is " + quiz.getPassingScore() + "%.");
        if (value == null) {
            return null;
        }
        int score = number(value, "Quiz score");
        var outcome = service.submitQuiz(actor().getId(), selected.getId(), quiz.getId(), score);
        return "Score " + score + "% • " + activityStatus(outcome) + " • best " + outcome.bestQuizScore() + "%";
    }

    private String editCourse(boolean edit) {
        Course selected = edit ? course() : null;
        var id = new JTextField(edit ? selected.getId() : "", 22);
        id.setEditable(!edit);
        var title = new JTextField(edit ? selected.getTitle() : "", 22);
        var description = new JTextField(edit ? selected.getDescription() : "", 22);
        if (!form(edit ? "Edit course" : "Add course", "Course ID", id, "Title", title, "Description", description)) {
            return null;
        }
        if (edit) {
            service.updateCourse(actor().getId(), selected.getId(), title.getText(), description.getText());
        } else {
            Course added = service.addCourse(actor().getId(), id.getText(), title.getText(), description.getText());
            refreshCourses();
            courses.setSelectedItem(added);
        }
        return edit ? "Course updated." : "Course added.";
    }

    private String addActivity() {
        Course selected = course();
        var id = new JTextField(22);
        var title = new JTextField(22);
        var type = new JComboBox<>(LearningActivity.Type.values());
        var difficulty = new JComboBox<>(Difficulty.values());
        var detail = new JTextField(22);
        if (!form("Add activity", "Activity ID", id, "Title", title, "Type", type, "Difficulty", difficulty,
                "Resource reference or quiz pass mark", detail)) {
            return null;
        }
        var level = (Difficulty) difficulty.getSelectedItem();
        var kind = (LearningActivity.Type) type.getSelectedItem();
        LearningActivity added = kind == LearningActivity.Type.QUIZ
                ? new Quiz(id.getText(), title.getText(), level, number(detail.getText(), "Pass mark"))
                : new StudyMaterial(id.getText(), title.getText(), level, kind, detail.getText());
        service.addActivity(actor().getId(), selected.getId(), added);
        return "Activity added to " + selected.getTitle() + ".";
    }

    private String enroll() {
        Course selected = course();
        User[] students = service.getUsers().stream()
                .filter(user -> user.getRole() == User.Role.STUDENT).toArray(User[]::new);
        if (students.length == 0) {
            throw new IllegalArgumentException("No student accounts are available.");
        }
        var options = new JComboBox<>(students);
        options.setRenderer((list, value, index, highlighted, focus) ->
                label(list, value == null ? "" : value.getName() + " (" + value.getId() + ")",
                        index, highlighted, focus));
        if (!form("Enroll student", "Student", options)) {
            return null;
        }
        User student = (User) options.getSelectedItem();
        return service.enrollStudent(actor().getId(), selected.getId(), student.getId())
                ? student.getName() + " enrolled." : student.getName() + " is already enrolled.";
    }

    private boolean form(String title, Object... pairs) {
        JPanel panel = new JPanel(new GridLayout(0, 2, 8, 8));
        for (int i = 0; i < pairs.length; i += 2) {
            panel.add(new JLabel((String) pairs[i]));
            panel.add((Component) pairs[i + 1]);
        }
        return JOptionPane.showConfirmDialog(this, panel, title, JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE) == JOptionPane.OK_OPTION;
    }

    private User actor() {
        return (User) accounts.getSelectedItem();
    }

    private Course course() {
        Course selected = (Course) courses.getSelectedItem();
        if (selected == null) {
            throw new IllegalArgumentException("Select a course first.");
        }
        return selected;
    }

    private LearningActivity activity() {
        int row = activityTable.getSelectedRow();
        if (row < 0 || tabs.getSelectedIndex() != 0) {
            throw new IllegalArgumentException("Select an activity in Learning activities.");
        }
        return course().getActivities().get(activityTable.convertRowIndexToModel(row));
    }

    private void addButton(String name, Supplier<String> action) {
        JButton button = new JButton(name);
        button.addActionListener(event -> runAction(action));
        actions.add(button);
    }

    private void runAction(Supplier<String> action) {
        try {
            String message = action.get();
            if (message != null) {
                refreshCourses();
                if (save()) {
                    status.setText(message);
                }
            }
        } catch (IllegalArgumentException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Review the input", JOptionPane.WARNING_MESSAGE);
        }
    }

    private boolean save() {
        try {
            store.save(service);
            return true;
        } catch (IOException ex) {
            status.setText("Save failed. Current changes are still open.");
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Unable to save classroom",
                    JOptionPane.ERROR_MESSAGE);
            return false;
        }
    }

    private static int number(String text, String label) {
        try {
            return Integer.parseInt(text.strip());
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(label + " must be a whole number.", ex);
        }
    }

    private static Component label(JList<?> list, String text, int index, boolean selected, boolean focus) {
        return new DefaultListCellRenderer().getListCellRendererComponent(list, text, index, selected, focus);
    }

    private static JScrollPane tableView(JTable table) {
        table.setRowHeight(25);
        table.setFillsViewportHeight(true);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        return new JScrollPane(table);
    }

    private static DefaultTableModel tableModel(String... headers) {
        return new DefaultTableModel(headers, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            for (var look : UIManager.getInstalledLookAndFeels()) {
                if ("Nimbus".equals(look.getName())) {
                    try {
                        UIManager.setLookAndFeel(look.getClassName());
                    } catch (ReflectiveOperationException | UnsupportedLookAndFeelException ex) {
                        System.err.println("Using the default Swing appearance: " + ex.getMessage());
                    }
                    break;
                }
            }
            Path path = args.length == 0
                    ? Path.of(System.getProperty("user.home"), ".smartlms", "classroom.dat") : Path.of(args[0]);
            var store = new LearningStore(path);
            LearningService service;
            try {
                service = store.load();
            } catch (NoSuchFileException ex) {
                service = SmartLmsCoreDemo.createDemo();
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(null, "Could not open " + path + "\n" + ex.getMessage(),
                        "Smart LMS startup error", JOptionPane.ERROR_MESSAGE);
                return;
            }
            new SmartLmsFrame(service, store).setVisible(true);
        });
    }
}
