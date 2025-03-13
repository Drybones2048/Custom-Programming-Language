package plc.exercises.regextesting;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class Regex {

    public static final Pattern
        EMAIL = Pattern.compile("[A-Za-z0-9._\\-]+@[A-Za-z0-9-]*\\.[a-z]{2,3}"),
        DISCOUNT_CSV = Pattern.compile("\\s*[!-+--~]+\\s*(,\\s*[!-+--~]+\\s*)*"), //TODO
        DICE_NOTATION = Pattern.compile("(?<count>[1-9]+[0-9]*)d(?<faces>[1-9]+[0-9]*)(?<bonus>[+-][0-9]+)*"), //TODO
        NUMBER = Pattern.compile("[0-9]+(.[0-9]+)*(e-*[0-9]+)*"), //TODO
        STRING = Pattern.compile("\"([^\"\\\\\\r\\n]|\\\\[bfnrt'\"\\\\])*\""); //TODO

    /**
     * Helper function for getting all capturing groups matched by the regex
     * (used for debugging & DICE_NOTATION). The key of the group is the group
     * name if present, otherwise just the group index as a string.
     */
    public static Map<String, String> getGroups(Matcher matcher) {
        var names = matcher.namedGroups().entrySet().stream()
            .collect(Collectors.toMap(Map.Entry::getValue, Map.Entry::getKey));
        var groups = new LinkedHashMap<String, String>(); //maintain group order
        for (int i = 1; i <= matcher.groupCount(); i++) {
            if (matcher.group(i) != null) {
                groups.put(names.getOrDefault(i, String.valueOf(i)), matcher.group(i));
            }
        }
        return groups;
    }

}
