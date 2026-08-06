import json
import math
import numpy as np
import glob
import os
from scipy.spatial.transform import Rotation as R

def average_quaternion(samples):
    if not samples:
        return None
    # Simple averaging (valid for small clusters)
    avg = np.zeros(4)
    for s in samples:
        # s is [timestamp, x, y, z, w]
        # scipy expects [x, y, z, w]
        q = np.array([s[1], s[2], s[3], s[4]])
        # Ensure all quaternions are in the same hemisphere
        if np.dot(avg, q) < 0 and np.linalg.norm(avg) > 0:
            avg -= q
        else:
            avg += q
    avg /= np.linalg.norm(avg)
    return R.from_quat(avg)

def get_rel_axis(q_center, q_pose):
    # Relative rotation from center to pose
    q_rel = q_pose * q_center.inv()
    rotvec = q_rel.as_rotvec()
    angle = np.linalg.norm(rotvec)
    if angle > 1e-6:
        axis = rotvec / angle
    else:
        axis = np.array([0,0,0])
    return axis, math.degrees(angle)

def analyze_bud(data, side="LEFT"):
    print(f"\n--- Analyzing {side} Earbud ---")
    
    center = average_quaternion(data['phases'].get(f'{side}_CENTER', []))
    down = average_quaternion(data['phases'].get(f'{side}_DOWN', []))
    left = average_quaternion(data['phases'].get(f'{side}_LEFT', []))
    
    if center is None or down is None or left is None:
        print("Missing essential phases (CENTER, DOWN, LEFT). Cannot analyze.")
        return
        
    axes = ['X', 'Y', 'Z']
    
    # Pitch (Nod DOWN should map to +X in standard visualizer)
    pitch_axis, pitch_angle = get_rel_axis(center, down)
    pitch_axis_idx = np.argmax(np.abs(pitch_axis))
    pitch_sign = np.sign(pitch_axis[pitch_axis_idx])
    raw_pitch_axis = axes[pitch_axis_idx]
    
    # Yaw (Turn LEFT should map to +Y in standard visualizer)
    yaw_axis, yaw_angle = get_rel_axis(center, left)
    yaw_axis_idx = np.argmax(np.abs(yaw_axis))
    yaw_sign = np.sign(yaw_axis[yaw_axis_idx])
    raw_yaw_axis = axes[yaw_axis_idx]
    
    # Roll (The remaining axis)
    roll_axis_idx = 3 - (pitch_axis_idx + yaw_axis_idx)
    raw_roll_axis = axes[roll_axis_idx]
    
    print(f"Hardware Pitch Axis (Nod Down): {raw_pitch_axis} (Magnitude: {abs(pitch_axis[pitch_axis_idx]):.2f}, Sign: {pitch_sign:+.0f}, Angle: {pitch_angle:.1f} deg)")
    print(f"Hardware Yaw Axis (Turn Left): {raw_yaw_axis} (Magnitude: {abs(yaw_axis[yaw_axis_idx]):.2f}, Sign: {yaw_sign:+.0f}, Angle: {yaw_angle:.1f} deg)")
    print(f"Hardware Roll Axis (Deduced): {raw_roll_axis}")
    
    print("\nRecommended Mapping (to standard X=Pitch, Y=Yaw, Z=Roll):")
    # For Pitch
    if pitch_sign > 0:
        print(f"  outX = {raw_pitch_axis.lower()}")
    else:
        print(f"  outX = -{raw_pitch_axis.lower()}")
        
    # For Yaw
    if yaw_sign > 0:
        print(f"  outY = {raw_yaw_axis.lower()}")
    else:
        print(f"  outY = -{raw_yaw_axis.lower()}")
        
    # For Roll (Assuming right-handed system preservation, we just map it)
    print(f"  outZ = {raw_roll_axis.lower()} (Sign depends on physical mirroring)")

def main():
    json_files = glob.glob(os.path.join(os.path.dirname(__file__), 'model_data', '*.json'))
    if not json_files:
        print("No JSON files found in model_data/")
        return
        
    for file in json_files:
        print(f"==================================================")
        print(f"Analyzing {os.path.basename(file)}")
        print(f"==================================================")
        with open(file, 'r') as f:
            data = json.load(f)
            analyze_bud(data, "LEFT")
            analyze_bud(data, "RIGHT")

if __name__ == "__main__":
    main()
