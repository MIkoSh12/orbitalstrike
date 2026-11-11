package com.example.orbitalstrike;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Орбитальный удар по скриншотам из МайнШилда:
 *  1) CHARGE - в небе кольцо из крупных красных точек: сначала 4, потом 6, потом 8
 *  2) в конце зарядки в центре кольца появляется тонкий белый луч
 *  3) FIRE   - из центра кольца бьёт толстый белый луч вниз, наносит урон
 */
public class OrbitalStrike {
	// ---- настройки ----
	public static final int CHARGE_TICKS = 90;    // 20 тиков = 1 сек
	public static final int BEAM_APPEAR_TICKS = 25; // за сколько тиков до выстрела появляется тонкий луч
	public static final int FIRE_TICKS = 50;
	public static final double HEIGHT = 150.0;    // высота кольца над целью (луч бьёт "из неба")
	public static final double RING_RADIUS = 14.0; // радиус кольца красных точек (большой, чтобы было видно с высоты)
	public static final double RADIUS = 3.0;      // радиус поражения луча
	public static final double VIEW_DISTANCE = 300.0; // кому показывать частицы
	public static final float DAMAGE = 4.0f;
	public static final int DAMAGE_INTERVAL = 4;
	public static final int FIRE_SECONDS = 5;

	private static final DustParticleOptions RED_DOT = new DustParticleOptions(0xFF0000, 4.0f);
	private static final DustParticleOptions WHITE = new DustParticleOptions(0xFFFFFF, 3.0f);

	private final ServerLevel level;
	private final Vec3 target;
	private final Vec3 ringPos;
	private int age = 0;

	public OrbitalStrike(ServerLevel level, Vec3 target) {
		this.level = level;
		this.target = target;
		this.ringPos = target.add(0, HEIGHT, 0);
	}

	/** @return true, когда удар закончен */
	public boolean tick() {
		if (age == 0) {
			level.playSound(null, ringPos.x, ringPos.y, ringPos.z,
					SoundEvents.BEACON_ACTIVATE, SoundSource.HOSTILE, 6.0f, 0.6f);
		}

		if (age < CHARGE_TICKS) {
			drawRing(dotsFor(age));
			if (age >= CHARGE_TICKS - BEAM_APPEAR_TICKS) {
				drawThinBeam(age - (CHARGE_TICKS - BEAM_APPEAR_TICKS));
			}
			if (age % 20 == 0) {
				level.playSound(null, target.x, target.y, target.z,
						SoundEvents.BEACON_POWER_SELECT, SoundSource.HOSTILE, 3.0f,
						0.5f + (float) age / CHARGE_TICKS);
			}
		} else if (age < CHARGE_TICKS + FIRE_TICKS) {
			tickFire(age - CHARGE_TICKS);
		} else {
			finish();
			return true;
		}

		age++;
		return false;
	}


	/** Показывает частицы всем игрокам поблизости, даже если частица далеко (нужно для луча из неба). */
	private void emit(ParticleOptions options, double x, double y, double z,
	                  int count, double dx, double dy, double dz) {
		for (ServerPlayer p : level.players()) {
			if (p.distanceToSqr(target.x, target.y, target.z) > VIEW_DISTANCE * VIEW_DISTANCE) continue;
			level.sendParticles(p, options, true, true, x, y, z, count, dx, dy, dz, 0.0);
		}
	}

	/** 4 точки -> 6 -> 8, как на скриншотах. */
	private int dotsFor(int a) {
		double p = (double) a / CHARGE_TICKS;
		if (p < 1.0 / 3.0) return 4;
		if (p < 2.0 / 3.0) return 6;
		return 8;
	}

	private void drawRing(int dots) {
		for (int i = 0; i < dots; i++) {
			double ang = Math.PI * 2 * i / dots;
			emit(RED_DOT,
					ringPos.x + Math.cos(ang) * RING_RADIUS, ringPos.y, ringPos.z + Math.sin(ang) * RING_RADIUS,
					1, 0, 0, 0);
		}
	}

	/** Тонкий белый луч в центре, растёт сверху вниз к земле. */
	private void drawThinBeam(int t) {
		double progress = Math.min(1.0, (double) t / BEAM_APPEAR_TICKS);
		double bottom = ringPos.y - HEIGHT * progress;
		for (double y = ringPos.y; y >= bottom; y -= 1.0) {
			emit(ParticleTypes.END_ROD, target.x, y, target.z, 2, 0.05, 0, 0.05);
		}
	}

	private void tickFire(int fireAge) {
		if (fireAge == 0) {
			level.playSound(null, target.x, target.y, target.z,
					SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.HOSTILE, 8.0f, 0.5f);
		}

		drawRing(8); // красные точки остаются

		// толстый белый луч из центра
		for (double y = ringPos.y; y >= target.y; y -= 1.0) {
			emit(ParticleTypes.END_ROD, target.x, y, target.z, 3, 0.3, 0, 0.3);
			emit(WHITE, target.x, y, target.z, 1, 0.35, 0, 0.35);
		}

		if (fireAge % DAMAGE_INTERVAL == 0) {
			AABB box = new AABB(
					target.x - RADIUS, target.y - 2, target.z - RADIUS,
					target.x + RADIUS, target.y + HEIGHT, target.z + RADIUS);
			for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box)) {
				double dx = e.getX() - target.x;
				double dz = e.getZ() - target.z;
				if (dx * dx + dz * dz > RADIUS * RADIUS) continue;
				e.hurtServer(level, level.damageSources().magic(), DAMAGE);
				e.igniteForSeconds(FIRE_SECONDS);
			}
		}
	}

	private void finish() {
		level.sendParticles(ParticleTypes.CLOUD,
				target.x, target.y + 1, target.z, 40, RADIUS * 0.5, 0.5, RADIUS * 0.5, 0.2);
		level.playSound(null, target.x, target.y, target.z,
				SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.HOSTILE, 10.0f, 0.7f);
	}
}
