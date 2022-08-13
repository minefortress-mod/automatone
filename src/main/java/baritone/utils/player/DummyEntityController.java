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
import baritone.api.minefortress.IMinefortressEntity;
import baritone.api.utils.IEntityContext;
import baritone.api.utils.IPlayerController;
import net.minecraft.block.*;
import net.minecraft.block.pattern.CachedBlockPosition;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.fluid.FlowableFluid;
import net.minecraft.fluid.Fluid;
import net.minecraft.fluid.Fluids;
import net.minecraft.item.BucketItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.tag.FluidTags;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.registry.Registry;
import net.minecraft.world.GameMode;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;
import net.minecraft.world.event.GameEvent;
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
        final var pos = result.getBlockPos();
        final var blockState = world.getBlockState(pos);
        final var mfEntity = IMinefortressEntity.of(entity);
        final var player = mfEntity.getPlayer();

        if(blockState.getBlock() instanceof DoorBlock doorBlock) {
            if(DoorBlock.isWoodenDoor(world, pos)) {
                doorBlock.setOpen(entity, world, blockState, pos, true);
                return ActionResult.SUCCESS;
            }
        }

        if(blockState.getBlock() instanceof TrapdoorBlock doorBlock) {
            if(DoorBlock.isWoodenDoor(world, pos)) {
                final var actionResult = doorBlock.onUse(blockState, world, pos, null, hand, result);
                if(actionResult.isAccepted())
                    return actionResult;
            }
        }

        if(blockState.getBlock() instanceof FenceGateBlock) {
            final var newState = blockState.with(FenceGateBlock.OPEN, true);
            world.setBlockState(pos, newState, 10);
            return ActionResult.SUCCESS;
        }

        if(hand != Hand.MAIN_HAND) {
            return ActionResult.FAIL;
        }

        final var stack = entity.getStackInHand(hand);
        final var item = stack.getItem();
        final var context = new FortressItemUsageContext(entity.world, player, hand, new ItemStack(item), result);
        final var actionResult = item.useOnBlock(context);
        if(actionResult.isAccepted()) {
            if(entity instanceof IFortressColonist colonist && stack.isIn(ctx.baritone().settings().acceptableThrowawayItems.get())) {
                colonist.getScaffoldsControl().addBlock(pos.offset(result.getSide()));
            }
        }
        return actionResult;
    }

    @Override
    public ActionResult processRightClick(LivingEntity entity, World world, Hand hand) {
        final var stackInHand = entity.getStackInHand(hand);
        if(stackInHand.getItem() instanceof BucketItem bucketItem) {
            return use(world, entity, hand, bucketItem).getResult();
        } else {
            return ActionResult.FAIL;
        }
    }

    private TypedActionResult<ItemStack> use(World world, LivingEntity user, Hand hand, BucketItem item) {
        final var mfEntity = IMinefortressEntity.of(user);
        final var bucketFluid = mfEntity.getBucketFluid(item);
        ItemStack itemStack = user.getStackInHand(hand);
        BlockHitResult blockHitResult = raycast(world, user, bucketFluid == Fluids.EMPTY ? RaycastContext.FluidHandling.SOURCE_ONLY : RaycastContext.FluidHandling.NONE);
        if (blockHitResult.getType() == HitResult.Type.MISS) {
            return TypedActionResult.pass(itemStack);
        } else if (blockHitResult.getType() != HitResult.Type.BLOCK) {
            return TypedActionResult.pass(itemStack);
        } else {
            BlockPos blockPos = blockHitResult.getBlockPos();
            Direction direction = blockHitResult.getSide();
            BlockPos blockPos2 = blockPos.offset(direction);
//            if (canPlaceOn(blockPos2, direction, itemStack, world)) {
                BlockState blockState;
                if (bucketFluid == Fluids.EMPTY) {
                    blockState = world.getBlockState(blockPos);
                    if (blockState.getBlock() instanceof FluidDrainable) {
                        FluidDrainable fluidDrainable = (FluidDrainable)blockState.getBlock();
                        ItemStack itemStack2 = fluidDrainable.tryDrainFluid(world, blockPos, blockState);
                        if (!itemStack2.isEmpty()) {
                            fluidDrainable.getBucketFillSound().ifPresent((sound) -> {
                                user.playSound(sound, 1.0F, 1.0F);
                            });
                            world.emitGameEvent(user, GameEvent.FLUID_PICKUP, blockPos);
//                            ItemStack itemStack3 = ItemUsage.exchangeStack(itemStack, user, itemStack2);


                            return TypedActionResult.success(itemStack2, world.isClient());
                        }
                    }

                    return TypedActionResult.fail(itemStack);
                } else {
                    blockState = world.getBlockState(blockPos);
                    BlockPos blockPos3 = blockState.getBlock() instanceof FluidFillable && bucketFluid == Fluids.WATER ? blockPos : blockPos2;
                    if (placeFluid(user, world, blockPos3, blockHitResult, bucketFluid)) {


                        return TypedActionResult.success(new ItemStack(Items.BUCKET), world.isClient());
                    } else {
                        return TypedActionResult.fail(itemStack);
                    }
                }
//            } else {
//                return TypedActionResult.fail(itemStack);
//            }
        }
    }

    public boolean placeFluid(@Nullable LivingEntity player, World world, BlockPos pos, @Nullable BlockHitResult hitResult, Fluid fluid) {
        if (!(fluid instanceof FlowableFluid)) {
            return false;
        } else {
            BlockState blockState = world.getBlockState(pos);
            Block block = blockState.getBlock();
            Material material = blockState.getMaterial();
            boolean bl = blockState.canBucketPlace(fluid);
            boolean bl2 = blockState.isAir() || bl || block instanceof FluidFillable && ((FluidFillable)block).canFillWithFluid(world, pos, blockState, fluid);
            if (!bl2) {
                return hitResult != null && this.placeFluid(player, world, hitResult.getBlockPos().offset(hitResult.getSide()), (BlockHitResult)null, fluid);
            } else if (world.getDimension().isUltrawarm() && fluid.isIn(FluidTags.WATER)) {
                int i = pos.getX();
                int j = pos.getY();
                int k = pos.getZ();

                for(int l = 0; l < 8; ++l) {
                    world.addParticle(ParticleTypes.LARGE_SMOKE, (double)i + Math.random(), (double)j + Math.random(), (double)k + Math.random(), 0.0D, 0.0D, 0.0D);
                }

                return true;
            } else if (block instanceof FluidFillable && fluid == Fluids.WATER) {
                ((FluidFillable)block).tryFillWithFluid(world, pos, blockState, ((FlowableFluid)fluid).getFlowing(7, false));
                return true;
            } else {
                if (!world.isClient && bl && !material.isLiquid()) {
                    world.breakBlock(pos, true);
                }

                final var newBlockState = ((FlowableFluid) fluid).getFlowing(7, false).getBlockState();
                return world.setBlockState(pos, newBlockState, 11);
            }
        }
    }

    public boolean canPlaceOn(BlockPos pos, Direction facing, ItemStack stack, World world) {
        BlockPos blockPos = pos.offset(facing.getOpposite());
        CachedBlockPosition cachedBlockPosition = new CachedBlockPosition(world, blockPos, false);
        return stack.canPlaceOn(world.getRegistryManager().get(Registry.BLOCK_KEY), cachedBlockPosition);
    }

    protected static BlockHitResult raycast(World world, LivingEntity entity, RaycastContext.FluidHandling fluidHandling) {
        float f = entity.getPitch();
        float g = entity.getYaw();
        Vec3d vec3d = entity.getEyePos();
        float h = MathHelper.cos(-g * ((float)Math.PI / 180) - (float)Math.PI);
        float i = MathHelper.sin(-g * ((float)Math.PI / 180) - (float)Math.PI);
        float j = -MathHelper.cos(-f * ((float)Math.PI / 180));
        float k = MathHelper.sin(-f * ((float)Math.PI / 180));
        float l = i * j;
        float m = k;
        float n = h * j;
        double d = 5.0;
        Vec3d vec3d2 = vec3d.add((double)l * 5.0, (double)m * 5.0, (double)n * 5.0);
        return world.raycast(new RaycastContext(vec3d, vec3d2, RaycastContext.ShapeType.OUTLINE, fluidHandling, entity));
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
