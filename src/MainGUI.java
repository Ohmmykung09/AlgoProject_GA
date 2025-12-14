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

    private MazePanel mazePanel;
    private JScrollPane mazeScrollPane;
    private JTextArea logArea;
    private JLabel statusLabel;
    
    private JButton btnLoad, btnSettings, btnClearLog, btnRunGA, btnStopGA;
    
    private JRadioButton rbPureGA;
    private JRadioButton rbCustomGA;
    private JCheckBox chkOptShortcut;
    private JCheckBox chkOptLoopCut;
    private JCheckBox chkOptBacktrack;
    
    private JCheckBox chkOptRefineChildren; 
    private JCheckBox chkOptSmoothPath;    

    private JCheckBox chkShowWeights;
    private JCheckBox chkOverlayDijkstra;
    private JCheckBox chkOverlayAStar;
    private JCheckBox chkOverlayGA;
    private JCheckBox chkShowDeadEnds;

    private JSlider historySlider;
    private JButton btnPrev100, btnPrev, btnNext, btnNext100;
    private JLabel lblGenCount;
    private JPanel playbackPanel;

    private int[][] grid;
    private int rows, cols;
    private int[] start = new int[2];
    private int[] goal = new int[2];
    private File currentMapFile;

    private List<int[]> pathDijkstra = null;
    private List<int[]> pathAStar = null;
    
    private static class GASnapshot {
        List<int[]> path;
        List<int[]> deadEnds;
        int gen;
        int cost;
        double time;
        String status;
        public GASnapshot(List<int[]> p, int g, int c, double t, String s, List<int[]> de) {
            this.path = (p != null) ? new ArrayList<>(p) : null;
            this.deadEnds = (de != null) ? new ArrayList<>(de) : null;
            this.gen = g;
            this.cost = c;
            this.time = t;
            this.status = s;
        }
    }
    private List<GASnapshot> gaHistory = new ArrayList<>();
    private int currentHistoryIndex = -1;
    
    private Thread gaThread;
    private volatile boolean isGARunning = false;
    private volatile int currentBestCost = Integer.MAX_VALUE;
    
    // [ADDED] ตัวแปรเก็บสถิติที่ดีที่สุดสำหรับการแสดงผลไม่ให้กระพริบ
    private int globalMinCost = Integer.MAX_VALUE;

    private int settingPopSize = 500;
    private int settingGenerations = 1000;
    private int settingElitism = 50;
    private double settingMutation = 0.05;

    private double zoomFactor = 1.0;

    // MainGUI
    public MainGUI() {
        setTitle("Maze Solver MFK");
        setSize(1280, 850);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        JPanel topPanel = new JPanel();
        topPanel.setLayout(new BoxLayout(topPanel, BoxLayout.Y_AXIS));
        
        JPanel row1 = new JPanel(new FlowLayout(FlowLayout.LEFT));
        btnLoad = new JButton("Load Map");
        btnSettings = new JButton("GA Settings");
        btnClearLog = new JButton("Clear Log");
        btnRunGA = new JButton("Run GA");
        btnStopGA = new JButton("Stop GA");
        
        row1.add(btnLoad);
        row1.add(new JSeparator(SwingConstants.VERTICAL));
        row1.add(btnSettings);
        row1.add(btnClearLog);
        row1.add(new JSeparator(SwingConstants.VERTICAL));
        row1.add(btnRunGA);
        row1.add(btnStopGA);
        
        JPanel row2 = new JPanel(new FlowLayout(FlowLayout.LEFT));
        row2.setBorder(BorderFactory.createTitledBorder("Algorithm Mode"));
        
        rbPureGA = new JRadioButton("Pure GA");
        rbCustomGA = new JRadioButton("Custom Mode", true);
        ButtonGroup modeGroup = new ButtonGroup();
        modeGroup.add(rbPureGA);
        modeGroup.add(rbCustomGA);
        
        chkOptShortcut = new JCheckBox("Shortcut");
        chkOptLoopCut = new JCheckBox("Loop Cut");
        chkOptBacktrack = new JCheckBox("Backtrack");
        
        chkOptRefineChildren = new JCheckBox("Refine Children");
        chkOptSmoothPath = new JCheckBox("Smooth Path");
        
        chkOptShortcut.setSelected(true);
        chkOptLoopCut.setSelected(true);
        chkOptBacktrack.setSelected(true);
        chkOptRefineChildren.setSelected(true);
        chkOptSmoothPath.setSelected(true);
        
        ActionListener modeListener = e -> {
            boolean isCustom = rbCustomGA.isSelected();
            chkOptShortcut.setEnabled(isCustom);
            chkOptLoopCut.setEnabled(isCustom);
            chkOptBacktrack.setEnabled(isCustom);
            chkOptRefineChildren.setEnabled(isCustom);
            chkOptSmoothPath.setEnabled(isCustom);
        };
        rbPureGA.addActionListener(modeListener);
        rbCustomGA.addActionListener(modeListener);

        row2.add(rbPureGA);
        row2.add(Box.createHorizontalStrut(20));
        row2.add(rbCustomGA);
        row2.add(new JSeparator(SwingConstants.VERTICAL));
        row2.add(chkOptShortcut);
        row2.add(chkOptLoopCut);
        row2.add(chkOptBacktrack);
        row2.add(new JSeparator(SwingConstants.VERTICAL));
        row2.add(chkOptRefineChildren);
        row2.add(chkOptSmoothPath);

        topPanel.add(row1);
        topPanel.add(row2);

        add(topPanel, BorderLayout.NORTH);

        JPanel leftPanel = new JPanel();
        leftPanel.setLayout(new BoxLayout(leftPanel, BoxLayout.Y_AXIS));
        leftPanel.setBorder(BorderFactory.createTitledBorder("Layers"));
        
        chkShowWeights = new JCheckBox("Show Weights");
        chkOverlayDijkstra = new JCheckBox("Dijkstra (Purple)");
        chkOverlayAStar = new JCheckBox("A* (Orange)");
        chkOverlayGA = new JCheckBox("GA Path (Blue)");
        chkShowDeadEnds = new JCheckBox("Dead Ends (Red X)");
        
        chkShowWeights.setSelected(true);
        chkOverlayDijkstra.setSelected(true);
        chkOverlayAStar.setSelected(true);
        chkOverlayGA.setSelected(true);
        chkShowDeadEnds.setSelected(true);

        ActionListener repaintAction = e -> mazePanel.repaint();
        chkShowWeights.addActionListener(repaintAction);
        chkOverlayDijkstra.addActionListener(repaintAction);
        chkOverlayAStar.addActionListener(repaintAction);
        chkOverlayGA.addActionListener(repaintAction);
        chkShowDeadEnds.addActionListener(repaintAction);

        leftPanel.add(chkShowWeights);
        leftPanel.add(chkOverlayDijkstra);
        leftPanel.add(chkOverlayAStar);
        leftPanel.add(chkOverlayGA);
        leftPanel.add(chkShowDeadEnds);
        leftPanel.add(Box.createVerticalGlue()); 

        add(leftPanel, BorderLayout.WEST);

        mazePanel = new MazePanel();
        mazeScrollPane = new JScrollPane(mazePanel);
        
        mazePanel.addMouseWheelListener(e -> {
            double rotation = e.getPreciseWheelRotation();
            double factor = 1.0 + (Math.abs(rotation) * 0.01);
            if (rotation < 0) zoomFactor *= factor;
            else zoomFactor /= factor;
            if (zoomFactor < 0.05) zoomFactor = 0.05;
            if (zoomFactor > 20.0) zoomFactor = 20.0; 
            mazePanel.revalidate(); mazePanel.repaint();
        });
        
        MouseAdapter mouseHandler = new MouseAdapter() {
            private Point origin; 
            @Override
            public void mousePressed(MouseEvent e) { origin = new Point(e.getPoint()); mazePanel.setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR)); }
            @Override
            public void mouseReleased(MouseEvent e) { mazePanel.setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR)); }
            @Override
            public void mouseDragged(MouseEvent e) {
                if (origin != null) {
                    JViewport viewPort = (JViewport) SwingUtilities.getAncestorOfClass(JViewport.class, mazePanel);
                    if (viewPort != null) {
                        int deltaX = origin.x - e.getX();
                        int deltaY = origin.y - e.getY();
                        Rectangle view = viewPort.getViewRect();
                        view.x += deltaX; view.y += deltaY;
                        mazePanel.scrollRectToVisible(view);
                    }
                }
            }
        };
        mazePanel.addMouseListener(mouseHandler);      
        mazePanel.addMouseMotionListener(mouseHandler); 
        
        add(mazeScrollPane, BorderLayout.CENTER);

        playbackPanel = new JPanel(new BorderLayout());
        playbackPanel.setBorder(BorderFactory.createTitledBorder("GA Timeline"));
        
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
        btnPrev100 = new JButton("<<"); btnPrev = new JButton("<");
        btnNext = new JButton(">"); btnNext100 = new JButton(">>");
        
        btnPrev100.addActionListener(e -> jumpHistory(-100));
        btnPrev.addActionListener(e -> jumpHistory(-1));
        btnNext.addActionListener(e -> jumpHistory(1));
        btnNext100.addActionListener(e -> jumpHistory(100));

        btnControlPanel.add(btnPrev100); btnControlPanel.add(btnPrev);
        btnControlPanel.add(btnNext); btnControlPanel.add(btnNext100);

        playbackPanel.add(sliderPanel, BorderLayout.NORTH);
        playbackPanel.add(btnControlPanel, BorderLayout.CENTER);
        playbackPanel.setVisible(false);
        
        add(playbackPanel, BorderLayout.SOUTH);

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

        btnLoad.addActionListener(e -> loadMapAction());
        btnClearLog.addActionListener(e -> { logArea.setText(""); log("Log Cleared."); });
        btnSettings.addActionListener(e -> showSettingsDialog());
        btnRunGA.addActionListener(e -> runGA());
        btnStopGA.addActionListener(e -> stopGA());

        historySlider.addChangeListener(e -> {
            if (!historySlider.getValueIsAdjusting() && !gaHistory.isEmpty()) showGASnapshot(historySlider.getValue());
        });

        setVisible(true);
    }

    // showSettingsDialog
    private void showSettingsDialog() {
        JPanel panel = new JPanel(new GridLayout(4, 2, 10, 10));
        JTextField txtPop = new JTextField(String.valueOf(settingPopSize));
        JTextField txtGen = new JTextField(String.valueOf(settingGenerations));
        JTextField txtEli = new JTextField(String.valueOf(settingElitism));
        JTextField txtMut = new JTextField(String.valueOf(settingMutation));
        panel.add(new JLabel("Population:")); panel.add(txtPop);
        panel.add(new JLabel("Generations:")); panel.add(txtGen);
        panel.add(new JLabel("Elitism:")); panel.add(txtEli);
        panel.add(new JLabel("Mutation (0-1):")); panel.add(txtMut);

        if (JOptionPane.showConfirmDialog(this, panel, "Settings", JOptionPane.OK_CANCEL_OPTION) == JOptionPane.OK_OPTION) {
            try {
                settingPopSize = Integer.parseInt(txtPop.getText());
                settingGenerations = Integer.parseInt(txtGen.getText());
                settingElitism = Integer.parseInt(txtEli.getText());
                settingMutation = Double.parseDouble(txtMut.getText());
                log("Settings: Pop=" + settingPopSize + ", Gen=" + settingGenerations + ", Mut=" + settingMutation);
            } catch (Exception ex) { JOptionPane.showMessageDialog(this, "Invalid Input!"); }
        }
    }

    // jumpHistory
    private void jumpHistory(int amount) {
        if (gaHistory.isEmpty()) return;
        int newIdx = currentHistoryIndex + amount;
        if (newIdx < 0) newIdx = 0;
        if (newIdx >= gaHistory.size()) newIdx = gaHistory.size() - 1;
        historySlider.setValue(newIdx);
    }

    // showGASnapshot
    private void showGASnapshot(int index) {
        if (index < 0 || index >= gaHistory.size()) return;
        currentHistoryIndex = index;
        GASnapshot snap = gaHistory.get(index);
        mazePanel.setGAPath(snap.path);
        mazePanel.setDeadEnds(snap.deadEnds);
        mazePanel.repaint();
        lblGenCount.setText("Gen: " + snap.gen + " / " + (gaHistory.size()));
        statusLabel.setText(String.format("Cost: %d | Time: %.3fs", snap.cost, snap.time));
    }

    // runGA
    private void runGA() {
        if (isGARunning) return;
        isGARunning = true;
        btnRunGA.setEnabled(false);
        btnStopGA.setEnabled(true);
        btnLoad.setEnabled(false);
        gaHistory.clear();
        playbackPanel.setVisible(true);
        historySlider.setEnabled(false);
        
        currentBestCost = Integer.MAX_VALUE; 
        globalMinCost = Integer.MAX_VALUE; // [MODIFIED] Reset best stat

        log("--- GA Started ---");
        boolean pureMode = rbPureGA.isSelected();
        if (!pureMode) {
            log("Config: SC=" + chkOptShortcut.isSelected() + 
                ", Loop=" + chkOptLoopCut.isSelected() + 
                ", Back=" + chkOptBacktrack.isSelected() + 
                ", Refine=" + chkOptRefineChildren.isSelected() +
                ", Smooth=" + chkOptSmoothPath.isSelected());
        }

        gaThread = new Thread(() -> {
            try {
                GA ga = new GA(grid, rows, cols, start, goal); 
                ga.setParameters(settingPopSize, settingGenerations, settingElitism, settingMutation);
                
                if (pureMode) {
                    ga.setHeuristics(false, false, false);
                    ga.setMemeticSettings(false, false);
                } else {
                    ga.setHeuristics(
                        chkOptShortcut.isSelected(),
                        chkOptLoopCut.isSelected(),
                        chkOptBacktrack.isSelected()
                    );
                    ga.setMemeticSettings(
                        chkOptSmoothPath.isSelected(),
                        chkOptRefineChildren.isSelected()
                    );
                }

                ga.setCallback((path, gen, cost, timeElapsed, status, deadEnds, isNewBest) -> {
                    if (!isGARunning) throw new RuntimeException("GA_STOPPED");

                    currentBestCost = cost;
                    
                    // [MODIFIED] Check visual improvement
                    boolean isVisuallyBetter = cost <= globalMinCost;
                    if (isVisuallyBetter) {
                        globalMinCost = cost;
                    }

                    synchronized(gaHistory) {
                        gaHistory.add(new GASnapshot(path, gen, cost, timeElapsed, status, deadEnds));
                    }
                    
                    SwingUtilities.invokeLater(() -> {
                        if (isNewBest || gen == 1) {
                            String logMsg = String.format("Gen %-4d | Cost: %-5d | Time: %.3fs | %s", 
                                                          gen, cost, timeElapsed, status);
                            log(logMsg);
                        }
                        
                        // Update Gen count always for smooth flow
                        lblGenCount.setText("Gen: " + gen);

                        // [MODIFIED] Update visual only if better or equal
                        if (isVisuallyBetter) {
                            mazePanel.setGAPath(path);
                            mazePanel.setDeadEnds(deadEnds);
                            mazePanel.repaint();
                            statusLabel.setText(String.format("Gen: %d Cost: %d Time: %.2fs", gen, cost, timeElapsed));
                        } else {
                            // Show current gen/time but keep best cost
                            statusLabel.setText(String.format("Gen: %d Cost: %d Time: %.2fs", gen, globalMinCost, timeElapsed));
                        }
                    });
                });

                GA.Result res = ga.run();
                SwingUtilities.invokeLater(() -> finishGA(true, res, 0, 0));

            } catch (RuntimeException e) {
                if (e.getMessage().equals("GA_STOPPED")) {
                } else {
                    e.printStackTrace();
                }
            }
        });
        gaThread.start();
    }

    // stopGA
    private void stopGA() {
        if (isGARunning) {
            isGARunning = false;
            if (gaThread != null) gaThread.interrupt();
            
            SwingUtilities.invokeLater(() -> {
                log(">> GA STOPPED BY USER.");
                log(">> Last Best Cost found: " + currentBestCost);
                finishGA(false, null, 0, currentBestCost);
            });
        }
    }

    // finishGA
    private void finishGA(boolean completedNormal, GA.Result res, double timeOverride, int costOverride) {
        isGARunning = false;
        btnRunGA.setEnabled(true);
        btnStopGA.setEnabled(false);
        btnLoad.setEnabled(true);
        
        historySlider.setMaximum(gaHistory.size() - 1);
        historySlider.setValue(gaHistory.size() - 1);
        historySlider.setEnabled(true);

        if (completedNormal && res != null) {
            log("=== FINISHED ===");
            log("Total Time: " + String.format("%.4f s", res.timeTaken));
            log("Final Cost: " + res.cost);
        }
    }

    // runAutoSolvers
    private void runAutoSolvers() {
        new Thread(() -> {
            log("Auto-running Dijkstra & A*...");
            try {
                Dijkstra dij = new Dijkstra(grid, rows, cols, start, goal);
                Dijkstra.Result dRes = dij.run();
                pathDijkstra = dRes.path;
                SwingUtilities.invokeLater(() -> log(" > Dijkstra: " + dRes.cost));
            } catch (Exception e) {}

            try {
                A_star astar = new A_star(grid, rows, cols, start, goal);
                A_star.Result aRes = astar.run();
                pathAStar = aRes.path;
                SwingUtilities.invokeLater(() -> log(" > A*: " + aRes.cost));
            } catch (Exception e) {}

            SwingUtilities.invokeLater(() -> mazePanel.repaint());
        }).start();
    }

    // loadMapAction
    private void loadMapAction() {
        JFileChooser fileChooser = new JFileChooser(new File("./MAZE")); 
        fileChooser.setDialogTitle("Select Maze Text File");
        if (fileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            currentMapFile = fileChooser.getSelectedFile();
            loadAndParseMap(currentMapFile);
            pathDijkstra = null; pathAStar = null;
            gaHistory.clear();
            mazePanel.setGAPath(null); mazePanel.setDeadEnds(null);
            playbackPanel.setVisible(false);
            btnRunGA.setEnabled(true);
            log("Loaded: " + currentMapFile.getName() + " (" + rows + "x" + cols + ")");
            runAutoSolvers();
        }
    }

    // log
    private void log(String msg) {
        logArea.append(msg + "\n");
        logArea.setCaretPosition(logArea.getDocument().getLength());
    }

    // loadAndParseMap
    private void loadAndParseMap(File file) {
        List<String> lines = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = br.readLine()) != null) if (!line.trim().isEmpty()) lines.add(line.trim());
        } catch (IOException e) { return; }

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
                rowData.add(val); c++;
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

    // main
    public static void main(String[] args) {
        try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); } catch (Exception ignored) {}
        SwingUtilities.invokeLater(MainGUI::new);
    }

    class MazePanel extends JPanel {
        private List<int[]> gaPath;
        private List<int[]> deadEnds;

        public void setGAPath(List<int[]> path) { this.gaPath = path; }
        public void setDeadEnds(List<int[]> deadEnds) { this.deadEnds = deadEnds; }
        
        @Override
        public Dimension getPreferredSize() {
            if (grid == null) return new Dimension(800, 600);
            return new Dimension((int)(cols * 40 * zoomFactor) + 100, (int)(rows * 40 * zoomFactor) + 100);
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (grid == null) return;
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.scale(zoomFactor, zoomFactor);

            int cellSize = 40; int xOff = 20; int yOff = 20;
            Font weightFont = new Font("SansSerif", Font.PLAIN, (int)(cellSize * 0.4));
            g2.setFont(weightFont);
            FontMetrics fm = g2.getFontMetrics();

            for (int r = 0; r < rows; r++) {
                for (int c = 0; c < cols; c++) {
                    int x = xOff + c * cellSize; int y = yOff + r * cellSize;
                    if (grid[r][c] == -1) {
                        g2.setColor(new Color(60, 63, 65));
                        g2.fillRect(x, y, cellSize, cellSize);
                    } else {
                        g2.setColor(Color.WHITE);
                        g2.fillRect(x, y, cellSize, cellSize);
                        g2.setColor(new Color(220, 220, 220));
                        g2.drawRect(x, y, cellSize, cellSize);
                        if (chkShowWeights.isSelected() && grid[r][c] > 0) {
                            g2.setColor(Color.GRAY);
                            String s = String.valueOf(grid[r][c]);
                            g2.drawString(s, x + (cellSize - fm.stringWidth(s))/2, y + (cellSize + fm.getAscent())/2 - 2);
                        }
                    }
                    if (r == start[0] && c == start[1]) {
                        g2.setColor(new Color(46, 204, 113));
                        g2.fillRect(x+3, y+3, cellSize-6, cellSize-6);
                        g2.setColor(Color.WHITE); g2.drawString("S", x+cellSize/3, y+2*cellSize/3);
                    } else if (r == goal[0] && c == goal[1]) {
                        g2.setColor(new Color(231, 76, 60));
                        g2.fillRect(x+3, y+3, cellSize-6, cellSize-6);
                        g2.setColor(Color.WHITE); g2.drawString("G", x+cellSize/3, y+2*cellSize/3);
                    }
                }
            }
            if (chkOverlayDijkstra.isSelected() && pathDijkstra != null) drawPath(g2, pathDijkstra, new Color(155, 89, 182, 120), cellSize, xOff, yOff, 45); 
            if (chkOverlayAStar.isSelected() && pathAStar != null) drawPath(g2, pathAStar, new Color(243, 156, 18, 120), cellSize, xOff, yOff, 40); 
            if (chkOverlayGA.isSelected() && gaPath != null) drawPath(g2, gaPath, new Color(52, 152, 219, 200), cellSize, xOff, yOff, 30); 

            if (chkShowDeadEnds.isSelected() && deadEnds != null) {
                g2.setColor(new Color(255, 0, 0, 200));
                g2.setStroke(new BasicStroke(3));
                for(int[] p : deadEnds) {
                    int x = xOff + p[1] * cellSize; int y = yOff + p[0] * cellSize;
                    g2.drawLine(x+8, y+8, x+cellSize-8, y+cellSize-8);
                    g2.drawLine(x+8, y+cellSize-8, x+cellSize-8, y+8);
                }
            }
        }
        private void drawPath(Graphics2D g2, List<int[]> path, Color c, int cs, int xo, int yo, int th) {
            if (path.isEmpty()) return;
            g2.setColor(c);
            g2.setStroke(new BasicStroke(th, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            int[] xp = new int[path.size()]; int[] yp = new int[path.size()];
            for (int i = 0; i < path.size(); i++) {
                xp[i] = xo + path.get(i)[1] * cs + cs / 2;
                yp[i] = yo + path.get(i)[0] * cs + cs / 2;
            }
            g2.drawPolyline(xp, yp, path.size());
        }
    }
}