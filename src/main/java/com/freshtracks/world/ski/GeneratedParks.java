package com.freshtracks.world.ski;

import com.freshtracks.FreshTracks;
import com.freshtracks.registry.ModBlocks;
import com.freshtracks.resort.ParkFeatureBlock;
import com.freshtracks.weather.GroomedSnowBlock;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import com.freshtracks.world.feature.AlpineSurfaceFeature;

/** Seeded progression parks for newly generated Ski Mountains. Contains no real-resort/map data. */
public final class GeneratedParks {
    private record Key(long seed,int x,int z) {}
    private record Plan(double x,double z,double dx,double dz) {}
    private static final Map<Key,Optional<Plan>> PLANS=new ConcurrentHashMap<>();
    private static Optional<Plan> plan(Key key) {
        SkiTerrain terrain=SkiTerrain.forSeed(key.seed);
        Random random=new Random(key.seed ^ key.x*341873128712L ^ key.z*132897987541L);
        if(random.nextInt(3)==0)return Optional.empty();
        for(int attempt=0;attempt<24;attempt++) {
            int x=key.x*1024+220+random.nextInt(584),z=key.z*1024+220+random.nextInt(584);
            double y=terrain.height(x,z),slope=terrain.slope(x,z);
            if(y<115||y>290||slope<.12||slope>.38||terrain.moisture(x,z)>.25)continue;
            double gx=terrain.height(x+4,z)-terrain.height(x-4,z),gz=terrain.height(x,z+4)-terrain.height(x,z-4),n=Math.hypot(gx,gz);
            double dx=-gx/n,dz=-gz/n;
            boolean clear=true;
            for(int d=-140;d<=140;d+=20) {
                int px=(int)(x+dx*d),pz=(int)(z+dz*d);
                if(terrain.slope(px,pz)>.48||terrain.height(px,pz)>310){clear=false;break;}
            }
            if(clear)return Optional.of(new Plan(x,z,dx,dz));
        }
        return Optional.empty();
    }
    public static boolean decorate(WorldGenLevel level,int bx,int bz) {
        if(!level.getLevel().dimensionTypeRegistration().is(ResourceKey.create(Registries.DIMENSION_TYPE,FreshTracks.id("ski_mountains"))))return false;
        Key key=new Key(level.getSeed(),Math.floorDiv(bx,1024),Math.floorDiv(bz,1024));
        if(PLANS.size()>4096)PLANS.clear();
        Optional<Plan> value=PLANS.computeIfAbsent(key,GeneratedParks::plan);
        if(value.isEmpty())return false;
        Plan p=value.get();
        if(Math.hypot(bx+8-p.x,bz+8-p.z)>165)return false;
        boolean changed=false;
        for(int oz=0;oz<16;oz++)for(int ox=0;ox<16;ox++) {
            int x=bx+ox,z=bz+oz;
            double a=(x+.5-p.x)*p.dx+(z+.5-p.z)*p.dz+140;
            double b=-(x+.5-p.x)*p.dz+(z+.5-p.z)*p.dx;
            if(a<0||a>280||Math.abs(b)>18)continue;
            int base=AlpineSurfaceFeature.groundTop(level,x,z);
            BlockState existingSnow=level.getBlockState(new BlockPos(x,base,z));
            double snowBase=base+(existingSnow.getBlock() instanceof SnowLayerBlock?existingSnow.getValue(SnowLayerBlock.LAYERS)/8.0:0);
            BlockPos foot=new BlockPos(x,base-1,z);
            if(!natural(level.getBlockState(foot)))continue;
            boolean blocked=false;
            for(int y=base;y<base+42;y++)if(!natural(level.getBlockState(new BlockPos(x,y,z)))){blocked=true;break;}
            if(blocked)continue;
            // Clear only natural growth in this newly generated corridor. Authored structures are protected above.
            for(int y=base;y<base+42;y++) {
                BlockPos pos=new BlockPos(x,y,z);BlockState s=level.getBlockState(pos);
                if(s.is(BlockTags.LOGS)||s.is(BlockTags.LEAVES)||s.getBlock() instanceof SnowLayerBlock)level.setBlock(pos,Blocks.AIR.defaultBlockState(),2);
            }
            double rise=0;
            // Three progressive tabletop jumps on one side; roomy bypass down the centre.
            for(int i=0;i<3;i++) {
                double t=(a-(30+i*56))/32,u=Math.abs(b-8)/6;
                if(t>=0&&t<=1&&u<1)rise=Math.max(rise,(1.4+i*.9)*(t<.35?Math.pow(t/.35,1.8):t<.56?1:(1-t)/.44)*Math.min(1,(1-u)*4));
            }
            // A broad halfpipe below the jump/jib lanes, with tapered entrances and exits.
            if(a>200&&a<275&&Math.abs(b)<=15) {
                double u=Math.abs(b)/15;
                rise=6.7*(1-Math.sqrt(Math.max(0,1-u*u)))*Math.min(1,Math.min(a-200,275-a)/12);
            }
            int hardware=-1,deck=0;double hardwareAlong=0;
            for(int i=0;i<4;i++) {
                double centreA=37+i*42-140;
                double cx=Math.floor(p.x+p.dx*centreA+p.dz*9)+.5,cz=Math.floor(p.z+p.dz*centreA-p.dx*9)+.5;
                boolean alongX=Math.abs(p.dx)>Math.abs(p.dz);
                double direction=Math.signum(alongX?p.dx:p.dz);
                double along=(alongX?x+.5-cx:z+.5-cz)*direction,across=alongX?z+.5-cz:x+.5-cx;
                if(Math.abs(along)>8||Math.abs(across)>(i%2==0?1:.1))continue;
                double entryX=cx-(alongX?direction*4:0),entryZ=cz-(alongX?0:direction*4);
                deck=(int)Math.ceil(SkiTerrain.forSeed(key.seed).height((int)Math.floor(entryX),(int)Math.floor(entryZ)))+1;
                if(deck-base>5||deck<base-1)continue;
                hardware=i;hardwareAlong=along;
                if(Math.abs(along)>4)rise=Math.max(rise,Math.max(0,deck+(i%2==0?.5:.875)-base)*(1-(Math.abs(along)-4)/4));
                break;
            }
            double snowTop=snowBase+rise;
            int floor=(int)Math.floor(snowTop);int layers=(int)Math.round((snowTop-floor)*8);
            if(layers==0){floor--;layers=8;}
            for(int y=base;y<floor;y++)level.setBlock(new BlockPos(x,y,z),Blocks.SNOW_BLOCK.defaultBlockState(),2);
            BlockState snow=ModBlocks.GROOMED_SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS,layers)
                .setValue(GroomedSnowBlock.AXIS,Math.abs(p.dx)>Math.abs(p.dz)?Direction.Axis.X:Direction.Axis.Z);
            level.setBlock(new BlockPos(x,floor,z),snow,2);
            // Ride-on boxes and rails opposite the jumps. The neutral style is the generated default.
            if(hardware>=0&&Math.abs(hardwareAlong)<=4) {
                for(int y=base;y<deck;y++)level.setBlock(new BlockPos(x,y,z),Blocks.SNOW_BLOCK.defaultBlockState(),2);
                BlockState hardwareState=(hardware%2==0?ModBlocks.PARK_BOX:ModBlocks.PARK_RAIL).defaultBlockState()
                    .setValue(ParkFeatureBlock.AXIS,Math.abs(p.dx)>Math.abs(p.dz)?Direction.Axis.X:Direction.Axis.Z);
                level.setBlock(new BlockPos(x,deck,z),hardwareState,2);
            }
            changed=true;
        }
        return changed;
    }
    private static boolean natural(BlockState s) {
        return s.isAir()||s.is(BlockTags.LOGS)||s.is(BlockTags.LEAVES)||s.is(BlockTags.DIRT)||s.is(BlockTags.BASE_STONE_OVERWORLD)
            ||s.is(Blocks.SNOW_BLOCK)||s.is(Blocks.POWDER_SNOW)||s.getBlock() instanceof SnowLayerBlock
            ||s.canBeReplaced()&&s.getFluidState().isEmpty();
    }
}
