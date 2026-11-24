package fr.ece.autoloc.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class CodeAccesTest {

    @Test
    void unCodeComporteDixSymbolesSansCaracteresAmbigus() {
        String code = CodeAcces.nouveau();
        assertThat(code).hasSize(CodeAcces.LONGUEUR).matches("[23456789ABCDEFGHJKMNPQRSTUVWXYZ]+")
                .doesNotContain("0", "O", "1", "I", "L");
    }

    @Test
    void lesCodesNeSeRepetentPasEtUtilisentToutLAlphabet() {
        Set<String> codes = new HashSet<>();
        Set<Character> symboles = new HashSet<>();
        for (int i = 0; i < 20_000; i++) {
            String code = CodeAcces.nouveau();
            codes.add(code);
            code.chars().forEach(c -> symboles.add((char) c));
        }
        assertThat(codes).hasSize(20_000);
        assertThat(symboles).hasSize(CodeAcces.ALPHABET.length());
    }

    @Test
    void laSaisieDUnHumainEstNormalisee() {
        assertThat(CodeAcces.normaliser("abcde-fghjk")).contains("ABCDEFGHJK");
        assertThat(CodeAcces.normaliser(" ABCDE FGHJK ")).contains("ABCDEFGHJK");
        assertThat(CodeAcces.lisible("ABCDEFGHJK")).isEqualTo("ABCDE-FGHJK");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "ABCDEFGHJ", "ABCDEFGHJKM", "ABCDEFGHJ0", "ABCDEFGHJI", "ABCDE_FGHJ", "ABCDÉFGHJK"})
    void uneSaisieImpossibleEstRefusee(String saisie) {
        assertThat(CodeAcces.normaliser(saisie)).isEmpty();
    }

    @Test
    void uneSaisieAbsenteEstRefusee() {
        assertThat(CodeAcces.normaliser(null)).isEmpty();
    }
}
