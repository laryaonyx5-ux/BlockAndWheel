package com.colorwheel;

/**
 * Les 11 couleurs de la roue, dans l'ordre de la video (sens horaire en partant du haut).
 */
public enum WheelColor {
	BLACK("Noir", 0xFF1A1A1A),
	WHITE("Blanc", 0xFFF5F5F5),
	ORANGE("Orange", 0xFFF28C1C),
	YELLOW("Jaune", 0xFFF2D21F),
	PINK("Rose", 0xFFF07FB5),
	GRAY("Gris", 0xFF8C8C8C),
	PURPLE("Violet", 0xFF9B3FC8),
	BLUE("Bleu", 0xFF2F6FE0),
	BROWN("Marron", 0xFF8B5A2B),
	GREEN("Vert", 0xFF3FA34D),
	RED("Rouge", 0xFFD32F2F);

	public static final int ALL_MASK = (1 << values().length) - 1;

	public final String fr;
	public final int argb;

	WheelColor(String fr, int argb) {
		this.fr = fr;
		this.argb = argb;
	}
}
