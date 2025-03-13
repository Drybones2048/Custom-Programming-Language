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
        String name = tokens.get(0).literal(); //gets the name and indexes
        tokens.index++;
        
        if(tokens.match(";")){ //if it is a let statement without further defintion, return empty
            return new Ast.Stmt.Let(name, Optional.empty());

        } else if (tokens.match("=")) { //if there is an equals, parse the rest
            if(tokens.has(1) && tokens.peek(Token.Type.IDENTIFIER)){ //gets the variable name
                String variable = tokens.get(0).literal();
                tokens.index++;

                if(tokens.match(";")){ //gets the semicolon
                    return new Ast.Stmt.Let(name, Optional.of(new Ast.Expr.Variable(variable)));
                } else {
                    throw new ParseException("Missing LET Statement Semicolon");
                }

            } else{ //missing variable name
                throw new ParseException("Invalid LET Statement");
            }

        }
        return null;
    }

    private Ast.Stmt.Def parseDefStmt() throws ParseException {
        String name = tokens.get(0).literal(); //get name and index
        tokens.index++;

        if(tokens.match("(")){ //captures parenthesis
            List<String> arguments = new ArrayList<>();

            if(!tokens.peek(")")){ //if there is not immediatly a closing parenthesis, capture all of the arguments in a list
                arguments.add(tokens.get(0).literal());
                tokens.index++;

                while(tokens.match(",")){
                    arguments.add(tokens.get(0).literal());
                    tokens.index++;
                }
            }
            if(!tokens.match(")")){
                throw new ParseException("Expected closing parenthesis for DEF Statement");
            }
            if(!tokens.match("DO")){
                throw new ParseException("Expected DO for DEF Statement");
            }
            if(!tokens.match("END")){
                throw new ParseException("Expected END for DEF Statement");
            }
            return new Ast.Stmt.Def(name, arguments, List.of()); //return results with or without arguments

        } else{
            throw new ParseException("Invalid DEF Statement");
        }
    }

    private Ast.Stmt.If parseIfStmt() throws ParseException {
        var ifCond = parseExpr(); //captures the condition using parseExpr

        if(!tokens.match("DO")){ //
            throw new ParseException("Missing DO in IF Statement");
        }
        List<Ast.Stmt> ifArguments = new ArrayList<>(); //create a list for the conditions

        if(!tokens.peek(";")){ //grab the semicolon
            ifArguments.add(parseStmt());

        } else{
            throw new ParseException("Missing DO result in IF Statement");
        }

        if(tokens.get(0).literal().equals("END")){ //check to see if it is END instead of ELSE, and if so, end
            return new Ast.Stmt.If(ifCond, ifArguments, List.of());
        }

        if(!tokens.match("ELSE")){ //if it has an else
            throw new ParseException("Missing Else");
        }

        List<Ast.Stmt> elseArguments = new ArrayList<>(); //array for else arguments

        if(!tokens.peek(";")){
            elseArguments.add(parseStmt());

        } else{
            throw new ParseException("Missing result in ELSE Statement");
        }

        if(!tokens.match("END")){
            throw new ParseException("Missing END in IF Statement");
        }

        return new Ast.Stmt.If(ifCond, ifArguments, elseArguments); //return

    }

    private Ast.Stmt.For parseForStmt() throws ParseException {
        String name = tokens.get(0).literal(); //grab the name and index
        tokens.index++;

        if(!tokens.match("IN")){
            throw new ParseException("Missing IN in FOR statement");
        }

        var expr = parseExpr(); //grabs the name

        if(!tokens.match("DO")){
            throw new ParseException("Missing DO in FOR statement");
        }

        var stmt = parseStmt(); //parse the statement/expression

        if(!tokens.match("END")){
            throw new ParseException("Missing END in FOR statement");
        }

        return new Ast.Stmt.For(name, expr, List.of(stmt)); //return successful FOR
    }

    private Ast.Stmt.Return parseReturnStmt() throws ParseException {
        if(!tokens.get(0).literal().equals(";")){ //if it is not just a semicolon and you are returning something of value
            return new Ast.Stmt.Return(Optional.of(parseExpr()));

        } else { //empty return
            tokens.index++;

            return new Ast.Stmt.Return(Optional.empty());
        }
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
        var expr = parsePrimaryExpr(); //continue chain

        while(tokens.match(".")){
            if(!tokens.peek(Token.Type.IDENTIFIER)){ //the next token should be a word that is a property or method
                throw new ParseException("No Identfiier after .");
            }
            String name = tokens.get(0).literal(); //grabs the name and idexes
            tokens.index++;
            List<Ast.Expr> arguments = new ArrayList<>(); //will create an arraylist to store the arguments of the method

            if(tokens.match("(")){ //checks for opening parenthesis

                if(!tokens.peek(")")){ //makes sure that closing parenthesis are not right after opening parenthesis
                    arguments.add(parseExpr());

                    while(tokens.match(",")){ //will go through all of the arguments and add them to the arraylist
                        arguments.add(parseExpr());
                    }

                }
                if(!tokens.match(")")){ //if there is no closing parenthesis
                    throw new ParseException("Expected ')' after arguments");
                }
                expr = new Ast.Expr.Method(expr, name, arguments); //stores all of the information as a method

            } else{ //if no opening parenthesis, then it is a property
                expr = new Ast.Expr.Property(expr, name);
            }
        }
        return expr; //return expression

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

        }
        throw new ParseException("Unexpected token: " + tokens.get(0).literal());

    }

    private Ast.Expr.Literal parseLiteralExpr() throws ParseException {
        Token currentToken = tokens.get(0); //gets the current token information and indexes
        tokens.index++;

        String currentTokenVal = currentToken.literal(); //gets the literal
        Object value; //will create an empty object to store the literal value

        if(currentToken.type() == Token.Type.DECIMAL || currentToken.type() == Token.Type.INTEGER){ //if the literal is numerical

            if(currentTokenVal.contains(".") || currentTokenVal.toLowerCase().contains("e")){ //if there is a number that has a decimal or e, it is a decimal
                value = new BigDecimal(currentTokenVal);

            } else{ //otherwise it is an integer
                value = new BigInteger(currentTokenVal);

            }
        } else if (currentToken.type() == Token.Type.CHARACTER) { //if it is a character, get the character and store it in value
            value = currentTokenVal.charAt(1);

        } else if (currentToken.type() == Token.Type.STRING) {
            //will replace all escape characters
            if (currentTokenVal.contains("\\n")){
                value = currentTokenVal.replace("\\n", "\n").replace("\"", "");
            } else if(currentTokenVal.contains("\\r")){
                value = currentTokenVal.replace("\\r", "\r").replace("\"", "");
            } else if(currentTokenVal.contains("\\t")){
                value = currentTokenVal.replace("\\t", "\t").replace("\"", "");
            } else if(currentTokenVal.contains("\\b")){
                value = currentTokenVal.replace("\\b", "\b").replace("\"", "");
            } else if(currentTokenVal.contains("\\f")){
                value = currentTokenVal.replace("\\f", "\f").replace("\"", "");
            } else{ //if there are no escape characters, return the string as is without quotes
                value = currentTokenVal.substring(1, currentTokenVal.length() - 1);
            }
        } else {
            throw new ParseException("Invalid literal: " + currentTokenVal);
        }
        return new Ast.Expr.Literal(value); //return the value
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

    private Ast.Expr.ObjectExpr parseObjectExpr() throws ParseException {
        if(!tokens.match("DO")){ //checks for DO
            throw new ParseException("Missing DO in OBJECT declaration");
        }

        if (tokens.match("DEF")){ //checks for DEF statement and runs method
            return new Ast.Expr.ObjectExpr(Optional.empty(), List.of(), List.of(parseDefStmt()));

        } else{ //checks for LET statement and runs method
            tokens.match("LET");
            return new Ast.Expr.ObjectExpr(Optional.empty(), List.of(parseLetStmt()), List.of());
        }

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
