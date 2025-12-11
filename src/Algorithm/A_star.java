package Algorithm;

import java.util.*;

public class A_star {
    private int[][] GRID;
    private int ROWS, COLS;
    private int[] START, GOAL;
    private final int[][] MOVES = { { -1, 0 }, { 1, 0 }, { 0, -1 }, { 0, 1 } };

    public A_star(int[][] grid, int rows, int cols, int[] start, int[] goal) {
        this.GRID = grid;
        this.ROWS = rows;
        this.COLS = cols;
        this.START = start;
        this.GOAL = goal;
    }

    public static class Result {
        public boolean success;
        public int cost;
        public List<int[]> path;
        public int visitedCount;
        public double timeTaken;
    }

    private class Node implements Comparable<Node> {
        int r, c, g, f;
        Node parent;

        public Node(int r, int c, int g, int f, Node parent) {
            this.r = r;
            this.c = c;
            this.g = g;
            this.f = f;
            this.parent = parent;
        }

        @Override
        public int compareTo(Node o) {
            return Integer.compare(this.f, o.f);
        }
    }

    public Result run() {
        long startTime = System.nanoTime();

        PriorityQueue<Node> pq = new PriorityQueue<>();
        pq.add(new Node(START[0], START[1], 0, heuristic(START[0], START[1]), null));

        int[][] gScores = new int[ROWS][COLS];
        for (int[] row : gScores)
            Arrays.fill(row, Integer.MAX_VALUE);
        gScores[START[0]][START[1]] = 0;

        int visitedCount = 0;
        Node finalNode = null;

        while (!pq.isEmpty()) {
            Node curr = pq.poll();
            visitedCount++;

            if (curr.r == GOAL[0] && curr.c == GOAL[1]) {
                finalNode = curr;
                break;
            }

            if (curr.g > gScores[curr.r][curr.c])
                continue;

            for (int[] m : MOVES) {
                int nr = curr.r + m[0];
                int nc = curr.c + m[1];

                if (nr >= 0 && nr < ROWS && nc >= 0 && nc < COLS && GRID[nr][nc] != -1) {
                    int newG = curr.g + GRID[nr][nc];
                    if (newG < gScores[nr][nc]) {
                        gScores[nr][nc] = newG;
                        int newF = newG + heuristic(nr, nc);
                        pq.add(new Node(nr, nc, newG, newF, curr));
                    }
                }
            }
        }

        long endTime = System.nanoTime();
        Result res = new Result();
        res.timeTaken = (endTime - startTime) / 1e9;
        res.visitedCount = visitedCount;

        if (finalNode != null) {
            res.success = true;
            res.cost = finalNode.g;
            res.path = new ArrayList<>();
            Node temp = finalNode;
            while (temp != null) {
                res.path.add(0, new int[] { temp.r, temp.c });
                temp = temp.parent;
            }
        } else {
            res.success = false;
            res.path = new ArrayList<>();
        }
        return res;
    }

    private int heuristic(int r, int c) {
        return Math.abs(r - GOAL[0]) + Math.abs(c - GOAL[1]);
    }
}