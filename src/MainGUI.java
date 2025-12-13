
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.util.*;
import java.util.List;
import java.util.regex.*;
import Algorithm.*;

public class MainGUI extends JFrame {

    // --- Components ---
    private MazePanel mazePanel;
    private JScrollPane mazeScrollPane; // เพื่อรองรับการ Zoom แล้วภาพใหญ่เกินจอ
    private JTextArea logArea;
    private JLabel statusLabel;

    // Control Buttons
    private JButton btnLoad, btnSettings, btnClearLog, btnRunGA, btnStopGA;

    // Visualization Options (Checkboxes)
    private JCheckBox chkShowWeights;
    private JCheckBox chkOverlayDijkstra;
    private JCheckBox chkOverlayAStar;
    private JCheckBox chkOverlayGA; // [New]

    // GA Playback Controls
    private JSlider historySlider;
    private JButton btnPrev100, btnPrev, btnNext, btnNext100;
    private JLabel lblGenCount;
    private JPanel playbackPanel;

    // --- Data ---
    private int[][] grid;
    private int rows, cols;
    private int[] start = new int[2];
    private int[] goal = new int[2];
    private File currentMapFile;

    // --- Path Storage ---
    private List<int[]> pathDijkstra = null;
    private List<int[]> pathAStar = null;

    // GA History
    private static class GASnapshot {
        List<int[]> path;
        int gen;
        int cost;

        public GASnapshot(List<int[]> p, int g, int c, String s) {
            this.path = (p != null) ? new ArrayList<>(p) : null;
            this.gen = g;
            this.cost = c;
        }
    }

    private List<GASnapshot> gaHistory = new ArrayList<>();
    private int currentHistoryIndex = -1;

    // Thread Control
    private Thread gaThread;
    private volatile boolean isGARunning = false;

    // --- Settings (Feature 7) ---
    private int settingPopSize = 200;
    private int settingGenerations = 2000;
    private int settingElitism = 20;
    private double settingMutation = 0.6;

    // --- Zoom (Feature 6) ---
    private double zoomFactor = 1.0;

    public MainGUI() {
        setTitle("Maze Solver MFK");
        setSize(1280, 850);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        // ==========================================
        // 1. Top Panel: Toolbar
        // ==========================================
        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));

        btnLoad = new JButton("Load Map");
        btnSettings = new JButton("GA Settings");
        btnClearLog = new JButton("Clear Log");

        // GA Controls
        btnRunGA = new JButton("Run GA");
        btnStopGA = new JButton("Stop GA");
        btnStopGA.setEnabled(false);
        btnRunGA.setEnabled(false);

        topPanel.add(btnLoad);
        topPanel.add(new JSeparator(SwingConstants.VERTICAL));
        topPanel.add(btnSettings);
        topPanel.add(btnClearLog);
        topPanel.add(new JSeparator(SwingConstants.VERTICAL));
        topPanel.add(btnRunGA);
        topPanel.add(btnStopGA);

        add(topPanel, BorderLayout.NORTH);

        // ==========================================
        // 2. Left Panel: Display Options (Feature 4)
        // ==========================================
        JPanel leftPanel = new JPanel();
        leftPanel.setLayout(new BoxLayout(leftPanel, BoxLayout.Y_AXIS));
        leftPanel.setBorder(BorderFactory.createTitledBorder("Layers"));

        chkShowWeights = new JCheckBox("Show Weights");
        chkOverlayDijkstra = new JCheckBox("Dijkstra Path (Purple)");
        chkOverlayAStar = new JCheckBox("A* Path (Orange)");
        chkOverlayGA = new JCheckBox("GA Path (Blue)");

        // Default Selections
        chkShowWeights.setSelected(true);
        chkOverlayDijkstra.setSelected(true);
        chkOverlayAStar.setSelected(true);
        chkOverlayGA.setSelected(true);

        ActionListener repaintAction = e -> mazePanel.repaint();
        chkShowWeights.addActionListener(repaintAction);
        chkOverlayDijkstra.addActionListener(repaintAction);
        chkOverlayAStar.addActionListener(repaintAction);
        chkOverlayGA.addActionListener(repaintAction);

        leftPanel.add(chkShowWeights);
        leftPanel.add(chkOverlayDijkstra);
        leftPanel.add(chkOverlayAStar);
        leftPanel.add(chkOverlayGA);
        leftPanel.add(Box.createVerticalGlue()); // Push to top

        add(leftPanel, BorderLayout.WEST);

        // ==========================================
        // 3. Center: Maze Panel with Zoom
        // ==========================================

        mazePanel = new MazePanel();
        mazeScrollPane = new JScrollPane(mazePanel);

        // --- [จุดที่แก้] 1. ระบบ ZOOM (รองรับ macOS Touchpad นุ่มๆ) ---
        mazePanel.addMouseWheelListener(e -> {
            // ใช้ getPreciseWheelRotation() เพื่อรับค่าละเอียดจาก Trackpad
            double rotation = e.getPreciseWheelRotation();

            // ความไว (Sensitivity) : ปรับเลขนี้ถ้ารู้สึกว่าซูมเร็ว/ช้าไป
            // 0.05 คือกำลังดีสำหรับ Mac Trackpad
            double sensitivity = 0.01;

            // สูตรคำนวณ: ยิ่งรูดนิ้วเร็ว ยิ่งซูมเร็ว (Dynamic Factor)
            double factor = 1.0 + (Math.abs(rotation) * sensitivity);

            if (rotation < 0) {
                zoomFactor *= factor;
            } else {
                zoomFactor /= factor;
            }

            // Limit Zoom (กันซูมจนมองไม่เห็น หรือใหญ่เกิน)
            if (zoomFactor < 0.05)
                zoomFactor = 0.05;
            if (zoomFactor > 20.0)
                zoomFactor = 20.0;

            mazePanel.revalidate(); // คำนวณขนาดใหม่
            mazePanel.repaint(); // วาดใหม่
        });

        // --- 2. ระบบ PAN (Click & Drag) ---
        MouseAdapter mouseHandler = new MouseAdapter() {
            private Point origin;

            @Override
            public void mousePressed(MouseEvent e) {
                origin = new Point(e.getPoint());
                mazePanel.setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                mazePanel.setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (origin != null) {
                    JViewport viewPort = (JViewport) SwingUtilities.getAncestorOfClass(JViewport.class, mazePanel);
                    if (viewPort != null) {
                        int deltaX = origin.x - e.getX();
                        int deltaY = origin.y - e.getY();

                        Rectangle view = viewPort.getViewRect();
                        view.x += deltaX;
                        view.y += deltaY;

                        mazePanel.scrollRectToVisible(view);
                    }
                }
            }
        };

        mazePanel.addMouseListener(mouseHandler);
        mazePanel.addMouseMotionListener(mouseHandler);

        add(mazeScrollPane, BorderLayout.CENTER);

        // ==========================================
        // 4. Bottom: Playback Controls (History)
        // ==========================================
        playbackPanel = new JPanel(new BorderLayout());
        playbackPanel.setBorder(BorderFactory.createTitledBorder("GA History Control"));

        JPanel sliderPanel = new JPanel(new BorderLayout());
        historySlider = new JSlider(0, 0, 0);
        historySlider.setMajorTickSpacing(100);
        historySlider.setPaintTicks(true);
        historySlider.setEnabled(false);
        lblGenCount = new JLabel("Gen: 0 / 0", SwingConstants.CENTER);
        lblGenCount.setPreferredSize(new Dimension(150, 30));

        sliderPanel.add(historySlider, BorderLayout.CENTER);
        sliderPanel.add(lblGenCount, BorderLayout.EAST);

        JPanel btnControlPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        btnPrev100 = new JButton("<<");
        btnPrev = new JButton("<");
        btnNext = new JButton(">");
        btnNext100 = new JButton(">>");

        btnPrev100.addActionListener(e -> jumpHistory(-100));
        btnPrev.addActionListener(e -> jumpHistory(-1));
        btnNext.addActionListener(e -> jumpHistory(1));
        btnNext100.addActionListener(e -> jumpHistory(100));

        btnControlPanel.add(btnPrev100);
        btnControlPanel.add(btnPrev);
        btnControlPanel.add(btnNext);
        btnControlPanel.add(btnNext100);

        playbackPanel.add(sliderPanel, BorderLayout.NORTH);
        playbackPanel.add(btnControlPanel, BorderLayout.CENTER);
        playbackPanel.setVisible(false); // Hide initially

        add(playbackPanel, BorderLayout.SOUTH);

        // ==========================================
        // 5. Right: Logs
        // ==========================================
        JPanel sidePanel = new JPanel(new BorderLayout());
        sidePanel.setPreferredSize(new Dimension(300, 0));

        statusLabel = new JLabel("Status: Ready", SwingConstants.CENTER);
        statusLabel.setFont(new Font("Arial", Font.BOLD, 14));
        statusLabel.setBorder(BorderFactory.createEmptyBorder(10, 0, 10, 0));

        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        JScrollPane logScroll = new JScrollPane(logArea);

        sidePanel.add(statusLabel, BorderLayout.NORTH);
        sidePanel.add(logScroll, BorderLayout.CENTER);
        add(sidePanel, BorderLayout.EAST);

        // ==========================================
        // Action Listeners
        // ==========================================
        btnLoad.addActionListener(e -> loadMapAction());

        // Feature 1: Clear Log
        btnClearLog.addActionListener(e -> {
            logArea.setText("");
            log("Log Cleared.");
        });

        // Feature 7: Settings Dialog
        btnSettings.addActionListener(e -> showSettingsDialog());

        // Feature 2: Run / Stop GA
        btnRunGA.addActionListener(e -> runGA());
        btnStopGA.addActionListener(e -> stopGA());

        // Slider Listener
        historySlider.addChangeListener(e -> {
            if (!historySlider.getValueIsAdjusting() && !gaHistory.isEmpty()) {
                showGASnapshot(historySlider.getValue());
            }
        });

        setVisible(true);
    }

    // --- Feature 7: Settings Dialog ---
    private void showSettingsDialog() {
        JPanel panel = new JPanel(new GridLayout(4, 2, 10, 10));
        JTextField txtPop = new JTextField(String.valueOf(settingPopSize));
        JTextField txtGen = new JTextField(String.valueOf(settingGenerations));
        JTextField txtEli = new JTextField(String.valueOf(settingElitism));
        JTextField txtMut = new JTextField(String.valueOf(settingMutation));

        panel.add(new JLabel("Population Size:"));
        panel.add(txtPop);
        panel.add(new JLabel("Generations:"));
        panel.add(txtGen);
        panel.add(new JLabel("Elitism Count:"));
        panel.add(txtEli);
        panel.add(new JLabel("Mutation Rate (0.0-1.0):"));
        panel.add(txtMut);

        int result = JOptionPane.showConfirmDialog(this, panel, "GA Parameters", JOptionPane.OK_CANCEL_OPTION);
        if (result == JOptionPane.OK_OPTION) {
            try {
                settingPopSize = Integer.parseInt(txtPop.getText());
                settingGenerations = Integer.parseInt(txtGen.getText());
                settingElitism = Integer.parseInt(txtEli.getText());
                settingMutation = Double.parseDouble(txtMut.getText());
                log("Settings Updated: Pop=" + settingPopSize + ", Gen=" + settingGenerations);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Invalid Input!");
            }
        }
    }

    // --- Feature 3: History Logic ---
    private void jumpHistory(int amount) {
        if (gaHistory.isEmpty())
            return;
        int newIdx = currentHistoryIndex + amount;
        if (newIdx < 0)
            newIdx = 0;
        if (newIdx >= gaHistory.size())
            newIdx = gaHistory.size() - 1;
        historySlider.setValue(newIdx);
    }

    private void showGASnapshot(int index) {
        if (index < 0 || index >= gaHistory.size())
            return;
        currentHistoryIndex = index;
        GASnapshot snap = gaHistory.get(index);

        // Update View
        mazePanel.setGAPath(snap.path);
        mazePanel.repaint();

        lblGenCount.setText("Gen: " + snap.gen + " / " + (gaHistory.size() - 1));
        statusLabel.setText("GA Cost: " + snap.cost);
    }

    // --- Logic: Run GA ---
    private void runGA() {
        if (isGARunning)
            return;

        isGARunning = true;
        btnRunGA.setEnabled(false);
        btnStopGA.setEnabled(true);
        btnLoad.setEnabled(false);

        gaHistory.clear();
        playbackPanel.setVisible(true);
        historySlider.setEnabled(false);

        log("--------------------------------");
        log("Starting GA...");
        log("Params: Pop=" + settingPopSize + ", Gen=" + settingGenerations);

        gaThread = new Thread(() -> {
            try {
                // ส่งค่า Settings เข้าไปใน Constructor (คุณต้องไปแก้ GA.java
                // ให้รับค่าพวกนี้ด้วยนะ)
                // ถ้ายังไม่แก้ ให้ใช้ Constructor เดิม: new GA(grid, rows, cols, start, goal);
                GA ga = new GA(grid, rows, cols, start, goal);

                // TODO: ถ้าแก้ GA.java แล้ว ให้ใช้บรรทัดล่างนี้แทน
                ga.setParameters(settingPopSize, settingGenerations, settingElitism, settingMutation);

                ga.setCallback((path, gen, cost, status) -> {
                    // Feature 2: Stop Check
                    if (!isGARunning) {
                        throw new RuntimeException("GA Stopped by User");
                    }

                    synchronized (gaHistory) {
                        gaHistory.add(new GASnapshot(path, gen, cost, status));
                    }

                    SwingUtilities.invokeLater(() -> {
                        mazePanel.setGAPath(path);
                        mazePanel.repaint();
                        lblGenCount.setText("Gen: " + gen);
                        statusLabel.setText("Gen: " + gen + " Cost: " + cost);
                    });
                });

                GA.Result res = ga.run();

                SwingUtilities.invokeLater(() -> finishGA(true, res));

            } catch (RuntimeException e) {
                if (e.getMessage().equals("GA Stopped by User")) {
                    SwingUtilities.invokeLater(() -> {
                        log(">> GA STOPPED BY USER.");
                        finishGA(false, null);
                    });
                } else {
                    e.printStackTrace();
                }
            }
        });
        gaThread.start();
    }

    private void stopGA() {
        if (isGARunning) {
            isGARunning = false; // Flag to stop callback loop
            log("Stopping GA...");
        }
    }

    private void finishGA(boolean success, GA.Result res) {
        isGARunning = false;
        btnRunGA.setEnabled(true);
        btnStopGA.setEnabled(false);
        btnLoad.setEnabled(true);

        historySlider.setMaximum(gaHistory.size() - 1);
        historySlider.setValue(gaHistory.size() - 1);
        historySlider.setEnabled(true);

        if (success && res != null) {
            log("GA Finished. Best Cost: " + res.cost);
            log("Time: " + String.format("%.4f s", res.timeTaken));
        }
    }

    // --- Feature 5: Auto-Run Dijkstra & A* ---
    private void runAutoSolvers() {
        new Thread(() -> {
            log("Auto-running Dijkstra & A*...");

            // 1. Dijkstra
            try {
                Dijkstra dij = new Dijkstra(grid, rows, cols, start, goal);
                Dijkstra.Result dRes = dij.run();
                pathDijkstra = dRes.path;
                SwingUtilities.invokeLater(() -> log(" > Dijkstra Done (Cost: " + dRes.cost + ")"));
            } catch (Exception e) {
                log("Dijkstra Error");
            }

            // 2. A*
            try {
                A_star astar = new A_star(grid, rows, cols, start, goal);
                A_star.Result aRes = astar.run();
                pathAStar = aRes.path;
                SwingUtilities.invokeLater(() -> log(" > A* Done (Cost: " + aRes.cost + ")"));
            } catch (Exception e) {
                log("A* Error");
            }

            SwingUtilities.invokeLater(() -> mazePanel.repaint());
        }).start();
    }

    private void loadMapAction() {
        JFileChooser fileChooser = new JFileChooser(new File("./MAZE"));
        fileChooser.setDialogTitle("Select Maze Text File");

        if (fileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            currentMapFile = fileChooser.getSelectedFile();
            loadAndParseMap(currentMapFile);

            // Reset Data
            pathDijkstra = null;
            pathAStar = null;
            gaHistory.clear();
            mazePanel.setGAPath(null);

            playbackPanel.setVisible(false);
            btnRunGA.setEnabled(true);

            log("================================");
            log("Map Loaded: " + currentMapFile.getName());
            log("Size: " + rows + "x" + cols);

            // Feature 5: Auto Run
            runAutoSolvers();
        }
    }

    private void log(String msg) {
        logArea.append(msg + "\n");
        logArea.setCaretPosition(logArea.getDocument().getLength());
    }

    private void loadAndParseMap(File file) {
        List<String> lines = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (!line.trim().isEmpty())
                    lines.add(line.trim());
            }
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }

        rows = lines.size();
        List<List<Integer>> tempGrid = new ArrayList<>();
        Pattern p = Pattern.compile("\"(\\d+)\"|([#SG])");

        int maxCols = 0;
        for (int r = 0; r < rows; r++) {
            List<Integer> rowData = new ArrayList<>();
            Matcher m = p.matcher(lines.get(r));
            int c = 0;
            while (m.find()) {
                String numStr = m.group(1);
                String charStr = m.group(2);
                int val = 0;
                if (numStr != null)
                    val = Integer.parseInt(numStr);
                else if (charStr != null) {
                    if (charStr.equals("#"))
                        val = -1;
                    else if (charStr.equals("S")) {
                        start[0] = r;
                        start[1] = c;
                        val = 0;
                    } else if (charStr.equals("G")) {
                        goal[0] = r;
                        goal[1] = c;
                        val = 0;
                    }
                }
                rowData.add(val);
                c++;
            }
            maxCols = Math.max(maxCols, c);
            tempGrid.add(rowData);
        }
        cols = maxCols;
        grid = new int[rows][cols];
        for (int r = 0; r < rows; r++) {
            List<Integer> row = tempGrid.get(r);
            for (int c = 0; c < cols; c++)
                grid[r][c] = (c < row.size()) ? row.get(c) : -1;
        }
    }

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
        }
        SwingUtilities.invokeLater(MainGUI::new);
    }

    // --- Custom Panel for Drawing (Zoomable) ---
    class MazePanel extends JPanel {
        private List<int[]> gaPath;

        public void setGAPath(List<int[]> path) {
            this.gaPath = path;
        }

        @Override
        public Dimension getPreferredSize() {
            if (grid == null)
                return new Dimension(800, 600);
            int baseSize = 40;
            int w = (int) (cols * baseSize * zoomFactor) + 100;
            int h = (int) (rows * baseSize * zoomFactor) + 100;
            return new Dimension(w, h);
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (grid == null)
                return;

            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            // Apply Zoom
            g2.scale(zoomFactor, zoomFactor);

            int cellSize = 40;
            int xOffset = 20;
            int yOffset = 20;

            Font weightFont = new Font("SansSerif", Font.PLAIN, (int) (cellSize * 0.4));
            g2.setFont(weightFont);
            FontMetrics fm = g2.getFontMetrics();

            // 1. Draw Grid
            for (int r = 0; r < rows; r++) {
                for (int c = 0; c < cols; c++) {
                    int x = xOffset + c * cellSize;
                    int y = yOffset + r * cellSize;

                    if (grid[r][c] == -1) {
                        g2.setColor(new Color(60, 63, 65)); // Dark Grey Wall
                        g2.fillRect(x, y, cellSize, cellSize);
                    } else {
                        g2.setColor(Color.WHITE);
                        g2.fillRect(x, y, cellSize, cellSize);
                        g2.setColor(new Color(220, 220, 220));
                        g2.drawRect(x, y, cellSize, cellSize);

                        if (chkShowWeights.isSelected() && grid[r][c] > 0) {
                            g2.setColor(Color.GRAY);
                            String s = String.valueOf(grid[r][c]);
                            int textW = fm.stringWidth(s);
                            int textH = fm.getAscent();
                            g2.drawString(s, x + (cellSize - textW) / 2, y + (cellSize + textH) / 2 - 2);
                        }
                    }

                    if (r == start[0] && c == start[1]) {
                        g2.setColor(new Color(46, 204, 113)); // Green
                        g2.fillRect(x + 3, y + 3, cellSize - 6, cellSize - 6);
                        g2.setColor(Color.WHITE);
                        g2.drawString("S", x + cellSize / 3, y + 2 * cellSize / 3);
                    } else if (r == goal[0] && c == goal[1]) {
                        g2.setColor(new Color(231, 76, 60)); // Red
                        g2.fillRect(x + 3, y + 3, cellSize - 6, cellSize - 6);
                        g2.setColor(Color.WHITE);
                        g2.drawString("G", x + cellSize / 3, y + 2 * cellSize / 3);
                    }
                }
            }

            // Feature 4: Overlays
            if (chkOverlayDijkstra.isSelected() && pathDijkstra != null) {
                drawPath(g2, pathDijkstra, new Color(155, 89, 182, 120), cellSize, xOffset, yOffset, 10); // Purple
            }
            if (chkOverlayAStar.isSelected() && pathAStar != null) {
                drawPath(g2, pathAStar, new Color(243, 156, 18, 120), cellSize, xOffset, yOffset, 15); // Orange
            }
            if (chkOverlayGA.isSelected() && gaPath != null) {
                drawPath(g2, gaPath, new Color(52, 152, 219, 200), cellSize, xOffset, yOffset, 7); // Blue
            }
        }

        private void drawPath(Graphics2D g2, List<int[]> path, Color c, int cellSize, int xOff, int yOff,
                int thickness) {
            if (path.isEmpty())
                return;
            g2.setColor(c);
            g2.setStroke(new BasicStroke(thickness, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

            int[] xPoints = new int[path.size()];
            int[] yPoints = new int[path.size()];

            for (int i = 0; i < path.size(); i++) {
                // path เก็บ {row, col} -> x=col, y=row
                xPoints[i] = xOff + path.get(i)[1] * cellSize + cellSize / 2;
                yPoints[i] = yOff + path.get(i)[0] * cellSize + cellSize / 2;
            }
            g2.drawPolyline(xPoints, yPoints, path.size());
        }
    }
}