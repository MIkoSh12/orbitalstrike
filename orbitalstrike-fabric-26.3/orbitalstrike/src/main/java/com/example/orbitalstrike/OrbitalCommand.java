package com.example.orbitalstrike;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.Collection;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;

/**
 * /orbital at <x y z>                          - удар в точку
 * /orbital on <игроки>                         - удар по игрокам
 * /orbital salvo <x y z> <число> <разброс> [пауза в тиках] - залп
 * /orbital set <параметр> <значение>           - настройки
 * /orbital config                              - показать настройки
 * /orbital clear                               - отменить все удары
 */
public class OrbitalCommand {
	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("orbital")
				.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS));

		root.then(Commands.literal("at")
				.then(Commands.argument("pos", Vec3Argument.vec3())
						.executes(ctx -> {
							Vec3 pos = Vec3Argument.getVec3(ctx, "pos");
							OrbitalStrikeMod.ACTIVE.add(new OrbitalStrike(ctx.getSource().getLevel(), pos));
							ctx.getSource().sendSuccess(() -> Component.literal("Орбитальный удар запущен"), true);
							return 1;
						})));

		root.then(Commands.literal("on")
				.then(Commands.argument("targets", EntityArgument.players())
						.executes(ctx -> {
							Collection<ServerPlayer> players = EntityArgument.getPlayers(ctx, "targets");
							for (ServerPlayer p : players) {
								OrbitalStrikeMod.ACTIVE.add(new OrbitalStrike((ServerLevel) p.level(), p.position()));
							}
							int n = players.size();
							ctx.getSource().sendSuccess(() -> Component.literal("Ударов запущено: " + n), true);
							return n;
						})));

		root.then(Commands.literal("salvo")
				.then(Commands.argument("pos", Vec3Argument.vec3())
						.then(Commands.argument("count", IntegerArgumentType.integer(1, 30))
								.then(Commands.argument("spread", DoubleArgumentType.doubleArg(0.0, 100.0))
										.executes(ctx -> salvo(ctx.getSource(),
												Vec3Argument.getVec3(ctx, "pos"),
												IntegerArgumentType.getInteger(ctx, "count"),
												DoubleArgumentType.getDouble(ctx, "spread"), 8))
										.then(Commands.argument("interval", IntegerArgumentType.integer(0, 200))
												.executes(ctx -> salvo(ctx.getSource(),
														Vec3Argument.getVec3(ctx, "pos"),
														IntegerArgumentType.getInteger(ctx, "count"),
														DoubleArgumentType.getDouble(ctx, "spread"),
														IntegerArgumentType.getInteger(ctx, "interval"))))))));

		root.then(Commands.literal("config")
				.executes(ctx -> {
					ctx.getSource().sendSuccess(() -> Component.literal(OrbitalConfig.describe()), false);
					return 1;
				}));

		root.then(Commands.literal("clear")
				.executes(ctx -> {
					int n = OrbitalStrikeMod.ACTIVE.size();
					OrbitalStrikeMod.ACTIVE.clear();
					ctx.getSource().sendSuccess(() -> Component.literal("Отменено ударов: " + n), true);
					return n;
				}));

		LiteralArgumentBuilder<CommandSourceStack> set = Commands.literal("set");
		set.then(num("tilt", "угол от вертикали, градусы", 0, 80, v -> OrbitalConfig.tiltDegrees = v));
		set.then(num("distance", "расстояние от кольца до цели", 20, 250, v -> OrbitalConfig.beamLength = v));
		set.then(num("ring", "радиус кольца", 1, 50, v -> OrbitalConfig.ringRadius = v));
		set.then(num("radius", "толщина зоны поражения", 0.5, 20, v -> OrbitalConfig.beamRadius = v));
		set.then(num("step", "шаг частиц луча (меньше = плотнее)", 0.25, 2, v -> OrbitalConfig.beamStep = v));
		set.then(num("charge", "зарядка, секунды", 0.5, 30, v -> OrbitalConfig.chargeTicks = (int) Math.round(v * 20)));
		set.then(num("fire", "длительность луча, секунды", 0.5, 30, v -> OrbitalConfig.fireTicks = (int) Math.round(v * 20)));
		set.then(num("damage", "урон за удар", 0, 100, v -> OrbitalConfig.damage = (float) v));
		set.then(num("power", "сила взрыва", 0, 20, v -> OrbitalConfig.explosionPower = (float) v));
		set.then(bool("explosion", "взрыв ломает блоки", v -> OrbitalConfig.realExplosion = v));
		set.then(bool("shake", "тряска камеры", v -> OrbitalConfig.shakeEnabled = v));
		root.then(set);

		dispatcher.register(root);
	}

	private static int salvo(CommandSourceStack src, Vec3 center, int count, double spread, int interval) {
		ServerLevel level = src.getLevel();
		ThreadLocalRandom rnd = ThreadLocalRandom.current();
		for (int i = 0; i < count; i++) {
			double ang = rnd.nextDouble() * Math.PI * 2;
			double r = spread * Math.sqrt(rnd.nextDouble());
			Vec3 pos = center.add(Math.cos(ang) * r, 0, Math.sin(ang) * r);
			OrbitalStrikeMod.ACTIVE.add(new OrbitalStrike(level, pos, i * interval));
		}
		src.sendSuccess(() -> Component.literal("Залп запущен: ударов " + count), true);
		return count;
	}

	private static LiteralArgumentBuilder<CommandSourceStack> num(String name, String what,
			double min, double max, Consumer<Double> setter) {
		return Commands.literal(name)
				.then(Commands.argument("value", DoubleArgumentType.doubleArg(min, max))
						.executes(ctx -> {
							double v = DoubleArgumentType.getDouble(ctx, "value");
							setter.accept(v);
							ctx.getSource().sendSuccess(() -> Component.literal(name + " (" + what + ") = " + v), true);
							return 1;
						}));
	}

	private static LiteralArgumentBuilder<CommandSourceStack> bool(String name, String what,
			Consumer<Boolean> setter) {
		return Commands.literal(name)
				.then(Commands.argument("value", BoolArgumentType.bool())
						.executes(ctx -> {
							boolean v = BoolArgumentType.getBool(ctx, "value");
							setter.accept(v);
							ctx.getSource().sendSuccess(() -> Component.literal(name + " (" + what + ") = " + v), true);
							return 1;
						}));
	}
}
