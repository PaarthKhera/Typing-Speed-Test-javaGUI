import java.awt.*;
import java.sql.*;
import java.util.*;
import java.util.List;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;

public class TypingTest extends JFrame {
    private JTextArea promptArea, typingArea;
    private JTextField nameField, limitField;
    private JButton startButton, submitButton, clearButton, updateLeaderboardButton;
    private JLabel timerLabel;
    private JComboBox<String> modeSelector;
    private javax.swing.Timer countdownTimer;
    private long startTime;
    private boolean isTimedMode = false;
    private int timeLeft = 60;
    private String[] currentPrompt;
    private JTable leaderboardTable;
    private Connection conn;

    public TypingTest() {
        setTitle("Typing Speed Test");
        setSize(1000, 600);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        connectToDB();
        initComponents();
        loadPrompt();
        updateLeaderboard();
    }

    private void connectToDB() {
        try {
            conn = DriverManager.getConnection("jdbc:sqlite:lb.db");
            Statement stmt = conn.createStatement();
            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS leaderboard (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "name TEXT," +
                    "speed_wpm REAL," +
                    "correct_words INTEGER," +
                    "time_taken REAL," +
                    "timestamp DATETIME DEFAULT CURRENT_TIMESTAMP)");
        } catch (Exception e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this, "Database connection failed:\n" + e.getMessage());
        }
    }

    private void initComponents() {
        JPanel topPanel = new JPanel(new GridLayout(2, 3));
        topPanel.add(new JLabel("Enter your name:"));
        nameField = new JTextField();
        topPanel.add(nameField);

        topPanel.add(new JLabel("Select Mode:"));
        modeSelector = new JComboBox<>(new String[]{"Full Prompt Mode", "1 Minute Timer Mode"});
        topPanel.add(modeSelector);

        timerLabel = new JLabel("Timer: -");
        topPanel.add(timerLabel);

        topPanel.add(new JLabel("Leaderboard Top N:"));
        JPanel leaderboardPanel = new JPanel(new BorderLayout());
        limitField = new JTextField("10");
        updateLeaderboardButton = new JButton("↻");
        leaderboardPanel.add(limitField, BorderLayout.CENTER);
        leaderboardPanel.add(updateLeaderboardButton, BorderLayout.EAST);
        topPanel.add(leaderboardPanel);

        add(topPanel, BorderLayout.NORTH);

        JPanel centerPanel = new JPanel(new GridLayout(1, 2));
        promptArea = new JTextArea();
        promptArea.setWrapStyleWord(true);
        promptArea.setLineWrap(true);
        promptArea.setEditable(false);
        promptArea.setBorder(BorderFactory.createTitledBorder("Prompt"));
        centerPanel.add(new JScrollPane(promptArea));

        typingArea = new JTextArea();
        typingArea.setLineWrap(true);
        typingArea.setWrapStyleWord(true);
        typingArea.setEnabled(false);
        typingArea.setBorder(BorderFactory.createTitledBorder("Type here"));
        centerPanel.add(new JScrollPane(typingArea));

        add(centerPanel, BorderLayout.CENTER);

        JPanel buttonPanel = new JPanel();
        startButton = new JButton("Start");
        submitButton = new JButton("Submit");
        clearButton = new JButton("Clear Leaderboard");

        buttonPanel.add(startButton);
        buttonPanel.add(submitButton);
        buttonPanel.add(clearButton);
        add(buttonPanel, BorderLayout.SOUTH);

        leaderboardTable = new JTable(new DefaultTableModel(new String[]{"Name", "WPM", "Correct", "Time", "Timestamp"}, 0));
        JScrollPane leaderboardScroll = new JScrollPane(leaderboardTable);
        leaderboardScroll.setPreferredSize(new Dimension(400, 0));
        leaderboardScroll.setBorder(BorderFactory.createTitledBorder("Leaderboard"));
        add(leaderboardScroll, BorderLayout.EAST);

        startButton.addActionListener(e -> startTest());
        submitButton.addActionListener(e -> submitTest());
        updateLeaderboardButton.addActionListener(e -> updateLeaderboard());
        clearButton.addActionListener(e -> {
            int confirm = JOptionPane.showConfirmDialog(this,
                "Are you sure you want to clear the leaderboard?", "Confirm", JOptionPane.YES_NO_OPTION);
            if (confirm == JOptionPane.YES_OPTION) {
                try {
                    conn.createStatement().executeUpdate("DELETE FROM leaderboard");
                    updateLeaderboard();
                    JOptionPane.showMessageDialog(this, "Leaderboard cleared.");
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(this, "Clear error: " + ex.getMessage());
                }
            }
        });
    }

    private void startTest() {
        typingArea.setEnabled(true);
        typingArea.setText("");
        typingArea.requestFocus();
        loadPrompt();

        isTimedMode = modeSelector.getSelectedIndex() == 1;
        startTime = System.currentTimeMillis();

        if (isTimedMode) {
            timeLeft = 60;
            timerLabel.setText("Timer: 60s");
            countdownTimer = new javax.swing.Timer(1000, e -> {
                timeLeft--;
                timerLabel.setText("Timer: " + timeLeft + "s");
                if (timeLeft <= 0) {
                    countdownTimer.stop();
                    submitTest();
                }
            });
            countdownTimer.start();
        } else {
            timerLabel.setText("Timer: Running...");
        }
    }

    private void submitTest() {
        if (countdownTimer != null) countdownTimer.stop();

        long endTime = System.currentTimeMillis();
        double totalTimeSec = (endTime - startTime) / 1000.0;

        String typed = typingArea.getText().trim();
        String[] wordsTyped = typed.split("\\s+");

        int correct = 0;
        List<String> mistakes = new ArrayList<>();

        for (int i = 0; i < Math.min(wordsTyped.length, currentPrompt.length); i++) {
            if (wordsTyped[i].equals(currentPrompt[i])) {
                correct++;
            } else {
                mistakes.add("Typed: \"" + wordsTyped[i] + "\" | Expected: \"" + currentPrompt[i] + "\"");
            }
        }

        for (int i = currentPrompt.length; i < wordsTyped.length; i++) {
            mistakes.add("Typed Extra: \"" + wordsTyped[i] + "\"");
        }

        double wpm = (correct / totalTimeSec) * 60;
        String name = nameField.getText().trim().isEmpty() ? "Anonymous" : nameField.getText().trim();

        try {
            PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO leaderboard(name, speed_wpm, correct_words, time_taken) VALUES (?, ?, ?, ?)");
            ps.setString(1, name);
            ps.setDouble(2, wpm);
            ps.setInt(3, correct);
            ps.setDouble(4, totalTimeSec);
            ps.executeUpdate();
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Insert Error: " + e.getMessage());
        }

        StringBuilder resultMsg = new StringBuilder(String.format(
            "WPM: %.2f\nCorrect Words: %d\nTime: %.2f s", wpm, correct, totalTimeSec));

        if (!mistakes.isEmpty()) {
            resultMsg.append("\n\nIncorrect Words:\n");
            for (String mistake : mistakes) {
                resultMsg.append("- ").append(mistake).append("\n");
            }
        }

        JOptionPane.showMessageDialog(this, resultMsg.toString());

        updateLeaderboard();
        typingArea.setEnabled(false);
    }

    private void loadPrompt() {
        String[][] shortPrompts = {
            {"Hello,", "Did", "you", "know", "white", "chocolate", "is", "not", "chocolate", "because", "it", "doesn't", "contain", "cocoa", "solids?"},
            {"Try", "typing", "this", "fast", "and", "accurately", "for", "your", "best", "score."},
            {"Typing", "is", "a", "valuable", "skill", "in", "today's", "world", "of", "technology."}
        };

        String[] longPrompts = {
            "Typing is a foundational skill in the digital era. Being able to type quickly and accurately improves productivity, communication, and confidence when working with computers. From programming to report writing, typing is everywhere.\n\nThe faster and more precisely you type, the more efficient you become. Practicing regularly using online tools, games, or even code editors can sharpen your speed and muscle memory, helping you outperform your peers in professional and academic environments.",
            "In today's world, typing is more than just a skill; it's a necessity. Whether you're a student, a software developer, or a content writer, your ability to put thoughts into text swiftly makes a big difference.\n\nConsider the daily tasks we do — sending emails, preparing documents, coding, chatting — all of them depend on your typing ability. A faster WPM allows you to save hours every week and reduces the risk of repetitive stress injuries caused by poor typing habits.",
            "Touch typing helps improve concentration and focus. Once your fingers know where each key is, your brain can fully engage in the creative or logical task at hand, without getting distracted by hunting for keys.\n\nWith enough training, typing becomes second nature, enabling a smoother flow of ideas and more effective communication in any domain — be it writing, researching, or developing software solutions."
        };

        Random rand = new Random();

        if (modeSelector.getSelectedIndex() == 1) { 
            String longPrompt = longPrompts[rand.nextInt(longPrompts.length)];
            currentPrompt = longPrompt.split("\\s+");
            promptArea.setText(longPrompt);
        } else {
            currentPrompt = shortPrompts[rand.nextInt(shortPrompts.length)];
            promptArea.setText(String.join(" ", currentPrompt));
        }
    }

    private void updateLeaderboard() {
        try {
            int limit = Integer.parseInt(limitField.getText().trim());
            ResultSet rs = conn.createStatement().executeQuery(
                "SELECT name, speed_wpm, correct_words, time_taken, timestamp FROM leaderboard ORDER BY speed_wpm DESC LIMIT " + limit);

            DefaultTableModel model = (DefaultTableModel) leaderboardTable.getModel();
            model.setRowCount(0);
            while (rs.next()) {
                model.addRow(new Object[]{
                    rs.getString("name"),
                    String.format("%.2f", rs.getDouble("speed_wpm")),
                    rs.getInt("correct_words"),
                    String.format("%.2f", rs.getDouble("time_taken")),
                    rs.getString("timestamp")
                });
            }
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Leaderboard error: " + e.getMessage());
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new TypingTest().setVisible(true));
    }
}