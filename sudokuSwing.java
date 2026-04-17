import java.awt.*;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import javax.swing.*;
import javax.swing.border.LineBorder;

public class sudokuSwing extends JFrame {
    private JTextField[][] cells = new JTextField[9][9];
    private JButton solveButton = new JButton("Solve Puzzle");
    private JButton clearButton = new JButton("Clear Board");
    private JButton saveButton  = new JButton("Save Game");
    private JButton loadButton  = new JButton("Load Game");
    private JLabel statusLabel  = new JLabel("Enter puzzle and press Solve", JLabel.CENTER);
    

    // Database Manager instance
    private DatabaseManager db = new DatabaseManager();

    // =========================================================
    //  INNER CLASS: DatabaseManager
    // =========================================================
    class DatabaseManager {
        private static final String DB_URL = "jdbc:sqlite:sudoku_saves.db";

        public DatabaseManager() {
            try (Connection conn = DriverManager.getConnection(DB_URL);
                 Statement stmt = conn.createStatement()) {
                stmt.execute(
                    "CREATE TABLE IF NOT EXISTS saves (" +
                    "  id       INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "  name     TEXT    NOT NULL," +
                    "  board    TEXT    NOT NULL," +
                    "  saved_at DATETIME DEFAULT CURRENT_TIMESTAMP" +
                    ")"
                );
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }

        /** Insert a new save record. */
        public void saveBoard(String name, String boardData) {
            String sql = "INSERT INTO saves (name, board) VALUES (?, ?)";
            try (Connection conn = DriverManager.getConnection(DB_URL);
                 PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, name);
                pstmt.setString(2, boardData);
                pstmt.executeUpdate();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }

        /** Return the most recent board string for the given save name. */
        public String loadLatestBoard(String name) {
            String sql = "SELECT board FROM saves WHERE name = ? ORDER BY saved_at DESC LIMIT 1";
            try (Connection conn = DriverManager.getConnection(DB_URL);
                 PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, name);
                ResultSet rs = pstmt.executeQuery();
                if (rs.next()) return rs.getString("board");
            } catch (SQLException e) {
                e.printStackTrace();
            }
            return null;
        }

        /** Return a distinct list of all save names, newest first. */
        public List<String> getAllSaveNames() {
            List<String> names = new ArrayList<>();
            String sql = "SELECT DISTINCT name FROM saves ORDER BY saved_at DESC";
            try (Connection conn = DriverManager.getConnection(DB_URL);
                 Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(sql)) {
                while (rs.next()) names.add(rs.getString("name"));
            } catch (SQLException e) {
                e.printStackTrace();
            }
            return names;
        }
    }

    // =========================================================
    //  CONSTRUCTOR
    // =========================================================
    public sudokuSwing() {
        setTitle("Elite Sudoku Solver");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(650, 750);
        setLayout(new BorderLayout(10, 10));
        setLocationRelativeTo(null);

        // --- Build the 9x9 board from nine 3x3 sub-grids ---
        JPanel mainBoard = new JPanel(new GridLayout(3, 3));
        mainBoard.setBorder(new LineBorder(Color.BLACK, 3));
        mainBoard.setBackground(Color.BLACK);

        for (int blockRow = 0; blockRow < 3; blockRow++)
            for (int blockCol = 0; blockCol < 3; blockCol++)
                mainBoard.add(createSubGrid(blockRow, blockCol));

        // --- Control panel ---
        JPanel controlPanel = new JPanel(new BorderLayout(5, 5));
        controlPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        statusLabel.setFont(new Font("SansSerif", Font.BOLD, 14));
        statusLabel.setForeground(new Color(44, 62, 80));

        JPanel buttonPanel = new JPanel(new FlowLayout());
        styleButton(solveButton, new Color(46, 204, 113));  // Green
        styleButton(clearButton, new Color(231, 76, 60));   // Red
        styleButton(saveButton,  new Color(52, 152, 219));  // Blue
        styleButton(loadButton,  new Color(155, 89, 182));  // Purple
        
        buttonPanel.add(solveButton);
        buttonPanel.add(clearButton);
        buttonPanel.add(saveButton);
        buttonPanel.add(loadButton);

        controlPanel.add(statusLabel, BorderLayout.NORTH);
        controlPanel.add(buttonPanel, BorderLayout.CENTER);

        // --- Action listeners ---
        solveButton.addActionListener(e -> solveAction());
        clearButton.addActionListener(e -> clearBoard());
        saveButton.addActionListener(e -> saveCurrentBoard());
        loadButton.addActionListener(e -> loadSavedBoard());

        add(mainBoard,    BorderLayout.CENTER);
        add(controlPanel, BorderLayout.SOUTH);

        setVisible(true);
    }

    // =========================================================
    //  UI HELPERS
    // =========================================================
    private JPanel createSubGrid(int bRow, int bCol) {
        JPanel subGrid = new JPanel(new GridLayout(3, 3));
        subGrid.setBorder(BorderFactory.createLineBorder(Color.DARK_GRAY, 1));

        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 3; c++) {
                int row = bRow * 3 + r;
                int col = bCol * 3 + c;

                cells[row][col] = new JTextField();
                cells[row][col].setHorizontalAlignment(JTextField.CENTER);
                cells[row][col].setFont(new Font("Monospaced", Font.BOLD, 24));
                cells[row][col].setBorder(BorderFactory.createLineBorder(new Color(200, 200, 200)));
                subGrid.add(cells[row][col]);
            }
        }
        return subGrid;
    }

    private void styleButton(JButton btn, Color bg) {
        btn.setBackground(bg);
        btn.setForeground(Color.BLACK);
        btn.setFocusPainted(false);
        btn.setFont(new Font("SansSerif", Font.BOLD, 14));
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
    }

    // =========================================================
    //  DATABASE INTEGRATION METHODS
    // =========================================================

    /** Collect the current board and prompt the user for a save name. */
    private void saveCurrentBoard() {
        StringBuilder sb = new StringBuilder();
        for (int r = 0; r < 9; r++)
            for (int c = 0; c < 9; c++) {
                String val = cells[r][c].getText().trim();
                sb.append(val.isEmpty() ? "0" : val);
            }

        String name = JOptionPane.showInputDialog(
            this, "Enter a name for this save:", "Save Game", JOptionPane.PLAIN_MESSAGE
        );

        if (name != null && !name.trim().isEmpty()) {
            db.saveBoard(name.trim(), sb.toString());
            statusLabel.setText("Game saved as: \"" + name.trim() + "\"");
            statusLabel.setForeground(new Color(39, 174, 96));
        }
    }

    /** Show a dropdown of all saves and load the chosen one onto the board. */
    private void loadSavedBoard() {
        List<String> names = db.getAllSaveNames();

        if (names.isEmpty()) {
            statusLabel.setText("No saved games found.");
            statusLabel.setForeground(Color.ORANGE);
            return;
        }

        String chosen = (String) JOptionPane.showInputDialog(
            this,
            "Select a saved game to load:",
            "Load Game",
            JOptionPane.PLAIN_MESSAGE,
            null,
            names.toArray(),
            names.get(0)
        );

        if (chosen == null) return; // user cancelled

        String boardData = db.loadLatestBoard(chosen);
        if (boardData != null && boardData.length() == 81) {
            clearBoard();
            for (int r = 0; r < 9; r++) {
                for (int c = 0; c < 9; c++) {
                    char ch = boardData.charAt(r * 9 + c);
                    if (ch != '0') {
                        cells[r][c].setText(String.valueOf(ch));
                        cells[r][c].setFont(new Font("Monospaced", Font.BOLD, 24));
                    }
                }
            }
            statusLabel.setText("Loaded: \"" + chosen + "\"");
            statusLabel.setForeground(new Color(41, 128, 185));
        } else {
            statusLabel.setText("Failed to load save: corrupted data.");
            statusLabel.setForeground(Color.RED);
        }
    }

    // =========================================================
    //  SUDOKU LOGIC
    // =========================================================
    private void solveAction() {
        resetCellColors();
        if (hasDuplicates()) {
            statusLabel.setText("Error: Duplicate numbers detected!");
            statusLabel.setForeground(Color.RED);
            return;
        }

        int[][] board = getBoardFromGUI();
        if (solve(board)) {
            updateGUIWithBoard(board);
            statusLabel.setText("Solved Successfully!");
            statusLabel.setForeground(new Color(39, 174, 96));
        } else {
            statusLabel.setText("No possible solution exists.");
            statusLabel.setForeground(Color.RED);
        }
    }

    private boolean hasDuplicates() {
        for (int r = 0; r < 9; r++) {
            for (int c = 0; c < 9; c++) {
                String val = cells[r][c].getText().trim();
                if (val.isEmpty()) continue;

                if (!val.matches("[1-9]")) {
                    cells[r][c].setBackground(new Color(255, 204, 203));
                    return true;
                }

                for (int i = 0; i < 9; i++) {
                    if (i != c && cells[r][i].getText().equals(val)) {
                        highlightError(r, c, r, i);
                        return true;
                    }
                    if (i != r && cells[i][c].getText().equals(val)) {
                        highlightError(r, c, i, c);
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private void highlightError(int r1, int c1, int r2, int c2) {
        cells[r1][c1].setBackground(new Color(231, 76, 60));
        cells[r2][c2].setBackground(new Color(231, 76, 60));
        cells[r1][c1].setForeground(Color.WHITE);
        cells[r2][c2].setForeground(Color.WHITE);
    }

    private void resetCellColors() {
        for (int r = 0; r < 9; r++)
            for (int c = 0; c < 9; c++) {
                cells[r][c].setBackground(Color.WHITE);
                cells[r][c].setForeground(Color.BLACK);
            }
    }

    private void clearBoard() {
        for (int r = 0; r < 9; r++)
            for (int c = 0; c < 9; c++) {
                cells[r][c].setText("");
                cells[r][c].setBackground(Color.WHITE);
                cells[r][c].setForeground(Color.BLACK);
            }
        statusLabel.setText("Board Cleared");
        statusLabel.setForeground(Color.BLACK);
    }

    private boolean solve(int[][] board) {
        for (int row = 0; row < 9; row++) {
            for (int col = 0; col < 9; col++) {
                if (board[row][col] == 0) {
                    for (int num = 1; num <= 9; num++) {
                        if (isValid(board, row, col, num)) {
                            board[row][col] = num;
                            if (solve(board)) return true;
                            board[row][col] = 0;
                        }
                    }
                    return false;
                }
            }
        }
        return true;
    }

    private boolean isValid(int[][] board, int row, int col, int num) {
        for (int i = 0; i < 9; i++) {
            if (board[row][i] == num || board[i][col] == num) return false;
            if (board[3 * (row / 3) + i / 3][3 * (col / 3) + i % 3] == num) return false;
        }
        return true;
    }

    private int[][] getBoardFromGUI() {
        int[][] board = new int[9][9];
        for (int r = 0; r < 9; r++)
            for (int c = 0; c < 9; c++) {
                String text = cells[r][c].getText().trim();
                board[r][c] = text.isEmpty() ? 0 : Integer.parseInt(text);
                if (board[r][c] != 0)
                    cells[r][c].setFont(new Font("Monospaced", Font.BOLD, 24));
            }
        return board;
    }

    private void updateGUIWithBoard(int[][] board) {
        for (int r = 0; r < 9; r++)
            for (int c = 0; c < 9; c++)
                if (cells[r][c].getText().isEmpty()) {
                    cells[r][c].setText(String.valueOf(board[r][c]));
                    cells[r][c].setForeground(new Color(41, 128, 185));
                }
    }

    // =========================================================
    //  ENTRY POINT
    // =========================================================
    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {}
        SwingUtilities.invokeLater(() -> new sudokuSwing());
    }
}
