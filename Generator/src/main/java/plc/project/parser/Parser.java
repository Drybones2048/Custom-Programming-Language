package plc.project.parser;

import plc.project.lexer.Token;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
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
        List<Ast.Stmt> results = new ArrayList<>(); //list to store the results

        while(tokens.has(0)){ //goes until there are no statements left
            results.add(parseStmt());

        }
        return new Ast.Source(results); //returns the results
    }

    public Ast.Stmt parseStmt() throws ParseException {
        if(tokens.match("LET")){ //checks for let statement
            return parseLetStmt();
        } else if(tokens.match("DEF")){ //checks for def statement
            return parseDefStmt();
        } else if(tokens.match("IF")){ //checks for if statement
            return parseIfStmt();
        } else if(tokens.match("FOR")){ //checks for for statement
            return parseForStmt();
        } else if(tokens.match("RETURN")){ //checks for return statement
            return parseReturnStmt();
        } else{ //if it is not any of the above, then it is an expression or an assignment operation
            return parseExpressionOrAssignmentStmt();
        }
    }

    private Ast.Stmt.Let parseLetStmt() throws ParseException {
        if (!tokens.peek(Token.Type.IDENTIFIER)) {
            throw new ParseException("Missing variable name in LET statement");
        }

        String name = tokens.get(0).literal();
        tokens.index++;

        Optional<Ast.Expr> value = Optional.empty();

        if (tokens.match("=")) {
            value = Optional.of(parseExpr());
        }

        if (!tokens.match(";")) {
            throw new ParseException("Missing semicolon in LET statement");
        }

        return new Ast.Stmt.Let(name, value);
    }

    private Ast.Stmt.Def parseDefStmt() throws ParseException {
        if (!tokens.peek(Token.Type.IDENTIFIER)) {
            throw new ParseException("Missing function name in DEF statement");
        }

        String name = tokens.get(0).literal();
        tokens.index++;

        if (!tokens.match("(")) {
            throw new ParseException("Missing opening parenthesis in DEF statement");
        }

        List<String> parameters = new ArrayList<>();

        if (tokens.peek(Token.Type.IDENTIFIER)) {
            parameters.add(tokens.get(0).literal());
            tokens.index++;

            while (tokens.match(",")) {
                if (!tokens.peek(Token.Type.IDENTIFIER)) {
                    throw new ParseException("Missing parameter name after comma in DEF statement");
                }
                parameters.add(tokens.get(0).literal());
                tokens.index++;
            }
        }

        if (!tokens.match(")")) {
            throw new ParseException("Missing closing parenthesis in DEF statement");
        }

        if (!tokens.match("DO")) {
            throw new ParseException("Missing DO in DEF statement");
        }

        List<Ast.Stmt> body = new ArrayList<>();
        while (tokens.has(0) && !tokens.peek("END")) {
            body.add(parseStmt());
        }

        if (!tokens.match("END")) {
            throw new ParseException("Missing END in DEF statement");
        }

        return new Ast.Stmt.Def(name, parameters, body);
    }

    private Ast.Stmt.If parseIfStmt() throws ParseException {
        Ast.Expr condition = parseExpr();

        if (!tokens.match("DO")) {
            throw new ParseException("Missing DO in IF statement");
        }

        List<Ast.Stmt> thenBody = new ArrayList<>();
        while (tokens.has(0) && !tokens.peek("END") && !tokens.peek("ELSE")) {
            thenBody.add(parseStmt());
        }

        List<Ast.Stmt> elseBody = new ArrayList<>();
        if (tokens.match("ELSE")) {
            while (tokens.has(0) && !tokens.peek("END")) {
                elseBody.add(parseStmt());
            }
        }

        if (!tokens.match("END")) {
            throw new ParseException("Missing END in IF statement");
        }

        return new Ast.Stmt.If(condition, thenBody, elseBody);

    }

    private Ast.Stmt.For parseForStmt() throws ParseException {
        if (!tokens.peek(Token.Type.IDENTIFIER)) {
            throw new ParseException("Missing variable name in FOR statement");
        }

        String name = tokens.get(0).literal();
        tokens.index++;

        if (!tokens.match("IN")) {
            throw new ParseException("Missing IN in FOR statement");
        }

        Ast.Expr expression = parseExpr();

        if (!tokens.match("DO")) {
            throw new ParseException("Missing DO in FOR statement");
        }

        List<Ast.Stmt> body = new ArrayList<>();
        while (tokens.has(0) && !tokens.peek("END")) {
            body.add(parseStmt());
        }

        if (!tokens.match("END")) {
            throw new ParseException("Missing END in FOR statement");
        }

        return new Ast.Stmt.For(name, expression, body);
    }

    private Ast.Stmt.Return parseReturnStmt() throws ParseException {
        Optional<Ast.Expr> value = Optional.empty();

        if (!tokens.peek(";")) {
            value = Optional.of(parseExpr());
        }

        if (!tokens.match(";")) {
            throw new ParseException("Missing semicolon in RETURN statement");
        }

        return new Ast.Stmt.Return(value);
    }

    private Ast.Stmt parseExpressionOrAssignmentStmt() throws ParseException {
        var left = parseExpr(); //captures the left

        if(tokens.match("=")){ //if it is an assignment
            var right = parseExpr(); //parse the right

            if (!tokens.match(";")) {
                throw new ParseException("Expecting semicolon");
            }

            return new Ast.Stmt.Assignment(left, right); //return the assignment statement
        }
        if (!tokens.match(";")) {
            throw new ParseException("Expecting semicolon");
        }

        return new Ast.Stmt.Expression(left); //return the expression alone
    }

    public Ast.Expr parseExpr() throws ParseException {
        return parseLogicalExpr(); //start the call chain with logical expression
    }

    private Ast.Expr parseLogicalExpr() throws ParseException {
        var left = parseComparisonExpr(); //calls comparison to check

        while(tokens.peek("AND") || tokens.peek("OR")){ //if there is an AND or an OR present
            String operator = tokens.get(0).literal(); //capture the operator
            tokens.index++;

            var right = parseComparisonExpr(); //capture the right
            left = new Ast.Expr.Binary(operator, left, right); //reassign left
        }
        return left; //return full
    }

    private Ast.Expr parseComparisonExpr() throws ParseException {
        var left = parseAdditiveExpr(); //continue chain

        //checks for comparisons
        while (tokens.peek("<") || tokens.peek("<=") || tokens.peek(">") || tokens.peek(">=") || tokens.peek("==") || tokens.peek("!=")){
            String operator = tokens.get(0).literal(); //catch the operator
            tokens.index++;

            var right = parseAdditiveExpr(); //gets the right side of the comparison
            left = new Ast.Expr.Binary(operator, left, right); //fills expression
        }

        return left; //return
    }

    private Ast.Expr parseAdditiveExpr() throws ParseException {
        var left = parseMultiplicativeExpr(); //continue chain

        while(tokens.peek("+") || tokens.peek("-")){ //checks for addition or subtraction
            String operator = tokens.get(0).literal(); //capture symbol
            tokens.index++;

            var right = parseMultiplicativeExpr(); //parse the other side
            left = new Ast.Expr.Binary(operator, left, right); //fix left
        }
        return left; //return full value
    }

    private Ast.Expr parseMultiplicativeExpr() throws ParseException {
        var left = parseSecondaryExpr(); //continue chain

        while(tokens.peek("*") || tokens.peek("/")){ //checks for mult or division
            String operator = tokens.get(0).literal(); //catches symbol
            tokens.index++;

            var right = parseSecondaryExpr(); //parses the other side
            left = new Ast.Expr.Binary(operator, left, right); //fills left
        }
        return left; //return full
    }

    private Ast.Expr parseSecondaryExpr() throws ParseException {
        Ast.Expr expr = parsePrimaryExpr();

        while (tokens.match(".")) {
            if (!tokens.peek(Token.Type.IDENTIFIER)) {
                throw new ParseException("Missing identifier after dot in property/method access");
            }

            String name = tokens.get(0).literal();
            tokens.index++;

            if (tokens.match("(")) {
                List<Ast.Expr> arguments = new ArrayList<>();

                if (!tokens.peek(")")) {
                    arguments.add(parseExpr());

                    while (tokens.match(",")) {
                        arguments.add(parseExpr());
                    }
                }

                if (!tokens.match(")")) {
                    throw new ParseException("Missing closing parenthesis in method call");
                }

                expr = new Ast.Expr.Method(expr, name, arguments);
            } else {
                expr = new Ast.Expr.Property(expr, name);
            }
        }

        return expr;

    }

    private Ast.Expr parsePrimaryExpr() throws ParseException {
        if(tokens.match("NIL")){ //if there is null
            return new Ast.Expr.Literal(null);

        } else if (tokens.match("TRUE")) { //if there is a true
            return new Ast.Expr.Literal(true);

        } else if (tokens.match("FALSE")) { //if there is a false
            return new Ast.Expr.Literal(false);

        //if there is an integer, character, or string, then it is a literal
        } else if ((tokens.peek(Token.Type.INTEGER) || tokens.peek(Token.Type.DECIMAL)) || tokens.peek(Token.Type.CHARACTER)|| tokens.peek(Token.Type.STRING)) {
            return parseLiteralExpr();

        } else if (tokens.peek("(")) { //if there is a parenthesis, then it is a group
            return parseGroupExpr();

        } else if (tokens.match("OBJECT")) { //if it says it is an object, then it is a statement and should be redirected
            return parseObjectExpr();

        } else if (tokens.peek(Token.Type.IDENTIFIER)) { //if it is an identifier, then it is redirected
            return parseVariableOrFunctionExpr();

        } else {
            throw new ParseException("Expected expression, found: " +
                    (tokens.has(0) ? tokens.get(0).literal() : "end of input"));
        }

    }

    private Ast.Expr.Literal parseLiteralExpr() throws ParseException {
        Token token = tokens.get(0);
        tokens.index++;

        if (token.type() == Token.Type.INTEGER) {
            String literal = token.literal();
            try {
                if (literal.toLowerCase().contains("e")) {
                    return new Ast.Expr.Literal(new BigDecimal(literal).toBigInteger());
                } else {
                    return new Ast.Expr.Literal(new BigInteger(literal));
                }
            } catch (NumberFormatException e) {
                throw new ParseException("Invalid integer format: " + literal);
            }
        } else if (token.type() == Token.Type.DECIMAL) {
            try {
                return new Ast.Expr.Literal(new BigDecimal(token.literal()));
            } catch (NumberFormatException e) {
                throw new ParseException("Invalid decimal format: " + token.literal());
            }
        } else if (token.type() == Token.Type.CHARACTER) {
            String literal = token.literal();
            // Remove surrounding quotes
            literal = literal.substring(1, literal.length() - 1);
            // Process escape sequences
            if (literal.startsWith("\\")) {
                char escape = literal.charAt(1);
                switch (escape) {
                    case 'b': return new Ast.Expr.Literal('\b');
                    case 'n': return new Ast.Expr.Literal('\n');
                    case 'r': return new Ast.Expr.Literal('\r');
                    case 't': return new Ast.Expr.Literal('\t');
                    case '\'': return new Ast.Expr.Literal('\'');
                    case '"': return new Ast.Expr.Literal('"');
                    case '\\': return new Ast.Expr.Literal('\\');
                    default: throw new ParseException("Invalid escape sequence: \\" + escape);
                }
            } else {
                return new Ast.Expr.Literal(literal.charAt(0));
            }
        } else if (token.type() == Token.Type.STRING) {
            String literal = token.literal();
            // Remove surrounding quotes
            literal = literal.substring(1, literal.length() - 1);
            // Process escape sequences
            literal = literal.replace("\\b", "\b")
                    .replace("\\n", "\n")
                    .replace("\\r", "\r")
                    .replace("\\t", "\t")
                    .replace("\\'", "'")
                    .replace("\\\"", "\"")
                    .replace("\\\\", "\\");
            return new Ast.Expr.Literal(literal);
        } else {
            throw new ParseException("Expected literal, found: " + token.literal());
        }
    }

    private Ast.Expr.Group parseGroupExpr() throws ParseException {
        if(!tokens.match("(")){ //opening parenthesis check
            throw new ParseException("Missing opening parenthesis for group");
        }
        var expr = parseExpr(); //parses

        if(!tokens.match(")")){ //closing parenthesis check
            throw new ParseException("No closing parenthesis for group");
        }

        return new Ast.Expr.Group(expr); //return group
    }

    // Might have to come back to fix this later
    private Ast.Expr.ObjectExpr parseObjectExpr() throws ParseException {
        tokens.match("OBJECT"); // Consume the OBJECT token

        Optional<String> name = Optional.empty();
        if (tokens.peek(Token.Type.IDENTIFIER)) {

            if(tokens.get(0).literal().equals("DO")){
                name = Optional.empty();

            } else{
                name = Optional.of(tokens.get(0).literal());
                //tokens.index++
            }
        }

        if (!tokens.match("DO")) {
            throw new ParseException("Missing DO in object expression");
        }

        List<Ast.Stmt.Let> fields = new ArrayList<>();
        List<Ast.Stmt.Def> methods = new ArrayList<>();

        while (tokens.has(0) && !tokens.peek("END")) {
            if (tokens.peek("LET")) {
                tokens.match("LET"); // Consume LET token
                Ast.Stmt.Let field = parseLetStmt();
                fields.add(field);
            } else if (tokens.peek("DEF")) {
                tokens.match("DEF"); // Consume DEF token
                Ast.Stmt.Def method = parseDefStmt();
                methods.add(method);
            } else {
                throw new ParseException("Expected LET or DEF in object expression, found: " +
                        tokens.get(0).literal());
            }
        }

        if (!tokens.match("END")) {
            throw new ParseException("Missing END in object expression");
        }

        return new Ast.Expr.ObjectExpr(name, fields, methods);

    }

    private Ast.Expr parseVariableOrFunctionExpr() throws ParseException {
        if(!tokens.peek(Token.Type.IDENTIFIER)){ //if not an identifier, then it is not a function or variable
            throw new ParseException("Expected identifier");
        }
        String name = tokens.get(0).literal(); //gets the name and indexes
        tokens.index++;

        if(tokens.match("(")){ //checks for parenthesis
            List<Ast.Expr> arguments = new ArrayList<>(); //for arguments

            if(!tokens.peek(")")){ //gets all arguments
                arguments.add(parseExpr());

                while(tokens.match(",")){
                    arguments.add(parseExpr());
                }
            }
            if(!tokens.match(")")){ //closing parenthesis check
                throw new ParseException("Expected closing parenthesis after function call");
            }
            return new Ast.Expr.Function(name, arguments); //return with arguments
        }
        return new Ast.Expr.Variable(name); //return without arguments

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
