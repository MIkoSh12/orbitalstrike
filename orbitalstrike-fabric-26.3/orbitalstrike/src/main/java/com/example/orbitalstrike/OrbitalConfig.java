package com.example.orbitalstrike;

/** Настройки удара. Меняются командой /orbital set ... прямо в игре (до перезапуска сервера). */
public class OrbitalConfig {
	public static double tiltDegrees = 30.0;   // угол от вертикали
	public static double beamLength = 100.0;   // расстояние от кольца до цели
	public static double ringRadius = 10.0;    // радиус кольца красных точек
	public static double beamRadius = 3.0;     // толщина зоны поражения луча
	public static double beamStep = 0.5;       // шаг частиц на луче (меньше = плотнее, но тяжелее)
	public static int chargeTicks = 90;        // зарядка, 20 тиков = 1 сек
	public static int fireTicks = 50;          // длительность луча
	public static float damage = 4.0f;         // урон за удар (2 сердца)
	public static boolean realExplosion = true; // взрыв ломает блоки
	public static float explosionPower = 4.0f; // сила взрыва (TNT = 4)
	public static boolean shakeEnabled = true; // тряска камеры

	public static String describe() {
		return "tilt=" + tiltDegrees + " distance=" + beamLength + " ring=" + ringRadius
				+ " radius=" + beamRadius + " step=" + beamStep
				+ " charge=" + chargeTicks / 20.0 + "с fire=" + fireTicks / 20.0 + "с"
				+ " damage=" + damage + " explosion=" + (realExplosion ? "on" : "off")
				+ " power=" + explosionPower + " shake=" + (shakeEnabled ? "on" : "off");
	}
}
