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
import sh.harold.fulcrum.physics.ParticleStyle;

public final class PendulumChain {

    private static final double MIN_LENGTH = 0.5;
    private static final double MAX_LENGTH = 3.0;
    private static final double DEFAULT_LENGTH = 1.0;
    private static final double MIN_MASS = 0.1;
    private static final double MAX_MASS = 25.0;

    private final int id;
    private final List<PendulumNode> nodes = new ArrayList<>();
    private double[] segmentLength = new double[0];
    private ParticleStyle particleStyle = ParticleStyle.WEIGHTED;
    private Location anchor;
    private double scale = 2.0;
    private double gravity = 9.81;
    private double drag = 0.01;
    private int iterations = 8;
    private int substeps = 10;
    private boolean active;
    private boolean traceTip;
    private boolean showNodes = true;
    private float nodeParticleSize = 1.0f;
    private TipTrailStyle tipTrailStyle = TipTrailStyle.END_ROD;

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

    public ParticleStyle particleStyle() {
        return this.particleStyle;
    }

    public void particleStyle(ParticleStyle style) {
        this.particleStyle = style;
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

    public boolean traceTip() {
        return this.traceTip;
    }

    public void traceTip(boolean traceTip) {
        this.traceTip = traceTip;
    }

    public boolean showNodes() {
        return this.showNodes;
    }

    public void showNodes(boolean showNodes) {
        this.showNodes = showNodes;
    }

    public float nodeParticleSize() {
        return this.nodeParticleSize;
    }

    public void nodeParticleSize(float size) {
        this.nodeParticleSize = Math.max(0.2f, Math.min(2.5f, size));
    }

    public TipTrailStyle tipTrailStyle() {
        return this.tipTrailStyle;
    }

    public void tipTrailStyle(TipTrailStyle style) {
        this.tipTrailStyle = style;
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
                if (i == 0) {
                    cumulativeAngle = rng.nextDouble(0.0, Math.PI * 2.0);
                } else {
                    final double perturb = rng.nextDouble(-Math.PI, Math.PI);
                    cumulativeAngle += perturb * 0.5;
                }
                direction = new Vec2(Math.cos(cumulativeAngle), Math.sin(cumulativeAngle));
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
            final ParticleStyle style = this.particleStyle;
            final double massA = Math.max(MIN_MASS, this.nodes.get(i).mass());
            final double massB = Math.max(MIN_MASS, this.nodes.get(i + 1).mass());
            final double massSample = (massA + massB) * 0.5;
            final Particle.DustOptions rodDust = style == ParticleStyle.WEIGHTED
                ? new Particle.DustOptions(colorForMass(massSample), 0.8f)
                : null;
            for (int s = 0; s <= samples; s++) {
                switch (style) {
                    case SPARK -> world.spawnParticle(Particle.ELECTRIC_SPARK, cursor, 1, 0.0, 0.0, 0.0, 0.0);
                    case BUBBLE -> world.spawnParticle(Particle.BUBBLE, cursor, 1, 0.0, 0.0, 0.0, 0.0);
                    case BUBBLE_COLUMN_UP -> world.spawnParticle(Particle.BUBBLE_COLUMN_UP, cursor, 1, 0.0, 0.0, 0.0, 0.0);
                    case BUBBLE_POP -> world.spawnParticle(Particle.BUBBLE_POP, cursor, 1, 0.0, 0.0, 0.0, 0.0);
                    case WEIGHTED -> {
                        if (rodDust != null) {
                            world.spawnParticle(Particle.DUST, cursor, 1, rodDust);
                        }
                    }
                }
                cursor.add(stride);
            }
        }

        for (int i = 0; i < this.nodes.size(); i++) {
            final PendulumNode node = this.nodes.get(i);
            final double mass = Math.max(MIN_MASS, node.mass());
            final Location at = toWorld(world, i);
            if (!this.showNodes && i != 0) {
                continue;
            }
            final float size = Math.max(this.nodeParticleSize, (float) Math.min(1.4, 0.3 + mass * 0.05));
            final ParticleStyle style = this.particleStyle;
            switch (style) {
                case SPARK -> world.spawnParticle(Particle.ELECTRIC_SPARK, at, 4, 0.0, 0.0, 0.0, 0.0);
                case BUBBLE -> world.spawnParticle(Particle.BUBBLE, at, 3, 0.0, 0.0, 0.0, 0.0);
                case BUBBLE_COLUMN_UP -> world.spawnParticle(Particle.BUBBLE_COLUMN_UP, at, 3, 0.0, 0.0, 0.0, 0.0);
                case BUBBLE_POP -> world.spawnParticle(Particle.BUBBLE_POP, at, 3, 0.0, 0.0, 0.0, 0.0);
                case WEIGHTED -> {
                    final Color color = Color.fromRGB(255, 255, 255);
                    final Particle.DustOptions bobDust = new Particle.DustOptions(color, size);
                    world.spawnParticle(Particle.DUST, at, 3, bobDust);
                }
            }
        }

        if (this.traceTip && !this.nodes.isEmpty()) {
            final Location tip = toWorld(world, this.nodes.size() - 1);
            final Vec2 tipPos = this.nodes.get(this.nodes.size() - 1).pos();
            final Vec2 tipPrev = this.nodes.get(this.nodes.size() - 1).prevPos();
            final Vec2 delta = tipPos.subtract(tipPrev);
            final double dist = delta.length();
            if (dist > 1e-6) {
                final int samples = Math.max(1, (int) Math.ceil(dist / 0.05));
                final Vec2 step = delta.multiply(1.0 / samples);
                Vec2 cursor = tipPrev;
                for (int i = 0; i <= samples; i++) {
                    final Location loc = new Location(
                        world,
                        this.anchor.getX() + cursor.x() * this.scale,
                        this.anchor.getY() + cursor.y() * this.scale,
                        this.anchor.getZ()
                    );
                    world.spawnParticle(mapTipParticle(), loc, 1, 0.0, 0.0, 0.0, 0.0);
                    cursor = cursor.add(step);
                }
            } else {
                world.spawnParticle(mapTipParticle(), tip, 1, 0.0, 0.0, 0.0, 0.0);
            }
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

    private Particle mapTipParticle() {
        return switch (this.tipTrailStyle) {
            case END_ROD -> Particle.END_ROD;
            case FALLING -> Particle.FALLING_OBSIDIAN_TEAR;
            case CHERRY -> Particle.CHERRY_LEAVES;
        };
    }

    private Color colorForMass(double mass) {
        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;
        for (int i = 1; i < this.nodes.size(); i++) {
            final double m = Math.max(MIN_MASS, this.nodes.get(i).mass());
            min = Math.min(min, m);
            max = Math.max(max, m);
        }
        if (!Double.isFinite(min) || !Double.isFinite(max)) {
            min = max = mass;
        }
        final double span = Math.max(1e-9, max - min);
        final double t = Math.max(0.0, Math.min(1.0, (mass - min) / span));
        final int r = (int) Math.round(64 + (180 - 64) * t);
        final int g = (int) Math.round(255 - (255 - 32) * t);
        final int b = (int) Math.round(128 - (128 - 32) * t);
        return Color.fromRGB(r, g, b);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
