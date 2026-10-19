package gt.edu.uvg.universityguide.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import gt.edu.uvg.universityguide.ModContent;
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

/** Operator command tree for spawning, finding, cancelling and removing guides or obtaining stop markers. */
public final class GuideCommands {
    // Evita crear instancias de esta clase de utilidades.
    private GuideCommands() {
    }

    /** The root permission gate protects every administrative subcommand. */
    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal("universityguide")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("guide")
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
                .then(Commands.literal("marker")
                        .then(Commands.literal("give").executes(GuideCommands::giveMarker))));
    }

    /** Validate the display name, initialize the skin and place the guide at the invoking player's location. */
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

    /** Markers are editing tools; require creative-operator access and drop the item if inventory is full. */
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

    /** Share target resolution while keeping cancellation distinct from permanent entity removal. */
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

    /** The nearest search is local; explicit UUID lookup is restricted to the player's current dimension. */
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
