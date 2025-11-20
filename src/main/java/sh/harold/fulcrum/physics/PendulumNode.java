package sh.harold.fulcrum.physics;

public final class PendulumNode {

    private Vec2 pos;
    private Vec2 prevPos;
    private double mass;
    private double invMass;

    public PendulumNode(Vec2 pos, Vec2 prevPos, double mass) {
        this.pos = pos;
        this.prevPos = prevPos;
        this.setMass(mass);
    }

    public Vec2 pos() {
        return this.pos;
    }

    public void pos(Vec2 pos) {
        this.pos = pos;
    }

    public Vec2 prevPos() {
        return this.prevPos;
    }

    public void prevPos(Vec2 prevPos) {
        this.prevPos = prevPos;
    }

    public double mass() {
        return this.mass;
    }

    public double invMass() {
        return this.invMass;
    }

    public void setMass(double mass) {
        if (mass <= 0.0) {
            this.mass = 0.0;
            this.invMass = 0.0;
            return;
        }
        this.mass = mass;
        this.invMass = 1.0 / mass;
    }
}
