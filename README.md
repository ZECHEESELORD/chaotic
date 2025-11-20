# Chaotic: Real-Time N-Link Pendulum Simulation via Discrete Variational Mechanics

---

## The Premise

The N link pendulum is a classic problem in chaotic dynamics. As the number of links ($N$) increases, the system exhibits high sensitivity to initial conditions. In a traditional computational physics context, this is often solved using Lagrangian mechanics to derive a system of coupled differential equations.

However, implementing a Lagrangian solver in Minecraft (real time game engine) presents a couple of annoyances:
1. Solving dense mass matrices for high $N$ is expensive.
2. Explicit Euler integration [drifts rapidly](https://dspace.mit.edu/bitstream/handle/1721.1/55903/18-034Spring-2007/NR/rdonlyres/Mathematics/18-034Spring-2007/Projects/eulerl.pdf) at the low sample rate of Minecraft (20 ticks per second).
3. Translating differential equations into robust Java code is error prone.

We explore a **Position-Based Dynamics (PBD)** approach, utilizing **Verlet Integration** to simulate chaotic motion. This method prioritizes numerical stability and visual plausibility over analytical perfection.

---

## WTF is a Verlet?

The core of the simulation is the integration scheme. We chose Verlet Integration because it is **symplectic** (it preserves the phase space volume), meaning it naturally conserves energy better than standard Euler methods without complex correction steps.

The Verlet formula is derived directly from the [**Taylor Series expansions**](https://en.wikipedia.org/wiki/Taylor_series) of the particle's position $x(t)$.

### The Taylor Series Expansion
To find the position of a particle in the future ($t + \Delta t$) and the past ($t - \Delta t$), we expand $x$ around time $t$:

**Forward Expansion:**
$$x(t + \Delta t) = x(t) + v(t)\Delta t + \frac{1}{2}a(t)\Delta t^2 + \frac{1}{6}b(t)\Delta t^3 + O(\Delta t^4)$$

**Backward Expansion:**
$$x(t - \Delta t) = x(t) - v(t)\Delta t + \frac{1}{2}a(t)\Delta t^2 - \frac{1}{6}b(t)\Delta t^3 + O(\Delta t^4)$$

### The Verlet Formula
By adding these two equations together, the odd power terms (velocity $v$ and jerk $b$) have opposite signs and **cancel out**:

$$x(t + \Delta t) + x(t - \Delta t) = 2x(t) + a(t)\Delta t^2 + O(\Delta t^4)$$

Rearranging for $x(t + \Delta t)$ gives us the discrete update rule:

$$x_{next} = 2x_{current} - x_{prev} + a \cdot \Delta t^2$$

> [!IMPORTANT]
> We do not need to store or calculate velocity explicitly. The velocity is implicitly encoded in the distance between the current position and the previous position. This "memory" of momentum makes the simulation incredibly robust against constraints.

---

## Physical Constraints & Mass

A pendulum is defined by rigid rods ([holonomic constraints](https://physics.stackexchange.com/questions/409951/what-are-holonomic-and-non-holonomic-constraints)). The distance $d$ between two nodes must equal the rod length $L$.

$$|P_1 - P_2| = L$$

### Inverse Mass Weighting
In a multibody system, objects must react to forces according to [Newton’s Second Law](https://en.wikipedia.org/wiki/Newton%27s_laws_of_motion) ($F=ma$). A light object reacts strongly to a force; a heavy object reacts weakly.

To simulate this without a physics solver, we use **Inverse Mass** ($w = 1/m$).
* If $m \to \infty$ (an anchor), $w = 0$.
* If $m$ is small, $w$ is large.

When a rod is stretched or compressed, we calculate a position correction vector $\vec{\delta}$. We distribute this correction to the two nodes ($A$ and $B$) based on their ratio of inverse mass.

$$w_{sum} = w_A + w_B$$

**Correction for Node A:**
$$\Delta P_A = \vec{\delta} \times \frac{w_A}{w_{sum}}$$

**Correction for Node B:**
$$\Delta P_B = -\vec{\delta} \times \frac{w_B}{w_{sum}}$$

This ensures that if a heavy iron bob is connected to a light string, the string moves to satisfy the constraint, while the bob maintains its trajectory, preserving the inertia of the system.

---

## Drag (Energy Dissipation)
> (insert generic deez nuts joke here)

Verlet integration conserves energy so well that a pendulum in a vacuum would swing forever. To simulate atmospheric drag and improve stability, we apply damping.

Since Verlet does not store velocity, we derive the **implicit velocity**:
$$\vec{v}_{implicit} = P_{current} - P_{prev}$$

We then apply a drag coefficient to the previous position, effectively shortening the step the particle "remembers" taking:

$$P_{prev} \leftarrow P_{current} - (\vec{v}_{implicit} \times (1.0 - \text{drag}))$$

---

## Temporal & Spatial Implementation

### Substepping (Solving the Tunneling Problem)
The Minecraft game loop runs at 20 Hz ($\Delta t = 0.05s$). For a chaotic system, this step size is too large; high velocity nodes will overshoot their constraints significantly, adding spurious energy (making the pendulum explode).

We implement **substepping**: dividing the game tick into $S$ smaller physics ticks.
$$\Delta t_{physics} = \frac{0.05}{S}$$
With $S=10$, we run the physics at an effective 200 Hz. This keeps the Taylor Series error term ($O(\Delta t^4)$) negligible.

### Coordinate Transformation
The simulation runs in a pure abstract 2D space (meters). Rendering involves an [affine transformation](https://www.mathworks.com/discovery/affine-transformation.html) to World Space (blocks):

$$\vec{P}_{world} = \vec{P}_{anchor} + (\vec{P}_{physics} \times \text{scale})$$

This separation of concerns allows us to perform pure double precision math for the physics engine while adhering to the integer aligned grid of the voxel world for rendering.


## References

- https://doi.org/10.1016/0960-0779(95)00018-6

- https://link.springer.com/article/10.1007/s13369-016-2342-9

- https://journals.aps.org/prl/abstract/10.1103/PhysRevLett.74.1974

- https://cdann.net/pub/dann14a-n-link-pendulum.pdf