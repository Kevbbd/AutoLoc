package fr.ece.autoloc.support;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Petit client HTTP de test : vraies requêtes sur l'application démarrée, sans aucun mock. */
public final class ClientHttp {

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final int port;

    public ClientHttp(int port) {
        this.port = port;
    }

    public HttpResponse<String> get(String chemin, String... identifiants) {
        return envoyer(requete(chemin, identifiants).GET());
    }

    public HttpResponse<String> post(String chemin, String json, String... identifiants) {
        return envoyer(requete(chemin, identifiants)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8)));
    }

    /** Extrait la valeur brute d'un champ JSON simple (nombre, booléen ou chaîne sans guillemets échappés). */
    public static String champ(String json, String nom) {
        Matcher m = Pattern.compile("\"" + Pattern.quote(nom) + "\"\\s*:\\s*(\"([^\"]*)\"|[^,}\\]]+)").matcher(json);
        if (!m.find()) {
            throw new AssertionError("Champ « " + nom + " » absent de : " + json);
        }
        return m.group(2) != null ? m.group(2) : m.group(1).trim();
    }

    private HttpRequest.Builder requete(String chemin, String... identifiants) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + chemin))
                .header("Accept", "application/json");
        if (identifiants.length == 2) {
            String valeur = identifiants[0] + ":" + identifiants[1];
            builder.header("Authorization",
                    "Basic " + Base64.getEncoder().encodeToString(valeur.getBytes(StandardCharsets.UTF_8)));
        }
        return builder;
    }

    private HttpResponse<String> envoyer(HttpRequest.Builder builder) {
        try {
            return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
