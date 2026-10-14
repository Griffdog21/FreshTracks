package com.freshtracks.resort;

import com.freshtracks.rig.RideableSurface;
import com.freshtracks.rig.Surface;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Wax-friendly park surfaces with a real collision top and no rocky-base damage. */
public class ParkFeatureBlock extends Block implements RideableSurface {
    public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.HORIZONTAL_AXIS;
    private final boolean box;
    public ParkFeatureBlock(boolean box, Properties properties) {
        super(properties); this.box=box;
        registerDefaultState(defaultBlockState().setValue(AXIS, Direction.Axis.Z));
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder) {builder.add(AXIS);}
    @Override public BlockState getStateForPlacement(BlockPlaceContext ctx) {return defaultBlockState().setValue(AXIS,ctx.getHorizontalDirection().getAxis());}
    @Override protected VoxelShape getShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext ctx) {
        if(box)return Block.box(0,0,0,16,8,16);
        return state.getValue(AXIS)==Direction.Axis.Z?Block.box(6,0,0,10,14,16):Block.box(0,0,6,16,14,10);
    }
    @Override public double rideHeight(BlockState state,BlockGetter level,BlockPos pos) {return box?.5:.875;}
    @Override public Surface surface(BlockState state) {return Surface.PARK_SLIDE;}
}
