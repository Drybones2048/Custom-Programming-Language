package plc.project.lexer;

import java.util.List;
import java.util.ArrayList;
import java.io.IOException;

/**
 * The lexer works through a combination of {@link #lex()}, which repeatedly
 * calls {@link #lexToken()} and skips over whitespace/comments, and
 * {@link #lexToken()}, which determines the type of the next token and
 * delegates to the corresponding lex method.
 *
 * <p>Additionally, {@link CharStream} manages the lexer state and contains
 * {@link CharStream#peek} and {@link CharStream#match}. These are helpful
 * utilities for working with character state and building tokens.
 */
public final class Lexer {

    private final CharStream chars;

    public Lexer(String input) {
        chars = new CharStream(input);
    }

    public List<Token> lex() throws LexException {
        List<Token> tokens = new ArrayList<>(); // Creates an array list that will keep track of the tokens

        while (chars.has(0)) {
            skipWhitespaceAndComments();
            if (!chars.has(0)) {
                break; // End of input after skipping whitespace/comments

            }

            Token token = lexToken();  // Get the next token
            if (token != null) {
                tokens.add(token);

            }
        }
        return tokens;

    }


    private void skipWhitespaceAndComments() throws LexException { // Skips any whitespace or comments in the character stream
        while (chars.has(0)) { // Check for whitespace characters
            if (chars.peek("[ \b\n\r\t]")) {
                chars.match("[ \b\n\r\t]");
                chars.emit(); // Clear the whitespace character
                continue;
            }

            if (chars.has(1) && chars.peek("/", "/")) { // Check for comments
                skipComment();
                continue;
            }

            // If not whitespace or comment, exit the loop
            break;
        }
    }

    private void skipComment() {
        // Skip the '//' comment markers
        chars.match("/");
        chars.match("/");
        chars.emit(); // Clear the comment markers

        while (chars.has(0) && !chars.peek("[\n\r]")) { // Skip characters until end of line or end of input
            chars.match(".");
        }

        if (chars.has(0) && chars.peek("[\n\r]")) { // Skip the newline if present
            chars.match(".");
        }

        chars.emit(); // Clear the comment content
    }


    private Token lexToken() throws LexException{
        if (!chars.has(0)) {
            throw new LexException("Reached end of input");

        }

        // Identify token type and delegate to appropriate method
        if (chars.peek("[A-Za-z_]")) {
            return lexIdentifier();

        } else if (chars.peek("[0-9]")) {
            return lexNumber();

        } else if (chars.peek("[+\\-]") && chars.has(1) && chars.peek("[+\\-]", "[0-9]")) {
            return lexNumber();

        } else if (chars.peek("'")) {
            return lexCharacter();

        } else if (chars.peek("\"")) {
            return lexString();

        } else {
            return lexOperator();

        }
    }

    private Token lexIdentifier() {
        StringBuilder builder = new StringBuilder();

        if (chars.match("[A-Za-z_]")) { // First character must be letter or underscore
            builder.append(chars.emit());
        }

        while (chars.has(0) && chars.peek("[A-Za-z0-9_\\-]")) { // Subsequent characters can include digits and hyphens
            chars.match(".");
            builder.append(chars.emit());
        }

        return new Token(Token.Type.IDENTIFIER, builder.toString());
    }

    private Token lexNumber() {
        StringBuilder builder = new StringBuilder();
        boolean isDecimal = false;

        if (chars.peek("[+\\-]")) { // Handle optional sign
            chars.match(".");
            builder.append(chars.emit());

        }

        while (chars.has(0) && chars.peek("[0-9]")) { // Digits before decimal point
            chars.match(".");
            builder.append(chars.emit());

        }


        if (chars.has(0) && chars.peek("\\.")) { // Handle decimal point and following digits
            chars.match("\\.");
            builder.append(chars.emit());

            if (chars.has(0) && chars.peek("[0-9]")) {
                isDecimal = true;

                while (chars.has(0) && chars.peek("[0-9]")) {
                    chars.match(".");
                    builder.append(chars.emit());

                }
            } else {
                // No digits after decimal point - it's an INTEGER followed by a dot operator
                String value = builder.substring(0, builder.length() - 1);
                chars.index--; // backtrack to the dot
                return new Token(Token.Type.INTEGER, value);
            }
        }

        // Handle exponent notation
        if (chars.has(0) && chars.peek("[eE]")) {
            // Save the current state before processing the exponent
            int savedIndex = chars.index;
            int savedLength = builder.length();

            chars.match(".");
            builder.append(chars.emit());

            if (chars.has(0) && chars.peek("[+\\-]")) { // Check for optional sign in exponent
                chars.match(".");
                builder.append(chars.emit());
            }

            if (chars.has(0) && chars.peek("[0-9]")) { // Check for digits in exponent
                // We have digits after 'e', so this is a valid exponent
                while (chars.has(0) && chars.peek("[0-9]")) {
                    chars.match(".");
                    builder.append(chars.emit());
                }

                // The presence of 'e' followed by digits makes this a DECIMAL only if
                // there was already a decimal point
                // Otherwise it remains an INTEGER
            } else {
                // No digits after 'e', so this is not a valid exponent
                // Revert to before the 'e' and return what we have so far
                chars.index = savedIndex;
                String value = builder.substring(0, savedLength);
                return new Token(isDecimal ? Token.Type.DECIMAL : Token.Type.INTEGER, value);
            }
        }

        Token.Type type = isDecimal ? Token.Type.DECIMAL : Token.Type.INTEGER;
        return new Token(type, builder.toString());
    }

    private Token lexCharacter() throws LexException{
        // Match opening quote
        chars.match("'");
        String openQuote = chars.emit();

        StringBuilder builder = new StringBuilder(openQuote);

        // Handle character content (regular char or escape sequence)
        if (chars.has(0)) {
            if (chars.peek("\\\\")) {
                // Escape sequence
                chars.match("\\\\");
                String escapeSlash = chars.emit();

                if (chars.has(0) && chars.peek("[bnrt'\"\\\\]")) {
                    chars.match(".");
                    String escapeChar = chars.emit();
                    builder.append(escapeSlash).append(escapeChar);

                } else {
                    throw new LexException("Invalid escape sequence");

                }
            } else if (!chars.peek("'") && !chars.peek("[\n\r]")) {
                // Regular character (not quote, newline, or return)
                chars.match(".");
                builder.append(chars.emit());

            } else if (chars.peek("[\n\r]")) {
                throw new LexException("Unterminated character literal");

            } else {
                throw new LexException("Empty character literal");

            }
        } else {
            throw new LexException("Unterminated character literal");

        }

        if (chars.has(0) && chars.peek("'")) { // Match closing quote
            chars.match("'");
            builder.append(chars.emit());

        } else {
            throw new LexException("Unterminated character literal");

        }

        return new Token(Token.Type.CHARACTER, builder.toString());
    }

    private Token lexString() throws LexException{
        // Match opening quote
        chars.match("\"");
        String openQuote = chars.emit();

        StringBuilder builder = new StringBuilder(openQuote);

        // Read string content until closing quote or error
        while (chars.has(0) && !chars.peek("\"") && !chars.peek("[\n\r]")) {
            if (chars.peek("\\\\")) {
                // Handle escape sequence
                chars.match("\\\\");
                String escapeSlash = chars.emit();

                if (chars.has(0) && chars.peek("[bnrt'\"\\\\]")) {
                    chars.match(".");
                    String escapeChar = chars.emit();
                    builder.append(escapeSlash).append(escapeChar);

                } else {
                    throw new LexException("Invalid escape sequence");

                }
            } else {
                // Handle regular character
                chars.match(".");
                builder.append(chars.emit());

            }
        }

        // Check for unterminated string
        if (!chars.has(0) || chars.peek("[\n\r]")) {
            throw new LexException("Unterminated string literal");

        }

        // Match closing quote
        chars.match("\"");
        builder.append(chars.emit());

        return new Token(Token.Type.STRING, builder.toString());

    }

    private void lexEscape() throws LexException {
        chars.match("\\\\"); // Matches escape

        if (chars.peek("[bnrt'\"\\\\]")) { // Checks if the escape sequence is valid
            chars.emit();  // Capture the escape character

        } else {
            throw new LexException("Invalid escape sequence: " + chars.peek());

        }
    }

    public Token lexOperator() {
        StringBuilder builder = new StringBuilder();

        // Match first character of operator
        chars.match(".");
        builder.append(chars.emit());

        // Handle compound operators like <= >= == !=
        if (builder.toString().matches("[<>!=]") && chars.has(0) && chars.peek("=")) {
            chars.match("=");
            builder.append(chars.emit());

        }

        return new Token(Token.Type.OPERATOR, builder.toString());
    }

    /**
     * A helper class for maintaining the state of the character stream (input)
     * and methods for building up token literals.
     */
    private static final class CharStream {

        private final String input;
        private int index = 0;
        private int length = 0;

        public CharStream(String input) {
            this.input = input;
        }

        public boolean has(int offset) {
            return index + offset < input.length();
        }

        /**
         * Returns true if the next characters match their corresponding
         * pattern. Each pattern is a regex matching only ONE character!
         */
        public boolean peek(String... patterns) {
            if (!has(patterns.length - 1)) {
                return false;
            }
            for (int offset = 0; offset < patterns.length; offset++) {
                var character = input.charAt(index + offset);
                if (!String.valueOf(character).matches(patterns[offset])) {
                    return false;
                }
            }
            return true;
        }

        /**
         * Equivalent to peek, but also advances the character stream.
         */
        public boolean match(String... patterns) {
            var peek = peek(patterns);
            if (peek) {
                index += patterns.length;
                length += patterns.length;
            }
            return peek;
        }

        /**
         * Returns the literal built by all characters matched since the last
         * call to emit(); also resetting the length for subsequent tokens.
         */
        public String emit() {
            var literal = input.substring(index - length, index);
            length = 0;
            return literal;
        }

    }

}
