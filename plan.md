# Plan for Rock Solid IMU Detection

1. **Calculate Both Projections**:
   Instead of just `vx = 2(xz + wy)`, we will calculate the true X-projection `gx = 2(xz - wy)`.

2. **Model-Specific Metrics**:
   - For **Buds 2 / Buds 2 Pro**: We will use `gx`. As analyzed, `gx` is phenomenally rock-solid for Buds 2 (always < -0.4 for Left, always > 0.4 for Right, regardless of head movement).
   - For **Buds 4 Pro / Others**: We will use the original `vx = 2(xz + wy)`. While not perfectly invariant to Yaw, it separates Left/Right well enough when looking forward.

3. **Remove Dangerous Self-Healing for Buds 4 Pro**:
   - Because `vx` can cross 0 during extreme Yaw for Buds 4 Pro, we will **disable** the "Universal Safety Net" and "Subtle drift self-healing" for non-Buds2 devices.
   - For Buds 4 Pro, the **180-degree Dot Product Swap Detection** is mathematically perfect and 100% reliable for detecting hijacks. We will rely entirely on that for mid-session hijacks.
   - For Buds 2, since `gx` is rock solid, we will keep the Universal Safety Net and Self-Healing using `gx`.

4. **Initial Auto-Detect Thresholds**:
   - For Buds 2 (using `gx`): `> 0.3f` and `< -0.3f`.
   - For Buds 4 Pro (using `vx`): `> 0.2f` and `< -0.2f` (since Center is ~0.39 and -0.29).
