package fr.ece.autoloc.service.compte;

import fr.ece.autoloc.domain.Utilisateur;
import fr.ece.autoloc.repository.UtilisateurRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Charge les comptes depuis la table {@code utilisateur}. Deux façons de se connecter, jamais mélangées :
 * <ul>
 *   <li>un nom contenant « @ » est une adresse e-mail : seuls les comptes <b>CLIENT</b> sont recherchés ;</li>
 *   <li>sinon c'est un identifiant : seuls les comptes du <b>personnel</b> (USER, ADMIN) sont recherchés.</li>
 * </ul>
 * Chaque compte n'a ainsi qu'un seul nom de connexion, ce qui garde exact le blocage après 5 échecs. Spring Security compare ensuite le mot de
 * passe reçu à l'empreinte BCrypt avec le {@code PasswordEncoder}. Pour un identifiant inconnu, il
 * calcule quand même un BCrypt factice : la réponse prend le même temps, ce qui empêche de deviner
 * les identifiants existants.
 */
@Service
@RequiredArgsConstructor
public class UtilisateurDetailsService implements UserDetailsService {

    private final UtilisateurRepository utilisateurRepository;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String saisie) {
        String nom = Utilisateur.normaliserIdentifiant(saisie);
        Optional<Utilisateur> compte = nom.contains("@")
                ? utilisateurRepository.findByEmailIgnoreCase(nom).filter(Utilisateur::estClient)
                : utilisateurRepository.findByIdentifiant(nom).filter(u -> !u.estClient());
        Utilisateur utilisateur = compte.orElseThrow(() -> new UsernameNotFoundException("Compte inconnu"));
        return User.withUsername(nom)
                .password(utilisateur.getMotDePasse())
                .roles(utilisateur.getRole().name())
                .disabled(!utilisateur.isActif())
                .build();
    }
}
