package fr.ece.autoloc.service;

import fr.ece.autoloc.service.compte.Jetons;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class JetonsTest {

    @Test
    void unJetonPorte256BitsEnBase64Url() {
        String jeton = Jetons.nouveau();
        // 32 octets en Base64 sans remplissage = 43 caractères, sans « + / = » qui poseraient problème dans une URL
        assertThat(jeton).hasSize(43).matches("[A-Za-z0-9_-]+");
    }

    @Test
    void lesJetonsNeSeRepetentPas() {
        Set<String> jetons = new HashSet<>();
        for (int i = 0; i < 10_000; i++) {
            jetons.add(Jetons.nouveau());
        }
        assertThat(jetons).hasSize(10_000);
    }

    @Test
    void lEmpreinteEstLeSha256Hexadecimal() {
        // Valeur de référence : SHA-256("abc") du NIST (FIPS 180-2, annexe B.1)
        assertThat(Jetons.empreinte("abc"))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
        String jeton = Jetons.nouveau();
        assertThat(Jetons.empreinte(jeton)).hasSize(64).isEqualTo(Jetons.empreinte(jeton)).doesNotContain(jeton);
    }
}
