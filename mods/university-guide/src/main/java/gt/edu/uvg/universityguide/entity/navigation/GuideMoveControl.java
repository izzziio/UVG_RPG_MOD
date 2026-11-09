package gt.edu.uvg.universityguide.entity.navigation;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.phys.Vec3;

/** Camina usando pasos físicos de medio bloque, sin solicitar saltos. */
public final class GuideMoveControl extends MoveControl {
    public GuideMoveControl(Mob mob) { super(mob); }

    @Override public void tick() {
        if (operation != Operation.MOVE_TO) {
            mob.setZza(0);
            return;
        }
        operation = Operation.WAIT;
        Vec3 target = new Vec3(wantedX, wantedY, wantedZ);
        boolean barrier=GuideRouteRules.blocked(mob.level(),mob.getBoundingBox());
        if (barrier || !GuideDoors.openAhead(mob,target)
                || !GuideRouteRules.canTraverse(mob.level(), mob.position(), target, mob.getBbWidth(), mob.getBbHeight())) {
            // Al bajar un peldaño espera el apoyo sin cancelar el resto de la ruta.
            if(!barrier&&!mob.onGround()&&mob.getDeltaMovement().y<0&&nearStep(target)){
                mob.setZza(0);mob.setDeltaMovement(0,mob.getDeltaMovement().y,0);return;
            }
            mob.getNavigation().stop();
            mob.setZza(0);
            mob.setDeltaMovement(0, mob.getDeltaMovement().y, 0);
            return;
        }
        double dx = wantedX-mob.getX(), dz = wantedZ-mob.getZ();
        if (dx*dx+dz*dz < 0.000225) { mob.setZza(0); return; }
        mob.setYRot(rotlerp(mob.getYRot(), (float)(Mth.atan2(dz,dx)*180/Math.PI)-90, 90));
        mob.setSpeed((float)(speedModifier*mob.getAttributeValue(Attributes.MOVEMENT_SPEED)));
    }
    private boolean nearStep(Vec3 target){
        for(var p:new net.minecraft.core.BlockPos[]{mob.blockPosition(),mob.blockPosition().below(),
                net.minecraft.core.BlockPos.containing(target).below()}){
            var s=mob.level().getBlockState(p);
            if(s.is(net.minecraft.tags.BlockTags.STAIRS)||s.is(net.minecraft.tags.BlockTags.SLABS))return true;
        }
        return false;
    }
}
