package Algorithm;

import java.util.List;

public interface VisualizationCallback {
    void onUpdate(List<int[]> path, int generation, int cost, String status);
}