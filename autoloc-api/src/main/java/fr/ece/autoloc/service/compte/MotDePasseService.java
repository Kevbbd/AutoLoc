package fr.ece.autoloc.service.compte;

import fr.ece.autoloc.domain.JetonReinitialisation;
import fr.ece.autoloc.domain.Utilisateur;
import fr.ece.autoloc.repository.JetonReinitialisationRepository;
import fr.ece.autoloc.repository.UtilisateurRepository;
import fr.ece.autoloc.service.exception.RequeteInvalideException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Locale;

/**
 * Mot de passe oublié, en deux temps :
 * <ol>
 *   <li>{@link #demanderReinitialisation} : si l'adresse correspond à un compte actif, crée un jeton
 *   et envoie un lien par e-mail. La réponse est identique dans tous les cas (anti-énumération).</li>
 *   <li>{@link #reinitialiser} : le jeton est consommé une seule fois, avant expiration, puis le
 *   nouveau mot de passe est enregistré (empreinte BCrypt) et les autres liens du compte expirent.</li>
 * </ol>
 */
@Slf4j
@Service
@Transactional
public class MotDePasseService {

    /** Au-delà, les demandes de l'heure sont ignorées : évite de bombarder une boîte mail. */
    public static final int DEMANDES_MAX_PAR_HEURE = 3;

    private static final String LIEN_INVALIDE =
            "Ce lien n'est plus valide : il a déjà servi ou il a expiré. Faites une nouvelle demande depuis l'espace agence.";

    private final UtilisateurRepository utilisateurRepository;
    private final JetonReinitialisationRepository jetonRepository;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher evenements;
    private final Clock clock;
    private final String urlPublique;
    private final Duration validite;

    public MotDePasseService(UtilisateurRepository utilisateurRepository, JetonReinitialisationRepository jetonRepository,
            PasswordEncoder passwordEncoder, ApplicationEventPublisher evenements, Clock clock,
            @Value("${autoloc.url-publique}") String urlPublique,
            @Value("${autoloc.mot-de-passe.validite-lien:30m}") Duration validite) {
        this.utilisateurRepository = utilisateurRepository;
        this.jetonRepository = jetonRepository;
        this.passwordEncoder = passwordEncoder;
        this.evenements = evenements;
        this.clock = clock;
        // L'adresse du lien vient de la configuration, jamais de l'en-tête Host de la requête :
        // sinon un attaquant pourrait faire envoyer à la victime un lien vers son propre site.
        this.urlPublique = urlPublique.endsWith("/") ? urlPublique.substring(0, urlPublique.length() - 1) : urlPublique;
        this.validite = validite;
    }

    public Duration validite() {
        return validite;
    }

    public void demanderReinitialisation(String email) {
        LocalDateTime maintenant = LocalDateTime.now(clock);
        utilisateurRepository.findByEmailIgnoreCase(email.trim().toLowerCase(Locale.ROOT))
                .filter(Utilisateur::isActif)
                .ifPresent(utilisateur -> creerLien(utilisateur, maintenant));
    }

    private void creerLien(Utilisateur utilisateur, LocalDateTime maintenant) {
        long recentes = jetonRepository.countByUtilisateurAndCreeLeAfter(utilisateur, maintenant.minusHours(1));
        if (recentes >= DEMANDES_MAX_PAR_HEURE) {
            log.warn("Demande de réinitialisation ignorée pour « {} » : {} demandes dans l'heure",
                    utilisateur.nomDeConnexion(), recentes);
            return;
        }
        // Seul le dernier lien envoyé fonctionne.
        jetonRepository.expirerTous(utilisateur, maintenant);
        String jeton = Jetons.nouveau();
        jetonRepository.save(new JetonReinitialisation(utilisateur, Jetons.empreinte(jeton), maintenant, maintenant.plus(validite)));
        // Le jeton est placé après « # » : le navigateur n'envoie jamais cette partie de l'adresse au
        // serveur, elle n'apparaît donc ni dans les journaux HTTP ni dans l'en-tête Referer.
        String lien = urlPublique + "/reinitialisation.html#jeton=" + jeton;
        evenements.publishEvent(new LienReinitialisationCree(utilisateur.getEmail(), utilisateur.nomDeConnexion(),
                utilisateur.estClient(), lien, validite));
    }

    /** Permet à la page d'annoncer un lien expiré avant que l'utilisateur ne saisisse un mot de passe. */
    @Transactional(readOnly = true)
    public void verifierJeton(String jeton) {
        jetonUtilisable(jeton, LocalDateTime.now(clock));
    }

    /** @return l'espace du compte réinitialisé, pour que la page propose le bon lien de connexion */
    public Espace reinitialiser(String jeton, String nouveauMotDePasse) {
        LocalDateTime maintenant = LocalDateTime.now(clock);
        JetonReinitialisation trouve = jetonUtilisable(jeton, maintenant);
        Utilisateur utilisateur = trouve.getUtilisateur();
        // Règles vérifiées AVANT de consommer le jeton : un mot de passe refusé laisse le lien utilisable.
        RegleMotDePasse.verifier(nouveauMotDePasse, nomPourRegles(utilisateur));
        if (jetonRepository.consommer(trouve.getIdJeton(), maintenant) == 0) {
            // Un autre envoi du même lien vient de le consommer.
            throw new RequeteInvalideException("jeton", LIEN_INVALIDE);
        }
        utilisateur.changerMotDePasse(passwordEncoder.encode(nouveauMotDePasse));
        jetonRepository.expirerTous(utilisateur, maintenant);
        // Le titulaire est prévenu : si ce n'est pas lui, il le sait aussitôt.
        evenements.publishEvent(new MotDePasseModifie(utilisateur.getEmail(), utilisateur.nomDeConnexion(),
                utilisateur.estClient(), maintenant));
        log.info("Mot de passe réinitialisé pour le compte « {} »", utilisateur.nomDeConnexion());
        return utilisateur.estClient() ? Espace.CLIENT : Espace.AGENCE;
    }

    /** Espace où se connecter après la réinitialisation. */
    public enum Espace { AGENCE, CLIENT }

    /** Ce que le mot de passe ne doit pas contenir : l'identifiant, ou la partie avant « @ » de l'e-mail. */
    static String nomPourRegles(Utilisateur utilisateur) {
        String nom = utilisateur.nomDeConnexion();
        int arobase = nom.indexOf('@');
        return arobase > 0 ? nom.substring(0, arobase) : nom;
    }

    private JetonReinitialisation jetonUtilisable(String jeton, LocalDateTime maintenant) {
        return jetonRepository.findByEmpreinte(Jetons.empreinte(jeton.trim()))
                .filter(j -> j.estUtilisable(maintenant))
                .filter(j -> j.getUtilisateur().isActif())
                .orElseThrow(() -> new RequeteInvalideException("jeton", LIEN_INVALIDE));
    }
}
