import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
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
    private JTextArea logArea;
    private JLabel statusLabel;
    
    // Control Buttons
    private JButton btnLoad, btnDijkstra, btnAStar, btnGA;
    
    // Visualization Options
    private JCheckBox chkShowWeights;
    private JCheckBox chkOverlayDijkstra;
    private JCheckBox chkOverlayAStar;

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
    
    // GA History (Time Machine)
    // เก็บ Snapshot ของทุก Gen ไว้: [Path, GenNumber, Cost]
    private static class GASnapshot {
        List<int[]> path;
        int gen;
        int cost;
        String status;
        public GASnapshot(List<int[]> p, int g, int c, String s) {
            this.path = (p != null) ? new ArrayList<>(p) : null;
            this.gen = g;
            this.cost = c;
            this.status = s;
        }
    }
    private List<GASnapshot> gaHistory = new ArrayList<>();
    private int currentHistoryIndex = -1;
    private boolean isGARunning = false;

    public MainGUI() {
        setTitle("Maze Solver Ultimate (Weight + Playback + Overlay)");
        setSize(1200, 800);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        // 1. Top Panel: Map Controls & Options
        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        btnLoad = new JButton("📂 Load Map");
        btnDijkstra = new JButton("Run Dijkstra");
        btnAStar = new JButton("Run A*");
        btnGA = new JButton("🧬 Run GA");
        
        btnDijkstra.setEnabled(false);
        btnAStar.setEnabled(false);
        btnGA.setEnabled(false);

        // Checkboxes
        chkShowWeights = new JCheckBox("Show Weights");
        chkOverlayDijkstra = new JCheckBox("Overlay Dijkstra");
        chkOverlayAStar = new JCheckBox("Overlay A*");
        
        chkShowWeights.addActionListener(e -> mazePanel.repaint());
        chkOverlayDijkstra.addActionListener(e -> mazePanel.repaint());
        chkOverlayAStar.addActionListener(e -> mazePanel.repaint());

        topPanel.add(btnLoad);
        topPanel.add(new JSeparator(SwingConstants.VERTICAL));
        topPanel.add(btnDijkstra);
        topPanel.add(btnAStar);
        topPanel.add(new JSeparator(SwingConstants.VERTICAL));
        topPanel.add(btnGA);
        topPanel.add(new JSeparator(SwingConstants.VERTICAL));
        topPanel.add(chkShowWeights);
        topPanel.add(chkOverlayDijkstra);
        topPanel.add(chkOverlayAStar);
        
        add(topPanel, BorderLayout.NORTH);

        // 2. Center: Maze Panel
        mazePanel = new MazePanel();
        add(mazePanel, BorderLayout.CENTER);

        // 3. Bottom: Playback Controls (Time Machine)
        playbackPanel = new JPanel(new BorderLayout());
        playbackPanel.setBorder(BorderFactory.createTitledBorder("GA Time Machine"));
        
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
        btnPrev100 = new JButton("<< -100");
        btnPrev = new JButton("< Prev");
        btnNext = new JButton("Next >");
        btnNext100 = new JButton("+100 >>");
        
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
        
        // ซ่อนไว้ก่อน จะโชว์เมื่อรัน GA
        playbackPanel.setVisible(false); 
        add(playbackPanel, BorderLayout.SOUTH);

        // 4. Right: Logs
        JPanel sidePanel = new JPanel(new BorderLayout());
        sidePanel.setPreferredSize(new Dimension(280, 0));
        
        statusLabel = new JLabel("Status: Ready", SwingConstants.CENTER);
        statusLabel.setFont(new Font("Arial", Font.BOLD, 14));
        statusLabel.setBorder(BorderFactory.createEmptyBorder(10, 0, 10, 0));
        
        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        JScrollPane scrollPane = new JScrollPane(logArea);
        
        sidePanel.add(statusLabel, BorderLayout.NORTH);
        sidePanel.add(scrollPane, BorderLayout.CENTER);
        add(sidePanel, BorderLayout.EAST);

        // 5. Main Action Listeners
        btnLoad.addActionListener(e -> loadMapAction());
        btnDijkstra.addActionListener(e -> runAlgorithm("Dijkstra"));
        btnAStar.addActionListener(e -> runAlgorithm("A*"));
        btnGA.addActionListener(e -> runAlgorithm("GA"));

        // Slider Listener
        historySlider.addChangeListener(new ChangeListener() {
            @Override
            public void stateChanged(ChangeEvent e) {
                if (!historySlider.getValueIsAdjusting() && !gaHistory.isEmpty()) {
                    int idx = historySlider.getValue();
                    showGASnapshot(idx);
                }
            }
        });

        setVisible(true);
    }

    // --- History / Playback Logic ---
    private void jumpHistory(int amount) {
        if (gaHistory.isEmpty()) return;
        int newIdx = currentHistoryIndex + amount;
        if (newIdx < 0) newIdx = 0;
        if (newIdx >= gaHistory.size()) newIdx = gaHistory.size() - 1;
        
        historySlider.setValue(newIdx); // This triggers the listener
        showGASnapshot(newIdx);
    }

    private void showGASnapshot(int index) {
        if (index < 0 || index >= gaHistory.size()) return;
        currentHistoryIndex = index;
        GASnapshot snap = gaHistory.get(index);
        
        mazePanel.setMainPath(snap.path, Color.BLUE);
        mazePanel.repaint();
        
        lblGenCount.setText("Gen: " + snap.gen + " / " + (gaHistory.size()-1));
        statusLabel.setText("Cost: " + snap.cost + " | " + snap.status);
    }

    // --- Algorithm Runners ---
    private void runAlgorithm(String algName) {
        if (isGARunning) return; // Prevent double click

        statusLabel.setText("Running " + algName + "...");
        log("--------------------------------");
        log("Starting " + algName + "...");

        new Thread(() -> {
            try {
                if (algName.equals("Dijkstra")) {
                    Dijkstra dij = new Dijkstra(grid, rows, cols, start, goal);
                    long t1 = System.nanoTime();
                    Dijkstra.Result res = dij.run();
                    double time = (System.nanoTime() - t1) / 1e9;
                    
                    pathDijkstra = res.path; // Store for overlay
                    updateGUIResult(res.path, res.cost, time, res.success, Color.MAGENTA);
                    
                } else if (algName.equals("A*")) {
                    A_star astar = new A_star(grid, rows, cols, start, goal);
                    long t1 = System.nanoTime();
                    A_star.Result res = astar.run();
                    double time = (System.nanoTime() - t1) / 1e9;
                    
                    pathAStar = res.path; // Store for overlay
                    updateGUIResult(res.path, res.cost, time, res.success, Color.ORANGE);
                    
                } else if (algName.equals("GA")) {
                    isGARunning = true;
                    gaHistory.clear(); // Reset History
                    SwingUtilities.invokeLater(() -> {
                        playbackPanel.setVisible(true);
                        historySlider.setEnabled(false); // Disable user scrub while running
                        btnLoad.setEnabled(false);
                    });

                    GA ga = new GA(grid, rows, cols, start, goal);
                    
                    // Callback: บันทึกประวัติทุกๆ Gen ที่มีการอัปเดต
                    ga.setCallback((path, gen, cost, status) -> {
                        // Add to History (Deep Copy of path is important!)
                        synchronized(gaHistory) {
                            gaHistory.add(new GASnapshot(path, gen, cost, status));
                        }
                        
                        // Update UI (Realtime view)
                        SwingUtilities.invokeLater(() -> {
                            mazePanel.setMainPath(path, Color.BLUE);
                            mazePanel.repaint();
                            lblGenCount.setText("Gen: " + gen);
                            statusLabel.setText("Cost: " + cost);
                        });
                    });

                    GA.Result res = ga.run();
                    
                    SwingUtilities.invokeLater(() -> {
                         log("GA Final Cost: " + res.cost);
                         log("Time: " + String.format("%.4f s", res.timeTaken));
                         statusLabel.setText("GA Finished.");
                         
                         // Enable Playback
                         historySlider.setMaximum(gaHistory.size() - 1);
                         historySlider.setValue(gaHistory.size() - 1);
                         historySlider.setEnabled(true);
                         btnLoad.setEnabled(true);
                         isGARunning = false;
                    });
                }
            } catch (Exception e) {
                e.printStackTrace();
                isGARunning = false;
                SwingUtilities.invokeLater(() -> {
                    log("Error: " + e.getMessage());
                    btnLoad.setEnabled(true);
                });
            }
        }).start();
    }

    private void updateGUIResult(List<int[]> path, int cost, double time, boolean success, Color c) {
        SwingUtilities.invokeLater(() -> {
            if (success) {
                mazePanel.setMainPath(path, c);
                mazePanel.repaint();
                log("Success! Cost: " + cost);
                log("Time: " + String.format("%.4f s", time));
                statusLabel.setText("Finished. Cost: " + cost);
            } else {
                log("Failed to find path.");
                statusLabel.setText("Failed.");
            }
        });
    }

    private void log(String msg) {
        logArea.append(msg + "\n");
        logArea.setCaretPosition(logArea.getDocument().getLength());
    }

    private void loadMapAction() {
        JFileChooser fileChooser = new JFileChooser(new File(".")); 
        fileChooser.setDialogTitle("Select Maze Text File");
        
        if (fileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            currentMapFile = fileChooser.getSelectedFile();
            loadAndParseMap(currentMapFile);
            
            // Reset Data
            pathDijkstra = null;
            pathAStar = null;
            gaHistory.clear();
            mazePanel.setMainPath(null, Color.BLACK);
            mazePanel.repaint();
            playbackPanel.setVisible(false);
            
            btnDijkstra.setEnabled(true);
            btnAStar.setEnabled(true);
            btnGA.setEnabled(true);
            
            log("Map Loaded: " + currentMapFile.getName());
            log("Size: " + rows + "x" + cols);
        }
    }

    // --- Map Parsing ---
    private void loadAndParseMap(File file) {
        List<String> lines = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (!line.trim().isEmpty()) lines.add(line.trim());
            }
        } catch (IOException e) { e.printStackTrace(); return; }

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
                if (numStr != null) val = Integer.parseInt(numStr);
                else if (charStr != null) {
                    if (charStr.equals("#")) val = -1;
                    else if (charStr.equals("S")) { start[0] = r; start[1] = c; val = 0; }
                    else if (charStr.equals("G")) { goal[0] = r; goal[1] = c; val = 0; }
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
            for (int c = 0; c < cols; c++) grid[r][c] = (c < row.size()) ? row.get(c) : -1;
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(MainGUI::new);
    }

    // --- Custom Panel for Drawing ---
    class MazePanel extends JPanel {
        private List<int[]> mainPath;
        private Color mainPathColor = Color.BLUE;

        public void setMainPath(List<int[]> path, Color c) {
            this.mainPath = path;
            this.mainPathColor = c;
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (grid == null) return;

            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int w = getWidth();
            int h = getHeight();
            int cellSize = Math.min(w / cols, h / rows);
            if(cellSize > 60) cellSize = 60; // Max cell size
            
            int xOffset = (w - (cols * cellSize)) / 2;
            int yOffset = (h - (rows * cellSize)) / 2;
            
            // Font for weights
            Font weightFont = new Font("SansSerif", Font.PLAIN, cellSize / 2);
            g2.setFont(weightFont);
            FontMetrics fm = g2.getFontMetrics();

            // 1. Draw Grid
            for (int r = 0; r < rows; r++) {
                for (int c = 0; c < cols; c++) {
                    int x = xOffset + c * cellSize;
                    int y = yOffset + r * cellSize;
                    
                    if (grid[r][c] == -1) {
                        g2.setColor(Color.DARK_GRAY);
                        g2.fillRect(x, y, cellSize, cellSize);
                    } else {
                        g2.setColor(Color.WHITE);
                        g2.fillRect(x, y, cellSize, cellSize);
                        g2.setColor(new Color(230,230,230));
                        g2.drawRect(x, y, cellSize, cellSize);

                        // [FEATURE 1] Draw Weights
                        if (chkShowWeights.isSelected() && grid[r][c] > 0) {
                            g2.setColor(Color.GRAY);
                            String s = String.valueOf(grid[r][c]);
                            int textW = fm.stringWidth(s);
                            int textH = fm.getAscent();
                            g2.drawString(s, x + (cellSize - textW)/2, y + (cellSize + textH)/2 - 2);
                        }
                    }
                    
                    // Start / Goal
                    if (r == start[0] && c == start[1]) {
                        g2.setColor(new Color(50, 205, 50)); // Green
                        g2.fillRect(x+2, y+2, cellSize-4, cellSize-4);
                        g2.setColor(Color.BLACK);
                        g2.drawString("S", x+cellSize/3, y+2*cellSize/3);
                    } else if (r == goal[0] && c == goal[1]) {
                        g2.setColor(new Color(220, 20, 60)); // Red
                        g2.fillRect(x+2, y+2, cellSize-4, cellSize-4);
                        g2.setColor(Color.WHITE);
                        g2.drawString("G", x+cellSize/3, y+2*cellSize/3);
                    }
                }
            }

            // [FEATURE 2] Draw Ghost Overlays (Dijkstra / A*)
            if (chkOverlayDijkstra.isSelected() && pathDijkstra != null) {
                drawPath(g2, pathDijkstra, new Color(255, 0, 255, 100), cellSize, xOffset, yOffset, 6); // Magenta Ghost
            }
            if (chkOverlayAStar.isSelected() && pathAStar != null) {
                drawPath(g2, pathAStar, new Color(255, 165, 0, 100), cellSize, xOffset, yOffset, 4); // Orange Ghost
            }

            // 3. Draw Main Path (GA or Active Algo)
            if (mainPath != null && mainPath.size() > 1) {
                drawPath(g2, mainPath, mainPathColor, cellSize, xOffset, yOffset, 3);
            }
        }

        private void drawPath(Graphics2D g2, List<int[]> path, Color c, int cellSize, int xOff, int yOff, int thickness) {
            g2.setColor(c);
            g2.setStroke(new BasicStroke(thickness, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            
            // Draw as polyline for better performance and looks
            int[] xPoints = new int[path.size()];
            int[] yPoints = new int[path.size()];

            for (int i = 0; i < path.size(); i++) {
                xPoints[i] = xOff + path.get(i)[1] * cellSize + cellSize / 2;
                yPoints[i] = yOff + path.get(i)[0] * cellSize + cellSize / 2;
            }
            g2.drawPolyline(xPoints, yPoints, path.size());
        }
    }
}