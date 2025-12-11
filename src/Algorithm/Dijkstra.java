package Algorithm;

import java.util.*;

public class Dijkstra {
    private int[][] GRID;
    private int ROWS, COLS;
    private int[] START, GOAL;
    private final int[][] MOVES = { { -1, 0 }, { 1, 0 }, { 0, -1 }, { 0, 1 } };

    public Dijkstra(int[][] grid, int rows, int cols, int[] start, int[] goal) {
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
        int r, c, cost;
        Node parent;

        public Node(int r, int c, int cost, Node parent) {
            this.r = r;
            this.c = c;
            this.cost = cost;
            this.parent = parent;
        }

        @Override
        public int compareTo(Node o) {
            return Integer.compare(this.cost, o.cost);
        }
    }

    public Result run() {
        long startTime = System.nanoTime();

        PriorityQueue<Node> pq = new PriorityQueue<>();
        pq.add(new Node(START[0], START[1], 0, null));

        int[][] minCosts = new int[ROWS][COLS];
        for (int[] row : minCosts)
            Arrays.fill(row, Integer.MAX_VALUE);
        minCosts[START[0]][START[1]] = 0;

        int visitedCount = 0;
        Node finalNode = null;

        while (!pq.isEmpty()) {
            Node curr = pq.poll();
            visitedCount++;

            if (curr.r == GOAL[0] && curr.c == GOAL[1]) {
                finalNode = curr;
                break;
            }

            if (curr.cost > minCosts[curr.r][curr.c])
                continue;

            for (int[] m : MOVES) {
                int nr = curr.r + m[0];
                int nc = curr.c + m[1];

                if (nr >= 0 && nr < ROWS && nc >= 0 && nc < COLS && GRID[nr][nc] != -1) {
                    int newCost = curr.cost + GRID[nr][nc];
                    if (newCost < minCosts[nr][nc]) {
                        minCosts[nr][nc] = newCost;
                        pq.add(new Node(nr, nc, newCost, curr));
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
            res.cost = finalNode.cost;
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
}