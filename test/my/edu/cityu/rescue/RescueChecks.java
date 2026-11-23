package my.edu.cityu.rescue;

import my.edu.cityu.rescue.model.Allocation;
import my.edu.cityu.rescue.service.RescueService;
import my.edu.cityu.rescue.service.RescueService.Plan;
import my.edu.cityu.rescue.service.RescueService.Totals;
import my.edu.cityu.rescue.storage.RescueStore;
import my.edu.cityu.rescue.ui.RescueFrame;

import javax.imageio.ImageIO;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JSpinner;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

public final class RescueChecks {
    private static final LocalDateTime TIME = RescueService.DEMO_TIME;

    public static void main(String[] args) throws Exception {
        Path scratch = Files.createTempDirectory("campus-rescue-checks-");
        Path evidence = args.length == 0 ? null : Files.createDirectories(Path.of(args[0]));
        try {
            testDemo(scratch.resolve("demo.tsv"), evidence);
            testBoundaryAndOrder(scratch.resolve("rules.tsv"));
            testInputs(scratch.resolve("inputs.tsv"));
            testStoreFailures(scratch);
        } finally {
            try (var files = Files.walk(scratch)) {
                for (Path path : files.sorted(Comparator.reverseOrder()).toList()) {
                    Files.deleteIfExists(path);
                }
            }
        }
    }

    private static void testDemo(Path path, Path evidence) throws Exception {
        List<Allocation> expected = List.of(new Allocation(TIME, 2, 2, 12),
                new Allocation(TIME, 1, 1, 15), new Allocation(TIME, 1, 3, 3),
                new Allocation(TIME, 4, 3, 3));
        try (RescueStore store = new RescueStore(path)) {
            RescueService service = new RescueService(store);
            service.seedDemoIfEmpty();
            require(service.totalsAt(TIME).equals(new Totals(60, 0, 52, 8)), "sample input totals");
            require(service.preview(TIME).allocations().equals(expected), "expiry order, cold storage and batch splitting");
            pass("T01", "Fixture totals and exact 4-row allocation");
            SwingUtilities.invokeAndWait(() -> checkDemoUi(service, evidence));
            require(service.totalsAt(TIME).equals(new Totals(60, 33, 19, 8)), "committed sample results");
            require(service.getHistory().equals(expected), "committed history");
            require(service.remainingByRecipient().values().stream().allMatch(left -> left == 0), "all capacities reached");
            require(service.preview(TIME).allocations().isEmpty(), "no repeat allocation");
            pass("T03", "Confirm updates totals and repeat preview stays empty");
        }
        try (RescueStore store = new RescueStore(path)) {
            RescueService reopened = new RescueService(store);
            reopened.seedDemoIfEmpty();
            require(reopened.totalsAt(TIME).equals(new Totals(60, 33, 19, 8)), "restored totals");
            require(reopened.getHistory().equals(expected), "restored pickup history");
            pass("T04", "Reopen restores all four committed pickups");
        }
    }

    private static void checkDemoUi(RescueService service, Path evidence) {
        RescueFrame frame = new RescueFrame(service, true);
        JTabbedPane tabs = find(frame, JTabbedPane.class, null);
        JTable plan = find(frame, JTable.class, "Proposed allocations");
        JButton preview = find(frame, JButton.class, "Preview allocation");
        JButton confirm = find(frame, JButton.class, "Confirm allocation");
        tabs.setSelectedIndex(2);
        require(!confirm.isEnabled(), "confirm initially disabled");
        preview.doClick(0);
        require(plan.getRowCount() == 4 && confirm.isEnabled(), "GUI preview rows");
        require(service.getHistory().isEmpty(), "GUI preview leaves history empty");
        capture(frame, evidence, "allocation-preview.png");
        JTextField simulation = find(frame, JTextField.class, "Simulation time");
        simulation.setText("2026-11-24 10:01");
        require(plan.getRowCount() == 0 && !confirm.isEnabled(), "changing time clears preview");
        simulation.setText("2026-11-24 10:00");
        preview.doClick(0);
        pass("T02", "Swing preview leaves records untouched and time edits invalidate it");
        confirm.doClick(0);
        tabs.setSelectedIndex(3);
        JTable history = find(frame, JTable.class, "Allocation history");
        require(history.getRowCount() == 4, "GUI confirmed history rows");
        capture(frame, evidence, "confirmed-history.png");
    }

    private static void testBoundaryAndOrder(Path path) throws Exception {
        try (RescueStore store = new RescueStore(path)) {
            RescueService service = new RescueService(store);
            service.addBatch("Deadline now", "Lab cafe", false, 2, TIME);
            service.addBatch("Urgent dairy", "Lab cafe", true, 2, TIME.plusMinutes(1));
            service.addBatch("Shelf packs", "Lab cafe", false, 2, TIME.plusMinutes(1));
            service.addRecipient("Fridge first by ID", true, 1);
            service.addRecipient("Ambient", false, 1);
            service.addRecipient("Zero capacity", true, 0);
            Plan initial = service.preview(TIME);
            require(initial.allocations().equals(List.of(new Allocation(TIME, 2, 1, 1),
                    new Allocation(TIME, 3, 2, 1))), "boundary, tie-break, refrigeration and zero capacity");
            service.updateCapacity(1, 2);
            expect(IllegalArgumentException.class, () -> service.commit(initial), "stale preview");
            service.commit(service.preview(TIME));
            expect(IllegalArgumentException.class, () -> service.updateCapacity(1, 1), "capacity below already allocated");
            expect(IllegalArgumentException.class, () -> service.preview(TIME.minusMinutes(1)), "backdated plan");
            pass("T05", "Exact expiry boundary, tie-break and zero-capacity recipient");
            pass("T06", "Stale plan and capacity below confirmed quantity are rejected");
        }
    }

    private static void testInputs(Path path) throws Exception {
        try (RescueStore store = new RescueStore(path)) {
            RescueService service = new RescueService(store);
            require(service.preview(TIME).allocations().isEmpty(), "empty preview");
            SwingUtilities.invokeAndWait(() -> {
                RescueFrame frame = new RescueFrame(service, true);
                JTextField food = find(frame, JTextField.class, "Food");
                JTextField donor = find(frame, JTextField.class, "Donor");
                JTextField deadline = find(frame, JTextField.class, "Use by");
                JButton add = find(frame, JButton.class, "Add batch");
                donor.setText("Campus Café");
                add.doClick(0);
                require(service.getBatches().isEmpty(), "blank food rejected");
                food.setText("Bread");
                deadline.setText("2026-02-30 10:00");
                add.doClick(0);
                require(service.getBatches().isEmpty(), "invalid date rejected");
                deadline.setText("2026-11-24 12:00");
                find(frame, JSpinner.class, "Batch portions").setValue(5);
                add.doClick(0);
                require(service.getBatches().size() == 1, "valid food saved");
                find(frame, JTabbedPane.class, null).setSelectedIndex(1);
                find(frame, JTextField.class, "Recipient").setText("Student Pantry");
                find(frame, JSpinner.class, "Recipient capacity").setValue(2);
                find(frame, JButton.class, "Add recipient").doClick(0);
                JTable recipients = find(frame, JTable.class, "Recipients");
                recipients.setRowSelectionInterval(0, 0);
                find(frame, JSpinner.class, "Revised capacity").setValue(3);
                find(frame, JButton.class, "Update selected").doClick(0);
                require(service.getRecipients().get(0).getCapacity() == 3, "GUI capacity updated");
            });
            require(service.preview(TIME).allocations().get(0).portions() == 3, "new data participates in the simulation");
            pass("T07", "Swing accepts valid records and rejects blank names and invalid dates");
        }
        try (RescueStore store = new RescueStore(path)) {
            RescueService reopened = new RescueService(store);
            require(reopened.getBatches().get(0).getDonor().equals("Campus Café"), "UTF-8 name restored");
            pass("T08", "UTF-8 names survive a save and reopen");
        }
    }

    private static void testStoreFailures(Path scratch) throws Exception {
        Path corrupt = scratch.resolve("corrupt.tsv");
        String damaged = "CAMPUS_FOOD_RESCUE\t1\nB\tbroken\n";
        Files.writeString(corrupt, damaged);
        try (RescueStore store = new RescueStore(corrupt)) {
            expect(IOException.class, () -> new RescueService(store), "malformed file");
            require(Files.readString(corrupt).equals(damaged), "failed load preserves original data");
        }
        Path blocked = scratch.resolve("blocked.tsv");
        try (RescueStore store = new RescueStore(blocked)) {
            RescueService service = new RescueService(store);
            expect(IOException.class, () -> {
                try (RescueStore duplicate = new RescueStore(blocked)) {
                    require(duplicate.getPath().equals(blocked), "unexpected duplicate store");
                }
            }, "second writer");
            Files.createDirectory(blocked);
            expect(IOException.class, () -> service.addRecipient("Blocked", false, 1), "failed atomic save");
            require(service.getRecipients().isEmpty(), "failed save leaves memory unchanged");
        }
        pass("T09", "Broken records, concurrent writers and failed saves are handled");
    }

    private static <T extends Component> T find(Container container, Class<T> type, String name) {
        for (Component component : container.getComponents()) {
            String identifier = component instanceof JButton button ? button.getText()
                    : component instanceof JLabel label ? label.getText() : component.getName();
            if (type.isInstance(component) && (name == null || name.equals(identifier))) {
                return type.cast(component);
            }
            if (component instanceof Container nested) {
                T found = find(nested, type, name);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static void capture(RescueFrame frame, Path evidence, String file) {
        if (evidence == null) {
            return;
        }
        frame.setSize(1080, 530);
        layout(frame);
        BufferedImage image = new BufferedImage(frame.getWidth(), frame.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        frame.printAll(graphics);
        graphics.dispose();
        try {
            ImageIO.write(image, "png", evidence.resolve(file).toFile());
        } catch (IOException error) {
            throw new IllegalStateException("Unable to capture test output.", error);
        }
    }

    private static void layout(Container container) {
        container.doLayout();
        for (Component child : container.getComponents()) {
            if (child instanceof Container nested) {
                layout(nested);
            }
        }
    }

    private static void expect(Class<? extends Exception> type, CheckedAction action, String description) throws Exception {
        try {
            action.run();
        } catch (Exception error) {
            if (type.isInstance(error)) {
                return;
            }
            throw error;
        }
        throw new AssertionError("Expected rejection: " + description);
    }

    private static void require(boolean result, String description) {
        if (!result) {
            throw new AssertionError(description);
        }
    }

    private static void pass(String id, String description) {
        System.out.println(id + " PASS  " + description);
    }

    @FunctionalInterface
    private interface CheckedAction {
        void run() throws Exception;
    }
}
