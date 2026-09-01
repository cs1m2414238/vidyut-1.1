package com.vidyut.company.service;

import com.vidyut.station.entity.ChargingConnector;
import com.vidyut.station.entity.ChargingStation;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/** Matches the complete, already-authorized network before response mapping (never a UI page). */
final class CompanyNetworkSearch {
    private static final Set<String> NOISE = Set.of("charger", "chargers", "charging", "station", "stations", "connector", "connectors");
    private static final Set<String> CONNECTOR_WORDS = Set.of("ccs2", "type1", "type2", "type", "1", "2", "chademo", "gb", "t", "ac", "dc");

    private CompanyNetworkSearch() {}

    static List<ChargingStation> stations(List<ChargingStation> authorized, String query) {
        List<String> terms = terms(query);
        List<ChargingStation> codeMatches = authorized.stream()
                .filter(station -> words(query).size() > 1 && normalize(station.getDemoSeedKey()).contains(normalize(query)))
                .toList();
        if (!codeMatches.isEmpty()) return codeMatches;
        return preferCity(authorized, terms).stream()
                .filter(station -> matches(terms, stationText(station) + " " + value(station.getDemoSeedKey())))
                .toList();
    }

    static List<ChargingConnector> chargers(List<ChargingStation> authorized, String query) {
        List<String> terms = terms(query);
        List<ChargingConnector> all = authorized.stream().flatMap(station -> station.getConnectors().stream()).toList();
        // Exact identity wins over incidental address/type matches for a fully specified charger code.
        List<ChargingConnector> exact = all.stream()
                .filter(connector -> !normalize(query).isEmpty() && normalize(connector.getChargerCode()).equals(normalize(query)))
                .toList();
        if (!exact.isEmpty()) return exact;
        List<String> locationTerms = terms.stream().filter(term -> !CONNECTOR_WORDS.contains(term)).toList();
        boolean cityQuery = authorized.stream().anyMatch(station -> {
            List<String> city = words(station.getCity());
            return !city.isEmpty() && city.size() == locationTerms.size()
                    && locationTerms.stream().allMatch(term -> city.stream().anyMatch(word -> tokenMatches(term, word)));
        });
        List<ChargingConnector> codeMatches = all.stream()
                .filter(connector -> !cityQuery && words(query).size() > 1 && normalize(connector.getChargerCode()).startsWith(normalize(query)))
                .toList();
        if (!codeMatches.isEmpty()) return codeMatches;
        return preferCity(authorized, locationTerms).stream().flatMap(station -> station.getConnectors().stream())
                .filter(connector -> matches(terms, stationText(connector.getStation()) + " "
                        + value(connector.getChargerCode()) + " " + connectorType(connector)))
                .toList();
    }

    private static List<ChargingStation> preferCity(List<ChargingStation> stations, List<String> locationTerms) {
        // A city query means that city, not a different city's address mentioning its highway.
        // Full address searches still use the entire network. No city-specific aliases are stored here.
        for (int rank = 0; rank <= 2; rank++) {
            int matchRank = rank;
            List<ChargingStation> cityMatches = stations.stream().filter(station -> {
                List<String> city = words(station.getCity());
                return !city.isEmpty() && city.size() == locationTerms.size()
                        && locationTerms.stream().allMatch(term -> city.stream().anyMatch(word ->
                                matchRank == 0 ? word.equals(term) : matchRank == 1 ? transposed(term, word) : tokenMatches(term, word)));
            }).toList();
            if (!cityMatches.isEmpty()) return cityMatches;
        }
        return stations;
    }

    private static String stationText(ChargingStation station) {
        // State/region is currently stored in the address, not a separate station column.
        return value(station.getName()) + " " + value(station.getCity()) + " " + value(station.getAddress());
    }

    private static String connectorType(ChargingConnector connector) {
        if (connector.getType() == null) return "";
        return switch (connector.getType()) {
            case TYPE1 -> "type1 type 1 ac";
            case TYPE2 -> "type2 type 2 ac";
            case CCS2 -> "ccs2 dc";
            case CHADEMO -> "chademo dc";
            case GB_T -> "gb t";
        };
    }

    private static boolean matches(List<String> terms, String text) {
        List<String> words = words(text);
        return terms.stream().allMatch(term -> words.stream().anyMatch(word -> tokenMatches(term, word)));
    }

    private static boolean tokenMatches(String term, String word) {
        if (word.equals(term)) return true;
        // Short location prefixes (agr) are useful; numeric/code tokens remain exact.
        if (!term.matches("[a-z]{3,}")) return false;
        if (word.startsWith(term)) return true;
        return transposed(term, word);
    }

    private static boolean transposed(String term, String word) {
        if (term.length() < 4 || term.length() != word.length()) return false;
        for (int i = 0; i < term.length() - 1; i++) {
            String swapped = term.substring(0, i) + term.charAt(i + 1) + term.charAt(i) + term.substring(i + 2);
            if (word.equals(swapped)) return true;
        }
        return false;
    }

    private static List<String> terms(String query) {
        return words(query).stream().filter(term -> !NOISE.contains(term)).distinct().toList();
    }

    private static List<String> words(String text) {
        return Arrays.stream(normalize(text).split(" ")).filter(word -> !word.isEmpty()).toList();
    }

    private static String normalize(String text) {
        return Normalizer.normalize(value(text), Normalizer.Form.NFKD).replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", " ").trim();
    }

    private static String value(String text) { return Objects.toString(text, ""); }
}
