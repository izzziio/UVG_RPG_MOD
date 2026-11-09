package com.postedsignage;

import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.*;
import net.minecraft.network.syncher.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/** Capa visual persistente; no ocupa celdas ni intercepta clics. */
public final class TextureSkinEntity extends Entity {
    private static final EntityDataAccessor<CompoundTag> SKIN = SynchedEntityData.defineId(TextureSkinEntity.class, EntityDataSerializers.COMPOUND_TAG);

    private CompoundTag decodedTag;
    private SkinData decoded;
    private BlockState shapeState;
    private Direction shapeHalf;
    private BlockPos shapePos;
    private java.util.List<AABB> shapeBoxes;
    private record SkinData(BlockPos pos,Direction face,Direction source,Direction half,int rotation,
            BlockState sample,BlockState original,AABB culling) {}

    public TextureSkinEntity(EntityType<? extends TextureSkinEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    // Los datos de la cara viajan juntos al cliente.
    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) { builder.define(SKIN, new CompoundTag()); }
    // Los paquetes reemplazan el compuesto; solo entonces se interpretan sus datos.
    private SkinData data() {
        CompoundTag tag=entityData.get(SKIN);
        if(tag!=decodedTag){
            BlockPos pos=BlockPos.of(tag.getLong("SupportPos"));
            Direction half=Direction.byName(tag.getString("Half"));
            if(half!=null&&!half.getAxis().isHorizontal())half=null;
            var blocks=level().holderLookup(Registries.BLOCK);
            decoded=new SkinData(pos,direction(tag,"Face"),direction(tag,"SampleFace"),half,Math.floorMod(tag.getInt("Rotation"),4),
                    NbtUtils.readBlockState(blocks,tag.getCompound("Sample")),NbtUtils.readBlockState(blocks,tag.getCompound("Support")),
                    new AABB(pos).inflate(.01));
            decodedTag=tag;
        }
        return decoded;
    }
    public BlockPos support() { return data().pos(); }
    public Direction face() { return data().face(); }
    public Direction sampleFace() { return data().source(); }
    public Direction half() { return data().half(); }
    public int rotation() { return data().rotation(); }
    public BlockState sample() { return data().sample(); }
    private static Direction direction(CompoundTag tag,String key) {
        Direction result=Direction.byName(tag.getString(key));
        return result==null?Direction.NORTH:result;
    }
    // Las formas dinámicas conservan su consulta al mundo; las estáticas se reutilizan.
    public java.util.List<AABB> surfaceBoxes() {
        var skin=data();var state=level().getBlockState(skin.pos());
        if(state.getBlock().hasDynamicShape()||state.hasBlockEntity()) {
            var current=TextureSkinSurface.boxes(state,level(),skin.pos(),skin.half());
            // Consulta al mundo, pero conserva la malla si la forma no cambió.
            if(state!=shapeState||skin.half()!=shapeHalf||!skin.pos().equals(shapePos)||!current.equals(shapeBoxes)) {
                shapeBoxes=java.util.List.copyOf(current);
                shapeState=state;shapeHalf=skin.half();shapePos=skin.pos();
            }
            return shapeBoxes;
        }
        if(state!=shapeState||skin.half()!=shapeHalf||!skin.pos().equals(shapePos)){
            shapeBoxes=java.util.List.copyOf(TextureSkinSurface.boxes(state,level(),skin.pos(),skin.half()));
            shapeState=state;shapeHalf=skin.half();shapePos=skin.pos();
        }
        return shapeBoxes;
    }

    // Conserva la consulta anterior para recubrimientos de mundos existentes.
    public static TextureSkinEntity find(Level level, BlockPos pos, Direction face) {
        return level.getEntitiesOfClass(TextureSkinEntity.class, new AABB(pos).inflate(.1),
                skin -> skin.support().equals(pos) && skin.face() == face).stream().findFirst().orElse(null);
    }
    private static TextureSkinEntity findPart(Level level, BlockPos pos, Direction face, Direction half) {
        return level.getEntitiesOfClass(TextureSkinEntity.class, new AABB(pos).inflate(.1),
                skin -> skin.support().equals(pos) && skin.face() == face && skin.half() == half).stream().findFirst().orElse(null);
    }
    public static TextureSkinEntity findAt(Level level, BlockPos pos, Direction face, net.minecraft.world.phys.Vec3 hit) {
        Direction half = TextureSkinSurface.halfAt(level.getBlockState(pos),pos,hit);
        TextureSkinEntity skin = findPart(level,pos,face,half);
        return skin == null && half != null ? findPart(level,pos,face,null) : skin;
    }
    public static TextureSkinEntity apply(Level level, BlockPos pos, Direction face, BlockState sample, Direction source, int rotation) {
        return apply(level,pos,face,sample,source,rotation,null);
    }
    public static TextureSkinEntity apply(Level level, BlockPos pos, Direction face, BlockState sample, Direction source, int rotation, Direction half) {
        // Divide la pintura antigua completa y conserva la mitad que no se repinta.
        TextureSkinEntity whole = half == null ? null : findPart(level,pos,face,null);
        if (whole != null) {
            var state = level.getBlockState(pos);
            if (state.getBlock() instanceof VerticalSlabBlock && state.getValue(VerticalSlabBlock.FILL) != VerticalSlabBlock.Fill.NONE
                    && findPart(level,pos,face,half.getOpposite()) == null)
                write(level,pos,face,whole.sample(),whole.sampleFace(),whole.rotation(),half.getOpposite());
            whole.discard();
        }
        return write(level,pos,face,sample,source,rotation,half);
    }
    // Una capa por cara y mitad; repintar reutiliza la entidad correspondiente.
    private static TextureSkinEntity write(Level level, BlockPos pos, Direction face, BlockState sample, Direction source, int rotation, Direction half) {
        TextureSkinEntity skin = findPart(level,pos,face,half);
        boolean fresh = skin == null;
        if (fresh) skin = new TextureSkinEntity(PostedSignage.TEXTURE_SKIN.get(), level);
        CompoundTag data = new CompoundTag();
        data.putLong("SupportPos", pos.asLong());
        data.putString("Face", face.getName());
        if (half != null) data.putString("Half", half.getName());
        data.put("Support", NbtUtils.writeBlockState(level.getBlockState(pos)));
        data.put("Sample", NbtUtils.writeBlockState(sample));
        data.putString("SampleFace", source.getName());
        data.putInt("Rotation", Math.floorMod(rotation, 4));
        skin.entityData.set(SKIN, data);
        skin.setPos(pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5);
        if (fresh) level.addFreshEntity(skin);
        return skin;
    }

    // El soporte desaparecido elimina la pintura sin generar objetos.
    @Override public void tick() {
        super.tick();
        if (!level().isClientSide && level().hasChunkAt(support())) {
            BlockState original = data().original();
            if (original.isAir() || !level().getBlockState(support()).is(original.getBlock())) discard();
        }
    }
    @Override public boolean shouldRenderAtSqrDistance(double distance) { return distance < 128 * 128; }
    @Override public AABB getBoundingBoxForCulling() { return data().culling(); }
    @Override public boolean isIgnoringBlockTriggers() { return true; }
    @Override public boolean isPickable() { return false; }
    @Override public boolean isPushable() { return false; }
    @Override public boolean canBeCollidedWith() { return false; }
    @Override protected void readAdditionalSaveData(CompoundTag tag) { entityData.set(SKIN, tag.getCompound("Skin").copy()); }
    @Override protected void addAdditionalSaveData(CompoundTag tag) { tag.put("Skin", entityData.get(SKIN).copy()); }
}
