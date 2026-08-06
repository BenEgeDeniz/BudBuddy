import math
import numpy as np
from scipy.spatial.transform import Rotation as R
import json

with open('sensor_debug_BUDS_2.json', 'r') as f:
    data = json.load(f)

def avg_q(quats):
    avg = [0.0, 0.0, 0.0, 0.0]
    for q in quats:
        avg[0] += q[1]
        avg[1] += q[2]
        avg[2] += q[3]
        avg[3] += q[4]
    norm = math.sqrt(sum(a*a for a in avg))
    return [a/norm for a in avg]

# Quaternions are [x, y, z, w]
left_center = R.from_quat(avg_q(data['phases']['LEFT_CENTER']))
left_up = R.from_quat(avg_q(data['phases']['LEFT_UP']))
left_down = R.from_quat(avg_q(data['phases']['LEFT_DOWN']))
left_left = R.from_quat(avg_q(data['phases']['LEFT_LEFT']))
left_right = R.from_quat(avg_q(data['phases']['LEFT_RIGHT']))

right_center = R.from_quat(avg_q(data['phases']['RIGHT_CENTER']))
right_up = R.from_quat(avg_q(data['phases']['RIGHT_UP']))
right_left = R.from_quat(avg_q(data['phases']['RIGHT_LEFT']))

def get_rel_axis(q_center, q_pose):
    q_rel = q_pose * q_center.inv()
    rotvec = q_rel.as_rotvec()
    angle = np.linalg.norm(rotvec)
    if angle > 1e-6:
        axis = rotvec / angle
    else:
        axis = np.array([0,0,0])
    return axis, math.degrees(angle)

print("LEFT_UP axis:", get_rel_axis(left_center, left_up))
print("LEFT_DOWN axis:", get_rel_axis(left_center, left_down))
print("LEFT_LEFT axis:", get_rel_axis(left_center, left_left))
print("LEFT_RIGHT axis:", get_rel_axis(left_center, left_right))

print("RIGHT_UP axis:", get_rel_axis(right_center, right_up))
print("RIGHT_LEFT axis:", get_rel_axis(right_center, right_left))

