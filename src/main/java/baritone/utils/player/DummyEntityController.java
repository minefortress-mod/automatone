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

package baritone.utils.player;

import baritone.api.minefortress.IFortressColonist;
import baritone.api.utils.IEntityContext;
import baritone.api.utils.IPlayerController;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.GameMode;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * A stubbed controller implementation for entities that cannot break or place blocks
 */
public class DummyEntityController implements IPlayerController {
    public static final DummyEntityController INSTANCE = new DummyEntityController();

    @Override
    public boolean hasBrokenBlock() {
        return false;
    }

    @Override
    public boolean onPlayerDamageBlock(BlockPos pos, Direction side) {
        return false;
    }

    @Override
    public void resetBlockRemoving() {
        // NO-OP
    }

    @Override
    public GameMode getGameType() {
        return GameMode.SURVIVAL;
    }

    @Override
    public ActionResult processRightClickBlock(LivingEntity entity, World world, Hand hand, BlockHitResult result, IEntityContext ctx) {
        if(hand != Hand.MAIN_HAND) {
            return ActionResult.FAIL;
        }

        final var stack = entity.getStackInHand(hand);
        final var item = stack.getItem();
        final var context = new FortressItemUsageContext(entity.world, null, hand, new ItemStack(item), result);
        final var actionResult = item.useOnBlock(context);
        if(actionResult.isAccepted()) {
            if(entity instanceof IFortressColonist colonist && stack.isIn(ctx.baritone().settings().acceptableThrowawayItems.get())) {
                colonist.getScaffoldsControl().addBlock(result.getBlockPos().offset(result.getSide()));
            }
        }
        return actionResult;
    }

    @Override
    public ActionResult processRightClick(LivingEntity player, World world, Hand hand) {
        return ActionResult.FAIL;
    }

    @Override
    public boolean clickBlock(BlockPos loc, Direction face) {
        return false;
    }

    @Override
    public void setHittingBlock(boolean hittingBlock) {
        // NO-OP
    }

    @Override
    public double getBlockReachDistance() {
        return 5.0;
    }

    private static class FortressItemUsageContext extends ItemUsageContext {
        public FortressItemUsageContext(World world, @Nullable PlayerEntity player, Hand hand, ItemStack stack, BlockHitResult hit) {
            super(world, player, hand, stack, hit);
        }
    }
}
