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
 *  1) CHARGE - кольцо из красных точек (4 -> 6 -> 8), повёрнутое лицом к цели
 *  2) в конце зарядки из центра кольца к цели вытягивается тонкий белый луч
 *  3) FIRE   - толстый белый луч по диагонали, урон по всей линии, взрыв и тряска
 *
 * Настройки берутся из OrbitalConfig (меняются командой /orbital set).
 */
public class OrbitalStrike {
	public static final int BEAM_APPEAR_TICKS = 25;   // сколько тиков тонкий луч вытягивается
	public static final double VIEW_DISTANCE = 300.0; // кому показывать частицы
	public static final int DAMAGE_INTERVAL = 4;
	public static final int FIRE_SECONDS = 5;
	public static final double SHAKE_DISTANCE = 60.0;
	public static final int SHAKE_TICKS = 24;

	private static final DustParticleOptions RED_DOT = new DustParticleOptions(0xFF0000, 4.0f);
	private static final DustParticleOptions WHITE = new DustParticleOptions(0xFFFFFF, 3.0f);

	private final ServerLevel level;
	private final Vec3 target;
	private final Vec3 dir;      // единичный вектор от цели к кольцу
	private final Vec3 ringPos;  // центр кольца
	private final Vec3 axisU;    // оси плоскости кольца
	private final Vec3 axisV;
	private final double beamLength;
	private int delay;           // тиков ожидания перед стартом (для залпа)
	private int age = 0;

	public OrbitalStrike(ServerLevel level, Vec3 target) {
		this(level, target, 0);
	}

	public OrbitalStrike(ServerLevel level, Vec3 target, int delayTicks) {
		this.level = level;
		this.target = target;
		this.delay = delayTicks;
		this.beamLength = OrbitalConfig.beamLength;

		double yaw = ThreadLocalRandom.current().nextDouble() * Math.PI * 2;
		double tilt = Math.toRadians(OrbitalConfig.tiltDegrees);
		this.dir = new Vec3(Math.sin(tilt) * Math.cos(yaw), Math.cos(tilt), Math.sin(tilt) * Math.sin(yaw));
		this.ringPos = target.add(dir.scale(beamLength));

		Vec3 u = dir.cross(new Vec3(0, 1, 0)).normalize();
		this.axisU = u;
		this.axisV = dir.cross(u).normalize();
	}

	private Vec3 beamPoint(double t) {
		return target.add(dir.scale(t));
	}

	/** @return true, когда удар закончен */
	public boolean tick() {
		if (delay > 0) {
			delay--;
			return false;
		}

		int charge = OrbitalConfig.chargeTicks;
		int fire = OrbitalConfig.fireTicks;

		if (age == 0) {
			level.playSound(null, ringPos.x, ringPos.y, ringPos.z,
					SoundEvents.BEACON_ACTIVATE, SoundSource.HOSTILE, 6.0f, 0.6f);
		}

		if (age < charge) {
			drawRing(dotsFor(age, charge));
			int appear = Math.min(BEAM_APPEAR_TICKS, charge);
			if (age >= charge - appear) {
				drawThinBeam(age - (charge - appear), appear);
			}
			if (age % 20 == 0) {
				level.playSound(null, target.x, target.y, target.z,
						SoundEvents.BEACON_POWER_SELECT, SoundSource.HOSTILE, 3.0f,
						0.5f + (float) age / charge);
			}
		} else if (age < charge + fire) {
			tickFire(age - charge);
		} else {
			explode(0.6f);
			shake();
			return true;
		}

		age++;
		return false;
	}

	private void emit(ParticleOptions options, double x, double y, double z,
	                  int count, double dx, double dy, double dz) {
		for (ServerPlayer p : level.players()) {
			if (p.distanceToSqr(target.x, target.y, target.z) > VIEW_DISTANCE * VIEW_DISTANCE) continue;
			level.sendParticles(p, options, true, true, x, y, z, count, dx, dy, dz, 0.0);
		}
	}

	/** 4 точки -> 6 -> 8. */
	private int dotsFor(int a, int charge) {
		double p = (double) a / charge;
		if (p < 1.0 / 3.0) return 4;
		if (p < 2.0 / 3.0) return 6;
		return 8;
	}

	private void drawRing(int dots) {
		double r = OrbitalConfig.ringRadius;
		for (int i = 0; i < dots; i++) {
			double ang = Math.PI * 2 * i / dots;
			Vec3 p = ringPos
					.add(axisU.scale(Math.cos(ang) * r))
					.add(axisV.scale(Math.sin(ang) * r));
			emit(RED_DOT, p.x, p.y, p.z, 1, 0, 0, 0);
		}
	}

	private void drawThinBeam(int t, int appear) {
		double progress = Math.min(1.0, (double) t / appear);
		double stop = beamLength * (1.0 - progress);
		for (double d = beamLength; d >= stop; d -= OrbitalConfig.beamStep) {
			Vec3 p = beamPoint(d);
			emit(ParticleTypes.END_ROD, p.x, p.y, p.z, 1, 0.02, 0.02, 0.02);
		}
	}

	private void tickFire(int fireAge) {
		if (fireAge == 0) {
			explode(1.0f);
		}
		if (fireAge < SHAKE_TICKS && fireAge % 3 == 0) {
			shake();
		}

		drawRing(8);

		for (double d = beamLength; d >= 0; d -= OrbitalConfig.beamStep) {
			Vec3 p = beamPoint(d);
			emit(WHITE, p.x, p.y, p.z, 1, 0.1, 0.1, 0.1);
			emit(ParticleTypes.END_ROD, p.x, p.y, p.z, 1, 0.08, 0.08, 0.08);
		}

		double rad = OrbitalConfig.beamRadius;
		emit(ParticleTypes.FLAME, target.x, target.y + 0.3, target.z, 10, rad * 0.4, 0.2, rad * 0.4);

		if (fireAge % DAMAGE_INTERVAL == 0) {
			damageAlongBeam();
		}
	}

	private void shake() {
		if (!OrbitalConfig.shakeEnabled) return;
		for (ServerPlayer p : level.players()) {
			if (p.distanceToSqr(target.x, target.y, target.z) > SHAKE_DISTANCE * SHAKE_DISTANCE) continue;
			level.broadcastEntityEvent(p, (byte) 2);
		}
	}

	private void damageAlongBeam() {
		double rad = OrbitalConfig.beamRadius;
		AABB box = new AABB(target, ringPos).inflate(rad);
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box)) {
			Vec3 pos = e.position().add(0, e.getBbHeight() * 0.5, 0);
			double t = pos.subtract(target).dot(dir);
			t = Math.max(0.0, Math.min(beamLength, t));
			if (pos.distanceToSqr(beamPoint(t)) > rad * rad) continue;
			e.hurtServer(level, level.damageSources().magic(), OrbitalConfig.damage);
			e.igniteForSeconds(FIRE_SECONDS);
		}
	}

	private void explode(float scale) {
		emit(ParticleTypes.EXPLOSION_EMITTER, target.x, target.y + 1, target.z,
				Math.max(1, Math.round(3 * scale)), 1.5 * scale, 0.5, 1.5 * scale);
		emit(ParticleTypes.FLAME, target.x, target.y + 1, target.z,
				(int) (25 * scale), 1.2 * scale, 0.5, 1.2 * scale);
		emit(ParticleTypes.CLOUD, target.x, target.y + 1, target.z,
				(int) (20 * scale), 1.5 * scale, 0.6, 1.5 * scale);
		level.playSound(null, target.x, target.y, target.z,
				SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.HOSTILE, 10.0f * scale, 0.6f);
		level.playSound(null, target.x, target.y, target.z,
				SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.HOSTILE, 10.0f * scale, 0.5f);

		if (OrbitalConfig.realExplosion) {
			level.explode(null, target.x, target.y, target.z,
					OrbitalConfig.explosionPower * scale, Level.ExplosionInteraction.BLOCK);
		}
	}
}
