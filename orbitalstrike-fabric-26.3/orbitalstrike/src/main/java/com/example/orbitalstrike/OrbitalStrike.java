package com.example.orbitalstrike;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Орбитальный удар под углом:
 *  1) CHARGE - в небе кольцо из красных точек (4 -> 6 -> 8), повёрнутое лицом к цели
 *  2) в конце зарядки из центра кольца к цели вытягивается тонкий белый луч
 *  3) FIRE   - толстый белый луч бьёт по диагонали, урон по всей линии луча
 *  4) взрыв  - в точке попадания в начале выстрела и слабее в конце
 */
public class OrbitalStrike {
	// ---- настройки ----
	public static final int CHARGE_TICKS = 90;      // 20 тиков = 1 сек
	public static final int BEAM_APPEAR_TICKS = 25; // за сколько тиков до выстрела вытягивается тонкий луч
	public static final int FIRE_TICKS = 50;

	public static final double BEAM_LENGTH = 100.0; // расстояние от кольца до цели
	public static final double TILT_DEGREES = 30.0; // угол от вертикали (0 = строго сверху)
	public static final double RING_RADIUS = 12.0;  // радиус кольца красных точек
	public static final double RADIUS = 3.0;        // толщина зоны поражения луча
	public static final double VIEW_DISTANCE = 300.0;

	public static final float DAMAGE = 4.0f;
	public static final int DAMAGE_INTERVAL = 4;
	public static final int FIRE_SECONDS = 5;

	// Взрыв: false = только эффект (блоки целы), true = настоящий взрыв с разрушением блоков
	public static final boolean REAL_EXPLOSION = false;
	public static final float EXPLOSION_POWER = 4.0f;

	private static final DustParticleOptions RED_DOT = new DustParticleOptions(0xFF0000, 4.0f);
	private static final DustParticleOptions WHITE = new DustParticleOptions(0xFFFFFF, 3.0f);

	private final ServerLevel level;
	private final Vec3 target;
	private final Vec3 dir;      // единичный вектор от цели к кольцу
	private final Vec3 ringPos;  // центр кольца
	private final Vec3 axisU;    // оси плоскости кольца (перпендикулярны лучу)
	private final Vec3 axisV;
	private int age = 0;

	public OrbitalStrike(ServerLevel level, Vec3 target) {
		this.level = level;
		this.target = target;

		// случайная сторона наклона
		double yaw = ThreadLocalRandom.current().nextDouble() * Math.PI * 2;
		double tilt = Math.toRadians(TILT_DEGREES);
		this.dir = new Vec3(Math.sin(tilt) * Math.cos(yaw), Math.cos(tilt), Math.sin(tilt) * Math.sin(yaw));
		this.ringPos = target.add(dir.scale(BEAM_LENGTH));

		// ось U: горизонтальная, перпендикулярна лучу; ось V: перпендикулярна и лучу, и U
		Vec3 u = dir.cross(new Vec3(0, 1, 0)).normalize();
		this.axisU = u;
		this.axisV = dir.cross(u).normalize();
	}

	/** Точка на луче: t = 0 у цели, t = BEAM_LENGTH у кольца. */
	private Vec3 beamPoint(double t) {
		return target.add(dir.scale(t));
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
			explode(0.6f);
			return true;
		}

		age++;
		return false;
	}

	/** Показывает частицы игрокам поблизости, даже если частица далеко. */
	private void emit(ParticleOptions options, double x, double y, double z,
	                  int count, double dx, double dy, double dz) {
		for (ServerPlayer p : level.players()) {
			if (p.distanceToSqr(target.x, target.y, target.z) > VIEW_DISTANCE * VIEW_DISTANCE) continue;
			level.sendParticles(p, options, true, true, x, y, z, count, dx, dy, dz, 0.0);
		}
	}

	/** 4 точки -> 6 -> 8. */
	private int dotsFor(int a) {
		double p = (double) a / CHARGE_TICKS;
		if (p < 1.0 / 3.0) return 4;
		if (p < 2.0 / 3.0) return 6;
		return 8;
	}

	private void drawRing(int dots) {
		for (int i = 0; i < dots; i++) {
			double ang = Math.PI * 2 * i / dots;
			Vec3 p = ringPos
					.add(axisU.scale(Math.cos(ang) * RING_RADIUS))
					.add(axisV.scale(Math.sin(ang) * RING_RADIUS));
			emit(RED_DOT, p.x, p.y, p.z, 3, 0.25, 0.25, 0.25);
		}
	}

	/** Тонкий белый луч, вытягивается от кольца к цели. */
	private void drawThinBeam(int t) {
		double progress = Math.min(1.0, (double) t / BEAM_APPEAR_TICKS);
		double stop = BEAM_LENGTH * (1.0 - progress);
		for (double d = BEAM_LENGTH; d >= stop; d -= 1.0) {
			Vec3 p = beamPoint(d);
			emit(ParticleTypes.END_ROD, p.x, p.y, p.z, 1, 0.02, 0.02, 0.02);
		}
	}

	private void tickFire(int fireAge) {
		if (fireAge == 0) {
			explode(1.0f);
		}

		drawRing(8);

		// толстый белый луч от кольца до цели
		for (double d = BEAM_LENGTH; d >= 0; d -= 1.0) {
			Vec3 p = beamPoint(d);
			emit(WHITE, p.x, p.y, p.z, 1, 0.1, 0.1, 0.1);
			emit(ParticleTypes.END_ROD, p.x, p.y, p.z, 1, 0.08, 0.08, 0.08);
		}

		// искры в точке попадания
		emit(ParticleTypes.FLAME, target.x, target.y + 0.3, target.z, 10, RADIUS * 0.4, 0.2, RADIUS * 0.4);

		if (fireAge % DAMAGE_INTERVAL == 0) {
			damageAlongBeam();
		}
	}

	/** Урон всем, кто ближе RADIUS к отрезку луча. */
	private void damageAlongBeam() {
		AABB box = new AABB(target, ringPos).inflate(RADIUS);
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box)) {
			Vec3 pos = e.position().add(0, e.getBbHeight() * 0.5, 0);
			double t = pos.subtract(target).dot(dir);          // проекция на луч
			t = Math.max(0.0, Math.min(BEAM_LENGTH, t));
			double distSq = pos.distanceToSqr(beamPoint(t));
			if (distSq > RADIUS * RADIUS) continue;
			e.hurtServer(level, level.damageSources().magic(), DAMAGE);
			e.igniteForSeconds(FIRE_SECONDS);
		}
	}

	/** Взрыв в точке попадания. scale: 1.0 = полный, меньше = слабее. */
	private void explode(float scale) {
		emit(ParticleTypes.EXPLOSION_EMITTER, target.x, target.y + 1, target.z,
				Math.max(1, Math.round(3 * scale)), 1.5 * scale, 0.5, 1.5 * scale);
		emit(ParticleTypes.FLAME, target.x, target.y + 1, target.z,
				(int) (25 * scale), 1.2 * scale, 0.5, 1.2 * scale);
		emit(ParticleTypes.CLOUD, target.x, target.y + 1, target.z,
				(int) (20 * scale), 1.5 * scale, 0.6, 1.5 * scale);
		level.playSound(null, target.x, target.y, target.z,
				SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.HOSTILE, 10.0f * scale, 0.6f);

		if (REAL_EXPLOSION) {
			level.explode(null, target.x, target.y, target.z,
					EXPLOSION_POWER * scale, Level.ExplosionInteraction.BLOCK);
		}
	}
}
