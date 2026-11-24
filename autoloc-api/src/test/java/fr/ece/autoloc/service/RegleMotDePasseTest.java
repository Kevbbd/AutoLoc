package fr.ece.autoloc.service;

import fr.ece.autoloc.service.compte.RegleMotDePasse;
import fr.ece.autoloc.service.exception.RequeteInvalideException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RegleMotDePasseTest {

    @ParameterizedTest
    @ValueSource(strings = {"Agence2026", "8caracts", "Ça1çaçaça", "longue phrase de passe 42"})
    void accepteUnMotDePasseConforme(String motDePasse) {
        assertThatCode(() -> RegleMotDePasse.verifier(motDePasse, "admin")).doesNotThrowAnyException();
    }

    @ParameterizedTest(name = "« {0} » refusé : {1}")
    @CsvSource(delimiter = '|', value = {
            "court1a     | au moins 8 caractères",
            "abcdefgh    | une lettre et un chiffre",
            "12345678    | une lettre et un chiffre",
            "' motdepasse1' | espace",
            "'motdepasse1 ' | espace",
            "Admin2026   | identifiant",
            "monADMINpass1 | identifiant"
    })
    void refuseAvecUnMessageClair(String motDePasse, String extraitDuMessage) {
        assertThatThrownBy(() -> RegleMotDePasse.verifier(motDePasse, "admin"))
                .isInstanceOf(RequeteInvalideException.class)
                .hasMessageContaining(extraitDuMessage)
                .satisfies(e -> assertThat(((RequeteInvalideException) e).getChamp()).isEqualTo("motDePasse"));
    }

    @Test
    void refuseCeQueBCryptTronquerait() {
        // 64 caractères mais 128 octets en UTF-8 : BCrypt n'en lirait que 72
        String accents = "é".repeat(63) + "1";
        assertThatThrownBy(() -> RegleMotDePasse.verifier(accents, "admin")).hasMessageContaining("trop long");
        assertThatThrownBy(() -> RegleMotDePasse.verifier("a1".repeat(33), "admin")).hasMessageContaining("trop long");
    }

    @Test
    void unNomTresCourtNEmpechePasDeChoisirSonMotDePasse() {
        // Client « jo@exemple.fr » : refuser tout mot de passe contenant « jo » n'aurait aucun intérêt
        assertThatCode(() -> RegleMotDePasse.verifier("bonjour2026", "jo")).doesNotThrowAnyException();
        assertThatThrownBy(() -> RegleMotDePasse.verifier("bonjour2026", "jour")).hasMessageContaining("identifiant");
    }

    @Test
    void refuseUnMotDePasseAbsent() {
        assertThatThrownBy(() -> RegleMotDePasse.verifier(null, "admin")).isInstanceOf(RequeteInvalideException.class);
    }
}
