package dev.dsh.killaura;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class AnglesTest {
    private static final double EPSILON = 1.0E-4;

    @Test
    void looksStraightAhead() {
        float[] rot = Angles.lookAt(Vec3.ZERO, new Vec3(0.0, 0.0, 10.0), 0.0F);
        assertEquals(0.0, rot[0], EPSILON);
        assertEquals(0.0, rot[1], EPSILON);
    }

    @Test
    void looksAlongPositiveX() {
        float[] rot = Angles.lookAt(Vec3.ZERO, new Vec3(10.0, 0.0, 0.0), 0.0F);
        assertEquals(-90.0, rot[0], EPSILON);
        assertEquals(0.0, rot[1], EPSILON);
    }

    @Test
    void keepsYawContinuousAcrossTheBoundary() {
        float[] rot = Angles.lookAt(Vec3.ZERO, new Vec3(0.1, 0.0, -10.0), 179.0F);
        // Target yaw is about -179.4 degrees; from 179 that is a ~1.6 degree turn, not -358.
        assertTrue(Math.abs(rot[0] - 179.0F) < 5.0F);
    }

    @Test
    void clampsPitch() {
        // Minecraft pitch is positive downwards.
        float[] straightDown = Angles.lookAt(Vec3.ZERO, new Vec3(0.0, -10.0, 0.0), 0.0F);
        assertEquals(90.0, straightDown[1], EPSILON);

        float[] straightUp = Angles.lookAt(Vec3.ZERO, new Vec3(0.0, 10.0, 0.0), 0.0F);
        assertEquals(-90.0, straightUp[1], EPSILON);
    }

    @Test
    void measuresOffAxisAngle() {
        Vec3 eyes = Vec3.ZERO;
        assertEquals(0.0, Angles.offAxisDegrees(0.0F, 0.0F, eyes, new Vec3(0.0, 0.0, 10.0)), EPSILON);
        assertEquals(90.0, Angles.offAxisDegrees(0.0F, 0.0F, eyes, new Vec3(10.0, 0.0, 0.0)), 1.0E-3);
        assertEquals(180.0, Angles.offAxisDegrees(0.0F, 0.0F, eyes, new Vec3(0.0, 0.0, -10.0)), EPSILON);
        assertEquals(0.0, Angles.offAxisDegrees(0.0F, 0.0F, eyes, eyes), EPSILON);
    }

    @Test
    void gapToBoxUsesShortestDistance() {
        AABB box = new AABB(0.0, 0.0, 0.0, 1.0, 1.0, 1.0);
        assertEquals(0.0, TargetFilter.gapToBox(new Vec3(0.5, 0.5, 0.5), box), EPSILON);
        assertEquals(2.0, TargetFilter.gapToBox(new Vec3(3.0, 0.5, 0.5), box), EPSILON);
        // 3-4-5 triangle from the corner in the XZ plane.
        assertEquals(5.0, TargetFilter.gapToBox(new Vec3(4.0, 0.5, 5.0), box), EPSILON);
    }
}
