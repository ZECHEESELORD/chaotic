package sh.harold.fulcrum.physics;

public record Vec2(double x, double y) {

    public static final Vec2 ZERO = new Vec2(0.0, 0.0);

    public Vec2 add(Vec2 other) {
        return new Vec2(this.x + other.x, this.y + other.y);
    }

    public Vec2 subtract(Vec2 other) {
        return new Vec2(this.x - other.x, this.y - other.y);
    }

    public Vec2 multiply(double scalar) {
        return new Vec2(this.x * scalar, this.y * scalar);
    }

    public double length() {
        return Math.hypot(this.x, this.y);
    }

    public double lengthSquared() {
        return this.x * this.x + this.y * this.y;
    }

    public Vec2 normalize() {
        final double len = this.length();
        if (len < 1e-12) {
            return ZERO;
        }
        return this.multiply(1.0 / len);
    }
}
