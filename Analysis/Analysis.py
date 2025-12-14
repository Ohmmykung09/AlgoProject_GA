import pandas as pd
import matplotlib.pyplot as plt
import os
import sys

current_dir = os.path.dirname(os.path.abspath(__file__))
data_dir = os.path.join(current_dir, '..', 'Data')

ga_csv = os.path.join(data_dir, 'ga_stats.csv')
opt_csv = os.path.join(data_dir, 'optimal.csv')

if not os.path.exists(ga_csv):
    print("❌ Error: ga_stats.csv not found.")
    sys.exit(1)

df = pd.read_csv(ga_csv)

optimal_cost = None
if os.path.exists(opt_csv):
    try:
        df_opt = pd.read_csv(opt_csv)
        optimal_cost = df_opt['OptimalCost'].iloc[0]
        print(f"✅ Loaded Optimal Cost: {optimal_cost}")
    except:
        print("⚠️ Could not read optimal.csv")

# Plot Graph
plt.style.use('ggplot')
fig, (ax1, ax2, ax3) = plt.subplots(3, 1, figsize=(10, 12), sharex=True)

# Convergence
ax1.plot(df['Generation'], df['AvgCost'], label='Average Cost', color='orange', alpha=0.7)
ax1.plot(df['Generation'], df['BestCost'], label='Best Cost (GA)', color='blue', linewidth=2)

if optimal_cost is not None:
    ax1.axhline(y=optimal_cost, color='green', linestyle='--', linewidth=2, label=f'Optimal (A*: {optimal_cost})')

ax1.set_title('GA Performance: Cost Convergence')
ax1.set_ylabel('Cost')
ax1.legend()
ax1.grid(True)

# Diversity
ax2.plot(df['Generation'], df['StdDev'], color='purple', label='Std Dev')
ax2.fill_between(df['Generation'], df['StdDev'], color='purple', alpha=0.1)
ax2.set_title('Population Diversity')
ax2.set_ylabel('Std Dev')
ax2.legend()

# Temperature
ax3.plot(df['Generation'], df['Temperature'], color='red', linestyle='--')
ax3.set_title('Temperature')
ax3.set_ylabel('Temp')
ax3.set_xlabel('Generation')

plt.tight_layout()
output_img = os.path.join(current_dir, 'ga_analysis_report.png')
plt.savefig(output_img)
print(f"✅ Graph saved to: {output_img}")
plt.show()