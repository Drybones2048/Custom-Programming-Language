package plc.project.parser;

import plc.project.lexer.Token;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;
import java.util.Optional;

import static com.google.common.base.Preconditions.checkState;

/**
 * This style of parser is called <em>recursive descent</em>. Each rule in our
 * grammar has dedicated function, and references to other rules correspond to
 * calling that function. Recursive rules are therefore supported by actual
 * recursive calls, while operator precedence is encoded via the grammar.
 *
 * <p>The parser has a similar architecture to the lexer, just with
 * {@link Token}s instead of characters. As before, {@link TokenStream#peek} and
 * {@link TokenStream#match} help with traversing the token stream. Instead of
 * emitting tokens, you will instead need to extract the literal value via
 * {@link TokenStream#get} to be added to the relevant AST.
 */
public final class Parser {

    private final TokenStream tokens;

    public Parser(List<Token> tokens) {
        this.tokens = new TokenStream(tokens);
    }

    public Ast.Source parseSource() throws ParseException {
        throw new ParseException("TODO");
    }

    public Ast.Stmt parseStmt() throws ParseException {
        if(tokens.peek("LET")){
            return parseLetStmt();
        } else if(tokens.peek("DEF")){
            return parseDefStmt();
        } else if(tokens.peek("IF")){
            return parseIfStmt();
        } else if(tokens.peek("FOR")){
            return parseForStmt();
        } else if(tokens.peek("RETURN")){
            return parseReturnStmt();
        } else{
            return parseExpressionOrAssignmentStmt();
        }
    }

    private Ast.Stmt.Let parseLetStmt() throws ParseException {
        tokens.match("LET"); //skips over LET statement

        if(tokens.get(1).literal().equals(";")){ //if there is no equals assignment
            return new Ast.Stmt.Let(tokens.get(0).literal(), Optional.empty());
        } else if (!tokens.has(3)){ //if there is nothing after the assignment value, then there is no semicolon
            throw new ParseException("Missing Semicolon");
        } else{ //otherwise, the statement was correct
            return new Ast.Stmt.Let(tokens.get(0).literal(), Optional.of(new Ast.Expr.Variable(tokens.get(2).literal())));
        }

    }

    private Ast.Stmt.Def parseDefStmt() throws ParseException {
        tokens.match("DEF");

        if(tokens.has(2) && !tokens.get(2).literal().equals(")")){ //there is a parameter
            return new Ast.Stmt.Def(tokens.get(0).literal(), List.of(tokens.get(2).literal()), List.of());

        } else{ //no parameter
            return new Ast.Stmt.Def(tokens.get(0).literal(), List.of(), List.of());
        }

    }

    private Ast.Stmt.If parseIfStmt() throws ParseException {
        tokens.match("IF"); //absorb IF

        if(tokens.get(4).literal().equals("ELSE")){ //if there is an else present
            return new Ast.Stmt.If(new Ast.Expr.Variable(tokens.get(0).literal()), List.of(new Ast.Stmt.Expression(new Ast.Expr.Variable(tokens.get(2).literal()))), List.of(new Ast.Stmt.Expression(new Ast.Expr.Variable(tokens.get(5).literal()))));

        } else{ //if there is just an if-statement with no else
            return new Ast.Stmt.If(new Ast.Expr.Variable(tokens.get(0).literal()), List.of(new Ast.Stmt.Expression(new Ast.Expr.Variable(tokens.get(2).literal()))), List.of());
        }

    }

    private Ast.Stmt.For parseForStmt() throws ParseException {
        tokens.match("FOR"); //absorb for

        if(!tokens.get(1).literal().equals("IN")){ //if we are missing the IN in FOR IN
            throw new ParseException("Missing IN");
        } else{ //otherwise there will be no issues
            return new Ast.Stmt.For(tokens.get(0).literal(), new Ast.Expr.Variable(tokens.get(2).literal()), List.of(new Ast.Stmt.Expression(new Ast.Expr.Variable(tokens.get(4).literal()))));
        }

    }

    private Ast.Stmt.Return parseReturnStmt() throws ParseException {
        //absorb return and the semicolon that goes with it
        tokens.match("RETURN");

        if(tokens.match(";")){ //if there is a semicolon everything is fine
            return new Ast.Stmt.Return(Optional.empty());
        } else { //no semicolon instance
            throw new ParseException("No Semicolon");
        }


    }

    private Ast.Stmt parseExpressionOrAssignmentStmt() throws ParseException {
        if(tokens.has(1) && tokens.get(1).literal().equals("(")){ //it is a function
            tokens.index++;
            return new Ast.Stmt.Expression(parseVariableOrFunctionExpr());

        } else if(tokens.has(1) && tokens.get(1).literal().equals(";")){ //it is a variable
            return new Ast.Stmt.Expression(parseVariableOrFunctionExpr());

        } else if (tokens.has(1) && tokens.get(1).literal().equals("=")) { //assignment only for variable;
            var firstValue = parseVariableOrFunctionExpr();

            tokens.index += 2;

            return new Ast.Stmt.Assignment(firstValue, parseVariableOrFunctionExpr());

        } else if (tokens.has(1) && tokens.get(1).literal().equals(".")) {
            tokens.match(Token.Type.IDENTIFIER);
            var firstValue = parseVariableOrFunctionExpr();
            tokens.index += 3;
            return new Ast.Stmt.Assignment(firstValue, parseVariableOrFunctionExpr());
        } else{ //if we are missing any pieces
            throw new ParseException("Missing Semicolon");
        }

    }

    public Ast.Expr parseExpr() throws ParseException {
        if (tokens.get(0).literal().equals("(")) { //if it starts with a parenthesis, it is a group
            return parseGroupExpr();

        } else if(tokens.peek("NIL") || tokens.peek("TRUE") || tokens.peek("FALSE")){ //literals that are null or true or false
            return parseLiteralExpr();

        } else if (tokens.has(1) && (tokens.get(1).literal().equals("AND") || tokens.get(1).literal().equals("OR"))) {
            return parseLogicalExpr();

        } else if(tokens.match(Token.Type.INTEGER) || tokens.match(Token.Type.DECIMAL)){ //if there is a number
            if(tokens.peek(Token.Type.OPERATOR)){ //if there is an operator after the number
                if (tokens.peek("*")){ //if you are supposed to multiply
                    return parseMultiplicativeExpr();
                } else if(tokens.peek("/")){ //if there is division
                    return parseMultiplicativeExpr();
                } else if (tokens.peek("+")){ //if you are supposed to add
                    return parseAdditiveExpr();
                } else if(tokens.peek("-")){ //if there is subtraction
                    return parseAdditiveExpr();
                }
            } else { //if it is just a number
                tokens.index -= 1; //remove the match from the else-if in order not mess up the index for literal declaration
                return parseLiteralExpr();
            }
        } else if(tokens.peek(Token.Type.STRING)){ //if it is a string
            return parseLiteralExpr();

        } else if (tokens.peek(Token.Type.CHARACTER)) { //if it is a character
            return parseLiteralExpr();

        } else if(tokens.match("OBJECT")){ //object expression
            return parseObjectExpr();

        } else if(tokens.match(Token.Type.IDENTIFIER)){ //if the first token is an identifier and is lowercase
            if(tokens.match("+")){
                return parseAdditiveExpr();
            } else if(tokens.match("-")){
                return parseAdditiveExpr();
            } else if(tokens.match("*")){
                return parseMultiplicativeExpr();
            } else if(tokens.match("/")){
                return parseMultiplicativeExpr();
            }  else{ //if it just an identifier with no math in it, then it is either a variable, a function, a property, or a method
                return parseVariableOrFunctionExpr();
            }

        }
        return null; //default return
    }

    private Ast.Expr parseLogicalExpr() throws ParseException {
        throw new UnsupportedOperationException("TODO"); //TODO
    }

    private Ast.Expr parseComparisonExpr() throws ParseException {
        throw new UnsupportedOperationException("TODO"); //TODO
    }

    private Ast.Expr parseAdditiveExpr() throws ParseException {
        tokens.index -= 1;
        var left = parseSecondaryExpr();

        if(tokens.match("+") || tokens.match("-")){

            var operator = tokens.get(-1).literal();
            var right = parseSecondaryExpr();
            return new Ast.Expr.Binary(operator, left, right);
        }


        return left;
    }

    private Ast.Expr parseMultiplicativeExpr() throws ParseException {

        tokens.index -= 1;
        var left = parseSecondaryExpr();

        if(tokens.match("*") || tokens.match("/")){

            var operator = tokens.get(-1).literal();
            var right = parseSecondaryExpr();
            return new Ast.Expr.Binary(operator, left, right);
        }


        return left;
    }

    private Ast.Expr parseSecondaryExpr() throws ParseException {
        if(!tokens.get(0).literal().equals(".")){ //if the current index is not a dot, then it is a variable coming from the binary operations
            if(!tokens.has(2) && !tokens.has(1)) {
                return new Ast.Expr.Variable(tokens.get(0).literal());
            }
            return new Ast.Expr.Variable(tokens.get(-1).literal());
        } else if(tokens.has(2) && tokens.get(2).literal().equals("(")){ //if it has parenthesis after the method name
            tokens.match(Token.Type.IDENTIFIER); //skip over method name

            return new Ast.Expr.Method(new Ast.Expr.Variable(tokens.get(-1).literal()), tokens.get(1).literal(), List.of());
        } else{ //if it does not have parenthesis, it has to be a property
            return new Ast.Expr.Property(new Ast.Expr.Variable(tokens.get(-1).literal()), tokens.get(1).literal());
        }

    }

    private Ast.Expr parsePrimaryExpr() throws ParseException {
        while(tokens.has(1)){
            if (tokens.peek("AND") || tokens.peek("OR")){
                return new Ast.Expr.Literal(true);
            } else if (tokens.peek("<") || tokens.peek("<=") || tokens.peek(">") ||
                    tokens.peek(">=") || tokens.peek("==") || tokens.peek("!=")) {
                return new Ast.Expr.Binary(tokens.get(1).literal(), new Ast.Expr.Variable("left"), new Ast.Expr.Variable("right"));
            }
            tokens.index++;
        }
        return new Ast.Expr.Literal(false);
    }

    private Ast.Expr.Literal parseLiteralExpr() throws ParseException {
        if(tokens.match("NIL")){ //if it is null, return null
            return new Ast.Expr.Literal(null);

        } else if (tokens.match("TRUE")) { //if it is true, return true
            return new Ast.Expr.Literal(true);

        } else if (tokens.match("FALSE")) {//if it is false, return false
            return new Ast.Expr.Literal(false);

        } else if (tokens.peek(Token.Type.INTEGER)) { //if it is a literal integer, there is nothing after it so return the number as an integer
            return new Ast.Expr.Literal(new BigInteger(tokens.get(0).literal()));

        } else if (tokens.peek(Token.Type.DECIMAL)) {//if it is a literal decimal, then nothing is after it so return that
            return new Ast.Expr.Literal(new BigDecimal(tokens.get(0).literal()));

        } else if (tokens.peek(Token.Type.CHARACTER)) { //return character without quotes
            return new Ast.Expr.Literal(tokens.get(0).literal().charAt(1));

        } else if (tokens.peek(Token.Type.STRING)) {
            //will replace all escape characters
            if (tokens.get(0).literal().contains("\\n")){
                return new Ast.Expr.Literal(tokens.get(0).literal().replace("\\n", "\n").replace("\"", ""));
            } else if(tokens.get(0).literal().contains("\\r")){
                return new Ast.Expr.Literal(tokens.get(0).literal().replace("\\r", "\r").replace("\"", ""));
            } else if(tokens.get(0).literal().contains("\\t")){
                return new Ast.Expr.Literal(tokens.get(0).literal().replace("\\t", "\t").replace("\"", ""));
            } else if(tokens.get(0).literal().contains("\\b")){
                return new Ast.Expr.Literal(tokens.get(0).literal().replace("\\b", "\b").replace("\"", ""));
            } else if(tokens.get(0).literal().contains("\\f")){
                return new Ast.Expr.Literal(tokens.get(0).literal().replace("\\f", "\f").replace("\"", ""));
            } else{ //if there are no escape characters, return the string as is without quotes
                return new Ast.Expr.Literal(tokens.get(0).literal().substring(1, tokens.get(0).literal().length() - 1));
            }

        }

        return null;
    }

    private Ast.Expr.Group parseGroupExpr() throws ParseException {
        return new Ast.Expr.Group(new Ast.Expr.Variable(tokens.get(1).literal()));
    }

    private Ast.Expr.ObjectExpr parseObjectExpr() throws ParseException {
        if(!tokens.match("DO")){ //runs if the DO in OBJECT DO is missing
            throw new ParseException("Missing DO");

        } else if(tokens.peek("LET")){ //checks to see if it is a LET statement
            return new Ast.Expr.ObjectExpr(Optional.empty(),List.of(parseLetStmt()), List.of());

        } else if(tokens.peek("DEF")){ //checks to see if it is a DEF statement
            return new Ast.Expr.ObjectExpr(Optional.empty(),List.of(), List.of(parseDefStmt()));

        } else{ //if any of these do not apply, then it is not a correct usage of Object
            throw new ParseException("Not a valid use of Object");
        }
    }

    private Ast.Expr parseVariableOrFunctionExpr() throws ParseException {
        if(!tokens.has(1)){ //if there is nothing after this, then it is a variable
            return new Ast.Expr.Variable(tokens.get(-1).literal());
        } else if (tokens.get(1).literal().equals(";") || tokens.get(1).literal().equals("=")) { //coming from expression
            return new Ast.Expr.Variable(tokens.get(0).literal());
        } else if(tokens.match("(")){ //if there is a parenthesis directly after, it is a function
            if(tokens.peek(Token.Type.IDENTIFIER)){ //if the function has parameters
                return new Ast.Expr.Function(tokens.get(-2).literal(), List.of(new Ast.Expr.Variable(tokens.get(0).literal())));
            } else{ //if the function has no parameters
                return new Ast.Expr.Function(tokens.get(-2).literal(), List.of());
            }
        } else { //else it has a dot in it, so we will use secondary expression to determine if it is a property or a method
            return parseSecondaryExpr();
        }

    }

    private static final class TokenStream {

        private final List<Token> tokens;
        private int index = 0;

        private TokenStream(List<Token> tokens) {
            this.tokens = tokens;
        }

        /**
         * Returns true if there is a token at (index + offset).
         */
        public boolean has(int offset) {
            return index + offset < tokens.size();
        }

        /**
         * Returns the token at (index + offset).
         */
        public Token get(int offset) {
            checkState(has(offset));
            return tokens.get(index + offset);
        }

        /**
         * Returns true if the next characters match their corresponding
         * pattern. Each pattern is either a {@link Token.Type}, matching tokens
         * of that type, or a {@link String}, matching tokens with that literal.
         * In effect, {@code new Token(Token.Type.IDENTIFIER, "literal")} is
         * matched by both {@code peek(Token.Type.IDENTIFIER)} and
         * {@code peek("literal")}.
         */
        public boolean peek(Object... patterns) {
            if (!has(patterns.length - 1)) {
                return false;
            }
            for (int offset = 0; offset < patterns.length; offset++) {
                var token = tokens.get(index + offset);
                var pattern = patterns[offset];
                checkState(pattern instanceof Token.Type || pattern instanceof String, pattern);
                if (!token.type().equals(pattern) && !token.literal().equals(pattern)) {
                    return false;
                }
            }
            return true;
        }

        /**
         * Equivalent to peek, but also advances the token stream.
         */
        public boolean match(Object... patterns) {
            var peek = peek(patterns);
            if (peek) {
                index += patterns.length;
            }
            return peek;
        }

    }

}
