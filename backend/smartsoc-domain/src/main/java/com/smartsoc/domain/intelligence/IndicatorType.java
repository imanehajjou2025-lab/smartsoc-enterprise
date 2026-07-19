package com.smartsoc.domain.intelligence;

import com.smartsoc.domain.common.BusinessRuleViolationException;
import com.smartsoc.domain.common.TextNormalization;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Nature d'un indicateur de compromission — et, indissociablement, la
 * façon de NORMALISER sa valeur.
 *
 * La normalisation vit ici parce qu'elle dépend du type : un hash se
 * met en minuscules sans réserve, un chemin d'URL non (il est sensible
 * à la casse — {@code /Login} et {@code /login} sont deux ressources).
 * Une règle unique pour tous les types produirait soit des ratés
 * silencieux, soit de faux rapprochements.
 *
 * Le type fait partie de l'IDENTITÉ de l'indicateur : la corrélation
 * compare toujours le couple (type, valeur), jamais la valeur seule.
 *
 * <p><b>Défangage.</b> Les analystes et les exports MISP écrivent les
 * IOC sous forme « défangée » ({@code 1.2.3[.]4}, {@code hxxp://},
 * {@code contact[at]evil[.]com}) pour qu'ils ne soient ni cliquables ni
 * attrapés par un antivirus. Stocké tel quel, un IOC défangé ne
 * correspond à AUCUN observable réel : il n'alerte jamais, sans que rien
 * ne le signale. Le refangage est donc la première étape obligatoire.
 */
public enum IndicatorType {

    IPV4,
    IPV6,
    DOMAIN,
    URL,
    MD5,
    SHA1,
    SHA256,
    EMAIL;

    private static final String INVALID = "INVALID_INDICATOR";

    // --- Formes défangées, insensibles à la casse et aux espaces internes ---
    private static final Pattern DEFANGED_DOT = Pattern.compile(
            "\\[\\s*(?:\\.|dot)\\s*]|\\(\\s*(?:\\.|dot)\\s*\\)", Pattern.CASE_INSENSITIVE);
    private static final Pattern DEFANGED_AT = Pattern.compile(
            "\\[\\s*(?:@|at)\\s*]|\\(\\s*(?:@|at)\\s*\\)", Pattern.CASE_INSENSITIVE);
    private static final Pattern DEFANGED_COLON = Pattern.compile("\\[\\s*:\\s*]");
    private static final Pattern DEFANGED_SCHEME = Pattern.compile("^hxxp", Pattern.CASE_INSENSITIVE);

    // --- Formats acceptés (validation stricte : un IOC douteux n'entre pas) ---
    private static final Pattern IPV4_FORMAT = Pattern.compile(
            "^((25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)\\.){3}(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)$");
    /** Littéral IPv6 uniquement : garantit qu'InetAddress ne fera JAMAIS de résolution DNS. */
    private static final Pattern IPV6_LITERAL = Pattern.compile(
            "^[0-9a-f:]*:[0-9a-f:]*(?:\\.\\d{1,3}){0,3}$", Pattern.CASE_INSENSITIVE);
    private static final Pattern DOMAIN_FORMAT = Pattern.compile(
            "^(?!-)[a-z0-9-]{1,63}(?<!-)(?:\\.(?!-)[a-z0-9-]{1,63}(?<!-))+$");
    private static final Pattern EMAIL_FORMAT = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final Pattern HEX = Pattern.compile("^[0-9a-f]+$");

    /**
     * Normalise une valeur brute selon ce type, ou rejette ce qui n'en est
     * pas un. Le résultat est la clé de corrélation : c'est cette forme,
     * et elle seule, qui est stockée et comparée.
     */
    public String normalize(String rawValue) {
        String refanged = refang(require(rawValue));
        return switch (this) {
            case IPV4 -> normalizeIpv4(refanged);
            case IPV6 -> normalizeIpv6(refanged);
            case DOMAIN -> normalizeDomain(refanged);
            case URL -> normalizeUrl(refanged);
            case MD5 -> normalizeHash(refanged, 32);
            case SHA1 -> normalizeHash(refanged, 40);
            case SHA256 -> normalizeHash(refanged, 64);
            case EMAIL -> normalizeEmail(refanged);
        };
    }

    /** Rend sa forme active à un IOC écrit en notation défangée. */
    private static String refang(String value) {
        String refanged = DEFANGED_DOT.matcher(value).replaceAll(".");
        refanged = DEFANGED_AT.matcher(refanged).replaceAll("@");
        refanged = DEFANGED_COLON.matcher(refanged).replaceAll(":");
        // « hxxps » → « http » + « s » : une seule règle couvre les deux schémas.
        return DEFANGED_SCHEME.matcher(refanged).replaceFirst("http");
    }

    private String normalizeIpv4(String value) {
        String normalized = value.trim();
        if (!IPV4_FORMAT.matcher(normalized).matches()) {
            throw invalid(value, "a dotted-quad IPv4 address");
        }
        return normalized;
    }

    /**
     * Canonicalisation IPv6 : {@code 2001:DB8::1} et
     * {@code 2001:0db8:0000:0000:0000:0000:0000:0001} sont la MÊME adresse.
     * Sans forme canonique, le même IOC écrit de deux façons ne se corrèle
     * pas — encore un raté silencieux. La forme retenue est celle
     * d'InetAddress (développée), appliquée des deux côtés de la
     * corrélation. L'entrée est validée comme littéral AVANT l'appel :
     * aucune résolution DNS n'est donc possible depuis le domaine.
     */
    private String normalizeIpv6(String value) {
        String candidate = TextNormalization.lowerTrim(value);
        if (!IPV6_LITERAL.matcher(candidate).matches()) {
            throw invalid(value, "an IPv6 literal");
        }
        try {
            return InetAddress.getByName(candidate).getHostAddress().toLowerCase(Locale.ROOT);
        } catch (UnknownHostException e) {
            throw invalid(value, "an IPv6 literal");
        }
    }

    private String normalizeDomain(String value) {
        // Le point final d'un FQDN absolu (« evil.com. ») ne change pas le nom.
        String normalized = TextNormalization.lowerTrim(value);
        while (normalized.endsWith(".")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        if (!DOMAIN_FORMAT.matcher(normalized).matches() || IPV4_FORMAT.matcher(normalized).matches()) {
            throw invalid(value, "a domain name");
        }
        return normalized;
    }

    /**
     * Seuls le schéma et l'autorité (hôte, port) se mettent en minuscules :
     * ils sont insensibles à la casse par la RFC 3986. Le chemin, la
     * requête et le fragment la conservent — les mettre en minuscules
     * fabriquerait de faux rapprochements entre ressources distinctes.
     */
    private String normalizeUrl(String value) {
        String normalized = value.trim();
        int schemeEnd = normalized.indexOf("://");
        if (schemeEnd <= 0) {
            throw invalid(value, "an absolute URL (scheme://host/…)");
        }
        String scheme = normalized.substring(0, schemeEnd).toLowerCase(Locale.ROOT);
        String rest = normalized.substring(schemeEnd + 3);
        int tailStart = rest.length();
        for (int i = 0; i < rest.length(); i++) {
            char c = rest.charAt(i);
            if (c == '/' || c == '?' || c == '#') {
                tailStart = i;
                break;
            }
        }
        String authority = rest.substring(0, tailStart).toLowerCase(Locale.ROOT);
        if (authority.isEmpty()) {
            throw invalid(value, "an absolute URL (scheme://host/…)");
        }
        return scheme + "://" + authority + rest.substring(tailStart);
    }

    /**
     * Un hash est de l'hexadécimal : la casse ne porte aucune information,
     * et les exports MISP le sortent parfois en majuscules. La longueur
     * exacte distingue les algorithmes — un « SHA256 » de 40 caractères
     * est une erreur de saisie, pas un IOC.
     */
    private String normalizeHash(String value, int length) {
        String normalized = TextNormalization.lowerTrim(value);
        if (normalized.length() != length || !HEX.matcher(normalized).matches()) {
            throw invalid(value, "a %d-character hexadecimal %s hash".formatted(length, name()));
        }
        return normalized;
    }

    /**
     * Casse ignorée sur l'adresse entière. Simplification assumée : la RFC
     * 5321 autorise une partie locale sensible à la casse, mais aucun
     * fournisseur réel ne l'applique — et un raté de corrélation sur un
     * expéditeur de phishing coûte plus cher que ce cas théorique.
     */
    private String normalizeEmail(String value) {
        String normalized = TextNormalization.lowerTrim(value);
        if (!EMAIL_FORMAT.matcher(normalized).matches()) {
            throw invalid(value, "an email address");
        }
        return normalized;
    }

    private static String require(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            throw new BusinessRuleViolationException(INVALID,
                    "An indicator must have a value");
        }
        return rawValue;
    }

    private BusinessRuleViolationException invalid(String value, String expected) {
        return new BusinessRuleViolationException(INVALID,
                "Value '%s' is not %s (indicator type %s)".formatted(value.trim(), expected, name()));
    }
}
