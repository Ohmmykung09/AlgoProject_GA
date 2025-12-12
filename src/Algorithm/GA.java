package Algorithm;

import java.util.*;

public class GA {
    // --- Configuration ---
    private int POPULATION_SIZE = 500; 
    private int GENERATIONS = 1000;   
    private int ELITISM_COUNT = 20;   
    private double MUTATION_RATE = 0.6; 
    private double SCORE_GOAL_REACHED = 1_000_000.0;

    // --- Direction Constants (กำหนดชื่อทิศให้ชัดเจน) ---
    public static final int UP    = 0;
    public static final int DOWN  = 1;
    public static final int LEFT  = 2;
    public static final int RIGHT = 3;

    // --- MOVES: { dRow, dCol } ---
    // ระบบ Grid Array: [Row][Col] โดย (0,0) อยู่มุมซ้ายบน
    private static final int[][] MOVES = { 
        { -1, 0 }, // 0: UP    (Row ลดลง)
        { 1, 0 },  // 1: DOWN  (Row เพิ่มขึ้น)
        { 0, -1 }, // 2: LEFT  (Col ลดลง)
        { 0, 1 }   // 3: RIGHT (Col เพิ่มขึ้น)
    };
    
    private int[][] grid;
    private int rows, cols; 
    private int[] startPoint, goalPoint; // เก็บ {row, col}
    private Random rand = new Random();

    private int[][] repulsionMap; 

    // --- Optimization Variables ---
    private int stagnationCount = 0;
    private int currentMaxSteps; 
    private int optimizationStableCount = 0; 
    private int bestAllTimeCost = Integer.MAX_VALUE;
    
    public List<Individual> TheGoat = new ArrayList<>(); 
    private VisualizationCallback callback;
    public int GENOME_LENGTH;
    
    // Constructor
    public GA(int[][] grid, int height, int width, int[] start, int[] goal) {
        this.grid = grid;
        this.rows = height; 
        this.cols = width;   
        
        // รับค่ามาเป็น {row, col} ใช้ได้เลย ไม่ต้องสลับให้งง
        this.startPoint = start; 
        this.goalPoint = goal;    
        
        this.GENOME_LENGTH = 2 * (height * width); 
        currentMaxSteps = GENOME_LENGTH;
        
        this.repulsionMap = new int[height][width];
    }
    public void setParameters(int popSize, int gens, int elitism, double mutation) {
        this.POPULATION_SIZE = popSize;
        this.GENERATIONS = gens;
        this.ELITISM_COUNT = elitism;
        this.MUTATION_RATE = mutation;
    }

    public static class Result {
        public boolean reachedGoal;
        public int cost;
        public List<int[]> path; 
        public double timeTaken;
    }

    private class Individual implements Comparable<Individual> {
        int[][] genome; 
        int cost;
        double fitness;
        boolean reachedGoal;
        List<int[]> path; // เก็บ path เป็น {row, col}

        int[][] currentPathStatus; 

        public Individual() {
            genome = new int[GENOME_LENGTH][4];
            path = new ArrayList<>();
            for (int i = 0; i < GENOME_LENGTH; i++) {
                // [CHANGED] ใช้ชื่อทิศทางชัดเจน แทนเลข 0,1,2,3
                List<Integer> dirs = new ArrayList<>(Arrays.asList(UP, DOWN, LEFT, RIGHT));
                Collections.shuffle(dirs, rand);
                for (int p = 0; p < 4; p++) {
                    genome[i][p] = dirs.get(p);
                }
            }
        }

        public Individual(int[][] inheritedGenes) {
            this.genome = inheritedGenes;
            this.path = new ArrayList<>();
        }

        public void calculateFitness() {
            int curR = startPoint[0];
            int curC = startPoint[1];
            
            cost = 0;
            path.clear();
            currentPathStatus = new int[rows][cols];
            
            path.add(new int[] { curR, curC });
            currentPathStatus[curR][curC] = 1; 
            
            reachedGoal = false;
            int stepLimit = (!reachedGoal && bestAllTimeCost == Integer.MAX_VALUE) ? GENOME_LENGTH : currentMaxSteps; 

            for (int step = 0; step < stepLimit; step++) {
                boolean moved = false;
                
                for (int p = 0; p < 4; p++) {
                    int moveIndex = genome[step][p];
                    
                    // MOVES: {dRow, dCol}
                    int nextR = curR + MOVES[moveIndex][0];
                    int nextC = curC + MOVES[moveIndex][1];

                    // 1. Check Bounds & Walls
                    if (!isValidPosition(nextR, nextC)) continue;

                    // 2. Check Local Loop (ห้ามเหยียบซ้ำ)
                    if (currentPathStatus[nextR][nextC] == 1) continue;

                    // 3. Smart Lookahead (ถ้า uncomment ต้องใช้ nextR, nextC)
                    // if (!(nextR == goalPoint[0] && nextC == goalPoint[1])) {
                    //     if (isTrapped(nextR, nextC, currentPathStatus)) {
                    //         continue; 
                    //     }
                    // }

                    // --- WALK ---
                    currentPathStatus[nextR][nextC] = 1; 
                    
                    cost += grid[nextR][nextC]; 
                    // if (repulsionMap[nextR][nextC] > 0) {
                    //     cost += (repulsionMap[nextR][nextC] * 50); 
                    // }

                    curR = nextR;
                    curC = nextC;
                    path.add(new int[] { curR, curC });
                    moved = true;
                    
                    if (curR == goalPoint[0] && curC == goalPoint[1]) reachedGoal = true; 
                    break;
                }
                
                // Dead End
                if (!moved) {
                    if (!reachedGoal) {
                        // repulsionMap[curR][curC] += 500;
                        fitness = 0.001; 
                        return; 
                    }
                    break;
                }
                if (reachedGoal) break;
            }

            if (reachedGoal) {
                fitness = SCORE_GOAL_REACHED + (20_000_000.0 / (cost + 1));
            } else {
                // Manhattan Distance
                double dist = Math.abs(curR - goalPoint[0]) + Math.abs(curC - goalPoint[1]);
                fitness = 10000.0 / (dist * dist + 1); 
            }
        }

        // Helper Methods ที่ปรับให้ใช้ MOVES ใหม่แล้ว
        

        private boolean isValidPosition(int r, int c) {
            return r >= 0 && r < rows && c >= 0 && c < cols && grid[r][c] != -1;
        }
        
        @Override
        public int compareTo(Individual other) { return Double.compare(other.fitness, this.fitness); }
    }

    // --- MAIN RUN LOOP ---
    public Result run() {
        int stableCount = 0;
        long startTime = System.nanoTime();
        List<Individual> population = new ArrayList<>();
        for (int i = 0; i < POPULATION_SIZE; i++) population.add(new Individual());
    
        Individual bestGlobal = population.get(0);

        for (int gen = 0; gen < GENERATIONS; gen++) {
            for (Individual ind : population) {
                ind.calculateFitness();
                if (ind.reachedGoal) {
                    if (!bestGlobal.reachedGoal || ind.cost < bestGlobal.cost) bestGlobal = ind; 
                    stableCount = 0;
                } else if (!bestGlobal.reachedGoal && ind.fitness > bestGlobal.fitness) {
                    bestGlobal = ind;
                    stableCount = 0;
                }
                else {
                    stableCount++;
                }
            }
            Collections.sort(population);
            
             if (gen % 1 == 0 || bestGlobal.reachedGoal) { 
                if (callback != null) {
                    String status = bestGlobal.reachedGoal ? "GOAL" : "SEARCH";
                    if(optimizationStableCount > 0) status = "OPT (" + optimizationStableCount + ")";
                    
                    // ส่ง path ไปวาดได้เลย ไม่ต้องแปลงกลับ เพราะเราใช้ {row, col} ตลอดแล้ว
                    callback.onUpdate(bestGlobal.path, gen, bestGlobal.cost, status);
                    try { Thread.sleep(1); } catch (InterruptedException e) {}
                }
            }
            population = createNextGeneration(population);
        }

        long endTime = System.nanoTime();
        Result res = new Result();
        
        res.reachedGoal = bestGlobal.reachedGoal; 
        res.cost = bestGlobal.cost;
        res.path = bestGlobal.path; // ส่ง path ตรงๆ
        // res.path = smoothPath(bestGlobal.path); // ถ้าเปิดใช้ smoothPath
        
        res.timeTaken = (endTime - startTime) / 1e9;
        return res;
    }
    
    private List<Individual> createNextGeneration(List<Individual> currentPop) {
        List<Individual> nextGen = new ArrayList<>();
        for (int i = 0; i < ELITISM_COUNT; i++) nextGen.add(new Individual(cloneGenome(currentPop.get(i).genome)));
        while (nextGen.size() < POPULATION_SIZE) {
            double newBloodRate = (stagnationCount > 20) ? 0.3 : 0.05;
            if (rand.nextDouble() < newBloodRate) {
                nextGen.add(new Individual()); 
            } else {
                Individual p1 = currentPop.get(rand.nextInt(currentPop.size()/2));
                Individual p2 = currentPop.get(rand.nextInt(currentPop.size()/2));
                int[][] childGenes = performCrossover(p1, p2);
                performMutation(childGenes);
                nextGen.add(new Individual(childGenes));
            }
        }
        return nextGen;
    }
    
    private int[][] performCrossover(Individual p1, Individual p2) {
        int cut = rand.nextInt(GENOME_LENGTH);
        int[][] child = new int[GENOME_LENGTH][4];
        for(int i=0; i<cut; i++) child[i] = p1.genome[i].clone();
        for(int i=cut; i<GENOME_LENGTH; i++) child[i] = p2.genome[i].clone();
        return child;
    }
    private void performMutation(int[][] genes) {
        for(int i=0; i<GENOME_LENGTH; i++){
            if(rand.nextDouble() < MUTATION_RATE) {
                int a=rand.nextInt(4), b=rand.nextInt(4);
                int t=genes[i][a]; genes[i][a]=genes[i][b]; genes[i][b]=t;
            }
        }
    }
    private int[][] cloneGenome(int[][] original) {
        int[][] copy = new int[original.length][];
        for(int i=0; i<original.length; i++) copy[i] = original[i].clone();
        return copy;
    }




    public interface VisualizationCallback {
        void onUpdate(List<int[]> path, int gen, int cost, String status);
    }
    public void setCallback(VisualizationCallback cb) { this.callback = cb; }
}