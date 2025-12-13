package Algorithm;

import java.util.*;

public class GA {

    // ==========================================
    // 1. DATA FROM GUI
    // ==========================================
    private int[][] grid;
    private int rows, cols;
    private Point startPos, endPos;

    // Settings (Map จาก GUI มาใช้กับ SA)
    private int maxIterations = 2000; // เทียบเท่า Generations
    private double initialTemp = 1000.0;

    // Callback สำหรับส่งข้อมูลกลับไป GUI
    private StepCallback callback;

    // ==========================================
    // 2. CONSTRUCTOR & SETUP
    // ==========================================

    // Constructor ที่ GUI จะเรียกใช้
    public GA(int[][] grid, int rows, int cols, int[] start, int[] goal) {
        this.grid = grid;
        this.rows = rows;
        this.cols = cols;
        // แปลง int[] เป็น Point ของเรา
        this.startPos = new Point(start[0], start[1]);
        this.endPos = new Point(goal[0], goal[1]);
    }

    // รับค่า Settings จาก GUI
    public void setParameters(int popSize, int generations, int elitism, double mutation) {
        // ในที่นี้เราจะเอา generations มากำหนดรอบของ SA
        this.maxIterations = generations;
        // เราอาจจะเอา mutation มาปรับเป็น Temp หรือ Cooling Rate ได้ถ้าต้องการ
        // แต่ตอนนี้ใช้ Default ของ SA ไปก่อน
    }

    // Interface สำหรับส่งค่ากลับ
    public interface StepCallback {
        void onStep(List<int[]> bestPath, int generation, int currentCost, String status);
    }

    public void setCallback(StepCallback cb) {
        this.callback = cb;
    }

    // Class สำหรับ Return ผลลัพธ์สุดท้าย
    public static class Result {
        public List<int[]> path;
        public int cost;
        public double timeTaken;
    }

    // ==========================================
    // 3. CORE LOGIC (Simulated Annealing)
    // ==========================================

    static final int NUM_WAYPOINTS = 12;
    static final double COOLING_RATE = 0.995;
    static final double MIN_TEMP = 0.1;
    static Random random = new Random();

    public Result run() {
        long startTime = System.nanoTime();

        // 1. Initial Solution
        Solution currentSol = new Solution();
        currentSol.evaluate();

        Solution bestSol = new Solution(currentSol.waypoints);
        bestSol.totalCost = currentSol.totalCost;

        double temp = initialTemp;
        int iteration = 0;

        // Loop จนกว่าจะครบ Gen หรือ Temp หมด
        while (temp > MIN_TEMP && iteration < maxIterations) {

            // Loop ย่อยต่ออุณหภูมิ (ลดลงเพื่อให้ GUI อัปเดตไวขึ้น)
            int iterationsPerTemp = 20;

            for (int i = 0; i < iterationsPerTemp; i++) {
                // 2. Create Neighbor
                Solution newSol = new Solution(currentSol.waypoints);

                // Random Move Logic
                int idx = random.nextInt(NUM_WAYPOINTS);
                Point oldP = newSol.waypoints.get(idx);
                int moveRange = (int) (5 + (temp * 0.05));
                int nr = oldP.r + (random.nextInt(moveRange * 2) - moveRange);
                int nc = oldP.c + (random.nextInt(moveRange * 2) - moveRange);

                nr = Math.max(0, Math.min(rows - 1, nr));
                nc = Math.max(0, Math.min(cols - 1, nc));

                // ตรวจสอบว่าจุดใหม่อยู่ในกำแพงหรือไม่ (ถ้าอยู่ในกำแพง
                // พยายามหาจุดใกล้เคียงที่ว่าง)
                if (grid[nr][nc] == -1) {
                    // Logic ง่ายๆ: ถ้าสุ่มโดนกำแพง ให้ยกเลิกการเดินรอบนี้ (เพื่อประหยัด Cost)
                    // หรือปล่อยไป เพราะ evaluate มี penalty 5000 อยู่แล้ว
                }

                newSol.waypoints.set(idx, new Point(nr, nc));
                newSol.evaluate();

                // 3. Accept / Reject
                int delta = newSol.totalCost - currentSol.totalCost;
                if (delta < 0 || Math.random() < Math.exp(-delta / temp)) {
                    currentSol = newSol;
                    if (currentSol.totalCost < bestSol.totalCost) {
                        bestSol = new Solution(currentSol.waypoints);
                        bestSol.totalCost = currentSol.totalCost;
                    }
                }
            }

            temp *= COOLING_RATE;
            iteration++;

            // --- SEND UPDATE TO GUI ---
            if (callback != null) {
                // สร้าง Full Path เพื่อส่งไปวาดบนจอ (List<int[]>)
                List<int[]> visualPath = reconstructPath(bestSol.waypoints);
                callback.onStep(visualPath, iteration, bestSol.totalCost, String.format("Temp: %.2f", temp));
            }

            // ชะลอเวลาเล็กน้อยเพื่อให้ GUI วาดทัน (Optional)
            try {
                Thread.sleep(2);
            } catch (InterruptedException e) {
            }
        }

        // Final Result
        Result res = new Result();
        res.path = reconstructPath(bestSol.waypoints);
        res.cost = bestSol.totalCost;
        res.timeTaken = (System.nanoTime() - startTime) / 1_000_000_000.0;
        return res;
    }

    // ==========================================
    // 4. HELPER METHODS & CLASSES
    // ==========================================

    // Helper: สร้างเส้นทางเต็มจาก Waypoints เพื่อส่งให้ GUI วาดเส้น
    private List<int[]> reconstructPath(List<Point> waypoints) {
        List<int[]> fullPath = new ArrayList<>();
        Point current = startPos;
        List<Point> targets = new ArrayList<>(waypoints);
        targets.add(endPos);

        for (Point target : targets) {
            // ใช้ A* หรือ Dijkstra หาเส้นทางระหว่างจุด (ในที่นี้ใช้ A* เพื่อความไวในการวาด)
            // หรือใช้ฟังก์ชัน runDijkstraPath ที่เขียนเพิ่มด้านล่าง
            List<Point> segment = getPathBetween(current, target);
            for (Point p : segment) {
                fullPath.add(new int[] { p.r, p.c });
            }
            if (!segment.isEmpty()) {
                current = target;
            }
        }
        return fullPath;
    }

    private List<Point> getPathBetween(Point s, Point e) {
        // ใช้ Logic Dijkstra เดิม แต่เก็บ Parent เพื่อสร้าง Path
        PriorityQueue<Node> openSet = new PriorityQueue<>();
        int[][] minCost = new int[rows][cols];
        Point[][] parent = new Point[rows][cols]; // เก็บพ่อแม่เพื่อย้อนรอย

        for (int[] row : minCost)
            Arrays.fill(row, Integer.MAX_VALUE);

        openSet.add(new Node(s, 0));
        minCost[s.r][s.c] = 0;
        int[][] dirs = { { -1, 0 }, { 1, 0 }, { 0, -1 }, { 0, 1 } };

        while (!openSet.isEmpty()) {
            Node current = openSet.poll();
            if (current.p.equals(e)) {
                // Reconstruct path
                List<Point> path = new ArrayList<>();
                Point curr = e;
                while (curr != null && !curr.equals(s)) {
                    path.add(curr);
                    curr = parent[curr.r][curr.c];
                }
                path.add(s);
                Collections.reverse(path);
                return path;
            }

            if (current.g > minCost[current.p.r][current.p.c])
                continue;

            for (int[] d : dirs) {
                int nr = current.p.r + d[0];
                int nc = current.p.c + d[1];
                if (nr >= 0 && nr < rows && nc >= 0 && nc < cols && grid[nr][nc] != -1) {
                    int newCost = current.g + grid[nr][nc];
                    if (newCost < minCost[nr][nc]) {
                        minCost[nr][nc] = newCost;
                        parent[nr][nc] = current.p;
                        openSet.add(new Node(new Point(nr, nc), newCost));
                    }
                }
            }
        }
        return new ArrayList<>(); // No path
    }

    // --- Inner Classes ---

    class Solution {
        List<Point> waypoints = new ArrayList<>();
        int totalCost = 0;

        public Solution() {
            for (int i = 0; i < NUM_WAYPOINTS; i++) {
                // สุ่มจุดเริ่มต้น (พยายามไม่ให้ลงกำแพงตั้งแต่ต้น)
                int r, c;
                do {
                    r = random.nextInt(rows);
                    c = random.nextInt(cols);
                } while (grid[r][c] == -1);

                waypoints.add(new Point(r, c));
            }
        }

        public Solution(List<Point> wp) {
            for (Point p : wp)
                this.waypoints.add(new Point(p.r, p.c));
        }

        public void evaluate() {
            int currentCost = 0;
            Point currentPos = startPos;
            List<Point> fullRoute = new ArrayList<>(this.waypoints);
            fullRoute.add(endPos);

            for (Point target : fullRoute) {
                if (grid[target.r][target.c] == -1) {
                    currentCost += 5000;
                } else {
                    currentCost += runDijkstraCost(currentPos, target);
                    currentPos = target;
                }
            }
            this.totalCost = currentCost;
        }
    }

    // Dijkstra สำหรับหา Cost เท่านั้น (เร็ว)
    private int runDijkstraCost(Point start, Point end) {
        if (start.equals(end))
            return 0;
        PriorityQueue<Node> openSet = new PriorityQueue<>();
        int[][] minCost = new int[rows][cols];
        for (int[] row : minCost)
            Arrays.fill(row, Integer.MAX_VALUE);

        openSet.add(new Node(start, 0));
        minCost[start.r][start.c] = 0;
        int[][] dirs = { { -1, 0 }, { 1, 0 }, { 0, -1 }, { 0, 1 } };

        while (!openSet.isEmpty()) {
            Node current = openSet.poll();
            if (current.p.equals(end))
                return current.g;
            if (current.g > minCost[current.p.r][current.p.c])
                continue;

            for (int[] d : dirs) {
                int nr = current.p.r + d[0];
                int nc = current.p.c + d[1];
                if (nr >= 0 && nr < rows && nc >= 0 && nc < cols && grid[nr][nc] != -1) {
                    int newCost = current.g + grid[nr][nc];
                    if (newCost < minCost[nr][nc]) {
                        minCost[nr][nc] = newCost;
                        openSet.add(new Node(new Point(nr, nc), newCost));
                    }
                }
            }
        }
        return 100_000; // Path not found
    }

    // Helper Objects
    static class Point {
        int r, c;

        Point(int r, int c) {
            this.r = r;
            this.c = c;
        }

        public boolean equals(Object o) {
            if (this == o)
                return true;
            if (o == null || getClass() != o.getClass())
                return false;
            Point p = (Point) o;
            return r == p.r && c == p.c;
        }

        public int hashCode() {
            return Objects.hash(r, c);
        }
    }

    static class Node implements Comparable<Node> {
        Point p;
        int g;

        Node(Point p, int g) {
            this.p = p;
            this.g = g;
        }

        public int compareTo(Node o) {
            return Integer.compare(this.g, o.g);
        }
    }
}