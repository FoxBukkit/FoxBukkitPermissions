/*
 * foxbukkit-permissions - ${project.description}
 * Copyright © ${year} Doridian (git@doridian.net)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package net.doridian.foxbukkit.permissions;

import net.doridian.foxbukkit.dependencies.config.Configuration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

public class FoxBukkitPermissions extends JavaPlugin implements Listener {
	Configuration configuration;
	FoxBukkitPermissionHandler handler;

	private StringBuilder makeMessageBuilder() {
		return new StringBuilder("\u00a75[FBP] \u00a7f");
	}

	public FoxBukkitPermissionHandler getHandler() {
		return handler;
	}

	@EventHandler(priority = EventPriority.LOWEST)
	public void onPlayerJoin(PlayerJoinEvent event) {
		Utils.patchPlayer(this, event.getPlayer());
	}

	@Override
	public void onDisable() {
		super.onDisable();
	}

	@Override
	public void onEnable() {
		configuration = new Configuration(getDataFolder());

		handler = new FoxBukkitPermissionHandler(this);
		handler.load();

		getServer().getPluginManager().registerEvents(this, this);

		getServer().getPluginCommand("reloadpermissions").setExecutor((commandSender, command, s, strings) -> {
			handler.reload();
			commandSender.sendMessage(makeMessageBuilder().append("Permissions reloaded").toString());
			return true;
		});

		getServer().getPluginCommand("pemu").setExecutor((commandSender, command, s, strings) -> {
			Player ply = (Player)commandSender;

			if (strings.length < 1) {
				handler.playerGroupsOverride.remove(ply.getUniqueId());
				commandSender.sendMessage(makeMessageBuilder().append("PEmu off").toString());
				return true;
			}

			if (!ply.hasPermission("foxbukkit.permissions.emu.set")) {
				commandSender.sendMessage(makeMessageBuilder().append("You can't use PEmu On").toString());
				return true;
			}

			String emuGroup = strings[0];
			if(handler.getImmunityLevel(ply) <= handler.getImmunityLevel(emuGroup)) {
				commandSender.sendMessage(makeMessageBuilder().append("You can't elevate yourself to same or higher immunity").toString());
				return true;
			}

			handler.playerGroupsOverride.put(ply.getUniqueId(), emuGroup);
			commandSender.sendMessage(makeMessageBuilder().append("PEmu on => ").append(emuGroup).toString());
			return true;
		});

		for(Player player : getServer().getOnlinePlayers()) {
			Utils.patchPlayer(this, player);
		}
	}
}
