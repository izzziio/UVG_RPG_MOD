package gt.edu.uvg.universityguide;

import com.mojang.authlib.GameProfile;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Entrega, recuperación, persistencia y permisos del libro. */
@GameTestHolder(UniversityGuideMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class InstructionBookGameTests {
    private static Player player(GameTestHelper h){
        return new Player(h.getLevel(),h.absolutePos(BlockPos.ZERO),0,new GameProfile(UUID.randomUUID(),"book test")){
            @Override public boolean isCreative(){return false;}
            @Override public boolean isSpectator(){return false;}
        };
    }

    @GameTest(template="empty") public static void instructionsAreDeliveredOnceAndCanBeRecovered(GameTestHelper h){
        var p=player(h);
        h.assertTrue(InstructionBook.give(p,true),"La primera entrega funciona");
        var book=p.getInventory().getItem(0);
        h.assertTrue(InstructionBook.isInstructionBook(book),"Libro escrito reconocible");
        h.assertTrue(book.get(DataComponents.WRITTEN_BOOK_CONTENT).pages().size()==5,"Cinco páginas de instrucciones");
        h.assertTrue(!InstructionBook.give(p,true)&&!InstructionBook.give(p,false),"Sin duplicados al entrar o con el comando");
        p.getInventory().setItem(40,book.copy());
        p.getInventory().setItem(0,ItemStack.EMPTY);
        h.assertTrue(!InstructionBook.give(p,false),"El libro en la mano secundaria también evita duplicados");
        p.getInventory().setItem(40,ItemStack.EMPTY);
        h.assertTrue(!InstructionBook.give(p,true),"Perder el libro no repite la entrega inicial");
        h.assertTrue(InstructionBook.give(p,false),"El comando de recuperación puede reemplazar un libro perdido");h.succeed();
    }

    @GameTest(template="empty") public static void fullInventoryKeepsDeliveryPending(GameTestHelper h){
        var p=player(h);
        for(int i=0;i<p.getInventory().items.size();i++)p.getInventory().setItem(i,new ItemStack(Items.STONE,64));
        h.assertTrue(!InstructionBook.give(p,true)&&!InstructionBook.hasReceived(p),"Sin pérdida ni marca de entrega con el inventario lleno");
        h.assertTrue(p.getInventory().getItem(0).is(Items.STONE),"Los objetos existentes no se alteran");
        p.getInventory().setItem(0,ItemStack.EMPTY);
        h.assertTrue(InstructionBook.give(p,true),"La entrega se reintenta al liberar espacio");h.succeed();
    }

    @GameTest(template="empty") public static void deathPreservesReceiptAndOtherPersistentData(GameTestHelper h){
        var original=player(h);InstructionBook.give(original,true);var clone=player(h);
        clone.getPersistentData().putString("OtherMod","keep");
        InstructionBook.onClone(new PlayerEvent.Clone(clone,original,true));
        h.assertTrue(InstructionBook.hasReceived(clone)&&!InstructionBook.give(clone,true),"Morir no duplica el libro");
        h.assertTrue(clone.getPersistentData().getString("OtherMod").equals("keep"),"Se conservan los demás datos persistentes");
        var saved=clone.getPersistentData().copy();var restored=player(h);restored.getPersistentData().merge(saved);
        h.assertTrue(InstructionBook.hasReceived(restored),"La marca de entrega sobrevive a la restauración de NBT");h.succeed();
    }

    @GameTest(template="empty") public static void bookIsPublicButAdministrationStaysRestricted(GameTestHelper h){
        var root=h.getLevel().getServer().getCommands().getDispatcher().getRoot().getChild("universityguide");
        var source=h.getLevel().getServer().createCommandSourceStack().withPermission(0);
        h.assertTrue(root.canUse(source)&&root.getChild("book").canUse(source),"El comando del libro funciona sin permisos de operador");
        for(var name:new String[]{"guide","barrier","marker"}){
            h.assertTrue(!root.getChild(name).canUse(source),"La rama administrativa sigue restringida:"+name);
            h.assertTrue(root.getChild(name).canUse(source.withPermission(2)),"El operador puede usar la rama:"+name);
        }h.succeed();
    }
}
