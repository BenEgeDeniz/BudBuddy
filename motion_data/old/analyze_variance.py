import json
import math
import numpy as np

def quat_to_euler(x, y, z, w):
    # Roll (x-axis rotation)
    sinr_cosp = 2 * (w * x + y * z)
    cosr_cosp = 1 - 2 * (x * x + y * y)
    roll = math.atan2(sinr_cosp, cosr_cosp)
    
    # Pitch (y-axis rotation)
    sinp = 2 * (w * y - z * x)
    if abs(sinp) >= 1:
        pitch = math.copysign(math.pi / 2, sinp)
    else:
        pitch = math.asin(sinp)
        
    # Yaw (z-axis rotation)
    siny_cosp = 2 * (w * z + x * y)
    cosy_cosp = 1 - 2 * (y * y + z * z)
    yaw = math.atan2(siny_cosp, cosy_cosp)
    
    return math.degrees(roll), math.degrees(pitch), math.degrees(yaw)

with open('sensor_debug_BUDS_2.json', 'r') as f:
    data = json.load(f)

for phase_name, samples in data['phases'].items():
    if not samples: continue
    eulers = []
    for s in samples:
        # s is [timestamp, x, y, z, w]
        r, p, y = quat_to_euler(s[1], s[2], s[3], s[4])
        eulers.append((r, p, y))
    
    eulers = np.array(eulers)
    
    # We want to see which axis changes the most (variance or min/max range)
    ranges = np.ptp(eulers, axis=0) # Peak to peak (max - min)
    stds = np.std(eulers, axis=0)
    
    # Eulers map to: X (Roll in this func), Y (Pitch in this func), Z (Yaw in this func)
    print(f"Phase: {phase_name}")
    print(f"  Range (deg) -> X: {ranges[0]:.1f}, Y: {ranges[1]:.1f}, Z: {ranges[2]:.1f}")
    print(f"  StdDev (deg)-> X: {stds[0]:.1f}, Y: {stds[1]:.1f}, Z: {stds[2]:.1f}")
    
    primary = np.argmax(ranges)
    axis_name = ['X', 'Y', 'Z'][primary]
    print(f"  -> Primary varying axis: {axis_name}\n")
