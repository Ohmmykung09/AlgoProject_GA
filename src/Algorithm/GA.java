package Algorithm;

import java.util.*;

public class GA {
    // --- Configuration ---
    private static final int POPULATION_SIZE = 200; 
    private static final int GENERATIONS = 3000;   
    private static final int ELITISM_COUNT = 20;   
    private static final double MUTATION_RATE = 0.6; 
    private static final double SCORE_GOAL_REACHED = 1_000_000.0;

    // --- State Variables ---
    private int[][] grid;
    private int rows, cols;
    private int[] startPoint, goalPoint;
    private final int[][] MOVES = { { -1, 0 }, { 1, 0 }, { 0, -1 }, { 0, 1 } }; 
    private Random rand = new Random();

    // [MEMORY SYSTEM]
    // 0 = Unknown, 2 = Global Dead End (ทางตันถาวร)
    private int[][] ancestralMemory; 
    private int[][] repulsionMap; 

    // [OPTIMIZATION VARIABLES]
    private double lastBestFitness = 0;
    private int stagnationCount = 0;
    private static final int STAGNATION_THRESHOLD = 50;
    
    private int currentMaxSteps; 
    private int optimizationStableCount = 0; 
    private int bestAllTimeCost = Integer.MAX_VALUE;
    
    private static final int BRANCHING_THRESHOLD = 100; 
    private static final int TABU_THRESHOLD = 300; 

    public List<Individual> TheGoat = new ArrayList<>(); // Hall of Fame
    private VisualizationCallback callback;
    public int GENOME_LENGTH;
    
    public GA(int[][] grid, int rows, int cols, int[] start, int[] goal) {
        this.grid = grid;
        this.rows = rows;
        this.cols = cols;
        this.startPoint = start;
        this.goalPoint = goal;
        // เผื่อความยาว Genome ไว้ 2 เท่าของขนาด map เพื่อให้เดินอ้อมได้ในช่วงแรก
        this.GENOME_LENGTH = 2 * (rows * cols); 
        currentMaxSteps = GENOME_LENGTH;
        
        this.ancestralMemory = new int[rows][cols];
        this.repulsionMap = new int[rows][cols];
    }

    // --- Result Class ---
    public static class Result {
        public boolean reachedGoal;
        public int cost;
        public List<int[]> path;
        public double timeTaken;
    }

    // --- Individual Class ---
    private class Individual implements Comparable<Individual> {
        int[][] genome; 
        int cost;
        double fitness;
        boolean reachedGoal;
        List<int[]> path;

        // [LOCAL MEMORY] 0 = White, 1 = Gray (Loop Protection)
        int[][] currentPathStatus; 

        public Individual() {
            genome = new int[GENOME_LENGTH][4];
            path = new ArrayList<>();
            for (int i = 0; i < GENOME_LENGTH; i++) {
                List<Integer> dirs = new ArrayList<>(Arrays.asList(0, 1, 2, 3));
                Collections.shuffle(dirs, rand);
                for (int p = 0; p < 4; p++) genome[i][p] = dirs.get(p);
            }
        }

        public Individual(int[][] inheritedGenes) {
            this.genome = inheritedGenes;
            this.path = new ArrayList<>();
        }

        public void calculateFitness() {
            int curRow = startPoint[0];
            int curCol = startPoint[1];
            cost = 0;
            path.clear();
            
            // Reset Local Memory (ใช้แค่ตัวนี้พอ ไม่ใช้ ancestralMemory แล้ว)
            currentPathStatus = new int[rows][cols];
            
            path.add(new int[] { curRow, curCol });
            currentPathStatus[curRow][curCol] = 1; 
            
            reachedGoal = false;
            
            // [FIX 1] บีบจำนวนก้าวให้โหดขึ้น
            // ถ้าเคยเจอทางออกแล้ว ให้ limit = ความยาวทางที่ดีที่สุด
            // เพื่อบังคับให้หาทางที่สั้นกว่าเดิมเท่านั้น (ทางยาวเท่าเดิมก็ไม่เอา)
            int stepLimit = (!reachedGoal && bestAllTimeCost == Integer.MAX_VALUE) ? GENOME_LENGTH : currentMaxSteps; 
    
            for (int step = 0; step < stepLimit; step++) {
                boolean moved = false;
                
                for (int p = 0; p < 4; p++) {
                    int moveIndex = genome[step][p];
                    int nextRow = curRow + MOVES[moveIndex][0];
                    int nextCol = curCol + MOVES[moveIndex][1];
    
                    if (!isValidPosition(nextRow, nextCol)) continue;
    
                    // [FIX 2] ลบการเช็ค ancestralMemory (สีดำ) ออกไปเลย
                    // เพราะมันสร้าง False Positive บล็อคทางดีๆ
                    // if (ancestralMemory[nextRow][nextCol] == 2) continue; <--- ลบออก
    
                    // เช็คแค่ห้ามเดินวนหาตัวเอง (สีเทา) ก็พอ
                    if (currentPathStatus[nextRow][nextCol] == 1) continue;
    
                    // --- WALK ---
                    currentPathStatus[nextRow][nextCol] = 1; 
                    cost += grid[nextRow][nextCol];
                    if (repulsionMap[nextRow][nextCol] > 0) cost += (repulsionMap[nextRow][nextCol] * 20); 
    
                    curRow = nextRow;
                    curCol = nextCol;
                    path.add(new int[] { curRow, curCol });
                    moved = true;
                    
                    if (curRow == goalPoint[0] && curCol == goalPoint[1]) reachedGoal = true;
                    break;
                }
                
                // [FIX 3] ไม่ต้อง Mark สีดำใส่ Map กลางแล้ว
                if (!moved) {
                    // ติดทางตัน ก็แค่จบรอบของตัวนี้ไป ให้ค่า Fitness ต่ำๆ เดี๋ยวตายเอง
                    break;
                }
                if (reachedGoal) break;
            }
    
            // --- Fitness Formula (ปรับให้ sensitive กับ Cost มากขึ้น) ---
            if (reachedGoal) {
                // ให้ความสำคัญกับ Cost มากๆ (ยิ่งน้อยยิ่งได้คะแนนพุ่ง)
                fitness = SCORE_GOAL_REACHED + (20_000_000.0 / (cost + 1));
            } else {
                double dist = Math.abs(curRow - goalPoint[0]) + Math.abs(curCol - goalPoint[1]);
                // เปลี่ยนสูตร: ยิ่งใกล้ Goal ยิ่งดี
                fitness = 1000.0 / (dist + 1); 
            }
        }

        private boolean isValidPosition(int r, int c) {
            return r >= 0 && r < rows && c >= 0 && c < cols && grid[r][c] != -1;
        }
        
        @Override
        public int compareTo(Individual other) { return Double.compare(other.fitness, this.fitness); }
    }

    // --- MAIN RUN LOOP ---
    public Result run() {
        long startTime = System.nanoTime();
        List<Individual> population = new ArrayList<>();
        for (int i = 0; i < POPULATION_SIZE; i++) population.add(new Individual());
    
        Individual bestGlobal = population.get(0);

        for (int gen = 0; gen < GENERATIONS; gen++) {
            // Eval
            for (Individual ind : population) {
                ind.calculateFitness();
                if (ind.reachedGoal) {
                    if (!bestGlobal.reachedGoal || ind.cost < bestGlobal.cost) bestGlobal = ind;
                } else if (!bestGlobal.reachedGoal && ind.fitness > bestGlobal.fitness) {
                    bestGlobal = ind;
                }
            }
            Collections.sort(population);

            // --- [RESTORED] OPTIMIZATION LOGIC ---
            // ต้องเปิดส่วนนี้ ไม่งั้นมันจะไม่พยายามลด Cost ลงครับ
            if (bestGlobal.reachedGoal) {
                if (bestGlobal.cost < bestAllTimeCost) {
                    bestAllTimeCost = bestGlobal.cost;
                    
                    currentMaxSteps = (int)(bestGlobal.path.size() * 1.05); 
                    
                    optimizationStableCount = 0; 
                    clearPanicHeat();
                    
                    Individual champ = new Individual(cloneGenome(bestGlobal.genome));
                    champ.path = new ArrayList<>(bestGlobal.path);
                    champ.cost = bestGlobal.cost;
                    champ.reachedGoal = true;
                    TheGoat.add(champ);
                } else {
                    optimizationStableCount++;
                }
            }

            // Cleanup Repulsion Map Periodically
            if (gen % 10 == 0) {
                 for(int r=0; r<rows; r++) for(int c=0; c<cols; c++) 
                    if (repulsionMap[r][c] < 1000) repulsionMap[r][c] = 0;
            }
            
            // Stagnation Detection
            if (bestGlobal.fitness == lastBestFitness) stagnationCount++;
            else { stagnationCount = 0; lastBestFitness = bestGlobal.fitness; }

            // --- [RESTORED] TABU & BRANCHING ---
            
            // 1. Branching: ถ้าเก่งแล้วแต่ไม่ขยับ ให้ลองแตกกิ่ง
            if (bestGlobal.reachedGoal && optimizationStableCount == BRANCHING_THRESHOLD) {
                performBranchingExploration(bestGlobal, population);
                if(callback != null) callback.onUpdate(bestGlobal.path, gen, bestGlobal.cost, "BRANCHING...");
                continue;
            }

            // 2. Tabu Reset: ถ้าตันนานเกินไป ให้ระเบิดทิ้งแล้วเริ่มใหม่ (โดยจำทางเก่าเป็น Tabu)
            if (bestGlobal.reachedGoal && optimizationStableCount > TABU_THRESHOLD) {
                Individual goat = new Individual(cloneGenome(bestGlobal.genome));
                goat.path = new ArrayList<>(bestGlobal.path);
                goat.cost = bestGlobal.cost;
                goat.reachedGoal = true;
                TheGoat.add(goat);

                triggerTabuBlock(bestGlobal, 0.4, 0.7);
                
                // Reset Parameters
                optimizationStableCount = 0;
                currentMaxSteps = GENOME_LENGTH; 
                bestAllTimeCost = Integer.MAX_VALUE; 
                bestGlobal = new Individual(); // Start Fresh
                
                population.clear();
                for(int i=0; i<POPULATION_SIZE; i++) population.add(new Individual());
                
                if(callback != null) callback.onUpdate(new ArrayList<>(), gen, 0, "TABU RESET!");
                continue;
            }

            // Visual Update
             if (gen % 5 == 0 || bestGlobal.reachedGoal) { 
                if (callback != null) {
                    String status = bestGlobal.reachedGoal ? "GOAL" : "SEARCH";
                    if(optimizationStableCount > 0) status = "OPT (" + optimizationStableCount + ")";
                    callback.onUpdate(bestGlobal.path, gen, bestGlobal.cost, status);
                    try { Thread.sleep(1); } catch (InterruptedException e) {}
                }
           }
            
            population = createNextGeneration(population);
        }

        long endTime = System.nanoTime();
        Result res = new Result();
        
        // --- FINAL RESULT SELECTION ---
        // เลือกตัวที่ดีที่สุดจาก TheGoat (Hall of Fame)
        // if(!TheGoat.isEmpty()) {
        //     TheGoat.sort(Comparator.comparingInt(a -> a.cost));
        //     Individual ultimate = TheGoat.get(0);
        //     res.reachedGoal = true; 
        //     res.cost = ultimate.cost; 
        //     // ** Apply Smoothing Here **
        //     res.path = smoothPath(ultimate.path); 
        // } 
        // else {
        //     res.reachedGoal = bestGlobal.reachedGoal; 
        //     res.cost = bestGlobal.cost;
        //     res.path = smoothPath(bestGlobal.path); 
        // }
        res.cost = bestGlobal.cost;
        res.path = bestGlobal.path;
        res.reachedGoal = bestGlobal.reachedGoal;
        
        res.timeTaken = (endTime - startTime) / 1e9;
        return res;
    }

    // --- Helper Methods ---

    // [NEW] Path Smoothing: ตัดจุดยึกยักออก
    // private List<int[]> smoothPath(List<int[]> originalPath) {
    //     if (originalPath == null || originalPath.size() < 3) return originalPath;
    //     List<int[]> smoothed = new ArrayList<>();
    //     smoothed.add(originalPath.get(0));
    //     int currentIdx = 0;
        
    //     while (currentIdx < originalPath.size() - 1) {
    //         int bestNextIdx = currentIdx + 1;
    //         // มองข้ามช็อตไปข้างหน้า ถ้าเจอจุดที่อยู่ติดกัน (Distance=1) ให้ลัดไปเลย
    //         for (int i = originalPath.size() - 1; i > currentIdx + 1; i--) {
    //             int[] cur = originalPath.get(currentIdx);
    //             int[] target = originalPath.get(i);
    //             if (Math.abs(cur[0] - target[0]) + Math.abs(cur[1] - target[1]) == 1) {
    //                 bestNextIdx = i;
    //                 break; 
    //             }
    //         }
    //         smoothed.add(originalPath.get(bestNextIdx));
    //         currentIdx = bestNextIdx;
    //     }
    //     return smoothed;
    // }

    private void clearPanicHeat() { for(int[] r : repulsionMap) Arrays.fill(r, 0); }

    private void triggerTabuBlock(Individual target, double startPct, double endPct) {
        if (target.path == null || target.path.isEmpty()) return;
        int startIdx = (int)(target.path.size() * startPct);
        int endIdx = (int)(target.path.size() * endPct);
        for (int i = Math.max(1, startIdx); i < Math.min(target.path.size()-1, endIdx); i++) {
            int[] pos = target.path.get(i);
            repulsionMap[pos[0]][pos[1]] = 5000; 
        }
    }
    
    private void performBranchingExploration(Individual bestInd, List<Individual> pop) {
        List<Individual> newPop = new ArrayList<>();
        for(int i=0; i<ELITISM_COUNT; i++) newPop.add(pop.get(i));

        int branchCandidates = POPULATION_SIZE - ELITISM_COUNT;
        int pathLen = bestInd.path.size();
        
        for (int i = 0; i < branchCandidates; i++) {
            int[][] newGenome = cloneGenome(bestInd.genome);
            // สุ่มจุดที่จะแยกกิ่ง
            int splitPoint = rand.nextInt(Math.max(1, pathLen - 1));
            
            // เปลี่ยนทิศทาง ณ จุดแยก
            int currentDir = newGenome[splitPoint][0];
            newGenome[splitPoint][0] = (currentDir + 1 + rand.nextInt(3)) % 4; // เปลี่ยนเป็นทิศอื่น
            
            // Reset genome หลังจากจุดแยก
            for(int k=splitPoint+1; k<GENOME_LENGTH; k++) {
                List<Integer> dirs = Arrays.asList(0, 1, 2, 3);
                Collections.shuffle(dirs, rand);
                for(int p=0; p<4; p++) newGenome[k][p] = dirs.get(p);
            }
            newPop.add(new Individual(newGenome));
        }
        pop.clear();
        pop.addAll(newPop);
        clearPanicHeat();
    }

    private List<Individual> createNextGeneration(List<Individual> currentPop) {
        List<Individual> nextGen = new ArrayList<>();
        for (int i = 0; i < ELITISM_COUNT; i++) nextGen.add(new Individual(cloneGenome(currentPop.get(i).genome)));
        while (nextGen.size() < POPULATION_SIZE) {
            // ถ้าติดขัดมาก ให้เพิ่มอัตราเลือดใหม่ (Random)
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
        for(int i=0; i<GENOME_LENGTH; i++) if(rand.nextDouble() < MUTATION_RATE) {
            int a=rand.nextInt(4), b=rand.nextInt(4);
            int t=genes[i][a]; genes[i][a]=genes[i][b]; genes[i][b]=t;
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