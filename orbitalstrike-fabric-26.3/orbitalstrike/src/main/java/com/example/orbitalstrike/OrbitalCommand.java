package com.example.orbitalstrike;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.Collection;

/**
 * /orbital at <x y z>      - удар в точку
 * /orbital on <игроки>     - удар по текущей позиции игроков (точка фиксируется в момент вызова)
 * /orbital clear           - отменить все активные удары
 */
public class OrbitalCommand {
	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("orbital")
				.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.then(Commands.literal("at")
						.then(Commands.argument("pos", Vec3Argument.vec3())
								.executes(ctx -> {
									Vec3 pos = Vec3Argument.getVec3(ctx, "pos");
									OrbitalStrikeMod.ACTIVE.add(new OrbitalStrike(ctx.getSource().getLevel(), pos));
									ctx.getSource().sendSuccess(() -> Component.literal("Орбитальный удар запущен"), true);
									return 1;
								})))
				.then(Commands.literal("on")
						.then(Commands.argument("targets", EntityArgument.players())
								.executes(ctx -> {
									Collection<ServerPlayer> players = EntityArgument.getPlayers(ctx, "targets");
									for (ServerPlayer p : players) {
										OrbitalStrikeMod.ACTIVE.add(new OrbitalStrike((ServerLevel) p.level(), p.position()));
									}
									int n = players.size();
									ctx.getSource().sendSuccess(() -> Component.literal("Ударов запущено: " + n), true);
									return n;
								})))
				.then(Commands.literal("clear")
						.executes(ctx -> {
							int n = OrbitalStrikeMod.ACTIVE.size();
							OrbitalStrikeMod.ACTIVE.clear();
							ctx.getSource().sendSuccess(() -> Component.literal("Отменено ударов: " + n), true);
							return n;
						})));
	}
}
