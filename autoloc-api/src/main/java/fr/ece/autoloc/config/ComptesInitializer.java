package fr.ece.autoloc.config;

import fr.ece.autoloc.domain.RoleUtilisateur;
import fr.ece.autoloc.domain.Utilisateur;
import fr.ece.autoloc.repository.UtilisateurRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Crée les deux comptes du TP au premier démarrage : {@code student / student123} (USER) et
 * {@code admin / admin123} (ADMIN). Un compte qui existe déjà n'est jamais modifié : un mot de passe
 * changé par « mot de passe oublié » survit donc aux redémarrages.
 *
 * <p>Ces mots de passe sont ceux de l'énoncé, réservés au développement. En production, le premier
 * administrateur serait créé par une procédure qui ne laisse aucun mot de passe dans le code.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ComptesInitializer implements ApplicationRunner {

    private final UtilisateurRepository utilisateurRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        creerSiAbsent("student", "student@autoloc.test", "student123", RoleUtilisateur.USER);
        creerSiAbsent("admin", "admin@autoloc.test", "admin123", RoleUtilisateur.ADMIN);
    }

    private void creerSiAbsent(String identifiant, String email, String motDePasse, RoleUtilisateur role) {
        if (!utilisateurRepository.existsByIdentifiant(identifiant)) {
            utilisateurRepository.save(new Utilisateur(identifiant, email, passwordEncoder.encode(motDePasse), role));
            log.info("Compte « {} » ({}) créé avec le mot de passe du TP", identifiant, role);
        }
    }
}
