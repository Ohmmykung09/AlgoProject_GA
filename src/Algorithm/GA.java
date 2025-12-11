package Algorithm;

import java.util.*;

public class GA {
    private int[][] GRID;
    private int[][] DIST_MAP;
    private int ROWS, COLS;
    private int[] START, GOAL;
    private final int[][] MOVES = { { -1, 0 }, { 1, 0 }, { 0, -1 }, { 0, 1 } };

    // Parameters
    private final int POPULATION_SIZE = 500;
    private final int GENOME_LENGTH = 1500;
    private final int GENERATIONS = 1000;
    private final int ELITISM_COUNT = 20;

    private Random rand = new Random();

    public GA(int[][] grid, int[][] distMap, int rows, int cols, int[] start, int[] goal) {
        this.GRID = grid;
        this.DIST_MAP = distMap;
        this.ROWS = rows;
        this.COLS = cols;
        this.START = start;
        this.GOAL = goal;
    }

    public static class Result {
        public boolean reachedGoal;
        public int cost;
        public List<int[]> path;
        public double timeTaken;
    }

    private class Individual implements Comparable<Individual> {
        int[] genome;
        int cost;
        double fitness;
        boolean reachedGoal;
        List<int[]> path;

        // Constructor for initialization
        public Individual() {
            genome = new int[GENOME_LENGTH];
            path = new ArrayList<>();
            int currR = START[0], currC = START[1];

            for (int i = 0; i < GENOME_LENGTH; i++) {
                List<int[]> candidates = new ArrayList<>(); // {distVal, moveIdx}

                for (int m = 0; m < MOVES.length; m++) {
                    int nr = currR + MOVES[m][0];
                    int nc = currC + MOVES[m][1];
                    if (isValid(nr, nc)) {
                        candidates.add(new int[] { DIST_MAP[nr][nc], m });
                    }
                }

                int move = rand.nextInt(4);
                if (!candidates.isEmpty()) {
                    // Sort by distance map value (asc)
                    candidates.sort(Comparator.comparingInt(a -> a[0]));
                    // 90% follow heatmap, 10% explore
                    if (rand.nextDouble() < 0.9)
                        move = candidates.get(0)[1];
                    else
                        move = candidates.get(rand.nextInt(candidates.size()))[1];

                    currR += MOVES[move][0];
                    currC += MOVES[move][1];
                }
                genome[i] = move;
            }
        }

        // Constructor for offspring
        public Individual(int[] genes) {
            this.genome = genes;
            this.path = new ArrayList<>();
        }

        public void evaluate() {
            int r = START[0], c = START[1];
            cost = 0;
            path.clear();
            path.add(new int[] { r, c });
            Set<String> visited = new HashSet<>();
            visited.add(r + "," + c);
            reachedGoal = false;

            for (int move : genome) {
                int nr = r + MOVES[move][0];
                int nc = c + MOVES[move][1];

                if (isValid(nr, nc)) {
                    if (visited.contains(nr + "," + nc))
                        cost += 50; // Loop penalty
                    else
                        cost += GRID[nr][nc];

                    r = nr;
                    c = nc;
                    path.add(new int[] { r, c });
                    visited.add(r + "," + c);

                    if (r == GOAL[0] && c == GOAL[1]) {
                        reachedGoal = true;
                        break;
                    }
                } else {
                    continue; // Hit wall
                }
            }

            // Calculate Fitness
            int finalDist = DIST_MAP[r][c];
            if (reachedGoal)
                fitness = 1_000_000 + (1_000_000.0 / (cost + 1));
            else
                fitness = 50_000.0 / (finalDist + 1);
        }

        private boolean isValid(int r, int c) {
            return r >= 0 && r < ROWS && c >= 0 && c < COLS && GRID[r][c] != -1;
        }

        @Override
        public int compareTo(Individual o) {
            return Double.compare(o.fitness, this.fitness); // Descending
        }
    }

    public Result run() {
        long startTime = System.nanoTime();

        List<Individual> pop = new ArrayList<>();
        for (int i = 0; i < POPULATION_SIZE; i++)
            pop.add(new Individual());

        Individual bestGlobal = pop.get(0);

        for (int gen = 0; gen < GENERATIONS; gen++) {
            for (Individual ind : pop) {
                ind.evaluate();
                if (ind.reachedGoal) {
                    if (!bestGlobal.reachedGoal || ind.cost < bestGlobal.cost)
                        bestGlobal = ind;
                } else if (!bestGlobal.reachedGoal && ind.fitness > bestGlobal.fitness) {
                    bestGlobal = ind;
                }
            }

            Collections.sort(pop);

            if (gen % 100 == 0) {
                if (bestGlobal.reachedGoal)
                    System.out.printf("  [GA] Gen %d: REACHED! Cost=%d%n", gen, bestGlobal.cost);
                else
                    System.out.printf("  [GA] Gen %d: Running...%n", gen);
            }

            // Evolution
            List<Individual> nextGen = new ArrayList<>();
            // Elitism
            for (int i = 0; i < ELITISM_COUNT; i++)
                nextGen.add(pop.get(i));

            while (nextGen.size() < POPULATION_SIZE) {
                Individual p1 = pop.get(rand.nextInt(100)); // Tournament select from top 100
                Individual p2 = pop.get(rand.nextInt(100));

                // Crossover
                int pt = rand.nextInt(GENOME_LENGTH - 1) + 1;
                int[] childGenes = new int[GENOME_LENGTH];
                System.arraycopy(p1.genome, 0, childGenes, 0, pt);
                System.arraycopy(p2.genome, pt, childGenes, pt, GENOME_LENGTH - pt);

                // Mutation (0.3 rate)
                if (rand.nextDouble() < 0.3) {
                    int idx = rand.nextInt(GENOME_LENGTH);
                    childGenes[idx] = rand.nextInt(4);
                }
                nextGen.add(new Individual(childGenes));
            }
            pop = nextGen;
        }

        long endTime = System.nanoTime();
        Result res = new Result();
        res.reachedGoal = bestGlobal.reachedGoal;
        res.cost = bestGlobal.cost;
        res.path = bestGlobal.path;
        res.timeTaken = (endTime - startTime) / 1e9;
        return res;
    }
}