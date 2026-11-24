package fr.ece.autoloc.securite;

import fr.ece.autoloc.domain.Utilisateur;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AuthenticationFailureBadCredentialsEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.WebAuthenticationDetails;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Protection contre les essais de mots de passe en série (force brute, listes de mots de passe volés).
 *
 * <ul>
 *   <li>Par <b>identifiant</b> : 5 échecs en 15 minutes bloquent cet identifiant 15 minutes, même avec le
 *   bon mot de passe ensuite. Les identifiants <b>inexistants</b> sont comptés de la même façon : le blocage
 *   ne révèle donc pas quels comptes existent.</li>
 *   <li>Par <b>adresse IP</b> : 20 échecs en 15 minutes, tous identifiants confondus, bloquent l'adresse.
 *   Cela arrête qui essaie un mot de passe courant sur beaucoup d'identifiants.</li>
 * </ul>
 * Une connexion réussie remet à zéro le compteur de l'identifiant (pas celui de l'IP : sinon un attaquant
 * possédant un compte pourrait l'effacer entre deux salves).
 *
 * <p>Les échecs sont connus par les événements que Spring Security publie à chaque authentification.
 */
@Slf4j
@Component
public class ProtectionConnexion {

    private final CompteurEchecs parIdentifiant;
    private final CompteurEchecs parIp;

    public ProtectionConnexion(Clock clock,
            @Value("${autoloc.securite.connexion.echecs-max-par-identifiant:5}") int maxParIdentifiant,
            @Value("${autoloc.securite.connexion.echecs-max-par-ip:20}") int maxParIp,
            @Value("${autoloc.securite.connexion.fenetre:15m}") Duration fenetre,
            @Value("${autoloc.securite.connexion.blocage:15m}") Duration blocage) {
        this.parIdentifiant = new CompteurEchecs(maxParIdentifiant, fenetre, blocage, clock);
        this.parIp = new CompteurEchecs(maxParIp, fenetre, blocage, clock);
    }

    /** Temps restant avant de pouvoir réessayer, si l'identifiant ou l'adresse est bloqué. */
    public Optional<Duration> blocage(String identifiant, String ip) {
        return Stream.of(parIdentifiant.blocageRestant(Utilisateur.normaliserIdentifiant(identifiant)),
                        ip == null ? Optional.<Duration>empty() : parIp.blocageRestant(ip))
                .flatMap(Optional::stream)
                .max(Duration::compareTo);
    }

    @EventListener
    public void surEchec(AuthenticationFailureBadCredentialsEvent evenement) {
        Authentication tentative = evenement.getAuthentication();
        String identifiant = Utilisateur.normaliserIdentifiant(tentative.getName());
        String ip = adresse(tentative);
        if (parIdentifiant.echec(identifiant)) {
            log.warn("Connexion bloquée temporairement pour l'identifiant « {} » (trop d'échecs ; dernier essai depuis {})",
                    identifiant, ip);
        }
        if (ip != null && parIp.echec(ip)) {
            log.warn("Connexions bloquées temporairement depuis l'adresse {} (trop d'échecs)", ip);
        }
    }

    @EventListener
    public void surReussite(AuthenticationSuccessEvent evenement) {
        parIdentifiant.reussite(Utilisateur.normaliserIdentifiant(evenement.getAuthentication().getName()));
    }

    private static String adresse(Authentication authentification) {
        return authentification.getDetails() instanceof WebAuthenticationDetails details ? details.getRemoteAddress() : null;
    }
}
