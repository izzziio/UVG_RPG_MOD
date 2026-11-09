package gt.edu.uvg.universityguide;

import java.util.ArrayList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.WrittenBookContent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/** Entrega instrucciones sin modificar guías ni paradas del mundo. */
public final class InstructionBook {
    private static final String RECEIVED="UniversityGuideInstructionsReceived";
    private static final String BOOK="UniversityGuideInstructions";
    private InstructionBook() {}

    public static ItemStack create(){
        var pages=new ArrayList<Filterable<Component>>();
        for(int i=1;i<=5;i++)pages.add(Filterable.passThrough(Component.translatable("book.universityguide.page."+i)));
        var book=new ItemStack(Items.WRITTEN_BOOK);
        book.set(DataComponents.WRITTEN_BOOK_CONTENT,new WrittenBookContent(Filterable.passThrough("Tour UVG"),"University Guide",0,pages,true));
        book.set(DataComponents.CUSTOM_NAME,Component.translatable("book.universityguide.title"));
        var marker=new CompoundTag();marker.putBoolean(BOOK,true);
        book.set(DataComponents.CUSTOM_DATA,CustomData.of(marker));
        return book;
    }

    public static boolean isInstructionBook(ItemStack stack){
        return stack.is(Items.WRITTEN_BOOK)&&stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag().getBoolean(BOOK);
    }

    public static boolean hasReceived(Player player){
        return player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG).getBoolean(RECEIVED);
    }

    private static void remember(Player player){
        var kept=player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        kept.putBoolean(RECEIVED,true);player.getPersistentData().put(Player.PERSISTED_NBT_TAG,kept);
    }

    // Si no cabe, conserva la entrega pendiente; nunca reemplaza objetos.
    public static boolean give(Player player,boolean automatic){
        if(player.level().isClientSide||(automatic&&hasReceived(player)))return false;
        for(int i=0;i<player.getInventory().getContainerSize();i++)if(isInstructionBook(player.getInventory().getItem(i))){
            remember(player);
            if(!automatic)player.displayClientMessage(Component.translatable("book.universityguide.already_have"),false);
            return false;
        }
        if(!player.getInventory().add(create())){
            player.displayClientMessage(Component.translatable("book.universityguide.inventory_full"),false);
            return false;
        }
        remember(player);player.inventoryMenu.broadcastChanges();
        player.displayClientMessage(Component.translatable("book.universityguide.received"),false);
        return true;
    }

    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event){
        if(event.getEntity() instanceof ServerPlayer player)give(player,true);
    }

    // Morir o cambiar de dimensión no vuelve a activar la entrega inicial.
    public static void onClone(PlayerEvent.Clone event){
        if(hasReceived(event.getOriginal()))remember(event.getEntity());
    }
}
