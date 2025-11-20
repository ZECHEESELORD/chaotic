package sh.harold.fulcrum.physics;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.random.RandomGenerator;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.util.Vector;

public final class PendulumChain {

    private static final double MIN_LENGTH = 0.5;
    private static final double MAX_LENGTH = 3.0;
    private static final double DEFAULT_LENGTH = 1.0;
    private static final double MIN_MASS = 0.1;
    private static final double MAX_MASS = 25.0;

    private final int id;
    private final List<PendulumNode> nodes = new ArrayList<>();
    private double[] segmentLength = new double[0];
    private Location anchor;
    private double scale = 2.0;
    private double gravity = 9.81;
    private double drag = 0.01;
    private int iterations = 8;
    private int substeps = 10;
    private boolean active;

    public PendulumChain(int id, Location anchor) {
        this.id = id;
        this.anchor = anchor.clone();
    }

    public int id() {
        return this.id;
    }

    public Location anchor() {
        return this.anchor.clone();
    }

    public void anchor(Location anchor) {
        this.anchor = anchor.clone();
    }

    public boolean active() {
        return this.active;
    }

    public void active(boolean active) {
        this.active = active;
    }

    public int substeps() {
        return this.substeps;
    }

    public void substeps(int substeps) {
        this.substeps = Math.max(1, substeps);
    }

    public int iterations() {
        return this.iterations;
    }

    public void iterations(int iterations) {
        this.iterations = Math.max(1, iterations);
    }

    public double drag() {
        return this.drag;
    }

    public void drag(double drag) {
        this.drag = Math.max(0.0, drag);
    }

    public double gravity() {
        return this.gravity;
    }

    public void gravity(double gravity) {
        this.gravity = gravity;
    }

    public double scale() {
        return this.scale;
    }

    public void scale(double scale) {
        this.scale = Math.max(0.1, scale);
    }

    public int segmentCount() {
        return this.segmentLength.length;
    }

    public double segmentLength(int index) {
        return this.segmentLength[index];
    }

    public void setSegmentLength(int index, double length) {
        ensureIndex(index);
        this.segmentLength[index] = clamp(length, MIN_LENGTH, MAX_LENGTH);
    }

    public double[] segmentLengths() {
        return this.segmentLength.clone();
    }

    public List<PendulumNode> nodes() {
        return List.copyOf(this.nodes);
    }

    public PendulumNode node(int index) {
        return this.nodes.get(index);
    }

    public double massAt(int nodeIndex) {
        return this.nodes.get(nodeIndex).mass();
    }

    public void setMass(int nodeIndex, double mass) {
        ensureNodeIndex(nodeIndex);
        if (nodeIndex == 0) {
            this.nodes.get(nodeIndex).setMass(0.0);
            return;
        }
        this.nodes.get(nodeIndex).setMass(clamp(mass, MIN_MASS, MAX_MASS));
    }

    public boolean configured() {
        return !this.nodes.isEmpty() && this.segmentLength.length == this.nodes.size() - 1;
    }

    public void configureSegments(int segments) {
        final int targetSegments = Math.max(1, segments);
        final double[] previousLengths = this.segmentLength;
        final List<PendulumNode> previousNodes = List.copyOf(this.nodes);

        this.segmentLength = new double[targetSegments];
        this.nodes.clear();
        for (int i = 0; i <= targetSegments; i++) {
            final double inheritedMass;
            if (i < previousNodes.size()) {
                inheritedMass = previousNodes.get(i).mass();
            } else if (i == 0) {
                inheritedMass = 0.0;
            } else if (i == targetSegments) {
                inheritedMass = 2.0;
            } else {
                inheritedMass = 1.0;
            }
            this.nodes.add(new PendulumNode(Vec2.ZERO, Vec2.ZERO, inheritedMass));
        }

        for (int i = 0; i < targetSegments; i++) {
            final double candidate = i < previousLengths.length ? previousLengths[i] : DEFAULT_LENGTH;
            this.segmentLength[i] = clamp(candidate, MIN_LENGTH, MAX_LENGTH);
        }

        this.resetPose(PoseType.DOWN, ThreadLocalRandom.current());
    }

    public void resetPose(PoseType poseType, RandomGenerator rng) {
        if (!this.configured()) {
            return;
        }

        final List<Vec2> positions = new ArrayList<>(this.nodes.size());
        positions.add(Vec2.ZERO);

        Vec2 direction = switch (poseType) {
            case DOWN -> new Vec2(0.0, -1.0);
            case UP -> new Vec2(0.0, 1.0);
            case LEFT -> new Vec2(-1.0, 0.0);
            case RIGHT -> new Vec2(1.0, 0.0);
            case RANDOMIZED -> Vec2.ZERO;
        };

        double cumulativeAngle = -Math.PI / 2.0;
        for (int i = 0; i < this.segmentLength.length; i++) {
            if (poseType == PoseType.RANDOMIZED) {
                final double perturb = rng.nextDouble(-Math.PI, Math.PI);
                cumulativeAngle += perturb * 0.25;
                final double dx = Math.cos(cumulativeAngle);
                final double dy = Math.sin(cumulativeAngle);
                direction = new Vec2(dx, dy);
            }
            final Vec2 last = positions.get(i);
            final Vec2 offset = direction.multiply(this.segmentLength[i]);
            positions.add(last.add(offset));
        }

        final double jiggle = poseType == PoseType.RANDOMIZED ? 0.02 : 0.0;
        for (int i = 0; i < this.nodes.size(); i++) {
            final Vec2 pos = positions.get(i);
            final Vec2 jitter = jiggle > 0.0
                ? new Vec2(rng.nextDouble(-jiggle, jiggle), rng.nextDouble(-jiggle, jiggle))
                : Vec2.ZERO;
            final Vec2 withJitter = pos.add(jitter);
            final PendulumNode node = this.nodes.get(i);
            node.pos(withJitter);
            node.prevPos(withJitter);
        }
    }

    public void stepTick(double dtTickSeconds) {
        if (!this.active || !this.configured()) {
            return;
        }
        final double dtSub = dtTickSeconds / this.substeps;
        for (int sub = 0; sub < this.substeps; sub++) {
            integrate(dtSub);
            for (int i = 0; i < this.iterations; i++) {
                satisfyConstraints();
            }
            applyDrag(dtSub);
        }
    }

    private void integrate(double dtSub) {
        final Vec2 acceleration = new Vec2(0.0, -this.gravity);
        for (final PendulumNode node : this.nodes) {
            if (node.invMass() == 0.0) {
                continue;
            }
            final Vec2 velocity = node.pos().subtract(node.prevPos());
            final Vec2 posNext = node.pos()
                .add(velocity)
                .add(acceleration.multiply(dtSub * dtSub));
            node.prevPos(node.pos());
            node.pos(posNext);
        }
    }

    private void satisfyConstraints() {
        for (int i = 0; i < this.nodes.size() - 1; i++) {
            final PendulumNode a = this.nodes.get(i);
            final PendulumNode b = this.nodes.get(i + 1);

            final Vec2 delta = b.pos().subtract(a.pos());
            final double dist = delta.length();
            if (dist < 1e-9) {
                continue;
            }

            final double w1 = a.invMass();
            final double w2 = b.invMass();
            final double wSum = w1 + w2;
            if (wSum == 0.0) {
                continue;
            }

            final double target = this.segmentLength[i];
            final double diff = dist - target;
            final Vec2 n = delta.multiply(1.0 / dist);
            final Vec2 correction = n.multiply(diff);

            a.pos(a.pos().add(correction.multiply(w1 / wSum)));
            b.pos(b.pos().subtract(correction.multiply(w2 / wSum)));
        }
    }

    private void applyDrag(double dtSub) {
        final double factor = Math.max(0.0, 1.0 - this.drag * dtSub);
        for (int i = 1; i < this.nodes.size(); i++) {
            final PendulumNode node = this.nodes.get(i);
            final Vec2 velocity = node.pos().subtract(node.prevPos()).multiply(factor);
            node.prevPos(node.pos().subtract(velocity));
        }
    }

    public void render(World world) {
        if (!this.configured()) {
            return;
        }
        Objects.requireNonNull(world, "world");
        final Particle.DustOptions rodDust = new Particle.DustOptions(Color.fromRGB(255, 200, 80), 0.8f);

        for (int i = 0; i < this.nodes.size() - 1; i++) {
            final Location from = toWorld(world, i);
            final Location to = toWorld(world, i + 1);
            final Vector delta = to.toVector().subtract(from.toVector());
            final double dist = delta.length();
            if (dist < 1e-6) {
                continue;
            }
            final double step = 0.15;
            final int samples = Math.max(1, (int) Math.ceil(dist / step));
            final Vector stride = delta.multiply(1.0 / samples);
            final Location cursor = from.clone();
            for (int s = 0; s <= samples; s++) {
                world.spawnParticle(Particle.DUST, cursor, 1, rodDust);
                cursor.add(stride);
            }
        }

        for (int i = 0; i < this.nodes.size(); i++) {
            final PendulumNode node = this.nodes.get(i);
            final double mass = Math.max(MIN_MASS, node.mass());
            final Location at = toWorld(world, i);
            final float size = (float) Math.min(1.4, 0.3 + mass * 0.05);
            final int intensity = (int) Math.min(255, 60 + mass * 8.0);
            final Particle.DustOptions bobDust = new Particle.DustOptions(
                Color.fromRGB(intensity, 70, 255 - Math.min(200, intensity)),
                size
            );
            world.spawnParticle(Particle.DUST, at, 3, bobDust);
        }
    }

    private Location toWorld(World world, int nodeIndex) {
        final Vec2 pos = this.nodes.get(nodeIndex).pos();
        final double worldX = this.anchor.getX() + pos.x() * this.scale;
        final double worldY = this.anchor.getY() + pos.y() * this.scale;
        final double worldZ = this.anchor.getZ();
        return new Location(world, worldX, worldY, worldZ);
    }

    private void ensureIndex(int idx) {
        if (idx < 0 || idx >= this.segmentLength.length) {
            throw new IndexOutOfBoundsException(idx);
        }
    }

    private void ensureNodeIndex(int idx) {
        if (idx < 0 || idx >= this.nodes.size()) {
            throw new IndexOutOfBoundsException(idx);
        }
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
