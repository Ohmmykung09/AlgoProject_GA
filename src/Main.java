import java.io.*;
import java.util.*;
import java.util.regex.*;
import Algorithm.*;

public class Main {
    // Global Constants
    public static int[][] GRID;
    public static int[][] DIST_MAP;
    public static int ROWS, COLS;
    public static int[] START = new int[2];
    public static int[] GOAL = new int[2];
    public static final int[][] MOVES = { { -1, 0 }, { 1, 0 }, { 0, -1 }, { 0, 1 } }; // U, D, L, R

    // [แก้ไข] ประกาศ Scanner ตัวเดียวใช้ร่วมกันทั้ง Class
    public static final Scanner scanner = new Scanner(System.in);

    public static void main(String[] args) {
        // 1. Select and Load Map
        File mapFile = selectMapInteractive("MAZE");
        if (mapFile == null)
            return;

        loadAndParseMap(mapFile);

        System.out.println("\n" + "=".repeat(60));
        System.out.println(" STARTING ALGORITHM COMPARISON (JAVA) ");
        System.out.println("=".repeat(60));

        // 3. Run Algorithms
        // --- GA ---
        System.out.println("\nRunning GA (Hybrid)...");
        GA ga = new GA(GRID, ROWS, COLS, START, GOAL);
        GA.Result gaResult = ga.run();

        // --- Dijkstra ---
        System.out.println("\nRunning Dijkstra...");
        Dijkstra dijkstra = new Dijkstra(GRID, ROWS, COLS, START, GOAL);
        Dijkstra.Result dijResult = dijkstra.run();

        // --- A* ---
        System.out.println("\nRunning A* (A-Star)...");
        A_star astar = new A_star(GRID, ROWS, COLS, START, GOAL);
        A_star.Result astarResult = astar.run();

        // 4. Print Summary Table
        printSummary(gaResult, dijResult, astarResult);

        // 5. Visualization Menu
        // [แก้ไข] ลบการประกาศ Scanner ซ้ำ ใช้ตัวแปร global 'scanner' แทน
        while (true) {
            System.out.println("\n[ VISUALIZATION MENU ]");
            System.out.println("1. Show GA Path");
            System.out.println("2. Show Dijkstra Path");
            System.out.println("3. Show A* Path");
            System.out.println("0. Exit");
            System.out.print("Select (0-3): ");

            String choice = scanner.nextLine();
            if (choice.equals("1")) {
                if (gaResult.reachedGoal)
                    printMazeWithPath(gaResult.path, "GA BEST PATH");
                else
                    System.out.println("GA Not Found");
            } else if (choice.equals("2")) {
                if (dijResult.success)
                    printMazeWithPath(dijResult.path, "DIJKSTRA PATH");
            } else if (choice.equals("3")) {
                if (astarResult.success)
                    printMazeWithPath(astarResult.path, "A* PATH");
            } else if (choice.equals("0")) {
                System.out.println("Goodbye!");
                break;
            }
        }

        scanner.close();
    }

    // --- Helper Methods ---

    private static File selectMapInteractive(String folderName) {
        File folder = new File(folderName);
        if (!folder.exists() || !folder.isDirectory()) {
            System.out.println("Error: Folder '" + folderName + "' not found.");
            // Try looking in current dir if structure is flat
            folder = new File(".");
        }

        File[] files = folder.listFiles((dir, name) -> name.endsWith(".txt"));
        if (files == null || files.length == 0) {
            System.out.println("Error: No .txt files found.");
            return null;
        }
        Arrays.sort(files);

        System.out.println("\n" + "=".repeat(40));
        System.out.println(" Please select a map (Folder: " + folder.getName() + ")");
        System.out.println("=".repeat(40));
        for (int i = 0; i < files.length; i++) {
            System.out.printf(" [%d] %s%n", i + 1, files[i].getName());
        }
        System.out.println("=".repeat(40));

        // [แก้ไข] ใช้ global scanner แทนการประกาศใหม่
        while (true) {
            System.out.print("Select file number: ");
            try {
                String input = scanner.nextLine(); // ใช้ scanner ตัวเดียวกับ main
                int idx = Integer.parseInt(input) - 1;
                if (idx >= 0 && idx < files.length)
                    return files[idx];
            } catch (NumberFormatException ignored) {
            }
            System.out.println("Invalid input.");
        }
    }

    private static void loadAndParseMap(File file) {
        System.out.println("\nLoading Map: " + file.getName() + " ...");
        List<String> lines = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (!line.trim().isEmpty())
                    lines.add(line.trim());
            }
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(1);
        }

        ROWS = lines.size();
        List<List<Integer>> tempGrid = new ArrayList<>();
        Pattern p = Pattern.compile("\"(\\d+)\"|([#SG])");

        int maxCols = 0;
        for (int r = 0; r < ROWS; r++) {
            List<Integer> rowData = new ArrayList<>();
            Matcher m = p.matcher(lines.get(r));
            int c = 0;
            while (m.find()) {
                String numStr = m.group(1);
                String charStr = m.group(2);
                int val = 0;
                if (numStr != null) {
                    val = Integer.parseInt(numStr);
                } else if (charStr != null) {
                    if (charStr.equals("#"))
                        val = -1;
                    else if (charStr.equals("S")) {
                        START[0] = r;
                        START[1] = c;
                        val = 0;
                    } else if (charStr.equals("G")) {
                        GOAL[0] = r;
                        GOAL[1] = c;
                        val = 0;
                    }
                }
                rowData.add(val);
                c++;
            }
            maxCols = Math.max(maxCols, c);
            tempGrid.add(rowData);
        }
        COLS = maxCols;
        GRID = new int[ROWS][COLS];

        for (int r = 0; r < ROWS; r++) {
            List<Integer> row = tempGrid.get(r);
            for (int c = 0; c < COLS; c++) {
                if (c < row.size())
                    GRID[r][c] = row.get(c);
                else
                    GRID[r][c] = -1;
            }
        }
        System.out.println("Map Size: " + ROWS + "x" + COLS);
    }

    

    private static void printSummary(GA.Result ga, Dijkstra.Result dij, A_star.Result astar) {
        System.out.println("\n" + "=".repeat(75));
        System.out.printf("%-15s | %-10s | %-8s | %-6s | %-10s | %-15s%n", "ALGORITHM", "STATUS", "COST", "STEPS",
                "TIME (s)", "EFFICIENCY");
        System.out.println("-".repeat(75));

        String gaStatus = ga.reachedGoal ? "Success" : "Fail";
        String gaCost = ga.reachedGoal ? String.valueOf(ga.cost) : "N/A";
        System.out.printf("%-15s | %-10s | %-8s | %-6d | %-10.4f | %s Gens%n", "GA (Hybrid)", gaStatus, gaCost,
                ga.path.size(), ga.timeTaken, "1000");

        String dijStatus = dij.success ? "Success" : "Fail";
        String dijCost = dij.success ? String.valueOf(dij.cost) : "N/A";
        System.out.printf("%-15s | %-10s | %-8s | %-6d | %-10.4f | %d Nodes%n", "Dijkstra", dijStatus, dijCost,
                dij.path.size(), dij.timeTaken, dij.visitedCount);

        String astarStatus = astar.success ? "Success" : "Fail";
        String astarCost = astar.success ? String.valueOf(astar.cost) : "N/A";
        System.out.printf("%-15s | %-10s | %-8s | %-6d | %-10.4f | %d Nodes%n", "A* (A-Star)", astarStatus, astarCost,
                astar.path.size(), astar.timeTaken, astar.visitedCount);
        System.out.println("=".repeat(75));
    }

    private static void printMazeWithPath(List<int[]> path, String title) {
        System.out.println("\n" + "=".repeat(50));
        System.out.println(" " + title);
        System.out.println("=".repeat(50));

        String[][] display = new String[ROWS][COLS];
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                if (GRID[r][c] == -1)
                    display[r][c] = "###";
                else if (r == START[0] && c == START[1])
                    display[r][c] = " S ";
                else if (r == GOAL[0] && c == GOAL[1])
                    display[r][c] = " G ";
                else
                    display[r][c] = " . ";
            }
        }

        for (int i = 0; i < path.size() - 1; i++) {
            int r = path.get(i)[0];
            int c = path.get(i)[1];
            int nr = path.get(i + 1)[0];
            int nc = path.get(i + 1)[1];

            if ((r == START[0] && c == START[1]) || (r == GOAL[0] && c == GOAL[1]))
                continue;

            if (nr > r)
                display[r][c] = " v ";
            else if (nr < r)
                display[r][c] = " ^ ";
            else if (nc > c)
                display[r][c] = " > ";
            else if (nc < c)
                display[r][c] = " < ";
        }

        System.out.println("-".repeat(COLS * 3));
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++)
                System.out.print(display[r][c]);
            System.out.println();
        }
        System.out.println("-".repeat(COLS * 3));
    }
}