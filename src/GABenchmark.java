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
        String filename;
    }

    // ❌ ลบตัวแปร Scanner ตรงนี้ออก เพราะ static method เรียกใช้ไม่ได้

    public static void main(String[] args) {
        // ✅ 1. สร้าง Scanner ที่นี่ (จุดเดียวของโปรแกรม)
        Scanner scanner = new Scanner(System.in);

        // ✅ 2. ส่ง scanner เข้าไปในฟังก์ชัน
        File selectedFile = selectMapFile("MAZE", scanner);

        if (selectedFile == null) {
            System.out.println("No file selected or Folder not found. Exiting.");
            scanner.close(); // ✅ ปิดก่อนจบโปรแกรมกรณี Error
            return;
        }

        System.out.println("\nLoading map: " + selectedFile.getName() + " ...");
        MapInfo mapInfo = loadComplexMap(selectedFile);

        if (!mapInfo.success) {
            System.err.println("Failed to parse map file.");
            scanner.close(); // ✅ ปิดก่อนจบโปรแกรมกรณี Error
            return;
        }

        int[][] grid = mapInfo.grid;
        int rows = grid.length;
        int cols = grid[0].length;
        int[] start = mapInfo.start;
        int[] goal = mapInfo.goal;

        if (start == null || goal == null) {
            System.err.println("Error: Map file must contain 'S' and 'G' markers.");
            scanner.close(); // ✅ ปิดก่อนจบโปรแกรมกรณี Error
            return;
        }

        System.out.println("=== BENCHMARK CONFIGURATION ===");
        System.out.printf("Map:       %s (%dx%d)\n", selectedFile.getName(), rows, cols);
        System.out.printf("Start:     [%d, %d]\n", start[0], start[1]);
        System.out.printf("Goal:      [%d, %d]\n", goal[0], goal[1]);
        System.out.println("GA Config: Pop=200, Gen=2000, Elite=1, Mut=0.1");
        System.out.println("-------------------------------");

        System.out.print("Running A* (Finding Optimal)... ");
        A_star aStar = new A_star(grid, rows, cols, start, goal);
        A_star.Result aStarResult = aStar.run();

        if (!aStarResult.success) {
            System.out.println("\n❌ Failed! This map has no valid path from S to G.");
            scanner.close(); // ✅ ปิดก่อนจบโปรแกรมกรณี Error
            return;
        }
        System.out.println("Done.");
        System.out.printf(" -> Optimal Cost: %d\n", aStarResult.cost);
        System.out.printf(" -> Time Taken:   %.4f s\n", aStarResult.timeTaken);

        saveOptimalToCSV(aStarResult.cost, "Data/optimal.csv");

        System.out.println("-------------------------------");
        System.out.print("Running GA... ");
        GA ga = new GA(grid, rows, cols, start, goal);

        ga.setParameters(200, 2000, 1, 0.1);
        ga.setCallback(null);

        GA.Result gaResult = ga.run();
        System.out.println("Done.");

        printAnalysis(aStarResult, gaResult);

        // ✅ 3. ปิด Scanner เมื่อจบการทำงานทั้งหมดอย่างสมบูรณ์
        scanner.close();
    }

    // ✅ แก้ไข method ให้รับ Scanner เข้ามา
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
            // ใช้ scanner ตัวที่ส่งเข้ามา (ไม่ต้อง new ใหม่)
            int choice = scanner.nextInt();
            if (choice < 1 || choice > files.length)
                return null;
            return files[choice - 1];
        } catch (Exception e) {
            return null;
        }
    }

    // --- Helper Methods อื่นๆ เหมือนเดิม ---

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

    private static void saveOptimalToCSV(int cost, String filename) {
        File dataDir = new File("Data");
        if (!dataDir.exists())
            dataDir.mkdir();

        try (PrintWriter writer = new PrintWriter(new File(filename))) {
            writer.println("OptimalCost"); // Header
            writer.println(cost); // Value
        } catch (FileNotFoundException e) {
            System.err.println("Could not write Optimal CSV: " + e.getMessage());
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
                        String symbol = matcher.group(2);
                        switch (symbol) {
                            case "#":
                                rowList.add(-1);
                                break;
                            case "S":
                                rowList.add(1);
                                info.start = new int[] { r, c };
                                break;
                            case "G":
                                rowList.add(1);
                                info.goal = new int[] { r, c };
                                break;
                        }
                    }
                    c++;
                }
                tempGrid.add(rowList);
                r++;
            }
        } catch (IOException e) {
            e.printStackTrace();
            info.success = false;
            return info;
        }

        if (tempGrid.isEmpty()) {
            info.success = false;
            return info;
        }

        int rows = tempGrid.size();
        int cols = tempGrid.get(0).size();
        info.grid = new int[rows][cols];

        for (int i = 0; i < rows; i++) {
            List<Integer> row = tempGrid.get(i);
            for (int j = 0; j < Math.min(cols, row.size()); j++) {
                info.grid[i][j] = row.get(j);
            }
        }
        info.success = true;
        return info;
    }
}