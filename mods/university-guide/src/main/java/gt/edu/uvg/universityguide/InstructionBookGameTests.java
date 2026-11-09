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
        h.assertTrue(InstructionBook.give(p,true),"First delivery succeeds");
        var book=p.getInventory().getItem(0);
        h.assertTrue(InstructionBook.isInstructionBook(book),"Recognizable written book");
        h.assertTrue(book.get(DataComponents.WRITTEN_BOOK_CONTENT).pages().size()==5,"Five instruction pages");
        h.assertTrue(!InstructionBook.give(p,true)&&!InstructionBook.give(p,false),"No duplicate on login or command");
        p.getInventory().setItem(40,book.copy());
        p.getInventory().setItem(0,ItemStack.EMPTY);
        h.assertTrue(!InstructionBook.give(p,false),"Off-hand book also prevents duplicates");
        p.getInventory().setItem(40,ItemStack.EMPTY);
        h.assertTrue(!InstructionBook.give(p,true),"Losing book does not repeat initial delivery");
        h.assertTrue(InstructionBook.give(p,false),"Recovery command can replace a lost book");h.succeed();
    }

    @GameTest(template="empty") public static void fullInventoryKeepsDeliveryPending(GameTestHelper h){
        var p=player(h);
        for(int i=0;i<p.getInventory().items.size();i++)p.getInventory().setItem(i,new ItemStack(Items.STONE,64));
        h.assertTrue(!InstructionBook.give(p,true)&&!InstructionBook.hasReceived(p),"No loss or receipt when inventory is full");
        h.assertTrue(p.getInventory().getItem(0).is(Items.STONE),"Existing items stay untouched");
        p.getInventory().setItem(0,ItemStack.EMPTY);
        h.assertTrue(InstructionBook.give(p,true),"Delivery retries after space is freed");h.succeed();
    }

    @GameTest(template="empty") public static void deathPreservesReceiptAndOtherPersistentData(GameTestHelper h){
        var original=player(h);InstructionBook.give(original,true);var clone=player(h);
        clone.getPersistentData().putString("OtherMod","keep");
        InstructionBook.onClone(new PlayerEvent.Clone(clone,original,true));
        h.assertTrue(InstructionBook.hasReceived(clone)&&!InstructionBook.give(clone,true),"Death does not duplicate book");
        h.assertTrue(clone.getPersistentData().getString("OtherMod").equals("keep"),"Other persistent data preserved");
        var saved=clone.getPersistentData().copy();var restored=player(h);restored.getPersistentData().merge(saved);
        h.assertTrue(InstructionBook.hasReceived(restored),"Receipt survives NBT restoration");h.succeed();
    }

    @GameTest(template="empty") public static void bookIsPublicButAdministrationStaysRestricted(GameTestHelper h){
        var root=h.getLevel().getServer().getCommands().getDispatcher().getRoot().getChild("universityguide");
        var source=h.getLevel().getServer().createCommandSourceStack().withPermission(0);
        h.assertTrue(root.canUse(source)&&root.getChild("book").canUse(source),"Book command works without operator permissions");
        for(var name:new String[]{"guide","barrier","marker"}){
            h.assertTrue(!root.getChild(name).canUse(source),"Administrative branch stays restricted: "+name);
            h.assertTrue(root.getChild(name).canUse(source.withPermission(2)),"Operator can use branch: "+name);
        }h.succeed();
    }
}
