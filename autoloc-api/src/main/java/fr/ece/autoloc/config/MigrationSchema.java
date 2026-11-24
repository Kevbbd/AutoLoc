package fr.ece.autoloc.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Locale;

/**
 * Seule modification de colonne existante que {@code ddl-auto=update} ne sait pas faire.
 *
 * <p>La première version créait {@code utilisateur.role} en {@code ENUM('ADMIN','USER')} (choix par défaut
 * d'Hibernate sous MySQL). Le rôle {@code CLIENT} n'y entre pas, et Hibernate ne modifie jamais une colonne
 * existante. Ce composant la convertit une fois en {@code VARCHAR(20)} : les valeurs sont conservées et
 * la conversion n'est plus refaite ensuite. Il s'exécute après la mise à jour du schéma par Hibernate et
 * avant l'ouverture du serveur web. En production, ce serait un script de migration versionné (Flyway).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MigrationSchema implements SmartInitializingSingleton {

    private final DataSource dataSource;

    @Override
    public void afterSingletonsInstantiated() {
        try (Connection connexion = dataSource.getConnection()) {
            String base = connexion.getMetaData().getDatabaseProductName().toLowerCase(Locale.ROOT);
            if (!base.contains("mysql") && !base.contains("mariadb")) {
                return; // H2 (tests) : schéma recréé à chaque lancement, déjà au bon format
            }
            if ("enum".equalsIgnoreCase(typeColonne(connexion, "utilisateur", "role"))) {
                try (Statement instruction = connexion.createStatement()) {
                    instruction.execute("alter table utilisateur modify column role varchar(20) not null");
                }
                log.info("Migration : utilisateur.role converti de ENUM en VARCHAR(20) (rôle CLIENT)");
            }
            supprimerContrainteRoleObsolete(connexion);
        } catch (SQLException e) {
            throw new IllegalStateException("Migration du schéma impossible : " + e.getMessage(), e);
        }
    }

    /**
     * Autre forme possible d'une ancienne colonne : un VARCHAR avec une contrainte CHECK qui n'autorise que
     * ADMIN et USER. Elle refuserait les comptes clients : on la retire (Bean Validation et l'énumération
     * Java garantissent déjà la valeur).
     */
    private static void supprimerContrainteRoleObsolete(Connection connexion) {
        String requete = """
                select tc.constraint_name, cc.check_clause
                from information_schema.table_constraints tc
                join information_schema.check_constraints cc
                  on cc.constraint_schema = tc.constraint_schema and cc.constraint_name = tc.constraint_name
                where tc.table_schema = database() and tc.table_name = 'utilisateur' and tc.constraint_type = 'CHECK'""";
        try (Statement lecture = connexion.createStatement(); ResultSet contraintes = lecture.executeQuery(requete)) {
            while (contraintes.next()) {
                String nom = contraintes.getString(1);
                String clause = contraintes.getString(2).toUpperCase(Locale.ROOT);
                if (clause.contains("ROLE") && clause.contains("'USER'") && !clause.contains("'CLIENT'")
                        && nom.matches("[A-Za-z0-9_]+")) {
                    try (Statement modification = connexion.createStatement()) {
                        modification.execute("alter table utilisateur drop constraint `" + nom + "`");
                    }
                    log.info("Migration : contrainte « {} » sur utilisateur.role retirée (rôle CLIENT)", nom);
                }
            }
        } catch (SQLException e) {
            // Serveur sans information_schema.check_constraints : aucune contrainte CHECK possible
            log.debug("Contraintes CHECK non lues : {}", e.getMessage());
        }
    }

    private static String typeColonne(Connection connexion, String table, String colonne) throws SQLException {
        try (PreparedStatement requete = connexion.prepareStatement("""
                select data_type from information_schema.columns
                where table_schema = database() and table_name = ? and column_name = ?""")) {
            requete.setString(1, table);
            requete.setString(2, colonne);
            try (ResultSet resultat = requete.executeQuery()) {
                return resultat.next() ? resultat.getString(1) : null;
            }
        }
    }
}
