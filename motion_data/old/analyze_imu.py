import json
import math

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

def average_quaternion(quats):
    # Simple averaging
    avg = [0.0, 0.0, 0.0, 0.0]
    for q in quats:
        avg[0] += q[1] # x
        avg[1] += q[2] # y
        avg[2] += q[3] # z
        avg[3] += q[4] # w
    
    norm = math.sqrt(sum(a*a for a in avg))
    return [a/norm for a in avg]

def quat_conjugate(q):
    return [-q[0], -q[1], -q[2], q[3]]

def quat_multiply(q1, q2):
    x1, y1, z1, w1 = q1
    x2, y2, z2, w2 = q2
    return [
        w1*x2 + x1*w2 + y1*z2 - z1*y2,
        w1*y2 - x1*z2 + y1*w2 + z1*x2,
        w1*z2 + x1*y2 - y1*x2 + z1*w2,
        w1*w2 - x1*x2 - y1*y2 - z1*z2
    ]

with open('sensor_debug_BUDS_2.json', 'r') as f:
    data = json.load(f)

print("Earbud Model:", data['model'])

# Extract average quaternions for all phases
phases_q = {}
for phase, samples in data['phases'].items():
    if len(samples) > 0:
        phases_q[phase] = average_quaternion(samples)

def analyze_side(side):
    print(f"\n--- {side} EARBUD ---")
    center_phase = f"{side}_CENTER"
    if center_phase not in phases_q:
        print(f"Missing {center_phase}")
        return
        
    q_center = phases_q[center_phase]
    q_center_inv = quat_conjugate(q_center)
    
    for phase, q_pose in phases_q.items():
        if not phase.startswith(side):
            continue
            
        # Relative rotation from center
        q_rel = quat_multiply(q_pose, q_center_inv)
        x, y, z, w = q_rel
        roll, pitch, yaw = quat_to_euler(x, y, z, w)
        
        # Also print the raw relative axis/angle
        angle = 2 * math.acos(max(-1.0, min(1.0, w)))
        if angle > math.pi:
            angle -= 2*math.pi
        
        sin_half = math.sin(angle/2)
        if abs(sin_half) > 1e-6:
            ax, ay, az = x/sin_half, y/sin_half, z/sin_half
        else:
            ax, ay, az = 0, 0, 0
            
        print(f"Phase: {phase.ljust(15)} | Rel Euler (R:{roll:5.1f}, P:{pitch:5.1f}, Y:{yaw:5.1f}) | Axis (X:{ax:5.2f}, Y:{ay:5.2f}, Z:{az:5.2f}) Angle: {math.degrees(angle):5.1f}")

analyze_side("LEFT")
analyze_side("RIGHT")
