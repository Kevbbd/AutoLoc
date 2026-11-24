package fr.ece.autoloc.domain;

import jakarta.persistence.Column;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.Locale;

/**
 * Compte de connexion : personnel de l'agence ({@code USER}, {@code ADMIN}) ou client ({@code CLIENT}).
 * Un compte client est relié à sa fiche {@link Client} et se connecte avec son adresse e-mail.
 *
 * <p>Le mot de passe n'est jamais stocké en clair : la colonne contient l'empreinte BCrypt
 * (60 caractères, sel compris). Il n'y a volontairement pas de setter : on ne peut le changer
 * que par {@link #changerMotDePasse(String)}, qui exige une empreinte déjà calculée.
 */
@Entity
@Table(name = "utilisateur")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Utilisateur {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idUtilisateur;

    /** Nom de connexion (HTTP Basic), enregistré en minuscules : la casse est ignorée à la connexion. */
    @Column(nullable = false, unique = true, length = 50)
    private String identifiant;

    /** Adresse où envoyer le lien de réinitialisation, enregistrée en minuscules. */
    @Column(nullable = false, unique = true, length = 150)
    private String email;

    @Column(nullable = false, length = 100)
    private String motDePasse;

    /** En VARCHAR et non en ENUM MySQL : ajouter un rôle ne demande pas de modifier la colonne. */
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private RoleUtilisateur role;

    /** Fiche client d'un compte CLIENT ; vide pour le personnel. */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_client", unique = true)
    private Client client;

    @Column(nullable = false)
    private boolean actif;

    public Utilisateur(String identifiant, String email, String empreinteMotDePasse, RoleUtilisateur role) {
        this.identifiant = normaliserIdentifiant(identifiant);
        this.email = email.trim().toLowerCase(Locale.ROOT);
        this.motDePasse = empreinteMotDePasse;
        this.role = role;
        this.actif = true;
    }

    /**
     * Même règle partout (base, connexion, limitation des tentatives) : MariaDB compare les textes sans
     * tenir compte de la casse, H2 non. Normaliser évite un comportement différent selon la base.
     */
    public static String normaliserIdentifiant(String identifiant) {
        return identifiant == null ? "" : identifiant.strip().toLowerCase(Locale.ROOT);
    }

    /**
     * Compte client : l'identifiant technique ({@code client-…}) n'est jamais saisi, on se connecte avec
     * l'adresse e-mail. Il ne contient pas de « @ », ce qui le distingue de tout identifiant de connexion client.
     */
    public static Utilisateur pourClient(Client client, String empreinteMotDePasse) {
        Utilisateur compte = new Utilisateur("client-" + CodeAcces.nouveau(), client.getEmail(), empreinteMotDePasse,
                RoleUtilisateur.CLIENT);
        compte.client = client;
        return compte;
    }

    public boolean estClient() {
        return role == RoleUtilisateur.CLIENT;
    }

    /** Ce que la personne saisit pour se connecter : son e-mail (client) ou son identifiant (agence). */
    public String nomDeConnexion() {
        return estClient() ? email : identifiant;
    }

    public void changerMotDePasse(String nouvelleEmpreinte) {
        this.motDePasse = nouvelleEmpreinte;
    }
}
