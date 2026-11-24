package fr.ece.autoloc.service.compte;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Jetons de réinitialisation : 256 bits d'aléa cryptographique, encodés en Base64 « URL ».
 *
 * <p>Seule leur empreinte SHA-256 est stockée. Un hachage rapide suffit ici, contrairement aux mots
 * de passe (BCrypt) : un mot de passe humain se devine, mais 2^256 jetons possibles ne peuvent pas
 * être essayés. L'empreinte, déterministe, permet en plus de retrouver le jeton par un index.
 */
public final class Jetons {

    private static final SecureRandom ALEA = new SecureRandom();
    private static final int OCTETS = 32;

    private Jetons() {
    }

    /** Nouveau jeton de 43 caractères ({@code A-Z a-z 0-9 - _}). */
    public static String nouveau() {
        byte[] octets = new byte[OCTETS];
        ALEA.nextBytes(octets);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(octets);
    }

    /** SHA-256 du jeton, en hexadécimal minuscule (64 caractères). */
    public static String empreinte(String jeton) {
        try {
            byte[] hache = MessageDigest.getInstance("SHA-256").digest(jeton.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hache);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 est garanti par toute JVM", e);
        }
    }
}
