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

import net.minecraft.entity.player.HungerManager;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventory;
import org.jetbrains.annotations.Nullable;

class PlayerMinefortressEntity implements IMinefortressEntity{

    private final PlayerEntity player;

    PlayerMinefortressEntity(PlayerEntity player){
        this.player = player;
    }

    @Override
    public Inventory getInventory() {
        return player.getInventory();
    }

    @Override
    public @Nullable PlayerEntity getPlayer() {
        return player;
    }

    @Override
    public HungerManager getHungerManager() {
        return player.getHungerManager();
    }

    @Override
    public void selectSlot(int slot) {
        player.getInventory().selectedSlot = slot;
    }

    @Override
    public int getSelectedSlot() {
        return player.getInventory().selectedSlot;
    }
}
