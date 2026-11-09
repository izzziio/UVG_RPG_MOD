package gt.edu.uvg.universityguide.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import gt.edu.uvg.universityguide.ModContent;
import gt.edu.uvg.universityguide.InstructionBook;
import gt.edu.uvg.universityguide.entity.GuideEntity;
import java.util.Comparator;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** Comandos para administrar guías y obtener marcadores de paradas. */
public final class GuideCommands {
    // Evita crear instancias de esta clase de utilidades.
    private GuideCommands() {
    }

    /** El libro es público; cada rama administrativa conserva su permiso. */
    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal("universityguide")
                .then(Commands.literal("book").executes(context ->
                        InstructionBook.give(context.getSource().getPlayerOrException(),false)?1:0))
                .then(Commands.literal("guide")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("spawn")
                                .then(Commands.argument("skinAccount", StringArgumentType.word())
                                        .then(Commands.argument("displayName", StringArgumentType.greedyString())
                                                .executes(GuideCommands::spawnGuide))))
                        .then(Commands.literal("cancel")
                                .then(Commands.argument("target", StringArgumentType.word())
                                        .executes(context -> affectGuide(context, false))))
                        .then(Commands.literal("remove")
                                .then(Commands.argument("target", StringArgumentType.word())
                                        .executes(context -> affectGuide(context, true)))))
                .then(Commands.literal("barrier")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("give").executes(GuideCommands::giveBarrier)))
                .then(Commands.literal("marker")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("give").executes(GuideCommands::giveMarker))));
    }

    /** Valida nombre, prepara apariencia y coloca el guía junto al jugador. */
    private static int spawnGuide(CommandContext<CommandSourceStack> context) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        ServerLevel level = context.getSource().getLevel();
        String skin = StringArgumentType.getString(context, "skinAccount");
        String displayName = StringArgumentType.getString(context, "displayName").trim();
        if (displayName.isBlank() || displayName.length() > 64) {
            context.getSource().sendFailure(Component.translatable("command.universityguide.invalid_name"));
            return 0;
        }

        GuideEntity guide = ModContent.GUIDE.get().create(level);
        if (guide == null) {
            context.getSource().sendFailure(Component.translatable("command.universityguide.spawn_failed"));
            return 0;
        }
        guide.moveTo(player.getX(), player.getY(), player.getZ(), player.getYRot(), 0.0F);
        guide.setCustomName(Component.literal(displayName));
        guide.setCustomNameVisible(true);
        guide.setSkinAccount(skin);
        if (!level.addFreshEntity(guide)) {
            context.getSource().sendFailure(Component.translatable("command.universityguide.spawn_failed"));
            return 0;
        }
        context.getSource().sendSuccess(
                () -> Component.translatable("command.universityguide.spawned", displayName, guide.getUUID().toString()), true);
        return 1;
    }

    /** Entrega marcadores solo a operadores creativos; suelta el objeto si no cabe. */
    private static int giveMarker(CommandContext<CommandSourceStack> context) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        if (!player.canUseGameMasterBlocks()) {
            context.getSource().sendFailure(Component.translatable("command.universityguide.creative_required"));
            return 0;
        }
        ItemStack marker = new ItemStack(ModContent.TOUR_STOP_ITEM.get());
        if (!player.addItem(marker)) {
            player.drop(marker, false);
        }
        context.getSource().sendSuccess(() -> Component.translatable("command.universityguide.marker_given"), false);
        return 1;
    }

    // Entrega la barrera únicamente a operadores en Creativo.
    private static int giveBarrier(CommandContext<CommandSourceStack> context) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        if (!gt.edu.uvg.universityguide.block.GuideBarrierBlock.canEdit(player)) {
            context.getSource().sendFailure(Component.translatable("command.universityguide.creative_required"));
            return 0;
        }
        ItemStack item = new ItemStack(ModContent.GUIDE_BARRIER_ITEM.get());
        if (!player.addItem(item)) player.drop(item, false);
        context.getSource().sendSuccess(() -> Component.translatable("command.universityguide.barrier_given"), false);
        return 1;
    }

    /** Comparte resolución del objetivo sin confundir cancelación y eliminación. */
    private static int affectGuide(CommandContext<CommandSourceStack> context, boolean remove)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        String target = StringArgumentType.getString(context, "target");
        GuideEntity guide = findGuide(player.serverLevel(), player, target);
        if (guide == null) {
            context.getSource().sendFailure(Component.translatable("command.universityguide.guide_not_found"));
            return 0;
        }
        String name = guide.getDisplayName().getString();
        if (remove) {
            guide.discard();
            context.getSource().sendSuccess(() -> Component.translatable("command.universityguide.removed", name), true);
        } else {
            guide.cancelTour(Component.translatable("message.universityguide.cancelled_by_admin"));
            context.getSource().sendSuccess(() -> Component.translatable("command.universityguide.cancelled", name), true);
        }
        return 1;
    }

    /** Busca cerca; los UUID explícitos se limitan a la dimensión actual. */
    @Nullable
    private static GuideEntity findGuide(ServerLevel level, ServerPlayer player, String target) {
        if (target.equalsIgnoreCase("nearest")) {
            return level.getEntitiesOfClass(GuideEntity.class, new AABB(player.blockPosition()).inflate(64.0)).stream()
                    .min(Comparator.comparingDouble(player::distanceToSqr))
                    .orElse(null);
        }
        try {
            Entity entity = level.getEntity(UUID.fromString(target));
            return entity instanceof GuideEntity guide ? guide : null;
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
