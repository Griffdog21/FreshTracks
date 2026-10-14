package com.freshtracks.rig;

import com.freshtracks.resort.ParkFeatureBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/** Exact deck contact. A narrow rail does not turn its entire block column into a snow ramp. */
public record ParkContact(BlockPos pos, Direction.Axis axis, double height, boolean box) {
    public double crossCenter() { return (axis == Direction.Axis.X ? pos.getZ() : pos.getX()) + 0.5; }
    public double along(double x, double z) { return axis == Direction.Axis.X ? x : z; }

    public boolean continues(ParkContact other) {
        return other.axis == axis && Math.abs(other.height - height) < 0.02
            && Math.abs(other.crossCenter() - crossCenter()) < 0.02;
    }

    /** Tolerance is only across the deck, for the width of the skis/board; never extends a rail's end. */
    public static @Nullable ParkContact find(BlockGetter level, double x, double z,
            double minHeight, double maxHeight, double tolerance) {
        ParkContact best = null;
        double bestDistance = Double.MAX_VALUE;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int bx = Mth.floor(x - tolerance); bx <= Mth.floor(x + tolerance); bx++) {
            for (int bz = Mth.floor(z - tolerance); bz <= Mth.floor(z + tolerance); bz++) {
                for (int by = Mth.floor(maxHeight); by >= Mth.floor(minHeight) - 1; by--) {
                    p.set(bx, by, bz);
                    BlockState state = level.getBlockState(p);
                    if (!(state.getBlock() instanceof ParkFeatureBlock park)) continue;
                    double height = by + park.rideHeight(state, level, p);
                    if (height < minHeight || height > maxHeight) continue;
                    Direction.Axis axis = state.getValue(ParkFeatureBlock.AXIS);
                    double along = axis == Direction.Axis.X ? x - bx : z - bz;
                    double cross = axis == Direction.Axis.X ? z - bz - 0.5 : x - bx - 0.5;
                    boolean box = park.rideHeight(state, level, p) <= 0.5;
                    double halfWidth = box ? 0.5 : 0.125;
                    if (along < 0 || along >= 1 || Math.abs(cross) > halfWidth + tolerance) continue;
                    // A deck covered by a solid block is not an available grinding surface.
                    if (!level.getBlockState(p.above()).getCollisionShape(level, p.above()).isEmpty()) continue;
                    double distance = Math.abs(cross);
                    if (best == null || height > best.height + 0.02 || distance < bestDistance && height >= best.height - 0.02) {
                        best = new ParkContact(p.immutable(), axis, height, box);
                        bestDistance = distance;
                    }
                }
            }
        }
        return best;
    }
}
