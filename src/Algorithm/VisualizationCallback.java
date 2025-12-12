package Algorithm;

import java.util.List;

public interface VisualizationCallback {
    // ใช้ส่งข้อมูลกลับมาอัปเดตหน้าจอ
    void onUpdate(List<int[]> path, int generation, int cost, String status);
}