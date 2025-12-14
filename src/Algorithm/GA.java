package Algorithm;

import java.util.*;

public class GA {
    private boolean useSmoothPath = true;
    private boolean useRefineChildren = true;     
    private boolean useShortcut = true;     
    private boolean useLoopCutting = true;  
    private boolean useBacktracking = true; 

    private int POPULATION_SIZE = 500; 
    private int GENERATIONS = 1000;   
    private int ELITISM_COUNT = 50;   
    private double MUTATION_RATE = 0.05; 
    private double SCORE_GOAL_REACHED = 1_000_000.0;

    public static final int UP    = 0;
    public static final int DOWN  = 1;
    public static final int LEFT  = 2;
    public static final int RIGHT = 3;

    private static final int[][] MOVES = { 
        { -1, 0 }, { 1, 0 }, { 0, -1 }, { 0, 1 }   
    };
    
    private int[][] grid;
    private int rows, cols; 
    private int[] startPoint, goalPoint; 
    private Random rand = new Random();

    private int stagnationCount = 0;
    private int currentMaxSteps; 
    private int bestAllTimeCost = Integer.MAX_VALUE;
    
    private boolean[][] cumulativeDeadEnds;
    private VisualizationCallback callback;
    public int GENOME_LENGTH;

    public void setMemeticSettings(boolean useSmooth, boolean useRefine) {
        this.useSmoothPath = useSmooth;
        this.useRefineChildren = useRefine;
    }

    public void setHeuristics(boolean shortcut, boolean loopCut, boolean backtrack) {
        this.useShortcut = shortcut;
        this.useLoopCutting = loopCut;
        this.useBacktracking = backtrack;
    }
    
    public GA(int[][] grid, int height, int width, int[] start, int[] goal) {
        this.grid = grid;
        this.rows = height; 
        this.cols = width;   
        this.startPoint = start; 
        this.goalPoint = goal;    
        this.GENOME_LENGTH = 2*(height * width); 
        currentMaxSteps = GENOME_LENGTH;
        this.cumulativeDeadEnds = new boolean[rows][cols];
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
        List<int[]> path;
        int[][] currentPathStatus; 

        public Individual() {
            genome = new int[GENOME_LENGTH][4];
            path = new ArrayList<>();
            for (int i = 0; i < GENOME_LENGTH; i++) {
                List<Integer> dirs = new ArrayList<>(Arrays.asList(UP, DOWN, LEFT, RIGHT));
                Collections.shuffle(dirs, rand);
                for (int p = 0; p < 4; p++) { genome[i][p] = dirs.get(p); }
            }
        }
        public Individual(int[][] Genes) {
            this.genome = Genes;
            this.path = new ArrayList<>();
        }

        public void calculateFitness() {
            int curR = startPoint[0];
            int curC = startPoint[1];
            cost = 0;
            double penalty =0;
            path.clear();
            currentPathStatus = new int[rows][cols];
            path.add(new int[] { curR, curC });
            currentPathStatus[curR][curC] = 1; 
            reachedGoal = false;
            int stepLimit = 0;



            if (!reachedGoal && bestAllTimeCost == Integer.MAX_VALUE) {
                stepLimit = GENOME_LENGTH;
            }else stepLimit = currentMaxSteps;

            for (int step = 0; step < stepLimit; step++) {
                boolean moved = false;
                for (int p = 0; p < 4; p++) {
                    int moveIndex = genome[step][p];
                    int nextR = curR + MOVES[moveIndex][0];
                    int nextC = curC + MOVES[moveIndex][1];

                    if (!isValidPosition(nextR, nextC)) continue;


                    if (currentPathStatus[nextR][nextC] > 0) {
                        if (useLoopCutting && currentPathStatus[nextR][nextC] == 1) {
                            int cutIndex = -1;
                            for (int i = 0; i < path.size(); i++) {
                                if (path.get(i)[0] == nextR && path.get(i)[1] == nextC) {
                                    cutIndex = i; break;
                                }
                            }
                            if (cutIndex != -1) {
                                while (path.size() > cutIndex + 1) {
                                    int[] removedNode = path.remove(path.size() - 1);
                                    currentPathStatus[removedNode[0]][removedNode[1]] = 0; 
                                    cost -= grid[removedNode[0]][removedNode[1]]; 
                                }
                                penalty += 10;
                                curR = nextR; curC = nextC; moved = true; 
                                break; 
                            }
                        }
                        continue;
                    }



                    cost += grid[nextR][nextC]; 
                    curR = nextR; curC = nextC;
                    currentPathStatus[curR][curC] = 1;
                    path.add(new int[] { curR, curC });
                    moved = true;

                    if (useShortcut) {
                        int bestShortcutIndex = -1;
                        for (int[] m : MOVES) {
                            int checkR = curR + m[0];
                            int checkC = curC + m[1];
                            if (isValidPosition(checkR, checkC) && currentPathStatus[checkR][checkC] == 1) {
                                for (int i = 0; i < path.size() - 2; i++) { 
                                    int[] pNode = path.get(i);
                                    if (pNode[0] == checkR && pNode[1] == checkC) {
                                        if (bestShortcutIndex == -1 || i < bestShortcutIndex) {
                                            bestShortcutIndex = i;
                                        }
                                        break; 
                                    }
                                }
                            }
                        }
                        if (bestShortcutIndex != -1) {
                            while (path.size() > bestShortcutIndex + 2) { 
                                int removeIndex = path.size() - 2; 
                                int[] removedNode = path.remove(removeIndex);
                                cost -= grid[removedNode[0]][removedNode[1]];
                                currentPathStatus[removedNode[0]][removedNode[1]] = 0; 
                            }
                            penalty += 20;
                        }
                    } 




                    if (curR == goalPoint[0] && curC == goalPoint[1]) reachedGoal = true; 
                    break; 
                }
                


                if (useBacktracking && !moved && !reachedGoal) {
                    while (path.size() > 1) {
                        if (hasWay(curR, curC)) break;
                        int[] deadNode = path.remove(path.size() - 1);
                        currentPathStatus[deadNode[0]][deadNode[1]] = 2; 
                        cost -= grid[deadNode[0]][deadNode[1]];
                        cumulativeDeadEnds[deadNode[0]][deadNode[1]] = true;
                        penalty += 50;
                        int[] prevNode = path.get(path.size() - 1);
                        curR = prevNode[0]; curC = prevNode[1];
                        
                    }
                }




                if (reachedGoal) break;
            }



            if (reachedGoal) {
                fitness = SCORE_GOAL_REACHED + (SCORE_GOAL_REACHED / (cost + penalty + 1)); 
            } else {
                double distFromStart = Math.abs(curR - startPoint[0]) + Math.abs(curC - startPoint[1]);
                
                fitness = distFromStart * distFromStart; 
            }





        }

        private boolean hasWay(int r, int c) {
            for (int[] m : MOVES) {
                int nr = r + m[0];
                int nc = c + m[1];
                if (nr >= 0 && nr < rows && nc >= 0 && nc < cols && 
                    grid[nr][nc] != -1 && currentPathStatus[nr][nc] == 0) return true;
            }
            return false;
        }

        private boolean isValidPosition(int r, int c) {
            return r >= 0 && r < rows && c >= 0 && c < cols && grid[r][c] != -1;
        }
        @Override
        public int compareTo(Individual other) { return Double.compare(other.fitness, this.fitness); }
    }

    public Result run() {
        long startTime = System.nanoTime();
        List<Individual> population = new ArrayList<>();
        int lastReportedCost = Integer.MAX_VALUE;
        double lastFitness = -1;
        
        for (int i = 0; i < POPULATION_SIZE; i++) population.add(new Individual());
        Individual bestGlobal = population.get(0);

        for (int gen = 0; gen < GENERATIONS; gen++) {
            if (Thread.currentThread().isInterrupted()) break; 

            for (Individual ind : population) {
                ind.calculateFitness();
                if (ind.reachedGoal) {
                    if (!bestGlobal.reachedGoal || ind.cost < bestGlobal.cost) {
                        bestGlobal = ind; 
                    }
                } else if (!bestGlobal.reachedGoal && ind.fitness > bestGlobal.fitness) {
                    bestGlobal = ind;
                }
            }
            Collections.sort(population);

            if (Math.abs(bestGlobal.fitness - lastFitness) < 0.000001) {
                stagnationCount++;
            } else {
                stagnationCount = 0;
                lastFitness = bestGlobal.fitness;
            }
            
            List<int[]> displayPath = bestGlobal.path;
            int displayCost = bestGlobal.cost;

            if (useSmoothPath && bestGlobal.reachedGoal) {
                List<int[]> smoothed = smoothPath(bestGlobal.path);
                int optimizedCost = 0;
                for (int[] node : smoothed) optimizedCost += grid[node[0]][node[1]]; 
                optimizedCost -= grid[startPoint[0]][startPoint[1]]; 
                displayPath = smoothed;
                displayCost = optimizedCost;
            }

            double currentTimeElapsed = (System.nanoTime() - startTime) / 1e9;
            boolean isNewBest = displayCost < lastReportedCost;
            
            if (isNewBest) {
                lastReportedCost = displayCost;
            }

            if (callback != null) {
                String status = bestGlobal.reachedGoal ? "REACHED" : "SEARCHING";
                if (useSmoothPath && bestGlobal.reachedGoal) status = "SMOOTHED";
                
                List<int[]> allDeadEnds = new ArrayList<>();
                for(int r=0; r<rows; r++){
                    for(int c=0; c<cols; c++) if(cumulativeDeadEnds[r][c]) allDeadEnds.add(new int[]{r, c});
                }
                
                callback.onUpdate(displayPath, gen + 1, displayCost, currentTimeElapsed, status, allDeadEnds, isNewBest);
                
                try { Thread.sleep(1); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            }
            population = createNextGeneration(population, bestGlobal);
        }

        long endTime = System.nanoTime();
        Result result = new Result();
        result.reachedGoal = bestGlobal.reachedGoal; 
        
        if (useSmoothPath && bestGlobal.reachedGoal) {
             List<int[]> smoothed = smoothPath(bestGlobal.path);
             int optimizedCost = 0;
             for (int[] node : smoothed) optimizedCost += grid[node[0]][node[1]]; 
             optimizedCost -= grid[startPoint[0]][startPoint[1]]; 
             result.cost = optimizedCost;
             result.path = smoothed;
        } else {
             result.cost = bestGlobal.cost;
             result.path = bestGlobal.path;
        }
        result.timeTaken = (endTime - startTime) / 1e9;
        return result;
    }
    
    private List<Individual> createNextGeneration(List<Individual> currentPop, Individual bestGlobal) {
        List<Individual> nextGen = new ArrayList<>();
        for (int i = 0; i < ELITISM_COUNT; i++) {
            Individual elite = currentPop.get(i);
            nextGen.add(new Individual(cloneGenome(elite.genome)));
        }
        while (nextGen.size() < POPULATION_SIZE) {
            if (useRefineChildren && bestGlobal != null && bestGlobal.reachedGoal && rand.nextDouble() < 0.1) {
                List<Individual> mutants = createRefinedChildren(bestGlobal, 1);
                nextGen.addAll(mutants);
                continue;
            }
            double newBloodRate = 0.08;
            if (stagnationCount > 40) newBloodRate = 0.4;
            if (rand.nextDouble() < newBloodRate) {
                nextGen.add(new Individual()); 
            } else {
                Individual p1 = currentPop.get(rand.nextInt(currentPop.size()/2));
                Individual p2 = currentPop.get(rand.nextInt(currentPop.size()/2));
                int[][] childGenes = Crossover(p1, p2);
                Mutation(childGenes);
                nextGen.add(new Individual(childGenes));
            }
        }
        return nextGen;
    }
    
    private int[][] Crossover(Individual p1, Individual p2) {
        int cut = rand.nextInt(GENOME_LENGTH);
        int[][] child = new int[GENOME_LENGTH][4];
        for(int i=0; i<cut; i++) child[i] = p1.genome[i].clone();
        for(int i=cut; i<GENOME_LENGTH; i++) child[i] = p2.genome[i].clone();
        return child;
    }
    
    private void Mutation(int[][] genes) {
        for(int i=0; i<GENOME_LENGTH; i++){
            if(rand.nextDouble() < MUTATION_RATE) {
                int a=rand.nextInt(4), b=rand.nextInt(4);
                int t=genes[i][a];
                genes[i][a]=genes[i][b];
                genes[i][b]=t;
            }
        }
    }

    private List<Individual> createRefinedChildren(Individual bestGlobal, int count) {
        List<Individual> refinedChildren = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            int[][] refinedGenome = cloneGenome(bestGlobal.genome);
            int mutationPoint = rand.nextInt(GENOME_LENGTH); 
            int originalMove = refinedGenome[mutationPoint][0];
            int newMove = rand.nextInt(4);
            while (newMove == originalMove) { newMove = rand.nextInt(4); }
            refinedGenome[mutationPoint][0] = newMove;
            if (rand.nextDouble() < 0.3) { 
                for (int k = mutationPoint + 1; k < GENOME_LENGTH; k++) {
                    List<Integer> dirs = Arrays.asList(0, 1, 2, 3);
                    Collections.shuffle(dirs, rand);
                    for(int p=0; p<4; p++) refinedGenome[k][p] = dirs.get(p);
                }
            }
            refinedChildren.add(new Individual(refinedGenome));
        }
        return refinedChildren;
    }

    private int[][] cloneGenome(int[][] original) {
        int[][] copy = new int[original.length][];
        for(int i=0; i<original.length; i++) copy[i] = original[i].clone();
        return copy;
    }

    public List<int[]> smoothPath(List<int[]> originalPath) {
        if (originalPath.size() < 3) return originalPath;
        List<int[]> smoothedPath = new ArrayList<>();
        smoothedPath.add(originalPath.get(0));
        int currentCheckIndex = 0;
        while (currentCheckIndex < originalPath.size() - 1) {
            boolean foundShortcut = false;
            for (int lookAheadIndex = originalPath.size() - 1; lookAheadIndex > currentCheckIndex + 1; lookAheadIndex--) {
                int[] startNode = originalPath.get(currentCheckIndex);
                int[] endNode = originalPath.get(lookAheadIndex);
                if (canWalkDirectly(startNode, endNode)) {
                    List<int[]> segment = generateDirectPath(startNode, endNode);
                    for (int i = 1; i < segment.size(); i++) smoothedPath.add(segment.get(i));
                    currentCheckIndex = lookAheadIndex;
                    foundShortcut = true;
                    break;
                }
            }
            if (!foundShortcut) {
                currentCheckIndex++;
                if (currentCheckIndex < originalPath.size()) smoothedPath.add(originalPath.get(currentCheckIndex));
            }
        }
        return smoothedPath;
    }

    private boolean canWalkDirectly(int[] p1, int[] p2) {
        int r1 = p1[0], c1 = p1[1];
        int r2 = p2[0], c2 = p2[1];
        if (r1 == r2) return isClearPath(r1, c1, r2, c2, true); 
        if (c1 == c2) return isClearPath(r1, c1, r2, c2, false); 
        boolean path1Clear = isClearPath(r1, c1, r2, c1, false) && isClearPath(r2, c1, r2, c2, true);
        boolean path2Clear = isClearPath(r1, c1, r1, c2, true) && isClearPath(r1, c2, r2, c2, false);
        return path1Clear || path2Clear;
    }

    private boolean isClearPath(int rStart, int cStart, int rEnd, int cEnd, boolean horizontal) {
        if (horizontal) {
            int minC = Math.min(cStart, cEnd);
            int maxC = Math.max(cStart, cEnd);
            for (int c = minC; c <= maxC; c++) {
                if (grid[rStart][c] == -1) return false; 
            }
        } else { 
            int minR = Math.min(rStart, rEnd);
            int maxR = Math.max(rStart, rEnd);
            for (int r = minR; r <= maxR; r++) {
                if (grid[r][cStart] == -1) return false; 
            }
        }
        return true;
    }

    private List<int[]> generateDirectPath(int[] p1, int[] p2) {
        List<int[]> pathSegment = new ArrayList<>();
        pathSegment.add(p1);
        int startR = p1[0], startC = p1[1];
        int targetR = p2[0], targetC = p2[1];
        boolean rowFirstValid = isClearPath(startR, startC, targetR, startC, false) && isClearPath(targetR, startC, targetR, targetC, true);
        int curR = startR;
        int curC = startC;
        if (rowFirstValid) {
            while (curR != targetR) { if (curR < targetR) curR++; else curR--; pathSegment.add(new int[]{curR, curC}); }
            while (curC != targetC) { if (curC < targetC) curC++; else curC--; pathSegment.add(new int[]{curR, curC}); }
        } else {
            while (curC != targetC) { if (curC < targetC) curC++; else curC--; pathSegment.add(new int[]{curR, curC}); }
            while (curR != targetR) { if (curR < targetR) curR++; else curR--; pathSegment.add(new int[]{curR, curC}); }
        }
        return pathSegment;
    }

    public interface VisualizationCallback {
        void onUpdate(List<int[]> path, int gen, int cost, double timeElapsed, String status, List<int[]> deadEnds, boolean isNewBest);
    }
    public void setCallback(VisualizationCallback cb) { this.callback = cb; }
}