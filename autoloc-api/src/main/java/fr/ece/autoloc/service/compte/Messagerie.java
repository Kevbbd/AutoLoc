package fr.ece.autoloc.service.compte;

/** Envoi d'un e-mail texte. Séparé de SMTP pour que les tests puissent lire les e-mails envoyés. */
public interface Messagerie {

    void envoyer(String destinataire, String sujet, String texte);
}
