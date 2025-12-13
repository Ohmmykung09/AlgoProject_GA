package Algorithm;

import java.util.*;

public class GA {

    private int[][] grid;
    private int rows, cols;
    private Point startPos, endPos;

    // GA Parameters
    private int populationSize = 50;
    private int maxGenerations = 2000;
    private double mutationRate = 0.1;
    private double initialTemp = 1000.0;
    private double coolingRate = 0.98;

    private StepCallback callback;

    static final int NUM_WAYPOINTS = 10; // Gene length
    static final int PENALTY_COST = 10000;
    static Random random = new Random();

    public GA(int[][] grid, int rows, int cols, int[] start, int[] goal) {
        this.grid = grid;
        this.rows = rows;
        this.cols = cols;
        this.startPos = new Point(start[0], start[1]);
        this.endPos = new Point(goal[0], goal[1]);
    }

    public void setParameters(int popSize, int generations, int elitism, double mutation) {
        this.populationSize = popSize;
        this.maxGenerations = generations;
        this.mutationRate = mutation;
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

    // CORE LOGIC

    public Result run() {
        long startTime = System.nanoTime();

        // Initialization
        List<Individual> population = new ArrayList<>();
        for (int i = 0; i < populationSize; i++) {
            Individual ind = new Individual();
            ind.randomize();
            ind.calculateFitness();
            population.add(ind);
        }

        // Hall of Fame
        Individual hallOfFame = getBest(population);

        Individual bestEver = new Individual(hallOfFame.waypoints);
        bestEver.totalCost = hallOfFame.totalCost;
        hallOfFame = bestEver;

        double currentTemp = initialTemp;

        // Main Loop
        for (int gen = 0; gen < maxGenerations; gen++) {

            List<Individual> newPopulation = new ArrayList<>();

            // Keep Hall of Fame
            Individual elite = new Individual(hallOfFame.waypoints);
            elite.totalCost = hallOfFame.totalCost;
            newPopulation.add(elite);

            // Evolution Loop
            while (newPopulation.size() < populationSize) {
                // Selection
                Individual p1 = tournamentSelect(population);
                Individual p2 = tournamentSelect(population);

                // Crossover
                Individual child = crossover(p1, p2);

                // Mutation with SA Logic
                mutateWithSA(child, currentTemp);

                // Calculate Fitness
                child.calculateFitness();
                newPopulation.add(child);
            }

            // Update Population
            population = newPopulation;

            // Update Hall of Fame
            Individual currentBest = getBest(population);
            if (currentBest.totalCost < hallOfFame.totalCost) {
                hallOfFame = new Individual(currentBest.waypoints);
                hallOfFame.totalCost = currentBest.totalCost;
            }

            // Cooling
            currentTemp *= coolingRate;
            if (currentTemp < 0.1)
                currentTemp = 0.1;

            // Callback
            if (callback != null) {
                List<int[]> visualPath = reconstructPath(hallOfFame.waypoints);
                String status = String.format("Gen: %d | Temp: %.1f | Best: %d", gen, currentTemp,
                        hallOfFame.totalCost);
                callback.onStep(visualPath, gen, hallOfFame.totalCost, status);
            }

            try {
                Thread.sleep(2);
            } catch (InterruptedException e) {
            }
        }

        // Final Result
        Result res = new Result();
        res.path = reconstructPath(hallOfFame.waypoints);
        res.cost = hallOfFame.totalCost;
        res.timeTaken = (System.nanoTime() - startTime) / 1_000_000_000.0;
        return res;
    }

    // Helper
    private Individual tournamentSelect(List<Individual> pop) {
        int tournamentSize = 5;
        Individual best = null;
        for (int i = 0; i < tournamentSize; i++) {
            Individual randInd = pop.get(random.nextInt(pop.size()));
            if (best == null || randInd.totalCost < best.totalCost) {
                best = randInd;
            }
        }
        return best;
    }

    // Feature: Crossover
    private Individual crossover(Individual p1, Individual p2) {
        List<Point> childWP = new ArrayList<>();
        // Single Point Crossover
        int cutPoint = random.nextInt(NUM_WAYPOINTS);

        for (int i = 0; i < NUM_WAYPOINTS; i++) {
            if (i < cutPoint) {
                Point p = p1.waypoints.get(i);
                childWP.add(new Point(p.r, p.c));
            } else {
                Point p = p2.waypoints.get(i);
                childWP.add(new Point(p.r, p.c));
            }
        }
        return new Individual(childWP);
    }

    // Temp + Penalty
    private void mutateWithSA(Individual ind, double temp) {
        if (random.nextDouble() > mutationRate)
            return;

        // Mutation
        int idx = random.nextInt(NUM_WAYPOINTS);
        Point oldP = ind.waypoints.get(idx);

        // if hot make it far
        int moveRange = (int) (2 + (temp * 0.02));
        int nr = oldP.r + (random.nextInt(moveRange * 2 + 1) - moveRange);
        int nc = oldP.c + (random.nextInt(moveRange * 2 + 1) - moveRange);

        nr = Math.max(0, Math.min(rows - 1, nr));
        nc = Math.max(0, Math.min(cols - 1, nc));

        // Penalty Logic 1
        if (grid[nr][nc] == -1) {
            // change coordinate
            ind.waypoints.set(idx, new Point(nr, nc));
        } else {
            ind.waypoints.set(idx, new Point(nr, nc));
        }
    }

    private Individual getBest(List<Individual> pop) {
        Individual best = pop.get(0);
        for (Individual ind : pop) {
            if (ind.totalCost < best.totalCost) {
                best = ind;
            }
        }
        return best;
    }

    // INDIVIDUAL
    class Individual {
        List<Point> waypoints = new ArrayList<>();
        int totalCost = Integer.MAX_VALUE;

        public Individual() {
        }

        public Individual(List<Point> wp) {
            for (Point p : wp)
                this.waypoints.add(new Point(p.r, p.c));
        }

        public void randomize() {
            waypoints.clear();
            for (int i = 0; i < NUM_WAYPOINTS; i++) {
                int r, c;
                do {
                    r = random.nextInt(rows);
                    c = random.nextInt(cols);
                } while (grid[r][c] == -1);
                waypoints.add(new Point(r, c));
            }
        }

        // Penalty & BFS
        public void calculateFitness() {
            int currentCost = 0;
            Point currentPos = startPos;
            List<Point> fullRoute = new ArrayList<>(this.waypoints);
            fullRoute.add(endPos);

            for (Point target : fullRoute) {
                // Penalty 1
                if (grid[target.r][target.c] == -1) {
                    currentCost += PENALTY_COST;
                } else {
                    // BFS find cost
                    int segmentCost = runBFSCost(currentPos, target);

                    // Penalty 2
                    if (segmentCost >= 100_000) {
                        currentCost += PENALTY_COST;
                    } else {
                        currentCost += segmentCost;
                    }
                }
                currentPos = target;
            }
            this.totalCost = currentCost;
        }
    }

    // BFS & UTILS

    static class Point {
        int r, c;

        Point(int r, int c) {
            this.r = r;
            this.c = c;
        }
    }

    // BFS Cost Calculation
    private int runBFSCost(Point start, Point end) {
        if (start.r == end.r && start.c == end.c)
            return 0;

        boolean[][] visited = new boolean[rows][cols];
        Queue<int[]> q = new LinkedList<>();

        q.add(new int[] { start.r, start.c, 0 });
        visited[start.r][start.c] = true;

        int[][] dirs = { { -1, 0 }, { 1, 0 }, { 0, -1 }, { 0, 1 } };

        while (!q.isEmpty()) {
            int[] curr = q.poll();
            int r = curr[0];
            int c = curr[1];
            int cost = curr[2];

            if (r == end.r && c == end.c)
                return cost;

            for (int[] d : dirs) {
                int nr = r + d[0];
                int nc = c + d[1];

                if (nr >= 0 && nr < rows && nc >= 0 && nc < cols
                        && !visited[nr][nc] && grid[nr][nc] != -1) {
                    visited[nr][nc] = true;
                    int moveCost = grid[nr][nc] > 0 ? grid[nr][nc] : 1;
                    q.add(new int[] { nr, nc, cost + moveCost });
                }
            }
        }
        return 100_000; // Unreachable
    }

    private List<int[]> reconstructPath(List<Point> waypoints) {
        List<int[]> fullPath = new ArrayList<>();
        Point current = startPos;
        List<Point> targets = new ArrayList<>(waypoints);
        targets.add(endPos);

        for (Point target : targets) {
            List<int[]> segment = getPathPoints(current, target);
            fullPath.addAll(segment);
            if (!segment.isEmpty())
                current = target;
        }
        return fullPath;
    }

    private List<int[]> getPathPoints(Point s, Point e) {
        if (s.r == e.r && s.c == e.c)
            return new ArrayList<>();

        Queue<Point> q = new LinkedList<>();
        Point[][] parent = new Point[rows][cols];
        boolean[][] visited = new boolean[rows][cols];

        q.add(s);
        visited[s.r][s.c] = true;
        int[][] dirs = { { -1, 0 }, { 1, 0 }, { 0, -1 }, { 0, 1 } };

        while (!q.isEmpty()) {
            Point curr = q.poll();
            if (curr.r == e.r && curr.c == e.c)
                break;

            for (int[] d : dirs) {
                int nr = curr.r + d[0];
                int nc = curr.c + d[1];
                if (nr >= 0 && nr < rows && nc >= 0 && nc < cols && !visited[nr][nc] && grid[nr][nc] != -1) {
                    visited[nr][nc] = true;
                    parent[nr][nc] = curr;
                    q.add(new Point(nr, nc));
                }
            }
        }

        List<int[]> path = new ArrayList<>();
        Point curr = e;
        while (curr != null && (curr.r != s.r || curr.c != s.c)) {
            path.add(new int[] { curr.r, curr.c });
            curr = parent[curr.r][curr.c];
        }
        if (path.size() > 0) {
            path.add(new int[] { s.r, s.c });
            Collections.reverse(path);
        }
        return path;
    }
}