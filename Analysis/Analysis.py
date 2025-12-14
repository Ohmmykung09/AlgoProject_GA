import pandas as pd
import matplotlib.pyplot as plt
import os
import sys

# Setup Paths
current_dir = os.path.dirname(os.path.abspath(__file__))
data_dir = os.path.join(current_dir, '..', 'Data')
ga_csv = os.path.join(data_dir, 'ga_stats.csv')
opt_csv = os.path.join(data_dir, 'optimal.csv')

# Load Data
if not os.path.exists(ga_csv):
    print("❌ Error: ga_stats.csv not found.")
    sys.exit(1)

df = pd.read_csv(ga_csv)
optimal_cost = None
if os.path.exists(opt_csv):
    try:
        optimal_cost = pd.read_csv(opt_csv)['OptimalCost'].iloc[0]
    except: pass

# Plot
plt.figure(figsize=(10, 6))
plt.style.use('ggplot')

# Plot GA Cost
plt.plot(df['Generation'], df['Cost'], label='GA Best Cost', color='blue', linewidth=2)

# Plot Optimal Line
if optimal_cost is not None:
    plt.axhline(y=optimal_cost, color='green', linestyle='--', linewidth=2, label=f'Optimal (A*: {optimal_cost})')

plt.title('GA Convergence Analysis', fontsize=16)
plt.xlabel('Generation')
plt.ylabel('Cost')
plt.legend()
plt.grid(True)

output_img = os.path.join(current_dir, 'convergence_report.png')
plt.savefig(output_img)
print(f"✅ Graph saved to: {output_img}")
plt.show()