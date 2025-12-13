package Algorithm;

import java.util.*;

public class GA {

    // insert data
    private int[][] grid;
    private int rows, cols;
    private Point startPos, endPos;

    // Settings
    private int maxIterations = 2000;
    private double initialTemp = 1000.0;

    // Callback
    private StepCallback callback;

    // CONSTRUCTOR & SETUP
    public GA(int[][] grid, int rows, int cols, int[] start, int[] goal) {
        this.grid = grid;
        this.rows = rows;
        this.cols = cols;
        this.startPos = new Point(start[0], start[1]);
        this.endPos = new Point(goal[0], goal[1]);
    }

    public void setParameters(int popSize, int generations, int elitism, double mutation) {
        this.maxIterations = generations;
    }

    public interface StepCallback {
        void onStep(List<int[]> bestPath, int generation, int currentCost, String status);
    }

    public void setCallback(StepCallback cb) {
        this.callback = cb;
    }

    public static class Result {
        public List<int[]> path;
        public int cost;
        public double timeTaken;
    }

    // Simulated Annealing + BFS
    static final int NUM_WAYPOINTS = 12;
    static final double COOLING_RATE = 0.996;
    static final double MIN_TEMP = 0.1;
    static Random random = new Random();

    public Result run() {
        long startTime = System.nanoTime();

        Solution currentSol = new Solution();
        currentSol.evaluate();

        Solution bestSol = new Solution(currentSol.waypoints);
        bestSol.totalCost = currentSol.totalCost;

        double temp = initialTemp;
        int iteration = 0;

        while (temp > MIN_TEMP && iteration < maxIterations) {
            int iterationsPerTemp = 30;
            for (int i = 0; i < iterationsPerTemp; i++) {
                // Create Neighbor
                Solution newSol = new Solution(currentSol.waypoints);

                // Random Move Logic
                int idx = random.nextInt(NUM_WAYPOINTS);
                Point oldP = newSol.waypoints.get(idx);

                int moveRange = (int) (5 + (temp * 0.05));
                int nr = oldP.r + (random.nextInt(moveRange * 2) - moveRange);
                int nc = oldP.c + (random.nextInt(moveRange * 2) - moveRange);

                nr = Math.max(0, Math.min(rows - 1, nr));
                nc = Math.max(0, Math.min(cols - 1, nc));

                if (grid[nr][nc] != -1) {
                    newSol.waypoints.set(idx, new Point(nr, nc));
                }

                newSol.evaluate();

                // Accept / Reject
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

            // SEND UPDATE TO GUI
            if (callback != null) {
                List<int[]> visualPath = reconstructPath(bestSol.waypoints);
                callback.onStep(visualPath, iteration, bestSol.totalCost, String.format("Temp: %.2f", temp));
            }

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
    // HELPER METHODS (BFS IMPLEMENTATION)

    private List<int[]> reconstructPath(List<Point> waypoints) {
        List<int[]> fullPath = new ArrayList<>();
        Point current = startPos;
        List<Point> targets = new ArrayList<>(waypoints);
        targets.add(endPos);

        for (Point target : targets) {
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

    // BFS
    private List<Point> getPathBetween(Point s, Point e) {
        if (s.equals(e))
            return new ArrayList<>();

        Queue<Point> queue = new LinkedList<>();
        boolean[][] visited = new boolean[rows][cols];
        Point[][] parent = new Point[rows][cols];

        queue.add(s);
        visited[s.r][s.c] = true;

        int[][] dirs = { { -1, 0 }, { 1, 0 }, { 0, -1 }, { 0, 1 } };

        while (!queue.isEmpty()) {
            Point current = queue.poll();

            if (current.equals(e)) {
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

            for (int[] d : dirs) {
                int nr = current.r + d[0];
                int nc = current.c + d[1];

                if (nr >= 0 && nr < rows && nc >= 0 && nc < cols
                        && grid[nr][nc] != -1
                        && !visited[nr][nc]) {

                    visited[nr][nc] = true;
                    parent[nr][nc] = current;
                    queue.add(new Point(nr, nc));
                }
            }
        }
        return new ArrayList<>();
    }

    // BFS Return int
    private int runBFSCost(Point start, Point end) {
        if (start.equals(end))
            return 0;

        Queue<Point> queue = new LinkedList<>();
        int[][] dist = new int[rows][cols];
        for (int[] row : dist)
            Arrays.fill(row, -1);

        queue.add(start);
        dist[start.r][start.c] = 0;

        int[][] dirs = { { -1, 0 }, { 1, 0 }, { 0, -1 }, { 0, 1 } };

        while (!queue.isEmpty()) {
            Point current = queue.poll();

            if (current.equals(end)) {
                return dist[current.r][current.c];
            }

            for (int[] d : dirs) {
                int nr = current.r + d[0];
                int nc = current.c + d[1];

                if (nr >= 0 && nr < rows && nc >= 0 && nc < cols
                        && grid[nr][nc] != -1
                        && dist[nr][nc] == -1) {

                    dist[nr][nc] = dist[current.r][current.c] + grid[nr][nc];
                    queue.add(new Point(nr, nc));
                }
            }
        }
        return 100_000;
    }

    class Solution {
        List<Point> waypoints = new ArrayList<>();
        int totalCost = 0;

        public Solution() {
            for (int i = 0; i < NUM_WAYPOINTS; i++) {
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
                    // เรียกใช้ BFS Cost แทน Dijkstra
                    currentCost += runBFSCost(currentPos, target);
                    currentPos = target;
                }
            }
            this.totalCost = currentCost;
        }
    }

    static class Point {
        int r, c;

        Point(int r, int c) {
            this.r = r;
            this.c = c;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o)
                return true;
            if (o == null || getClass() != o.getClass())
                return false;
            Point p = (Point) o;
            return r == p.r && c == p.c;
        }

        @Override
        public int hashCode() {
            return Objects.hash(r, c);
        }
    }
}