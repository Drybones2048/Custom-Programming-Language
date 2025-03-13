package plc.exercises.regextesting;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class RegexTests {

    @ParameterizedTest
    @MethodSource
    public void testEmailRegex(String test, String input, boolean matches) {
        test(input, Regex.EMAIL, matches);
    }

    public static Stream<Arguments> testEmailRegex() {
        return Stream.of(
            /*Arguments.of("Alphanumeric", "thelegend27@gmail.com", true),
            Arguments.of("UF Domain", "otherdomain@ufl.edu", true),
            Arguments.of("Missing Domain Dot", "missingdot@gmailcom", false),
            Arguments.of("Symbols", "symbols#$%@gmail.com", false),*/
            //TODO: Test coverage (*minimum* 5 matching, 5 non-matching)

            //My test cases 10 true, 10 false
            Arguments.of("Plain Jane", "regularemail@gmail.com", true),
            Arguments.of("Underscores", "ILove_Gaps@gmail.com", true),
            Arguments.of("Numbers in Email", "BeastMode46@gmail.com", true),
            Arguments.of("All Numbers", "123456789@gmail.com", true),
            Arguments.of("Starting with Symbol", "-hellothere@gmail.com", true),
            Arguments.of("Only Underscores", "________@gmail.com", true),
            Arguments.of("Excess Symbols", "!L%^&ov*()I@eS#$ymbols", false),
            Arguments.of("Out of Order Email", "@gmail.comItsOppositeDay", false),
            Arguments.of("Whitespace in Middle", "no spaceLeft@gmail.com", false),
            Arguments.of("Starting Whitespace", " IDidntForget@gmail.com", false),
            Arguments.of("All Caps Start", "WHYAMIYELLING@gmail.com", true),
            Arguments.of("All Caps End", "whyamicalm@GMAIL.COM", false),
            Arguments.of("No @ Symbol", "everythingIsNormalgmail.com", false),
            Arguments.of("Forgot Period", "DotsAreAmazing@gmailcom", false),
            Arguments.of("Non-gmail or School Email", "ILoveOldStuff@hotmail.com", true),
            Arguments.of("Really Long Other Email", "SusEmailsAreMyFav@outlookhotmailbox.com", true),
            Arguments.of("Too Short After Dot", "IHaveTroubleTyping@gmail.c", false),
            Arguments.of("Numbers in Email Company", "CheapEmails@1111111.com", true),
            Arguments.of("Numbers After Dot", "WeirdDots@gmail.c0m", false),
            Arguments.of("Long After Dot", "ExcessivePerson@gmail.companybasedoutoftheunitedstates", false),
            Arguments.of("Empty Address", "", false), //initial fail
            Arguments.of("Underscore in Domain", "hellothere@g_mail.com", false), //initial fail
            Arguments.of("Empty Domain", "hellothere@", false), //initial fail
            Arguments.of("Plus Address", "Kenobi+hellothere@gmail.com", false), //initial fail
            Arguments.of("Subdomain", "hellothere@a.gmail.com", false) //initial fail
        );
    }

    @ParameterizedTest
    @MethodSource
    public void testDiscountCsvRegex(String test, String input, boolean matches) {
        test(input, Regex.DISCOUNT_CSV, matches);
    }

    public static Stream<Arguments> testDiscountCsvRegex() {
        return Stream.of(
            /*Arguments.of("Single", "single", true),
            Arguments.of("Multiple", "one,two,three", true),
            Arguments.of("Spaces", "first , second", true),
            Arguments.of("Missing Value", "first,,second", false)*/
            //TODO: Test coverage (*minimum* 5 matching, 5 non-matching)
            Arguments.of("Plain Jane", "first, second", true),
            Arguments.of("All Caps", "FIRST,SECOND", true),
            Arguments.of("Numbers", "1,2", true),
            Arguments.of("Symbols", "!,%", true),
            Arguments.of("Multiple Commas", "first,,,,,,,,,second", false),
            Arguments.of("Non-Comma Separater", "first'second", true),
            Arguments.of("Lots of Whitespace", "first             ,           second", true),
            Arguments.of("No Entries", " , ", false),
            Arguments.of("Just Commas", ",,,", false),
            Arguments.of("No Entry or Comma", "", false),
            Arguments.of("Single", "single", true),
            Arguments.of("Multiple", "one, two, three, four", true)
        );
    }

    @ParameterizedTest
    @MethodSource
    public void testDiceNotationRegex(String test, String input, boolean matches, Map<String, String> groups) {
        var matcher = Regex.DICE_NOTATION.matcher(input);
        Assertions.assertEquals(matches, matcher.matches());
        if (matches) {
            Assertions.assertEquals(groups, Regex.getGroups(matcher));
        }
    }

    public static Stream<Arguments> testDiceNotationRegex() {
        return Stream.of(
            Arguments.of("Standard Dice", "1d6", true, Map.of(
                "count", "1", "faces", "6"
            )),
            Arguments.of("Positive Bonus", "1d6+4", true, Map.of(
                "count", "1", "faces", "6", "bonus", "+4"
            )),
            Arguments.of("Missing Faces", "1d", false, Map.of()),
            Arguments.of("Negative Count", "-1d6", false, Map.of()),
            //TODO: Test coverage (*minimum* 5 matching, 5 non-matching)
            Arguments.of("Negative Bonus", "1d6-4", true, Map.of("count", "1", "faces", "6", "bonus", "-4")),
                Arguments.of("Leading Count Zero", "01d6", false, Map.of()),
                Arguments.of("Leading Bonus Zero", "1d06", false, Map.of()),
                Arguments.of("Large Die Size", "1d1000000", true, Map.of("count", "1", "faces", "1000000")),
                Arguments.of("Large Number of Dice", "1000000d6", true, Map.of("count", "1000000", "faces", "6")),
                Arguments.of("Wrong letter", "1f6", false, Map.of()),
                Arguments.of("Zero Bonus", "1d6+0", true, Map.of("count", "1", "faces", "6", "bonus", "+0"))
        );
    }

    @ParameterizedTest
    @MethodSource
    public void testNumberRegex(String test, String input, boolean matches) {
        test(input, Regex.NUMBER, matches);
    }

    public static Stream<Arguments> testNumberRegex() {
        return Stream.of(
            //TODO: Test coverage (*minimum* 5 matching, 5 non-matching)
            Arguments.of("Plain Jane", "12", true),
                    Arguments.of("Decimal Test", "12.5", true),
                    Arguments.of("Single Digit", "1", true),
                    Arguments.of("Exponential", "1e5", true),
                    Arguments.of("Decimal Only", "0.5", true),
                    Arguments.of("Missing Integer", ".5", false),
                    Arguments.of("Missing Exponent", "10e", false),
                    Arguments.of("Letter Instead of Number", "y", false),
                    Arguments.of("Empty Input", "", false),
                    Arguments.of("Symbol in Input", "10%", false),
                Arguments.of("Signed Exponent", "1e-5", true)
            //throw new UnsupportedOperationException("TODO");
        );
    }

    @ParameterizedTest
    @MethodSource
    public void testStringRegex(String test, String input, boolean matches) {
        test(input, Regex.STRING, matches);
    }

    public static Stream<Arguments> testStringRegex() {
        return Stream.of(
        //TODO: Test coverage (*minimum* 5 matching, 5 non-matching)

                Arguments.of("Empty String", "\"\"", true),
                Arguments.of("Slashes", "\"1\\\\2\"", true),
                Arguments.of("Regular Word", "\"hello\"", true),
                Arguments.of("Punctuation", "\"why so serious?\"", true),
                Arguments.of("Capital Letters", "\"YELLING TIME\"", true),
                Arguments.of("Forgot Quotes", "I am forgetful", false),
                Arguments.of("Missing End Quote", "\"It is time to jam", false),
                Arguments.of("No Double Quotes Inside String", "Hel\"lo", false),
                Arguments.of("No New Line Character", "Hel\nlo", false),
                Arguments.of("No Slash-R Character", "Hel\rlo", false)


        );
    }

    private static void test(String input, Pattern pattern, boolean matches) {
        Assertions.assertEquals(matches, pattern.matcher(input).matches());
    }

}
