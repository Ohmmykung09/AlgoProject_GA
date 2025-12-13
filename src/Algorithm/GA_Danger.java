// package Algorithm;

// import java.util.*;

// public class GA_Danger {
//     //memetic Mode
//     private boolean useMemetic = true;

//     // --- Configuration ---
//     private int POPULATION_SIZE = 500; 
//     private int GENERATIONS = 1000;   
//     private int ELITISM_COUNT = 20;   
//     private double MUTATION_RATE = 0.6; 
//     private double NAUGHTY_RATE = 0; 
//     private double SCORE_GOAL_REACHED = 1_000_000.0;
//     // private double PENALTY_COST = 5000;

//     // --- Direction Constants ---
//     public static final int UP    = 0;
//     public static final int DOWN  = 1;
//     public static final int LEFT  = 2;
//     public static final int RIGHT = 3;

//     private static final int[][] MOVES = { 
//         { -1, 0 }, // 0: UP
//         { 1, 0 },  // 1: DOWN
//         { 0, -1 }, // 2: LEFT
//         { 0, 1 }   // 3: RIGHT
//     };
//     
//     private int[][] grid;
//     private int rows, cols; 
//     private int[] startPoint, goalPoint; 
//     private Random rand = new Random();

//     private int stagnationCount = 0;
//     private int currentMaxSteps; 
//     private int optimizationStableCount = 0; 
//     private int bestAllTimeCost = Integer.MAX_VALUE;
//     
//     public List<Individual> TheGoat = new ArrayList<>(); 
//     private VisualizationCallback callback;
//     public int GENOME_LENGTH;

//     
//     public void setMemetic(boolean enable) {
//         this.useMemetic = enable;
//     }
//     
//     // Constructor
//     public GA(int[][] grid, int height, int width, int[] start, int[] goal) {
//         this.grid = grid;
//         this.rows = height; 
//         this.cols = width;   
//         
//         this.startPoint = start; 
//         this.goalPoint = goal;    
//         
//         this.GENOME_LENGTH = 5* (height * width); 
//         currentMaxSteps = GENOME_LENGTH;
//     }
//     
//     public void setParameters(int popSize, int gens, int elitism, double mutation) {
//         this.POPULATION_SIZE = popSize;
//         this.GENERATIONS = gens;
//         this.ELITISM_COUNT = elitism;
//         this.MUTATION_RATE = mutation;
//     }

//     public static class Result {
//         public boolean reachedGoal;
//         public int cost;
//         public List<int[]> path; 
//         public double timeTaken;
//     }

//     private class Individual implements Comparable<Individual> {
//         int[][] genome; 
//         int cost;
//         double fitness;
//         boolean reachedGoal;
//         List<int[]> path;

//         // --- เพิ่ม: ความจำส่วนตัว (Memory Map) ---
//         // true = จำได้ว่าเป็นทางตัน/ห้ามเข้า
//         int[][] dangerMemory; 
//         int[][] currentPathStatus; 

//         public Individual() {
//             genome = new int[GENOME_LENGTH][4];
//             dangerMemory = new int[rows][cols]; // สร้างสมองเปล่าๆ
//             path = new ArrayList<>();
//             
//             for (int i = 0; i < GENOME_LENGTH; i++) {
//                 List<Integer> dirs = new ArrayList<>(Arrays.asList(UP, DOWN, LEFT, RIGHT));
//                 Collections.shuffle(dirs, rand);
//                 for (int p = 0; p < 4; p++) {
//                     genome[i][p] = dirs.get(p);
//                 }
//             }
//         }

//         public Individual(int[][] Genes, int[][] Memory) {
//             this.genome = Genes;
//             this.dangerMemory = Memory; // รับความจำที่รวมมาแล้ว
//             this.path = new ArrayList<>();
//         }

//         public void calculateFitness() {
//             int curR = startPoint[0];
//             int curC = startPoint[1];
//             
//             cost = 0;
//             path.clear();
//             currentPathStatus = new int[rows][cols];
//             
//             path.add(new int[] { curR, curC });
//             currentPathStatus[curR][curC] = 1; 
//             
//             reachedGoal = false;
//             int stepLimit = (!reachedGoal && bestAllTimeCost == Integer.MAX_VALUE) ? GENOME_LENGTH : currentMaxSteps; 

//             for (int step = 0; step < stepLimit; step++) {
//                 boolean moved = false;
//                 
//                 for (int p = 0; p < 4; p++) {
//                     int moveIndex = genome[step][p];
//                     
//                     int nextR = curR + MOVES[moveIndex][0];
//                     int nextC = curC + MOVES[moveIndex][1];

//                     if (!isValidPosition(nextR, nextC)) continue;
//                     
//                     if (currentPathStatus[nextR][nextC] > 0) {
//                         int cutIndex = -1;
//                         for (int i = 0; i < path.size(); i++) {
//                             if (path.get(i)[0] == nextR && path.get(i)[1] == nextC) {
//                                 cutIndex = i;
//                                 break;
//                             }
//                         }
//                         if (cutIndex != -1) {
//                             
//                             while (path.size() > cutIndex + 1) {
//                                 int[] removedNode = path.remove(path.size() - 1);
//                                 currentPathStatus[removedNode[0]][removedNode[1]] = 0; 
//                                 cost -= grid[removedNode[0]][removedNode[1]]; 
//                             }
//                             
//                             curR = nextR;
//                             curC = nextC;
//                             moved = true; 
//                             break; 
//                         }
//                     }

//                     int danger = dangerMemory[nextR][nextC];
//                     if (danger > 0) {
//                         double passChange = 10.0 / (10.0+danger);
//                         if (rand.nextDouble() > passChange) {
//                             continue;
//                         }
//                     }
//                     
//                     cost += grid[nextR][nextC]; 
//                     
//                     curR = nextR;
//                     curC = nextC;
//                     currentPathStatus[curR][curC] = 1; // Mark ว่าเดินแล้ว
//                     path.add(new int[] { curR, curC });
//                     moved = true;


//                     //shortcut
//                     int bestShortcutIndex = -1;

//                     // ลองมองดู 4 ทิศรอบตัวปัจจุบัน (curR, curC)
//                     for (int[] m : MOVES) {
//                         int checkR = curR + m[0];
//                         int checkC = curC + m[1];

//                         // ถ้าทิศนั้น ไม่ใช่กำแพง และ เคยเดินผ่านมาแล้ว (อยู่ใน Path)
//                         if (isValidPosition(checkR, checkC) && currentPathStatus[checkR][checkC] > 0) {
//                             
//                             // วนลูปหาว่าจุด checkR, checkC มันอยู่ลำดับที่เท่าไหร่ใน path
//                             for (int i = 0; i < path.size() - 2; i++) { 
//                                 // (-2 เพราะเราไม่นับจุดล่าสุดที่เพิ่งเดินมา กับจุดก่อนหน้ามัน)
//                                 
//                                 int[] pNode = path.get(i);
//                                 if (pNode[0] == checkR && pNode[1] == checkC) {
//                                     // เจอแล้ว! จุดนี้เคยเดินผ่านตอน step ที่ i
//                                     // เราจะเลือกจุดที่ "เก่าแก่ที่สุด" (Index น้อยสุด) เพื่อให้ตัดได้เยอะสุด
//                                     if (bestShortcutIndex == -1 || i < bestShortcutIndex) {
//                                         bestShortcutIndex = i;
//                                     }
//                                     break; // เจอ index ของจุดนี้แล้ว หยุดหาใน path
//                                 }
//                             }
//                         }
//                     }

//                     // ถ้าเจอทางลัด (bestShortcutIndex != -1) -> ลงมือตัดเส้นทาง!
//                     if (bestShortcutIndex != -1) {
//                         // เส้นทางที่เราต้องการคือ: 0...bestShortcutIndex -> ตัวปัจจุบัน
//                         // สิ่งที่ต้องลบคือ: bestShortcutIndex+1 ... จนถึงตัวก่อนปัจจุบัน
//                         
//                         // ลบย้อนหลังจากท้ายแถว มาจนถึงจุดที่เชื่อมต่อ
//                         while (path.size() > bestShortcutIndex + 2) { // +2 คือเก็บตัว best ไว้ และเก็บตัวปัจจุบันไว้
//                             // ลบตัวรองสุดท้ายออก (เพราะตัวสุดท้ายคือ curR, curC ห้ามลบ)
//                             int removeIndex = path.size() - 2; 
//                             int[] removedNode = path.remove(removeIndex);
//                             
//                             // คืนค่า Cost และ Status
//                             cost -= grid[removedNode[0]][removedNode[1]];
//                             currentPathStatus[removedNode[0]][removedNode[1]] = 0; // เคลียร์พื้นที่ให้เดินใหม่ได้
//                         }
//                         // ตอนนี้ path จะกระโดดจาก bestShortcutIndex -> มา curR, curC เลย
//                     }
//                     
//                     if (curR == goalPoint[0] && curC == goalPoint[1]) reachedGoal = true; 
//                     break; 
//                 }
//                 
//                 if (!moved) {
//                     // ถ้าขยับไม่ได้เลย (ตันทุกทิศ หรือ ติด Memory)
//                     if (!reachedGoal) {
//                         dangerMemory[curR][curC] += 5;
//                         
//                         if (dangerMemory[curR][curC] > 100) {
//                             dangerMemory[curR][curC] = 100;
//                         }
//                         
//                         fitness = 0.001; 
//                         return; 
//                     }
//                     break;
//                 }
//                 if (reachedGoal) break;
//             }

//             if (reachedGoal) {
//                 fitness = SCORE_GOAL_REACHED + (10_000_000.0 / (cost + 1));
//             } else {
//                 double dist = Math.abs(curR - goalPoint[0]) + Math.abs(curC - goalPoint[1]);
//                 fitness = 10000.0 / (dist * dist + 1); 
//             }
//         }

//         private boolean isValidPosition(int r, int c) {
//             return r >= 0 && r < rows && c >= 0 && c < cols && grid[r][c] != -1;
//         }
//         
//         @Override
//         public int compareTo(Individual other) { return Double.compare(other.fitness, this.fitness); }
//     }

//     //TODO : Run Main GA ตรงนี้
//     public Result run() {
//         
//         long startTime = System.nanoTime();
//         List<Individual> population = new ArrayList<>();
//         double lastFitness = -1;
//         
//         for (int i = 0; i < POPULATION_SIZE; i++) population.add(new Individual());
//         Individual bestGlobal = population.get(0);

//         for (int gen = 0; gen < GENERATIONS; gen++) {
//             for (Individual ind : population) {
//                 ind.calculateFitness();
//                 if (ind.reachedGoal) {
//                     if (!bestGlobal.reachedGoal || ind.cost < bestGlobal.cost) {
//                         bestGlobal = ind; 
//                     }
//                 } else if (!bestGlobal.reachedGoal && ind.fitness > bestGlobal.fitness) {
//                     bestGlobal = ind;
//                 }
//             }
//             Collections.sort(population);

//             if (Math.abs(bestGlobal.fitness - lastFitness) < 0.000001) {
//                 stagnationCount++;
//             } else {
//                 // ถ้าพัฒนาขึ้น (Fitness เปลี่ยน) ให้รีเซ็ตเป็น 0
//                 stagnationCount = 0;
//                 lastFitness = bestGlobal.fitness; // จำค่าใหม่ไว้
//             }
//             
//             if (gen % 5 == 0 || bestGlobal.reachedGoal) { 
//                 
//                 List<int[]> displayPath = bestGlobal.path;
//                 int displayCost = bestGlobal.cost;

//                 if (useMemetic && bestGlobal.reachedGoal) {
//                     
//                     List<int[]> smoothed = smoothPath(bestGlobal.path);
//                     
//                     int optimizedCost = 0;
//                     for (int[] node : smoothed) optimizedCost += grid[node[0]][node[1]]; 
//                     optimizedCost -= grid[startPoint[0]][startPoint[1]]; 

//                     bestGlobal.path = smoothed;
//                     bestGlobal.cost = optimizedCost;
//                     
//                     displayPath = smoothed;
//                     displayCost = optimizedCost;
//                 }
//                 if (callback != null) {
//                     String status = bestGlobal.reachedGoal ? "REACHED!" : "SEARCH...";
//                     if(optimizationStableCount > 0) status = "OPTIMIZE (" + optimizationStableCount + ")";
//                     callback.onUpdate(displayPath, gen +1, displayCost, status);
//                     
//                     try { Thread.sleep(1); } catch (InterruptedException e) {}
//                 }
//             }
//             population = createNextGeneration(population, bestGlobal);
//         }

//         long endTime = System.nanoTime();

//         Result result = new Result();
//         result.reachedGoal = bestGlobal.reachedGoal; 
//         result.cost = bestGlobal.cost;
//         result.path = bestGlobal.path;
//         result.timeTaken = (endTime - startTime) / 1e9;
//         
//         return result;
//     }
//     
//     private List<Individual> createNextGeneration(List<Individual> currentPop,Individual bestGlobal) {
//         List<Individual> nextGen = new ArrayList<>();
//         
//         for (int i = 0; i < ELITISM_COUNT; i++) {
//             Individual elite = currentPop.get(i);
//             nextGen.add(new Individual(cloneGenome(elite.genome), cloneMemory(elite.dangerMemory)));
//         }
//         
//         while (nextGen.size() < POPULATION_SIZE) {
//             if (useMemetic && bestGlobal != null && bestGlobal.reachedGoal && rand.nextDouble() < 0.2) {
//                 List<Individual> mutants = createRefinedChildren(bestGlobal, 1);
//                 nextGen.addAll(mutants);
//                 continue;
//             }
//             
//             double newBloodRate = 0.08;
//             if (stagnationCount > 40) {
//                 newBloodRate = 0.4;
//             }
//             if (rand.nextDouble() < newBloodRate) {
//                 nextGen.add(new Individual()); 
//             } else {
//                 Individual p1 = currentPop.get(rand.nextInt(currentPop.size()/2));
//                 Individual p2 = currentPop.get(rand.nextInt(currentPop.size()/2));
//                 
//                 // Crossover ยีน
//                 int[][] childGenes = Crossover(p1, p2);
//                 Mutation(childGenes);
//                 
//                 int[][] childMemory = mergeMemory(p1.dangerMemory, p2.dangerMemory);
//                 
//                 nextGen.add(new Individual(childGenes, childMemory));
//             }
//         }
//         return nextGen;
//     }
//     
//     private int[][] Crossover(Individual p1, Individual p2) {
//         int cut = rand.nextInt(GENOME_LENGTH);
//         int[][] child = new int[GENOME_LENGTH][4];
//         for(int i=0; i<cut; i++){
//             child[i] = p1.genome[i].clone();
//         }
//         for(int i=cut; i<GENOME_LENGTH; i++){
//             child[i] = p2.genome[i].clone();
//         }
//         return child;
//     }
//     
//     private void Mutation(int[][] genes) {
//         for(int i=0; i<GENOME_LENGTH; i++){
//             if(rand.nextDouble() < MUTATION_RATE) {
//                 int a=rand.nextInt(4), b=rand.nextInt(4);
//                 int t=genes[i][a];
//                 genes[i][a]=genes[i][b];
//                 genes[i][b]=t;
//             }
//         }

//     }
//     // เพิ่ม Method นี้ใน Class GA
//     private List<Individual> createRefinedChildren(Individual bestGlobal, int count) {
//         List<Individual> refinedChildren = new ArrayList<>();
//         
//         for (int i = 0; i < count; i++) {
//             int[][] refinedGenome = cloneGenome(bestGlobal.genome);

//             int mutationPoint = rand.nextInt(GENOME_LENGTH); 

//             int originalMove = refinedGenome[mutationPoint][0];
//             int newMove = rand.nextInt(4);
//         
//             while (newMove == originalMove) {
//                 newMove = rand.nextInt(4);
//             }
//             
//             // Assign ทิศใหม่ (ให้เป็น Priority แรก)
//             refinedGenome[mutationPoint][0] = newMove;
//             
//             // *** สำคัญ: การดัดแปลงแบบ Smart ***
//             // ถ้าเราเปลี่ยนทิศก้าวที่ 10... ก้าวที่ 11, 12 อาจจะพัง
//             // เราอาจจะปล่อยให้มัน Random ใหม่ตั้งแต่จุดที่แก้เป็นต้นไปก็ได้
//             // เพื่อให้มันลองหาทางใหม่จากจุดที่เปลี่ยน
//             if (rand.nextDouble() < 0.5) { // 50% ที่จะสุ่มหางใหม่
//                 for (int k = mutationPoint + 1; k < GENOME_LENGTH; k++) {
//                     // สุ่มลำดับทิศใหม่หมดเลยสำหรับส่วนหาง
//                     List<Integer> dirs = Arrays.asList(0, 1, 2, 3);
//                     Collections.shuffle(dirs, rand);
//                     for(int p=0; p<4; p++) refinedGenome[k][p] = dirs.get(p);
//                 }
//             }

//             // สร้างลูกตัวใหม่ พร้อมความจำของตัวเทพ (หรือจะให้ความจำว่างก็ได้ เพื่อลองเสี่ยงดู)
//             refinedChildren.add(new Individual(refinedGenome, cloneMemory(bestGlobal.dangerMemory)));
//         }
//         
//         return refinedChildren;
//     }

//     private int[][] cloneMemory(int[][] original) {
//         int[][] copy = new int[rows][cols];
//         for (int r = 0; r < rows; r++) {
//             copy[r] = original[r].clone();
//         }
//         return copy;
//     }

//     private int[][] mergeMemory(int[][] mem1, int[][] mem2) {
//         int[][] merged = new int[rows][cols];
//         for (int r = 0; r < rows; r++) {
//             for (int c = 0; c < cols; c++) {
//                 Double naughtyChange = rand.nextDouble();
//                 if(naughtyChange < NAUGHTY_RATE){

//                     merged[r][c] = Math.min(mem1[r][c], mem2[r][c]);
//                 }
//                 else{
//                     merged[r][c] = Math.max(mem1[r][c], mem2[r][c]);
//                 }
//             }
//         }
//         return merged;
//     }

//     private int[][] cloneGenome(int[][] original) {
//         int[][] copy = new int[original.length][];
//         for(int i=0; i<original.length; i++) copy[i] = original[i].clone();
//         return copy;
//     }


//     //memetic algo
//     // ฟังก์ชันสำหรับ "รีดเส้นทาง" ให้สั้นที่สุด (Post-Processing)
//     public List<int[]> smoothPath(List<int[]> originalPath) {
//         if (originalPath.size() < 3) return originalPath;

//         List<int[]> smoothedPath = new ArrayList<>();
//         
//         // จุดเริ่มต้นต้องมีเสมอ
//         smoothedPath.add(originalPath.get(0));
//         
//         int currentCheckIndex = 0;
//         
//         while (currentCheckIndex < originalPath.size() - 1) {
//             // เราจะพยายามมองข้ามช็อตไปให้ไกลที่สุดเท่าที่จะทำได้
//             // เริ่มมองจากจุดปลายทาง ย้อนกลับมาหาจุดปัจจุบัน
//             boolean foundShortcut = false;
//             
//             for (int lookAheadIndex = originalPath.size() - 1; lookAheadIndex > currentCheckIndex + 1; lookAheadIndex--) {
//                 int[] startNode = originalPath.get(currentCheckIndex);
//                 int[] endNode = originalPath.get(lookAheadIndex);
//                 
//                 // เช็คว่า: เดินจาก start -> end ตรงๆ (แบบ Manhattan หรือ L-Shape) ได้ไหม โดย cost ไม่แพงกว่าเดิม
//                 // ใน Grid การเดิน "ตรง" คือการเดินแบบ L-Shape เดียว (เลี้ยวทีเดียว)
//                 // ถ้าเดินแบบ L-Shape ได้โดยไม่ชนกำแพง ถือว่าตัดผ่านได้
//                 if (canWalkDirectly(startNode, endNode)) {
//                     // ถ้าตัดผ่านได้ ให้ข้าม node ตรงกลางทั้งหมด!
//                     // แต่เนื่องจากเราต้องเก็บ path ไว้วาด เราต้อง generate path เส้นตรงระหว่างสองจุดนี้ขึ้นมาใหม่
//                     List<int[]> segment = generateDirectPath(startNode, endNode);
//                     
//                     // Add เข้า path ใหม่ (ไม่รวมจุด start เพราะมีแล้ว)
//                     for (int i = 1; i < segment.size(); i++) {
//                         smoothedPath.add(segment.get(i));
//                     }
//                     
//                     // ขยับ index ปัจจุบันไปที่ปลายทางเลย (วาร์ป)
//                     currentCheckIndex = lookAheadIndex;
//                     foundShortcut = true;
//                     break;
//                 }
//             }
//             
//             // ถ้าหาทางลัดไกลๆ ไม่ได้เลย ก็ขยับไปแค่ node ถัดไปตามปกติ
//             if (!foundShortcut) {
//                 currentCheckIndex++;
//                 if (currentCheckIndex < originalPath.size()) {
//                     smoothedPath.add(originalPath.get(currentCheckIndex));
//                 }
//             }
//         }
//         
//         return smoothedPath;
//     }

//     // เช็คว่าเดินแบบ L-Shape (เลี้ยว 1 ที) ระหว่างจุด A กับ B ได้ไหม
//     private boolean canWalkDirectly(int[] p1, int[] p2) {
//         int r1 = p1[0], c1 = p1[1];
//         int r2 = p2[0], c2 = p2[1];
//         
//         // กรณีเดินเส้นตรงแนวนอนหรือแนวตั้ง
//         if (r1 == r2) return isClearPath(r1, c1, r2, c2, true); // แนวนอน
//         if (c1 == c2) return isClearPath(r1, c1, r2, c2, false); // แนวตั้ง
//         
//         // กรณีอยู่คนละมุม (ต้องเลี้ยว 1 ที) มี 2 ทางเลือก (Corner 1 หรือ Corner 2)
//         // ทางเลือก 1: เดินแนวตั้งไปหา r2 ก่อน แล้วค่อยแนวนอนไป c2
//         boolean path1Clear = isClearPath(r1, c1, r2, c1, false) && isClearPath(r2, c1, r2, c2, true);
//         
//         // ทางเลือก 2: เดินแนวนอนไปหา c2 ก่อน แล้วค่อยแนวตั้งไป r2
//         boolean path2Clear = isClearPath(r1, c1, r1, c2, true) && isClearPath(r1, c2, r2, c2, false);
//         
//         return path1Clear || path2Clear;
//     }

//     // เช็คว่ามีกำแพงขวางในเส้นตรงไหม
//     private boolean isClearPath(int rStart, int cStart, int rEnd, int cEnd, boolean horizontal) {
//         if (horizontal) {
//             int minC = Math.min(cStart, cEnd);
//             int maxC = Math.max(cStart, cEnd);
//             for (int c = minC; c <= maxC; c++) {
//                 if (grid[rStart][c] == -1) return false; // ชนกำแพง
//             }
//         } else { // vertical
//             int minR = Math.min(rStart, rEnd);
//             int maxR = Math.max(rStart, rEnd);
//             for (int r = minR; r <= maxR; r++) {
//                 if (grid[r][cStart] == -1) return false; // ชนกำแพง
//             }
//         }
//         return true;
//     }

//     // สร้าง Path เส้นใหม่ที่เป็นเส้นตรง (L-Shape)
//     private List<int[]> generateDirectPath(int[] p1, int[] p2) {
//         List<int[]> pathSegment = new ArrayList<>();
//         pathSegment.add(p1);
//         
//         int startR = p1[0], startC = p1[1];
//         int targetR = p2[0], targetC = p2[1];
//         
//         // เราต้องเช็คอีกทีว่า ทางแบบไหนที่เดินได้ (Row-First หรือ Col-First)
//         
//         // ลองเช็คแบบ Row-First (ปรับ Row ก่อน แล้วค่อยปรับ Col) -> เดินตั้ง แล้วค่อย นอน
//         boolean rowFirstValid = isClearPath(startR, startC, targetR, startC, false) && // เช็คแนวตั้ง
//                                 isClearPath(targetR, startC, targetR, targetC, true);  // เช็คแนวนอนจากจุดหักมุม

//         int curR = startR;
//         int curC = startC;

//         if (rowFirstValid) {
//             // แบบที่ 1: ปรับ Row ก่อน (เดินแนวตั้งไปหา targetR)
//             while (curR != targetR) {
//                 curR += (curR < targetR) ? 1 : -1;
//                 pathSegment.add(new int[]{curR, curC});
//             }
//             // แล้วค่อยปรับ Col (เดินแนวนอนไปหา targetC)
//             while (curC != targetC) {
//                 curC += (curC < targetC) ? 1 : -1;
//                 pathSegment.add(new int[]{curR, curC});
//             }
//         } else {
//             // แบบที่ 2: ปรับ Col ก่อน (เดินแนวนอนไปหา targetC)
//             while (curC != targetC) {
//                 curC += (curC < targetC) ? 1 : -1;
//                 pathSegment.add(new int[]{curR, curC});
//             }
//             // แล้วค่อยปรับ Row (เดินแนวตั้งไปหา targetR)
//             while (curR != targetR) {
//                 curR += (curR < targetR) ? 1 : -1;
//                 pathSegment.add(new int[]{curR, curC});
//             }
//         }
//         
//         return pathSegment;
//     }

//     public interface VisualizationCallback {
//         void onUpdate(List<int[]> path, int gen, int cost, String status);
//     }
//     public void setCallback(VisualizationCallback cb) { this.callback = cb; }
// }