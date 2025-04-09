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
        List<Token> tokens = new ArrayList<>(); //creates an array list that will keep track of the tokens
        while (chars.has(0)) {
            Token token = lexToken();  // Get the next token
            if (token != null) {
                tokens.add(token);
            }
        }
        return tokens;
    }

    private void lexComment() {
        chars.match("//"); //grabs the opening double slash
        while (chars.has(0) && !chars.peek("\n")) { //allows anything to go through except newline characters
            chars.match(".");
        }
        chars.match("\n");  // Skip newline character
    }

    private Token lexToken() throws LexException{
        if (chars.peek(" ") || chars.peek("\n")) { //ignores whitespace
            chars.match(" ");
            chars.match("\n");
            chars.emit();
            return null;
        }

        if (chars.peek("/")) { //calls the comment lexer and ignores them
            lexComment();
            return null;
        }

        if (chars.peek("[A-Za-z_]")) { //will handle most of the words outside of strings
            return lexIdentifier();
        }

        if (chars.peek("[0-9]") || chars.peek("[+-]")) { //anything starting with a digit is designated as a number
            return lexNumber();
        }

        if (chars.peek("'")) { //anything that starts with a single quote will be marked as a char
            return lexCharacter();
        }

        if (chars.peek("\"")) { //anything marked with a double quote will be marked as a string
            return lexString();
        }

        if (chars.peek("[;.<>!=()]")) { //all the rest of the symbols will be put here
            return lexOperator();
        }

        throw new LexException("Invalid character: " + chars.peek());
    }

    private Token lexIdentifier() {
        //[A-Za-z_] [A-Za-z0-9_-]*
        StringBuilder builder = new StringBuilder();
        builder.append(chars.emit());  //grabs the first character

        while (chars.has(0) && chars.match("[A-Za-z0-9_-]")) { //grabs the remaining characters
            builder.append(chars.emit());
        }

        String identifier = builder.toString();
        return new Token(Token.Type.IDENTIFIER, identifier); //return
    }

    private Token lexNumber() {
        StringBuilder builder = new StringBuilder();
        boolean isDecimal = false; //will be helpful in controling control flow based on if the number has a decimal
        if (chars.peek("[+-]")) { //captures optional sign
            chars.match("[+-]");

            if(!chars.peek("[0-9]")){ //if the + or - sign is used as an operator instead, redirect
                builder.append(chars.emit());
                return new Token(Token.Type.OPERATOR, builder.toString());
            }
        }

        while (chars.has(0) && chars.peek("[0-9]")) { //captures number in between sign
            chars.match("[0-9]");
            builder.append(chars.emit());
        }


        if (chars.peek("\\.")) { //checks if there is a decimal
            isDecimal = true; //marks the number as being a decimal

            chars.match("\\.");
            builder.append(chars.emit());

            if(!chars.match("[0-9]")){ //if there is no number after the decimal point
                builder.deleteCharAt(builder.length() - 1); //invalid decimal, so separate the int and the dot
                chars.index -= 1; //decriment the index
                isDecimal = false; //not a decimal anymore
            }

            while (chars.has(0) && chars.match("[0-9]")) { //captures the numbers after the decimal point
                builder.append(chars.emit());
            }
        }


        if (chars.peek("[eE]")) { //checks if there is an exponent
            chars.match("[eE]");
            builder.append(chars.emit());

            if (chars.match("[+-]")) { //checks if there is a sign
                chars.match("[+-]");
                builder.append(chars.emit());
            }
            if(!chars.match("[0-9]")){ //if there is a missing exponent
                builder.deleteCharAt(builder.length() - 1); //deletes the e that was added
                chars.index -= 1; //decriments the index so that e can be evaluated as an operator
            }
            while (chars.has(0) && chars.match("[0-9]")) { //capture all the numbers in the decimal
                builder.append(chars.emit());
            }
        }
        builder.append(chars.emit()); //add the number
        String number = builder.toString();

        if(isDecimal){ //if number is marked as a decimal, return it as such
            return new Token(Token.Type.DECIMAL, number);
        } else{ //otherwise, it is an integer
            return new Token(Token.Type.INTEGER, number);
        }
    }

    private Token lexCharacter() throws LexException{
            chars.match("'"); //cleans up opening single quote

            StringBuilder builder = new StringBuilder();
            if (chars.match("[^'\\n\\r]")) {  // Match any valid character (not a single quote or newline)
                while(chars.match("[\\'nr]")){
                    builder.append(chars.emit());
                }
            } else if (chars.match("\\")) {  // Match escape sequences
                lexEscape();  // Delegate to lexEscape for handling escape sequences
                builder.append(chars.emit());
            } else {
                throw new LexException("Invalid character literal: " + chars.peek());
            }

            chars.match("'"); //add the final single quote
            builder.append(chars.emit());

            if (builder.charAt(chars.index - 1) != '\'') { //make sure that there is a closing quote
                throw new LexException("Unterminated character literal");
            }

           return new Token(Token.Type.CHARACTER, builder.toString()); //return
    }

    private Token lexString() throws LexException{
        StringBuilder builder = new StringBuilder();
        chars.match("\""); //gets open quote
        builder.append(chars.emit());

        while (chars.has(0) && !chars.peek("\"")) {  // Keeps going until hitting the closing double quote
            if (!chars.peek("\\\\")) {  // This will add all non escape characters
                chars.match(Character.toString(chars.input.charAt(chars.index))); //long command that adds the current symbol to match
                builder.append(chars.emit());

            } else if (chars.peek("[\\\\]") && chars.has(1)) {  //used to handle escape commands
                lexEscape();
                builder.append("\\");
                builder.append(chars.emit());
            } else { //will throw an error if there is a literal that cannot be used as a string
                throw new LexException("Invalid String literal");
            }

        }

        chars.match("\""); //tries to catch closing quotes if there are any
        builder.append(chars.emit());

        try{
            if (builder.charAt(chars.index - 1) != '\"') { //checks to make sure it has closing quotes
                throw new LexException("Unterminated string literal");
            }
        } catch(StringIndexOutOfBoundsException e){
            //If it is a long string we will get an out of bounds error, this is to correct this
        }

        return new Token(Token.Type.STRING, builder.toString()); //return
    }

    private void lexEscape() throws LexException {
        chars.match("\\\\"); //matches escape

        if (chars.peek("[bnrt'\"\\\\]")) { //checks if the escape sequence is valid
            chars.emit();  // Capture the escape character
        } else {
            throw new LexException("Invalid escape sequence: " + chars.peek());
        }
    }

    public Token lexOperator() {
        StringBuilder builder = new StringBuilder();

        while (chars.has(0) && !chars.peek("[A-Za-z0-9]")) { //while we are going through, make sure we are operating on symbols
            if(builder.toString().equals("=") && !chars.peek("[><!=+*/-]")){ //checks to see if the symbol is just a lone equals
                return new Token(Token.Type.OPERATOR, builder.toString());
            }
            //checks to see if the equals sign is a combo with another symbol
            if(builder.toString().equals("<=") || builder.toString().equals("=>") || builder.toString().equals("==") || builder.toString().equals("!=") || builder.toString().equals("-=") || builder.toString().equals("+=") || builder.toString().equals("*=") || builder.toString().equals("/=") || builder.toString().equals("(") || builder.toString().equals(")")){
                return new Token(Token.Type.OPERATOR, builder.toString());
            }

            chars.match("[;$^!=?()<==>*+-.]"); //otherwise, keep going with adding the other symbols
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
