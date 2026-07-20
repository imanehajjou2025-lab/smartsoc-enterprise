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
    /**
     * UN label de domaine, borné. La validation d'un nom complet se fait
     * label par label EN BOUCLE, jamais par une expression à groupe
     * répété : le moteur d'expressions régulières de Java récurse à chaque
     * répétition de groupe, si bien qu'un nom à un millier de labels
     * ferait déborder la pile. La valeur venant d'un flux CTI externe,
     * ce serait un déni de service offert au producteur (java:S5998).
     */
    private static final Pattern DOMAIN_LABEL =
            Pattern.compile("[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?");
    private static final Pattern HEX = Pattern.compile("^[0-9a-f]+$");

    // Libellés attendus, factorisés : ils servent aussi bien au message
    // d'erreur qu'à la lecture des règles de validation.
    private static final String EXPECTED_IPV4 = "a dotted-quad IPv4 address";
    private static final String EXPECTED_IPV6 = "an IPv6 literal";
    private static final String EXPECTED_DOMAIN = "a domain name";
    private static final String EXPECTED_URL = "an absolute URL (scheme://host/…)";
    private static final String EXPECTED_EMAIL = "an email address";

    /** Longueur maximale d'un nom de domaine complet (RFC 1035). */
    private static final int MAX_DOMAIN_LENGTH = 253;
    /** Longueur maximale d'un littéral IPv6 (avec queue IPv4 éventuelle). */
    private static final int MAX_IPV6_LENGTH = 45;
    /** Garde de longueur du domaine, alignée sur la colonne indicators.value. */
    private static final int MAX_VALUE_LENGTH = 2048;

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
            throw invalid(value, EXPECTED_IPV4);
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
        if (!isIpv6Literal(candidate)) {
            throw invalid(value, EXPECTED_IPV6);
        }
        try {
            return InetAddress.getByName(candidate).getHostAddress().toLowerCase(Locale.ROOT);
        } catch (UnknownHostException e) {
            throw invalid(value, EXPECTED_IPV6);
        }
    }

    /**
     * Vrai si la chaîne ne peut être QU'un littéral IPv6 : uniquement des
     * chiffres hexadécimaux, « : » et « . », et au moins un « : ».
     *
     * <p>Vérification caractère par caractère plutôt qu'expression
     * régulière : un motif à deux répétitions non bornées de part et
     * d'autre d'un « : » rétro-suit de façon super-linéaire
     * (java:S8786), et l'entrée vient d'un flux CTI externe. Une boucle
     * est linéaire par construction.
     *
     * <p>Aucun nom d'hôte ne peut contenir « : » — c'est ce qui garantit
     * qu'{@code InetAddress} ne fera jamais de résolution DNS.
     */
    private static boolean isIpv6Literal(String candidate) {
        if (candidate.isEmpty() || candidate.length() > MAX_IPV6_LENGTH) {
            return false;
        }
        boolean hasColon = false;
        for (int i = 0; i < candidate.length(); i++) {
            char c = candidate.charAt(i);
            if (c == ':') {
                hasColon = true;
            } else if (c != '.' && (c < '0' || c > '9') && (c < 'a' || c > 'f')) {
                return false;
            }
        }
        return hasColon;
    }

    private String normalizeDomain(String value) {
        // Le point final d'un FQDN absolu (« evil.com. ») ne change pas le nom.
        String normalized = TextNormalization.lowerTrim(value);
        int end = normalized.length();
        while (end > 0 && normalized.charAt(end - 1) == '.') {
            end--;
        }
        normalized = normalized.substring(0, end);

        if (!isValidDomain(normalized)) {
            throw invalid(value, EXPECTED_DOMAIN);
        }
        return normalized;
    }

    /**
     * Nom de domaine valide : longueur bornée, au moins deux labels, et
     * chaque label conforme — vérifié EN BOUCLE (voir {@link #DOMAIN_LABEL}).
     * Une adresse IPv4 n'est pas un domaine.
     *
     * <p>Extrait en méthode car le domaine d'une adresse e-mail EST un
     * domaine : une seule implémentation de la règle, pas deux qui
     * pourraient diverger.
     */
    private static boolean isValidDomain(String candidate) {
        if (candidate.isEmpty() || candidate.length() > MAX_DOMAIN_LENGTH
                || IPV4_FORMAT.matcher(candidate).matches()) {
            return false;
        }
        String[] labels = candidate.split("\\.", -1);
        if (labels.length < 2) {
            return false;
        }
        for (String label : labels) {
            if (!DOMAIN_LABEL.matcher(label).matches()) {
                return false;
            }
        }
        return true;
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
            throw invalid(value, EXPECTED_URL);
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
            throw invalid(value, EXPECTED_URL);
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
        int at = normalized.indexOf('@');
        // Une seule arobase, une partie locale et un domaine non vides.
        if (at <= 0 || at != normalized.lastIndexOf('@') || at == normalized.length() - 1) {
            throw invalid(value, EXPECTED_EMAIL);
        }
        String local = normalized.substring(0, at);
        for (int i = 0; i < local.length(); i++) {
            if (Character.isWhitespace(local.charAt(i))) {
                throw invalid(value, EXPECTED_EMAIL);
            }
        }
        // Le domaine d'une adresse EST un domaine : même règle, une seule
        // implémentation. Découpage explicite plutôt qu'expression
        // régulière — « [^@\s]+@[^@\s]+\.[^@\s]+ » rétro-suit de façon
        // super-linéaire sur une entrée longue (java:S8786).
        if (!isValidDomain(normalized.substring(at + 1))) {
            throw invalid(value, EXPECTED_EMAIL);
        }
        return normalized;
    }

    private static String require(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            throw new BusinessRuleViolationException(INVALID,
                    "An indicator must have a value");
        }
        // Le domaine se défend seul : il ne suppose pas que la couche API
        // a déjà borné la taille (la valeur peut aussi venir d'un test,
        // d'un import ou d'un futur appelant).
        if (rawValue.length() > MAX_VALUE_LENGTH) {
            throw new BusinessRuleViolationException(INVALID,
                    "An indicator value must not exceed %d characters".formatted(MAX_VALUE_LENGTH));
        }
        return rawValue;
    }

    private BusinessRuleViolationException invalid(String value, String expected) {
        return new BusinessRuleViolationException(INVALID,
                "Value '%s' is not %s (indicator type %s)".formatted(value.trim(), expected, name()));
    }
}
