package com.vidyut.autopilot.service;

import com.vidyut.autopilot.dto.JourneyIntentParseResponse;
import org.springframework.stereotype.Service;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class JourneyIntentParser {

    private static final String ROUTE_BOUNDARY =
            "(?=\\s*(?:,|;|\\bwith\\b|\\bcurrent\\b|\\bstarting\\b|\\bbattery\\b|\\bkeep\\b|"
                    + "\\breserve\\b|\\bbudget\\b|\\bunder\\b|\\bwithin\\b|\\barriv(?:e|al)\\b|"
                    + "\\breach\\b|\\bfastest\\b|\\bquickest\\b|\\bcheapest\\b|\\blowest\\b|"
                    + "\\bbalanced\\b|\\brecommend\\b|\\bask\\b|\\bfull\\s+autopilot\\b|$))";
    private static final Pattern ROUTE = Pattern.compile(
            "(?i)(?:^|\\b)from\\s+(.+?)\\s+to\\s+(.+?)" + ROUTE_BOUNDARY);
    private static final Pattern ROUTE_WITHOUT_FROM = Pattern.compile(
            "(?i)^\\s*(.+?)\\s+to\\s+(.+?)" + ROUTE_BOUNDARY);
    private static final Pattern CURRENT_BATTERY = Pattern.compile(
            "(?i)(?:\\b(?:current|starting|start)\\s+(?:battery\\s*)?(?:is|at|of|:)?|"
                    + "(?<!arrival )(?<!reserve )\\bbattery\\s*(?:is|at|of|:)?)[\\s₹]*([0-9]{1,3}(?:\\.[0-9]+)?)\\s*%");
    private static final Pattern CURRENT_BATTERY_BEFORE_LABEL = Pattern.compile(
            "(?i)(?<!arrive with )(?<!arrival )(?<!reserve )\\b([0-9]{1,3}(?:\\.[0-9]+)?)"
                    + "\\s*%\\s*(?:battery|charge|soc)\\b");
    private static final Pattern RESERVE = Pattern.compile(
            "(?i)(?:\\b(?:safety|minimum|arrival)?\\s*reserve\\s*(?:is|at|of|:)?|"
                    + "\\barrive\\s+with(?:\\s+at\\s+least)?|\\bkeep(?:\\s+at\\s+least)?)"
                    + "\\s*([0-9]{1,2}(?:\\.[0-9]+)?)\\s*%");
    private static final Pattern BUDGET_AFTER_LABEL = Pattern.compile(
            "(?i)\\b(?:maximum(?:\\s+charging)?\\s+budget|max(?:\\s+charging)?\\s+budget|budget|under|within)"
                    + "\\s*(?:is|of|:)?\\s*(?:₹|rs\\.?|inr)?\\s*([0-9][0-9,]*(?:\\.[0-9]+)?)");
    private static final Pattern BUDGET_AFTER_CURRENCY = Pattern.compile(
            "(?i)(?:₹|\\brs\\.?|\\binr)\\s*([0-9][0-9,]*(?:\\.[0-9]+)?)"
                    + "(?:\\s*(?:maximum|max)?\\s*budget)?");
    private static final Pattern DEADLINE = Pattern.compile(
            "(?i)\\b(?:arrive|arrival|reach|deadline)(?:\\s+(?:by|before|at))?\\s+"
                    + "([0-9]{1,2})(?::([0-9]{2}))?\\s*(am|pm)?\\b");
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm");

    public JourneyIntentParseResponse parse(String request) {
        String text = request == null ? "" : request.trim().replaceAll("\\s+", " ");
        List<String> recognized = new ArrayList<>();

        Matcher route = firstMatch(ROUTE, ROUTE_WITHOUT_FROM, text);
        String origin = null;
        String destination = null;
        if (route != null) {
            origin = cleanLocation(route.group(1));
            destination = cleanLocation(route.group(2));
            if (!origin.isBlank() && !destination.isBlank()) {
                recognized.add("route");
            } else {
                origin = null;
                destination = null;
            }
        }

        Double currentBattery = firstNumber(text, CURRENT_BATTERY, CURRENT_BATTERY_BEFORE_LABEL);
        if (currentBattery != null && currentBattery >= 1 && currentBattery <= 100) {
            recognized.add("currentBatteryPercent");
        } else {
            currentBattery = null;
        }

        Double reserve = number(RESERVE, text);
        if (reserve != null && reserve >= 5 && reserve <= 50) {
            recognized.add("minimumArrivalBatteryPercent");
        } else {
            reserve = null;
        }

        Double budget = firstNumber(text, BUDGET_AFTER_LABEL, BUDGET_AFTER_CURRENCY);
        if (budget != null && budget > 0) {
            recognized.add("maximumChargingBudget");
        } else {
            budget = null;
        }

        String deadline = parseDeadline(text);
        if (deadline != null) recognized.add("arrivalDeadline");

        String lower = text.toLowerCase(Locale.ROOT);
        String optimizeFor = null;
        if (containsAny(lower, "lowest cost", "low cost", "cheapest", "minimum cost", "save money")) {
            optimizeFor = "COST";
        } else if (containsAny(lower, "balanced", "balance time", "convenience")) {
            optimizeFor = "BALANCED";
        } else if (containsAny(lower, "fastest", "quickest", "minimum time", "minimize total trip time",
                "minimise total trip time", "minimize trip time", "minimise trip time", "as soon as possible")) {
            optimizeFor = "TIME";
        }
        if (optimizeFor != null) recognized.add("optimizeFor");

        String autonomyMode = null;
        if (containsAny(lower, "recommend only", "only recommend", "i will book", "i take the actions")) {
            autonomyMode = "RECOMMEND_ONLY";
        } else if (containsAny(lower, "full autopilot", "act automatically", "book automatically")) {
            autonomyMode = "FULL_AUTOPILOT";
        } else if (containsAny(lower, "ask before", "ask me before", "ask permission", "get my approval")) {
            autonomyMode = "ASK_BEFORE_ACTIONS";
        }
        if (autonomyMode != null) recognized.add("autonomyMode");

        String tripPurpose = null;
        if (containsAny(lower, "mall", "shopping")) {
            tripPurpose = "MALL_VISIT";
        } else if (containsAny(lower, "rest stop", "rest and food", "rest & food", "meal", "food stop")) {
            tripPurpose = "REST_STOP";
        } else if (containsAny(lower, "commute", "office", "work trip")) {
            tripPurpose = "COMMUTE";
        } else if (containsAny(lower, "charge near destination", "destination charging", "charge at destination")) {
            tripPurpose = "DESTINATION_CHARGING";
        }
        if (tripPurpose != null) recognized.add("tripPurpose");

        return JourneyIntentParseResponse.builder()
                .origin(origin)
                .destination(destination)
                .currentBatteryPercent(currentBattery)
                .minimumArrivalBatteryPercent(reserve)
                .maximumChargingBudget(budget)
                .arrivalDeadline(deadline)
                .optimizeFor(optimizeFor)
                .autonomyMode(autonomyMode)
                .tripPurpose(tripPurpose)
                .recognizedFields(List.copyOf(recognized))
                .build();
    }

    private Matcher firstMatch(Pattern primary, Pattern fallback, String text) {
        Matcher matcher = primary.matcher(text);
        if (matcher.find()) return matcher;
        matcher = fallback.matcher(text);
        return matcher.find() ? matcher : null;
    }

    private Double firstNumber(String text, Pattern... patterns) {
        for (Pattern pattern : patterns) {
            Double value = number(pattern, text);
            if (value != null) return value;
        }
        return null;
    }

    private Double number(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text);
        if (!matcher.find()) return null;
        return Double.parseDouble(matcher.group(1).replace(",", ""));
    }

    private String parseDeadline(String text) {
        Matcher matcher = DEADLINE.matcher(text);
        if (!matcher.find()) return null;
        int hour = Integer.parseInt(matcher.group(1));
        int minute = matcher.group(2) == null ? 0 : Integer.parseInt(matcher.group(2));
        String meridiem = matcher.group(3);
        if (minute > 59) return null;
        if (meridiem != null) {
            if (hour < 1 || hour > 12) return null;
            if (hour == 12) hour = 0;
            if (meridiem.equalsIgnoreCase("pm")) hour += 12;
        } else if (hour > 23) {
            return null;
        }
        return LocalTime.of(hour, minute).format(CLOCK);
    }

    private String cleanLocation(String location) {
        return location.trim().replaceAll("^[,;:.\\-\\s]+|[,;:.\\-\\s]+$", "");
    }

    private boolean containsAny(String text, String... values) {
        for (String value : values) {
            if (text.contains(value)) return true;
        }
        return false;
    }
}
