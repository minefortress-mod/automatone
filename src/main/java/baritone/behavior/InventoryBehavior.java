/*
 * This file is part of Baritone.
 *
 * Baritone is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Baritone is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Baritone.  If not, see <https://www.gnu.org/licenses/>.
 */

package baritone.behavior;

import baritone.Baritone;
import baritone.api.minefortress.IMinefortressEntity;
import baritone.utils.ToolSet;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.*;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.OptionalInt;
import java.util.Random;
import java.util.function.Predicate;

public final class InventoryBehavior extends Behavior {

    public InventoryBehavior(Baritone baritone) {
        super(baritone);
    }

    @Override
    public void onTickServer() {
        if (!baritone.settings().allowInventory.get()) {
            return;
        }
        final var entity = ctx.entity();
        final var inventory = IMinefortressEntity.of(entity).getInventory();
        if (inventory == null) return;

        if (firstValidThrowaway(inventory) >= 9) { // aka there are none on the hotbar, but there are some in main inventory
            swapWithHotBar(firstValidThrowaway(inventory), 8, inventory);
        }
        int pick = bestToolAgainst(Blocks.STONE, PickaxeItem.class);
        if (pick >= 9) {
            swapWithHotBar(pick, 0, inventory);
        }
    }

    public void attemptToPutOnHotbar(int inMainInvy, Predicate<Integer> disallowedHotbar, Inventory inventory) {
        OptionalInt destination = getTempHotbarSlot(disallowedHotbar);
        if (destination.isPresent()) {
            swapWithHotBar(inMainInvy, destination.getAsInt(), inventory);
        }
    }

    public OptionalInt getTempHotbarSlot(Predicate<Integer> disallowedHotbar) {
        Inventory inventory = ctx.inventory();
        if (inventory == null) return OptionalInt.empty();

        // we're using 0 and 8 for pickaxe and throwaway
        ArrayList<Integer> candidates = new ArrayList<>();
        for (int i = 1; i < 8; i++) {
            if (inventory.getStack(i).isEmpty() && !disallowedHotbar.test(i)) {
                candidates.add(i);
            }
        }

        if (candidates.isEmpty()) {
            for (int i = 1; i < 8; i++) {
                if (!disallowedHotbar.test(i)) {
                    candidates.add(i);
                }
            }
        }

        if (candidates.isEmpty()) {
            return OptionalInt.empty();
        }

        return OptionalInt.of(candidates.get(new Random().nextInt(candidates.size())));
    }

    private void swapWithHotBar(int inInventory, int inHotbar, Inventory inventory) {
        ItemStack h = inventory.getStack(inHotbar);
        inventory.setStack(inHotbar, inventory.getStack(inInventory));
        inventory.setStack(inInventory, h);
    }

    private int firstValidThrowaway(Inventory inventory) { // TODO offhand idk
        for (int i = 0; i < inventory.size(); i++) {
            if (inventory.getStack(i).isIn(baritone.settings().acceptableThrowawayItems.get())) {
                return i;
            }
        }
        return -1;
    }

    private int bestToolAgainst(Block against, Class<? extends ToolItem> cla$$) {
        Inventory invy = ctx.inventory();
        if(invy == null) return -1;
        int bestInd = -1;
        double bestSpeed = -1;
        for (int i = 0; i < invy.size(); i++) {
            ItemStack stack = invy.getStack(i);
            if (stack.isEmpty()) {
                continue;
            }
            if (baritone.settings().itemSaver.get() && stack.getDamage() >= stack.getMaxDamage() && stack.getMaxDamage() > 1) {
                continue;
            }
            if (cla$$.isInstance(stack.getItem())) {
                double speed = ToolSet.calculateSpeedVsBlock(stack, against.getDefaultState()); // takes into account enchants
                if (speed > bestSpeed) {
                    bestSpeed = speed;
                    bestInd = i;
                }
            }
        }
        return bestInd;
    }

    public boolean hasGenericThrowaway() {
        return throwaway(false,
                stack -> stack.isIn(baritone.settings().acceptableThrowawayItems.get()));
    }

    public boolean selectThrowawayForLocation(boolean select, int x, int y, int z) {
        final var entity = ctx.entity();
        final PlayerEntity player = IMinefortressEntity.of(entity).getPlayer();

        BlockState maybe = baritone.getBuilderProcess().placeAt(x, y, z, baritone.bsi.get0(x, y, z));
        if (maybe != null && throwaway(select, stack -> stack.getItem() instanceof BlockItem && maybe.equals(((BlockItem) stack.getItem()).getBlock().getPlacementState(new ItemPlacementContext(new ItemUsageContext(ctx.world(), player, Hand.MAIN_HAND, stack, new BlockHitResult(new Vec3d(entity.getX(), entity.getY(), entity.getZ()), Direction.UP, ctx.feetPos(), false)) {}))))) {
            return true; // gotem
        }
        if (maybe != null && throwaway(select, stack -> stack.getItem() instanceof BlockItem && ((BlockItem) stack.getItem()).getBlock().equals(maybe.getBlock()))) {
            return true;
        }
        return throwaway(select,
                stack -> stack.isIn(baritone.settings().acceptableThrowawayItems.get()));
    }

    public boolean throwaway(boolean select, Predicate<? super ItemStack> desired) {
        final var entity = ctx.entity();
        final var mfEnt = IMinefortressEntity.of(entity);
        Inventory inv = mfEnt.getInventory();
        if (inv == null) {
            return false;
        }
        for (int i = 0; i < 9; i++) {
            ItemStack item = inv.getStack(i);
            // this usage of settings() is okay because it's only called once during pathing
            // (while creating the CalculationContext at the very beginning)
            // and then it's called during execution
            // since this function is never called during cost calculation, we don't need to migrate
            // acceptableThrowawayItems to the CalculationContext
            if (desired.test(item)) {
                if (select) {
                    mfEnt.selectSlot(i);
                }
                return true;
            }
        }
//        if (desired.test(p.getInventory().offHand.get(0))) {
//            // main hand takes precedence over off hand
//            // that means that if we have block A selected in main hand and block B in off hand, right clicking places block B
//            // we've already checked above ^ and the main hand can't possible have an acceptablethrowawayitem
//            // so we need to select in the main hand something that doesn't right click
//            // so not a shovel, not a hoe, not a block, etc
//            for (int i = 0; i < 9; i++) {
//                ItemStack item = inv.getStack(i);
//                if (item.isEmpty() || item.getItem() instanceof PickaxeItem) {
//                    if (select) {
//                        p.getInventory().selectedSlot = i;
//                    }
//                    return true;
//                }
//            }
//        }
        return false;
    }

    public static int getSlotWithStack(Inventory inv, TagKey<Item> tag) {
        for(int i = 0; i < inv.size(); ++i) {
            if (!inv.getStack(i).isEmpty() && inv.getStack(i).isIn(tag)) {
                return i;
            }
        }

        return -1;
    }
}
