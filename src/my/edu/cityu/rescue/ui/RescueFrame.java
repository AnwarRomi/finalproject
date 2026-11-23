package my.edu.cityu.rescue.ui;

import my.edu.cityu.rescue.model.Allocation;
import my.edu.cityu.rescue.model.ChilledBatch;
import my.edu.cityu.rescue.model.FoodBatch;
import my.edu.cityu.rescue.model.Recipient;
import my.edu.cityu.rescue.model.ShelfStableBatch;
import my.edu.cityu.rescue.service.RescueService;
import my.edu.cityu.rescue.service.RescueService.Plan;
import my.edu.cityu.rescue.service.RescueService.Totals;
import my.edu.cityu.rescue.storage.RescueStore;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.WindowConstants;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.HashMap;
import java.util.Map;

/** Swing entry point and the operator's redistribution workspace. */
public final class RescueFrame extends JPanel {
    private static final long serialVersionUID = 1L;
    private static final Color INK = new Color(21, 44, 38);
    private static final Color GREEN = new Color(26, 99, 72);
    private static final Color PAGE = new Color(240, 245, 241);
    private static final Color LINE = new Color(209, 224, 215);
    private static final DateTimeFormatter TIME =
            DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm").withResolverStyle(ResolverStyle.STRICT);

    private final RescueService service;
    private final JTextField timeInput = new JTextField(15);
    private final JLabel registered = new JLabel();
    private final JLabel allocated = new JLabel();
    private final JLabel available = new JLabel();
    private final JLabel expired = new JLabel();
    private final JLabel status = new JLabel(" ");
    private final JLabel planMessage = new JLabel("Preview the plan to see proposed pickups.");
    private final JButton confirm = button("Confirm allocation");
    private final JTabbedPane tabs = new JTabbedPane();
    private final JTable batchTable = table("Food batches", "ID", "Food", "Donor", "Storage",
            "Use by", "Registered", "Remaining", "Status");
    private final JTable recipientTable = table("Recipients", "ID", "Recipient", "Refrigerated",
            "Capacity", "Remaining capacity");
    private final JTable planTable = table("Proposed allocations", "Batch ID", "Food", "Recipient", "Portions");
    private final JTable historyTable = table("Allocation history", "Time", "Batch ID", "Food", "Recipient", "Portions");
    private Plan activePlan;

    public RescueFrame(RescueService service, boolean demo) {
        super(new BorderLayout(0, 14));
        this.service = service;
        setBackground(PAGE);
        setBorder(BorderFactory.createEmptyBorder(19, 22, 16, 22));
        setPreferredSize(new Dimension(1080, 690));
        LocalDateTime initialTime = demo ? RescueService.DEMO_TIME : LocalDateTime.now();
        if (!service.getHistory().isEmpty()) {
            LocalDateTime last = service.getHistory().get(service.getHistory().size() - 1).time();
            if (last.isAfter(initialTime)) {
                initialTime = last;
            }
        }
        timeInput.setName("Simulation time");
        timeInput.setText(TIME.format(initialTime));
        timeInput.setToolTipText("Use YYYY-MM-DD HH:mm in campus local time.");
        add(buildHeader(demo), BorderLayout.NORTH);

        tabs.addTab("Food batches", buildBatchTab());
        tabs.addTab("Recipients", buildRecipientTab());
        tabs.addTab("Allocation", buildPlanTab());
        tabs.addTab("History", buildHistoryTab());
        add(tabs, BorderLayout.CENTER);

        status.setForeground(INK);
        status.setBorder(BorderFactory.createEmptyBorder(0, 2, 0, 0));
        status.getAccessibleContext().setAccessibleName("Status message");
        add(status, BorderLayout.SOUTH);
        timeInput.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent event) { timeChanged(); }
            @Override public void removeUpdate(DocumentEvent event) { timeChanged(); }
            @Override public void changedUpdate(DocumentEvent event) { timeChanged(); }
        });
        clearPlan();
        refresh();
        showStatus(demo ? "Demo data is separate from regular records." : "Records are saved automatically.", false);
    }

    private JComponent buildHeader(boolean demo) {
        JPanel group = new JPanel(new BorderLayout(0, 13));
        group.setOpaque(false);
        JPanel titleBlock = new JPanel();
        titleBlock.setLayout(new BoxLayout(titleBlock, BoxLayout.Y_AXIS));
        titleBlock.setOpaque(false);
        JLabel title = new JLabel("Campus Food Rescue");
        title.setForeground(INK);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 28f));
        JLabel context = new JLabel(demo ? "SDG 12  /  Demo session" : "SDG 12  /  Redistribution session");
        context.setForeground(GREEN);
        titleBlock.add(title);
        titleBlock.add(context);

        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        top.add(titleBlock, BorderLayout.WEST);
        JPanel clock = row();
        clock.add(labelFor("Simulation time", timeInput));
        clock.add(timeInput);
        JButton useNow = button("Use current time");
        useNow.addActionListener(event -> timeInput.setText(TIME.format(LocalDateTime.now())));
        clock.add(useNow);
        top.add(clock, BorderLayout.EAST);
        group.add(top, BorderLayout.NORTH);

        JPanel stats = new JPanel(new GridLayout(1, 4, 12, 0));
        stats.setOpaque(false);
        stats.add(metric(registered, "Registered portions"));
        stats.add(metric(allocated, "Allocated portions"));
        stats.add(metric(available, "Eligible portions remaining"));
        stats.add(metric(expired, "Expired portions remaining"));
        group.add(stats, BorderLayout.SOUTH);
        return group;
    }

    private JPanel buildBatchTab() {
        JTextField name = new JTextField(12);
        name.setName("Food");
        JTextField donor = new JTextField(12);
        donor.setName("Donor");
        JComboBox<String> type = new JComboBox<>(new String[]{ShelfStableBatch.STORAGE_TYPE, ChilledBatch.STORAGE_TYPE});
        type.setName("Storage type");
        JSpinner portions = new JSpinner(new SpinnerNumberModel(1, 1, 10_000, 1));
        portions.setName("Batch portions");
        JTextField deadline = new JTextField(15);
        deadline.setName("Use by");
        deadline.setToolTipText("YYYY-MM-DD HH:mm");
        deadline.setText(TIME.format(readTime().plusHours(2)));
        JButton add = button("Add batch");
        add.addActionListener(event -> {
            try {
                LocalDateTime useBy = parseTime(deadline.getText());
                portions.commitEdit();
                FoodBatch batch = service.addBatch(name.getText(), donor.getText(), type.getSelectedIndex() == 1,
                        (Integer) portions.getValue(), useBy);
                name.setText("");
                donor.setText("");
                changed("Saved food batch #" + batch.getId() + ".");
            } catch (DateTimeParseException error) {
                showStatus("Enter the use-by time as YYYY-MM-DD HH:mm.", true);
            } catch (java.text.ParseException error) {
                showStatus("Enter a whole number from 1 to 10,000 for portions.", true);
            } catch (IllegalArgumentException | IOException error) {
                showStatus(error.getMessage(), true);
            }
        });
        JPanel inputs = row();
        inputs.add(field("Food", name));
        inputs.add(field("Donor", donor));
        inputs.add(field("Storage", type));
        inputs.add(field("Portions", portions));
        inputs.add(field("Use by (YYYY-MM-DD HH:mm)", deadline));
        inputs.add(buttonField(add));
        return tab(inputs, batchTable, "Use-by times are exclusive. At the deadline, unallocated portions are expired.");
    }

    private JPanel buildRecipientTab() {
        JTextField name = new JTextField(18);
        name.setName("Recipient");
        JCheckBox fridge = new JCheckBox("Has refrigeration");
        fridge.setOpaque(false);
        JSpinner capacity = new JSpinner(new SpinnerNumberModel(0, 0, 10_000, 1));
        capacity.setName("Recipient capacity");
        JButton add = button("Add recipient");
        add.addActionListener(event -> {
            try {
                capacity.commitEdit();
                int id = service.addRecipient(name.getText(), fridge.isSelected(), (Integer) capacity.getValue());
                name.setText("");
                changed("Saved recipient #" + id + ".");
            } catch (java.text.ParseException error) {
                showStatus("Enter a whole number from 0 to 10,000 for capacity.", true);
            } catch (IllegalArgumentException | IOException error) {
                showStatus(error.getMessage(), true);
            }
        });
        JSpinner revised = new JSpinner(new SpinnerNumberModel(0, 0, 10_000, 1));
        revised.setName("Revised capacity");
        recipientTable.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
        recipientTable.getSelectionModel().addListSelectionListener(event -> {
            int selected = recipientTable.getSelectedRow();
            if (selected >= 0) {
                revised.setValue(recipientTable.getValueAt(selected, 3));
            }
        });
        JButton update = button("Update selected");
        update.addActionListener(event -> {
            int selected = recipientTable.getSelectedRow();
            if (selected < 0) {
                showStatus("Select a recipient in the table to update capacity.", true);
                return;
            }
            try {
                revised.commitEdit();
                int id = (Integer) recipientTable.getValueAt(selected, 0);
                service.updateCapacity(id, (Integer) revised.getValue());
                changed("Updated recipient #" + id + ".");
            } catch (java.text.ParseException error) {
                showStatus("Enter a whole number from 0 to 10,000 for capacity.", true);
            } catch (IllegalArgumentException | IOException error) {
                showStatus(error.getMessage(), true);
            }
        });
        JPanel inputs = row();
        inputs.add(field("Recipient", name));
        inputs.add(field("Storage capability", fridge));
        inputs.add(field("Capacity", capacity));
        inputs.add(buttonField(add));
        inputs.add(Box.createHorizontalStrut(18));
        inputs.add(field("New total capacity", revised));
        inputs.add(buttonField(update));
        return tab(inputs, recipientTable, "Capacity applies to the session and includes portions already allocated.");
    }

    private JPanel buildPlanTab() {
        JButton preview = button("Preview allocation");
        preview.addActionListener(event -> {
            try {
                Plan plan = service.preview(readTime());
                showPlan(plan);
            } catch (DateTimeParseException error) {
                clearPlan();
                showStatus("Enter simulation time as YYYY-MM-DD HH:mm.", true);
            } catch (IllegalArgumentException error) {
                clearPlan();
                showStatus(error.getMessage(), true);
            }
        });
        confirm.addActionListener(event -> {
            try {
                Plan plan = activePlan;
                if (plan == null || !plan.time().equals(readTime())) {
                    showStatus("Preview the current data before confirming.", true);
                    return;
                }
                service.commit(plan);
                changed("Saved " + totalProposed(plan) + " allocated portions in " + plan.allocations().size() + " pickups.");
            } catch (DateTimeParseException error) {
                showStatus("Enter simulation time as YYYY-MM-DD HH:mm.", true);
            } catch (IllegalArgumentException | IOException error) {
                showStatus(error.getMessage(), true);
            }
        });
        JPanel controls = row();
        controls.add(preview);
        controls.add(confirm);
        controls.add(planMessage);
        return tab(controls, planTable, "Earliest deadline first. Shelf-stable food fills ambient capacity before refrigerated capacity.");
    }

    private JPanel buildHistoryTab() {
        JPanel controls = row();
        controls.add(new JLabel("Confirmed pickups are saved and restored when the application reopens."));
        return tab(controls, historyTable, "Portions are counted as allocated when confirmed in the simulator.");
    }

    private void showPlan(Plan plan) {
        activePlan = plan;
        DefaultTableModel model = model(planTable);
        model.setRowCount(0);
        Map<Integer, String> foods = foodNames();
        Map<Integer, String> recipients = recipientNames();
        for (Allocation allocation : plan.allocations()) {
            model.addRow(new Object[]{allocation.batchId(), foods.get(allocation.batchId()),
                    recipients.get(allocation.recipientId()), allocation.portions()});
        }
        confirm.setEnabled(!plan.allocations().isEmpty());
        String result = plan.allocations().isEmpty()
                ? "No eligible portions fit the remaining capacity."
                : totalProposed(plan) + " portions proposed across " + plan.allocations().size() + " pickups.";
        planMessage.setText(result);
        showStatus(" ", false);
    }

    private static long totalProposed(Plan plan) {
        return plan.allocations().stream().mapToLong(Allocation::portions).sum();
    }

    private void clearPlan() {
        activePlan = null;
        model(planTable).setRowCount(0);
        confirm.setEnabled(false);
        planMessage.setText("Preview the plan to see proposed pickups.");
    }

    private void changed(String message) {
        clearPlan();
        refresh();
        showStatus(message, false);
    }

    private void timeChanged() {
        clearPlan();
        refresh();
        showStatus("Simulation time changed. Preview again before confirming.", false);
    }

    private void refresh() {
        LocalDateTime time;
        try {
            time = readTime();
        } catch (DateTimeParseException error) {
            time = null;
        }
        Map<Integer, Integer> leftByBatch = service.remainingByBatch();
        Map<Integer, Integer> leftByRecipient = service.remainingByRecipient();

        DefaultTableModel batches = model(batchTable);
        batches.setRowCount(0);
        for (FoodBatch batch : service.getBatches()) {
            int left = leftByBatch.get(batch.getId());
            String batchStatus = "Eligible";
            if (left == 0) {
                batchStatus = "Allocated";
            } else if (time == null) {
                batchStatus = "Check time";
            } else if (batch.hasExpiredAt(time)) {
                batchStatus = "Expired";
            }
            batches.addRow(new Object[]{batch.getId(), batch.getName(), batch.getDonor(), batch.getStorageType(),
                    TIME.format(batch.getUseBy()), batch.getPortions(), left, batchStatus});
        }
        DefaultTableModel recipients = model(recipientTable);
        recipients.setRowCount(0);
        for (Recipient recipient : service.getRecipients()) {
            recipients.addRow(new Object[]{recipient.getId(), recipient.getName(),
                    recipient.hasRefrigeration() ? "Yes" : "No", recipient.getCapacity(),
                    leftByRecipient.get(recipient.getId())});
        }
        DefaultTableModel history = model(historyTable);
        history.setRowCount(0);
        Map<Integer, String> foods = foodNames();
        Map<Integer, String> recipientNames = recipientNames();
        for (Allocation allocation : service.getHistory()) {
            history.addRow(new Object[]{TIME.format(allocation.time()), allocation.batchId(), foods.get(allocation.batchId()),
                    recipientNames.get(allocation.recipientId()), allocation.portions()});
        }

        if (time == null) {
            registered.setText("-");
            allocated.setText("-");
            available.setText("-");
            expired.setText("-");
        } else {
            Totals totals = service.totalsAt(time);
            registered.setText(Long.toString(totals.registered()));
            allocated.setText(Long.toString(totals.allocated()));
            available.setText(Long.toString(totals.available()));
            expired.setText(Long.toString(totals.expired()));
        }
    }

    private Map<Integer, String> foodNames() {
        Map<Integer, String> names = new HashMap<>();
        for (FoodBatch batch : service.getBatches()) {
            names.put(batch.getId(), batch.getName());
        }
        return names;
    }

    private Map<Integer, String> recipientNames() {
        Map<Integer, String> names = new HashMap<>();
        for (Recipient recipient : service.getRecipients()) {
            names.put(recipient.getId(), recipient.getName());
        }
        return names;
    }

    private LocalDateTime readTime() {
        return parseTime(timeInput.getText());
    }

    private static LocalDateTime parseTime(String value) {
        return LocalDateTime.parse(value.strip(), TIME);
    }

    private void showStatus(String message, boolean error) {
        status.setForeground(error ? new Color(150, 35, 39) : INK);
        status.setText(message);
    }

    private static JPanel row() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEADING, 9, 4));
        panel.setOpaque(false);
        return panel;
    }

    private static JLabel labelFor(String text, JComponent input) {
        JLabel label = new JLabel(text);
        label.setLabelFor(input);
        label.setForeground(INK);
        return label;
    }

    private static JPanel field(String title, JComponent input) {
        JPanel panel = new JPanel(new BorderLayout(0, 4));
        panel.setOpaque(false);
        panel.add(labelFor(title, input), BorderLayout.NORTH);
        panel.add(input, BorderLayout.CENTER);
        return panel;
    }

    private static JPanel buttonField(JButton button) {
        return field(" ", button);
    }

    private static JPanel metric(JLabel number, String caption) {
        JPanel card = new JPanel(new BorderLayout(0, 3));
        card.setBackground(Color.WHITE);
        card.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(LINE),
                BorderFactory.createEmptyBorder(8, 13, 8, 13)));
        number.setFont(number.getFont().deriveFont(Font.BOLD, 25f));
        number.setForeground(GREEN);
        number.getAccessibleContext().setAccessibleName(caption);
        card.add(number, BorderLayout.NORTH);
        card.add(new JLabel(caption), BorderLayout.SOUTH);
        return card;
    }

    private static JPanel tab(JPanel inputs, JTable table, String note) {
        JPanel panel = new JPanel(new BorderLayout(0, 9));
        panel.setBackground(Color.WHITE);
        panel.setBorder(BorderFactory.createEmptyBorder(10, 11, 10, 11));
        panel.add(inputs, BorderLayout.NORTH);
        JScrollPane scroll = new JScrollPane(table);
        scroll.setColumnHeaderView(table.getTableHeader());
        scroll.setBorder(BorderFactory.createLineBorder(LINE));
        panel.add(scroll, BorderLayout.CENTER);
        JLabel hint = new JLabel(note);
        hint.setForeground(GREEN);
        panel.add(hint, BorderLayout.SOUTH);
        return panel;
    }

    private static JTable table(String name, String... columns) {
        DefaultTableModel data = new DefaultTableModel(columns, 0) {
            private static final long serialVersionUID = 1L;
            @Override public boolean isCellEditable(int row, int column) { return false; }
        };
        JTable table = new JTable(data);
        table.setName(name);
        table.getAccessibleContext().setAccessibleName(name);
        table.setRowHeight(28);
        table.setFillsViewportHeight(true);
        table.setGridColor(LINE);
        table.setSelectionBackground(new Color(206, 231, 217));
        table.setSelectionForeground(INK);
        table.getTableHeader().setReorderingAllowed(false);
        if (name.equals("Food batches")) {
            table.getColumnModel().getColumn(0).setPreferredWidth(35);
            table.getColumnModel().getColumn(4).setPreferredWidth(165);
        }
        return table;
    }

    private static DefaultTableModel model(JTable table) {
        return (DefaultTableModel) table.getModel();
    }

    private static JButton button(String title) {
        JButton button = new JButton(title);
        button.setForeground(INK);
        return button;
    }

    public static void main(String[] args) {
        boolean demo = args.length == 1 && args[0].equals("--demo");
        if (args.length > 0 && !demo) {
            System.err.println("Usage: java -jar CampusFoodRescue.jar [--demo]");
            System.exit(2);
        }
        SwingUtilities.invokeLater(() -> openWindow(demo));
    }

    private static void openWindow(boolean demo) {
        UIManager.put("Panel.background", PAGE);
        UIManager.put("TabbedPane.selected", Color.WHITE);
        UIManager.put("TableHeader.background", PAGE);
        RescueStore store = null;
        try {
            store = new RescueStore(Path.of("data", demo ? "demo.tsv" : "campus-rescue.tsv"));
            RescueService service = new RescueService(store);
            if (demo) {
                service.seedDemoIfEmpty();
            }
            RescueStore session = store;
            JFrame window = new JFrame("Campus Food Rescue");
            window.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
            window.addWindowListener(new WindowAdapter() {
                @Override public void windowClosed(WindowEvent event) {
                    closeStore(session);
                }
            });
            window.setContentPane(new RescueFrame(service, demo));
            window.pack();
            window.setMinimumSize(new Dimension(980, 635));
            window.setLocationRelativeTo(null);
            window.setVisible(true);
        } catch (IOException error) {
            if (store != null) {
                closeStore(store);
            }
            JOptionPane.showMessageDialog(null, error.getMessage(), "Unable to open records", JOptionPane.ERROR_MESSAGE);
        }
    }

    private static void closeStore(RescueStore store) {
        try {
            store.close();
        } catch (IOException error) {
            System.err.println("Unable to close data file: " + error.getMessage());
        }
    }
}
