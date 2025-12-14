import Algorithm.*;
import java.io.*;
import java.util.*;
import java.util.regex.*;

public class GABenchmark {

    static class MapInfo {
        int[][] grid;
        int[] start;
        int[] goal;
        boolean success;
    }

    static class GAConfig {
        String name;
        boolean smooth, refine, shortcut, loopcut, backtrack;

        public GAConfig(String name, boolean s, boolean r, boolean sh, boolean l, boolean b) {
            this.name = name;
            this.smooth = s;
            this.refine = r;
            this.shortcut = sh;
            this.loopcut = l;
            this.backtrack = b;
        }
    }

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);

        File selectedFile = selectMapFile("MAZE", scanner);
        if (selectedFile == null) {
            System.out.println("Exiting.");
            scanner.close();
            return;
        }

        MapInfo mapInfo = loadComplexMap(selectedFile);
        if (!mapInfo.success || mapInfo.start == null) {
            System.out.println("Error loading map or missing 'S'/'G'.");
            scanner.close();
            return;
        }

        System.out.println("\nRunning A* (Optimal)...");
        A_star aStar = new A_star(mapInfo.grid, mapInfo.grid.length, mapInfo.grid[0].length, mapInfo.start,
                mapInfo.goal);
        A_star.Result aStarResult = aStar.run();

        if (aStarResult.success) {
            System.out.println("-> Optimal Cost: " + aStarResult.cost);
            saveOptimalToCSV(aStarResult.cost, "Data/optimal.csv");
        } else {
            System.out.println("❌ Map is unreachable!");
            scanner.close();
            return;
        }

        GAConfig config = selectGAConfiguration(scanner);
        System.out.println("\n--------------------------------");
        System.out.println("Starting Run with: " + config.name);
        System.out.println("(Smooth=" + config.smooth + ", Refine=" + config.refine +
                ", Shortcut=" + config.shortcut + ", LoopCut=" + config.loopcut + ", Backtrack=" + config.backtrack
                + ")");
        System.out.println("--------------------------------");

        System.out.print("Running GA...");
        GA ga = new GA(mapInfo.grid, mapInfo.grid.length, mapInfo.grid[0].length, mapInfo.start, mapInfo.goal);

        // Apply Configuration
        ga.setMemeticSettings(config.smooth, config.refine);
        ga.setHeuristics(config.shortcut, config.loopcut, config.backtrack);

        // Fixed Parameters
        ga.setParameters(200, 500, 1, 0.1);
        ga.setCallback(null);

        GA.Result gaResult = ga.run();
        System.out.println(" Done.");

        saveGAStatsToCSV(gaResult.costHistory, "Data/ga_stats.csv");
        printAnalysis(aStarResult, gaResult);

        scanner.close();
    }

    private static GAConfig selectGAConfiguration(Scanner scanner) {
        boolean smooth = false;
        boolean refine = false;
        boolean shortcut = false;
        boolean loopcut = false;
        boolean backtrack = false;

        while (true) {
            System.out.println("\n=== CONFIGURE GA SETTINGS ===");
            System.out.println("Type the number to toggle ON/OFF");
            System.out.println("-----------------------------");
            System.out.printf("[1] Smooth Path       : %s\n", getStatus(smooth));
            System.out.printf("[2] Refine Children   : %s\n", getStatus(refine));
            System.out.printf("[3] Shortcut Heuristic: %s\n", getStatus(shortcut));
            System.out.printf("[4] Loop Cutting      : %s\n", getStatus(loopcut));
            System.out.printf("[5] Backtracking      : %s\n", getStatus(backtrack));
            System.out.println("-----------------------------");
            System.out.println("[9] Enable ALL (Full GA)");
            System.out.println("[8] Disable ALL (Pure GA)");
            System.out.println("[0] >> START RUNNING <<");
            System.out.println("-----------------------------");
            System.out.print("Choice: ");

            int choice = -1;
            try {
                choice = scanner.nextInt();
            } catch (Exception e) {
                scanner.next(); // Clear buffer
            }

            if (choice == 0) {
                break; // Start Running
            }

            switch (choice) {
                case 1:
                    smooth = !smooth;
                    break;
                case 2:
                    refine = !refine;
                    break;
                case 3:
                    shortcut = !shortcut;
                    break;
                case 4:
                    loopcut = !loopcut;
                    break;
                case 5:
                    backtrack = !backtrack;
                    break;
                case 9: // Enable All
                    smooth = true;
                    refine = true;
                    shortcut = true;
                    loopcut = true;
                    backtrack = true;
                    System.out.println(">> All Enabled.");
                    break;
                case 8: // Disable All
                    smooth = false;
                    refine = false;
                    shortcut = false;
                    loopcut = false;
                    backtrack = false;
                    System.out.println(">> All Disabled.");
                    break;
                default:
                    System.out.println("Invalid option.");
            }
        }

        return new GAConfig("Custom Config", smooth, refine, shortcut, loopcut, backtrack);
    }

    private static String getStatus(boolean isOn) {
        return isOn ? "[ON] ✅" : "[OFF]";
    }

    private static void saveGAStatsToCSV(List<Integer> history, String filename) {
        File dataDir = new File("Data");
        if (!dataDir.exists())
            dataDir.mkdir();

        try (PrintWriter writer = new PrintWriter(new File(filename))) {
            writer.println("Generation,Cost");
            for (int i = 0; i < history.size(); i++) {
                writer.println((i + 1) + "," + history.get(i));
            }
            System.out.println(">> GA Stats saved to '" + filename + "'");
        } catch (FileNotFoundException e) {
            System.err.println("Error writing GA Stats: " + e.getMessage());
        }
    }

    private static void saveOptimalToCSV(int cost, String filename) {
        File dataDir = new File("Data");
        if (!dataDir.exists())
            dataDir.mkdir();

        try (PrintWriter writer = new PrintWriter(new File(filename))) {
            writer.println("OptimalCost");
            writer.println(cost);
        } catch (FileNotFoundException e) {
            System.err.println("Could not write Optimal CSV.");
        }
    }

    private static File selectMapFile(String folderPath, Scanner scanner) {
        File folder = new File(folderPath);
        if (!folder.exists() || !folder.isDirectory()) {
            System.err.println("❌ Folder '" + folderPath + "' not found at: " + System.getProperty("user.dir"));
            return null;
        }
        File[] files = folder.listFiles((dir, name) -> name.toLowerCase().endsWith(".txt"));
        if (files == null || files.length == 0) {
            System.err.println("❌ No .txt files found in '" + folderPath + "'");
            return null;
        }

        System.out.println("=== SELECT MAP FILE ===");
        Arrays.sort(files);
        for (int i = 0; i < files.length; i++) {
            System.out.printf("[%d] %s\n", (i + 1), files[i].getName());
        }
        System.out.println("=======================");
        System.out.print("Enter number to select: ");

        try {
            int choice = scanner.nextInt();
            if (choice < 1 || choice > files.length)
                return null;
            return files[choice - 1];
        } catch (Exception e) {
            return null;
        }
    }

    private static MapInfo loadComplexMap(File file) {
        MapInfo info = new MapInfo();
        List<List<Integer>> tempGrid = new ArrayList<>();

        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            int r = 0;
            Pattern pattern = Pattern.compile("\"(\\d+)\"|([#SG])");

            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty())
                    continue;

                List<Integer> rowList = new ArrayList<>();
                Matcher matcher = pattern.matcher(line);
                int c = 0;
                while (matcher.find()) {
                    if (matcher.group(1) != null) {
                        rowList.add(Integer.parseInt(matcher.group(1)));
                    } else if (matcher.group(2) != null) {
                        String s = matcher.group(2);
                        if (s.equals("#")) {
                            rowList.add(-1);
                        } else {
                            rowList.add(1);
                            if (s.equals("S"))
                                info.start = new int[] { r, c };
                            else if (s.equals("G"))
                                info.goal = new int[] { r, c };
                        }
                    }
                    c++;
                }
                tempGrid.add(rowList);
                r++;
            }
        } catch (Exception e) {
            e.printStackTrace();
            info.success = false;
            return info;
        }

        if (tempGrid.isEmpty()) {
            info.success = false;
            return info;
        }

        info.grid = new int[tempGrid.size()][tempGrid.get(0).size()];
        for (int i = 0; i < info.grid.length; i++) {
            for (int j = 0; j < info.grid[0].length; j++) {
                info.grid[i][j] = tempGrid.get(i).get(j);
            }
        }
        info.success = true;
        return info;
    }

    private static void printAnalysis(A_star.Result optimal, GA.Result heuristic) {
        System.out.println("\n=== COMPARISON RESULTS ===");
        double errorGap = ((double) (heuristic.cost - optimal.cost) / optimal.cost) * 100;

        System.out.printf("%-15s | %-15s | %-15s\n", "Metric", "A* (Optimal)", "GA (Heuristic)");
        System.out.println("----------------|-----------------|----------------");
        System.out.printf("%-15s | %-15d | %-15d\n", "Path Cost", optimal.cost, heuristic.cost);
        System.out.printf("%-15s | %-15.4fs | %-15.4fs\n", "Time", optimal.timeTaken, heuristic.timeTaken);
        System.out.println("---------------------------------------------------");
        System.out.printf("Accuracy Gap:  %.2f%% %s\n", errorGap, (errorGap == 0 ? "(PERFECT!)" : "(Difference)"));

        if (heuristic.cost == optimal.cost)
            System.out.println("✅ GA found the Optimal Path!");
        else if (errorGap < 10.0)
            System.out.println("⚠️ GA is close (Acceptable).");
        else
            System.out.println("❌ GA needs improvement.");

        System.out.println("\n>> Data saved to 'Data/optimal.csv' and 'Data/ga_stats.csv'");
    }
}