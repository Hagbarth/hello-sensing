package dk.au.cs.ubi.hellosensing.model;

import com.google.common.base.Objects;

/**
 * An {@code AccelerometerEvent} holds a timestamp and the values for three axes.
 *
 * @author Markus Wüstenberg
 */
public class AccelerometerEvent {

    private final long timestamp;
    private final float x;
    private final float y;
    private final float z;

    public AccelerometerEvent(long timestamp, float x, float y, float z) {
        this.timestamp = timestamp;
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public float getX() {
        return x;
    }

    public float getY() {
        return y;
    }

    public float getZ() {
        return z;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        AccelerometerEvent that = (AccelerometerEvent) o;

        if (timestamp != that.timestamp) return false;
        if (Float.compare(that.x, x) != 0) return false;
        if (Float.compare(that.y, y) != 0) return false;
        if (Float.compare(that.z, z) != 0) return false;

        return true;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(timestamp, x, y, z);
    }

    @Override
    public String toString() {
        return Objects.toStringHelper(this)
                .add("timestamp", timestamp)
                .add("x", x)
                .add("y", y)
                .add("z", z)
                .toString();
    }
}
