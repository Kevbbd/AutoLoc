package fr.ece.autoloc.service.compte;

import fr.ece.autoloc.domain.Client;
import fr.ece.autoloc.domain.JetonInscription;
import fr.ece.autoloc.domain.RegleMetierException;
import fr.ece.autoloc.domain.Utilisateur;
import fr.ece.autoloc.repository.ClientRepository;
import fr.ece.autoloc.repository.JetonInscriptionRepository;
import fr.ece.autoloc.repository.UtilisateurRepository;
import fr.ece.autoloc.securite.LimiteurDebit;
import fr.ece.autoloc.service.exception.RequeteInvalideException;
import fr.ece.autoloc.service.exception.RessourceIntrouvableException;
import fr.ece.autoloc.web.dto.ClientProfilDto;
import fr.ece.autoloc.web.dto.FinalisationInscriptionRequest;
import fr.ece.autoloc.web.dto.InscriptionDto;
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
import java.util.Optional;

/**
 * Comptes clients, en deux temps :
 * <ol>
 *   <li>{@link #demanderInscription} : la personne donne seulement son adresse. Rien n'est créé hormis un lien
 *   de confirmation, envoyé à cette adresse. La réponse est la même dans tous les cas.</li>
 *   <li>{@link #finaliser} : depuis ce lien, elle complète son identité et choisit son mot de passe. Le compte
 *   n'existe qu'à ce moment, quand on sait qu'elle reçoit bien les e-mails de l'adresse.</li>
 * </ol>
 * Choisir le mot de passe <b>après</b> la confirmation est essentiel. Sinon, quelqu'un pourrait s'inscrire avec
 * l'adresse d'un client, fixer lui-même le mot de passe, et il suffirait que la victime clique sur le lien reçu
 * pour qu'il accède à ses réservations.
 */
@Slf4j
@Service
@Transactional
public class CompteClientService {

    /** Au-delà, les demandes de l'heure pour une même adresse sont ignorées (pas de bombardement de mails). */
    public static final int DEMANDES_MAX_PAR_HEURE = 3;

    private static final String LIEN_INVALIDE =
            "Ce lien n'est plus valide : il a déjà servi ou il a expiré. Recommencez l'inscription depuis le site.";

    private final UtilisateurRepository utilisateurRepository;
    private final ClientRepository clientRepository;
    private final JetonInscriptionRepository jetonRepository;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher evenements;
    private final Clock clock;
    private final String urlPublique;
    private final Duration validite;
    private final LimiteurDebit demandesParAdresse;

    public CompteClientService(UtilisateurRepository utilisateurRepository, ClientRepository clientRepository,
            JetonInscriptionRepository jetonRepository, PasswordEncoder passwordEncoder,
            ApplicationEventPublisher evenements, Clock clock,
            @Value("${autoloc.url-publique}") String urlPublique,
            @Value("${autoloc.inscription.validite-lien:24h}") Duration validite) {
        this.utilisateurRepository = utilisateurRepository;
        this.clientRepository = clientRepository;
        this.jetonRepository = jetonRepository;
        this.passwordEncoder = passwordEncoder;
        this.evenements = evenements;
        this.clock = clock;
        this.urlPublique = urlPublique.endsWith("/") ? urlPublique.substring(0, urlPublique.length() - 1) : urlPublique;
        this.validite = validite;
        this.demandesParAdresse = new LimiteurDebit(DEMANDES_MAX_PAR_HEURE, Duration.ofHours(1), clock);
    }

    public Duration validite() {
        return validite;
    }

    public void demanderInscription(String emailSaisi) {
        String email = emailSaisi.trim().toLowerCase(Locale.ROOT);
        if (demandesParAdresse.enregistrer(email).isPresent()) {
            log.warn("Demande d'inscription ignorée : plus de {} demandes dans l'heure pour la même adresse", DEMANDES_MAX_PAR_HEURE);
            return;
        }
        Optional<Utilisateur> existant = utilisateurRepository.findByEmailIgnoreCase(email);
        if (existant.isPresent()) {
            // Compte client : son titulaire est prévenu. Adresse du personnel : rien n'est envoyé, et elle
            // ne pourra jamais devenir un compte client (une adresse = un seul compte).
            if (existant.get().estClient()) {
                evenements.publishEvent(new CompteDejaExistant(email));
            }
            return;
        }
        LocalDateTime maintenant = LocalDateTime.now(clock);
        jetonRepository.expirerTous(email, maintenant);
        String jeton = Jetons.nouveau();
        jetonRepository.save(new JetonInscription(email, Jetons.empreinte(jeton), maintenant, maintenant.plus(validite)));
        // Jeton après « # » : jamais transmis au serveur dans l'URL (journaux, en-tête Referer).
        evenements.publishEvent(new LienInscriptionCree(email, urlPublique + "/inscription.html#jeton=" + jeton, validite));
    }

    @Transactional(readOnly = true)
    public InscriptionDto verifier(String jeton) {
        JetonInscription lien = lienUtilisable(jeton, LocalDateTime.now(clock));
        Optional<Client> client = clientRepository.findByEmailIgnoreCase(lien.getEmail());
        return new InscriptionDto(lien.getEmail(), client.isPresent(),
                client.map(Client::getPrenom).orElse(null), client.map(Client::getNom).orElse(null));
    }

    public void finaliser(FinalisationInscriptionRequest demande) {
        LocalDateTime maintenant = LocalDateTime.now(clock);
        JetonInscription lien = lienUtilisable(demande.jeton(), maintenant);
        String email = lien.getEmail();
        if (utilisateurRepository.findByEmailIgnoreCase(email).isPresent()) {
            throw new RequeteInvalideException("jeton", LIEN_INVALIDE);
        }
        int arobase = email.indexOf('@');
        RegleMotDePasse.verifier(demande.motDePasse(), arobase > 0 ? email.substring(0, arobase) : email);
        // Calcul BCrypt (~0,1 s) fait avant de prendre le verrou sur le lien, pour le tenir le moins longtemps possible.
        String empreinte = passwordEncoder.encode(demande.motDePasse());

        // Le lien est consommé en premier, par une mise à jour conditionnelle : si deux finalisations arrivent
        // en même temps, la seconde attend le verrou de la ligne puis trouve le lien utilisé. Tout est dans la
        // même transaction : si une étape suivante échoue (champ manquant, permis déjà pris), le lien redevient
        // utilisable.
        if (jetonRepository.consommer(lien.getIdJeton(), maintenant) == 0) {
            throw new RequeteInvalideException("jeton", LIEN_INVALIDE);
        }

        // Réservations faites sans compte avec cette adresse : la fiche existe, elle est rattachée au compte.
        Client client = clientRepository.findByEmailIgnoreCase(email)
                .orElseGet(() -> nouveauClient(email, demande, maintenant));
        utilisateurRepository.save(Utilisateur.pourClient(client, empreinte));
        log.info("Compte client créé pour la fiche client n°{}", client.getIdClient());
    }

    @Transactional(readOnly = true)
    public ClientProfilDto profil(String email) {
        Client client = clientRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new RessourceIntrouvableException("Fiche client introuvable."));
        return new ClientProfilDto(client.getPrenom(), client.getNom(), client.getEmail(), client.getTelephone(),
                client.getNumPermis());
    }

    private Client nouveauClient(String email, FinalisationInscriptionRequest demande, LocalDateTime maintenant) {
        String prenom = obligatoire(demande.prenom(), "prenom", "Le prénom est obligatoire.");
        String nom = obligatoire(demande.nom(), "nom", "Le nom est obligatoire.");
        String numPermis = obligatoire(demande.numPermis(), "numPermis", "Le numéro de permis est obligatoire.")
                .toUpperCase(Locale.ROOT);
        if (clientRepository.existsByNumPermisIgnoreCase(numPermis)) {
            throw new RegleMetierException("Ce numéro de permis est déjà associé à une autre adresse e-mail.");
        }
        Client client = new Client();
        client.setPrenom(prenom);
        client.setNom(nom);
        client.setEmail(email);
        client.setTelephone(demande.telephone() == null || demande.telephone().isBlank() ? null : demande.telephone().trim());
        client.setNumPermis(numPermis);
        client.setDateInscription(maintenant.toLocalDate());
        return clientRepository.save(client);
    }

    private static String obligatoire(String valeur, String champ, String message) {
        if (valeur == null || valeur.isBlank()) {
            throw new RequeteInvalideException(champ, message);
        }
        return valeur.trim();
    }

    private JetonInscription lienUtilisable(String jeton, LocalDateTime maintenant) {
        return jetonRepository.findByEmpreinte(Jetons.empreinte(jeton.trim()))
                .filter(j -> j.estUtilisable(maintenant))
                .orElseThrow(() -> new RequeteInvalideException("jeton", LIEN_INVALIDE));
    }
}
