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
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.HashMap;
import java.util.HashSet;
import java.util.UUID;

public class FoxBukkitPermissionHandler {
	private final FoxBukkitPermissions plugin;
	private boolean loaded = false;
	private final Configuration playerGroups;
	private final Configuration rankLevels;
	private final Configuration rankTags;
	private final HashMap<GroupWorld,HashSet<String>> groupPermissions = new HashMap<>();
	private final HashMap<GroupWorld,HashSet<String>> groupProhibitions = new HashMap<>();

	final HashMap<UUID, String> playerGroupsOverride = new HashMap<>();

	public FoxBukkitPermissionHandler(FoxBukkitPermissions plugin) {
		this.plugin = plugin;
		this.playerGroups = new Configuration(plugin.getDataFolder(), "playergroups.txt");
		this.rankLevels = new Configuration(plugin.getDataFolder(), "ranklevels.txt");
        this.rankTags = new Configuration(plugin.getDataFolder(), "ranktags.txt");

		addRankChangeHandler((uuid, newRank) -> playerGroupsOverride.remove(uuid));
	}

	class GroupWorld {
		public final String group;
		public final String world;
		
		public GroupWorld(String group, String world) {
			this.group = group;
			this.world = world;
		}
		
		@Override
		public boolean equals(Object other) {
			return (other instanceof  GroupWorld) && equals((GroupWorld)other);
		}

		public boolean equals(GroupWorld other) {
			return other.group.equals(this.group) && other.world.equals(this.world);
		}
		
		@Override
		public int hashCode() {
			return (group.hashCode() / 2) + (world.hashCode() / 2);
		}
	}
	
	private String defaultWorld = "world";

	public void setDefaultWorld(String world) {
		defaultWorld = world;
	}

	public void load() {
		if(loaded) return;
		reload();
	}

	public void reload() {
		loaded = true;
		groupPermissions.clear();
		groupProhibitions.clear();

		this.playerGroups.load();
		this.rankLevels.load();
		this.rankTags.load();

		final File permissionsDirectory = new File(plugin.getDataFolder() + "/permissions");
		permissionsDirectory.mkdirs();
		File[] files = permissionsDirectory.listFiles();
		if (files == null) {
			return;
		}

		for(File file : files) {
			try {
				String currentWorld;
				GroupWorld currentGroupWorld = null;
				currentWorld = file.getName();
				if(currentWorld.indexOf('.') > 0) {
					currentWorld = currentWorld.substring(0, currentWorld.indexOf('.'));
				}
				HashSet<String> currentPermissions = null;
				HashSet<String> currentProhibitions = null;
				try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
					String line;
					while ((line = reader.readLine()) != null) {
						line = line.trim().toLowerCase();
						if (line.length() < 1) continue;
						char c = line.charAt(0);
						if (c == '-') {
							line = line.substring(1).trim();
							currentPermissions.remove(line);
							currentProhibitions.add(line);
						} else if (c == '+') {
							line = line.substring(1).trim();
							currentPermissions.add(line);
							currentProhibitions.remove(line);
						} else {
							if (currentGroupWorld != null) {
								groupPermissions.put(currentGroupWorld, currentPermissions);
								groupProhibitions.put(currentGroupWorld, currentProhibitions);
							}
							int i = line.indexOf(' ');
							currentPermissions = new HashSet<>();
							currentProhibitions = new HashSet<>();
							if (i > 0) {
								currentGroupWorld = new GroupWorld(line.substring(0, i).trim(), currentWorld);
								GroupWorld tmp = new GroupWorld(line.substring(i + 1).trim(), currentWorld);
								currentPermissions.addAll(groupPermissions.get(tmp));
								currentProhibitions.addAll(groupProhibitions.get(tmp));
							} else {
								currentGroupWorld = new GroupWorld(line, currentWorld);
							}
						}
					}
					if (currentGroupWorld != null) {
						groupPermissions.put(currentGroupWorld, currentPermissions);
						groupProhibitions.put(currentGroupWorld, currentProhibitions);
					}
				}
			}
			catch(Exception e) {
				e.printStackTrace();
			}
		}
	}

	public void save() {

	}

	public boolean has(CommandSender commandSender, String permission) {
		// Console can do everything
		return (!(commandSender instanceof Player)) || has((Player) commandSender, permission);
	}

	public boolean has(Player player, String permission) {
		return has(player.getWorld().getName(), player.getUniqueId(), permission);
	}

	public boolean has(String worldName, UUID uuid, String permission) {
		permission = permission.toLowerCase();

		GroupWorld currentGroupWorld = new GroupWorld(getGroup(uuid), worldName);

		HashSet<String> currentPermissions = groupPermissions.get(currentGroupWorld);
		if(currentPermissions == null) {
			currentGroupWorld = new GroupWorld(currentGroupWorld.group, defaultWorld);
			currentPermissions = groupPermissions.get(currentGroupWorld);
			if(currentPermissions == null) return false;
		}
		if(currentPermissions.contains(permission)) return true;

		HashSet<String> currentProhibitions = groupProhibitions.get(currentGroupWorld);
		if(currentProhibitions != null && currentProhibitions.contains(permission)) return false;

		int xpos = 0;
		String tperm = permission;
		while((xpos = tperm.lastIndexOf('.')) > 0) {
			tperm = tperm.substring(0, xpos);
			String tperm2 = tperm + ".*";
			if(currentProhibitions != null && currentProhibitions.contains(tperm2)) { currentProhibitions.add(permission); return false; }
			if(currentPermissions.contains(tperm2)) { currentPermissions.add(permission); return true; }
		}

		if(currentProhibitions != null && currentProhibitions.contains("*")) { currentProhibitions.add(permission); return false; }
		if(currentPermissions.contains("*")) { currentPermissions.add(permission); return true; }

		if(currentProhibitions == null) {
			currentProhibitions = new HashSet<>();
			groupProhibitions.put(currentGroupWorld, currentProhibitions);
		}
		currentProhibitions.add(permission);
		return false;
	}

	public boolean has(UUID uuid, String permission) {
		return has(defaultWorld, uuid, permission);
	}

	public int getImmunityLevel(UUID uuid) {
		return getImmunityLevel(getGroup(uuid));
	}

	public int getImmunityLevel(Player ply) {
		return getImmunityLevel(getGroup(ply));
	}

	public int getImmunityLevel(String group) {
		return Integer.parseInt(rankLevels.get(group));
	}

	public String getGroup(Player ply) {
		return getGroup(ply.getUniqueId());
	}

	public String getGroup(UUID uuid) {
		String result = playerGroupsOverride.get(uuid);
		if (result != null) {
			return result;
		}
		result = playerGroups.get(uuid.toString());
		if (result == null) {
			return "guest";
		}
		return result;
	}

    public interface OnRankChange {
        void rankChanged(UUID uuid, String newRank);
    }

	public void addRankChangeHandler(final OnRankChange handler) {
        playerGroups.addOnChangeHook((key, value) -> handler.rankChanged(UUID.fromString(key), value));
	}

    public String getGroupTag(String group) {
        return rankTags.get(group);
    }

	public void setGroup(UUID uuid, String group) {
		group = group.toLowerCase();
		playerGroups.put(uuid.toString(), group);
		save();
	}

	public boolean inGroup(String world, UUID uuid, String group) {
		return getGroup(uuid).equalsIgnoreCase(group);
	}

	public boolean inGroup(UUID uuid, String group) {
		return inGroup(defaultWorld, uuid, group);
	}
}