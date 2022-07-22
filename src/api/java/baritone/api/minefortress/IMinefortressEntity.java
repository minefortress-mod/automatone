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

package baritone.api.minefortress;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.HungerManager;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventory;
import org.jetbrains.annotations.Nullable;

public interface IMinefortressEntity {

    Inventory getInventory();

    @Nullable PlayerEntity getPlayer();

    HungerManager getHungerManager();

    void selectSlot(int slot);

    int getSelectedSlot();

    static IMinefortressEntity of(LivingEntity livingEntity){
        if(livingEntity instanceof PlayerEntity p)
            return new PlayerMinefortressEntity(p);
        if(livingEntity instanceof IMinefortressEntity ime)
            return ime;

        throw new IllegalArgumentException("Cannot create IMinefortressEntity for " + livingEntity.getClass().getName());
    }

}
