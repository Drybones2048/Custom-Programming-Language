package plc.project.evaluator;

import org.checkerframework.checker.signature.qual.Identifier;
import plc.project.lexer.Token;
import plc.project.parser.Ast;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.*;

import static java.lang.Math.floor;
import static java.lang.Math.round;

public final class Evaluator implements Ast.Visitor<RuntimeValue, EvaluateException> {

    private Scope scope;

    public Evaluator(Scope scope) {
        this.scope = scope;
    }

    @Override
    public RuntimeValue visit(Ast.Source ast) throws EvaluateException {
        RuntimeValue value = new RuntimeValue.Primitive(null);
        for (var stmt : ast.statements()) {
            value = visit(stmt);
        }
        //TODO: Handle the possibility of RETURN being called outside of a function.
        return value;
    }

    @Override
    public RuntimeValue visit(Ast.Stmt.Let ast) throws EvaluateException {
        if(scope.get(ast.name(), true).isPresent()){
            throw new EvaluateException("Variable is already defined: " + ast.name());
        }

        RuntimeValue value = ast.value().isPresent() ? visit(ast.value().get()) : new RuntimeValue.Primitive(null);
        scope.define(ast.name(), value);
        return value;

        /*var allValues = scope.collect(false); //stores all the current variables

        if(allValues.get(ast.name()) == null){ //variable has not been defined yet
            if(ast.value().isEmpty()){
                scope.define(ast.name(), new RuntimeValue.Primitive(null));
            } else{
                scope.define(ast.name(), new RuntimeValue.Primitive(ast.value()));
            }
        } else{
            //var value = allValues.get(ast.name());
            if(ast.value().isEmpty()){
                scope.define(ast.name(), new RuntimeValue.Primitive(null));
            } else{
                scope.define(ast.name(), new RuntimeValue.Primitive(ast.value()));
            }
        }


        return scope.get(ast.name(), true).get();*/
    }

    @Override
    public RuntimeValue visit(Ast.Stmt.Def ast) throws EvaluateException {
        throw new UnsupportedOperationException("TODO"); //TODO
    }

    @Override
    public RuntimeValue visit(Ast.Stmt.If ast) throws EvaluateException {
        throw new UnsupportedOperationException("TODO"); //TODO
    }

    @Override
    public RuntimeValue visit(Ast.Stmt.For ast) throws EvaluateException {
        throw new UnsupportedOperationException("TODO"); //TODO
    }

    @Override
    public RuntimeValue visit(Ast.Stmt.Return ast) throws EvaluateException {
        throw new UnsupportedOperationException("TODO"); //TODO
    }

    @Override
    public RuntimeValue visit(Ast.Stmt.Expression ast) throws EvaluateException {
        return visit(ast.expression());
    }

    @Override
    public RuntimeValue visit(Ast.Stmt.Assignment ast) throws EvaluateException {
        throw new UnsupportedOperationException("TODO"); //TODO
    }

    @Override
    public RuntimeValue visit(Ast.Expr.Literal ast) throws EvaluateException {
        return new RuntimeValue.Primitive(ast.value());
    }

    @Override
    public RuntimeValue visit(Ast.Expr.Group ast) throws EvaluateException {

        String groupVal = peel(ast.expression().toString());

        return new RuntimeValue.Primitive(groupVal);
    }

    @Override
    public RuntimeValue visit(Ast.Expr.Binary ast) throws EvaluateException {
        String operator = ast.operator();
        scope.define("left", new RuntimeValue.Primitive(ast.left()));
        scope.define("right", new RuntimeValue.Primitive(ast.right()));

        //RuntimeValue.Primitive

        
        var leftValue = peel(requireType(scope.get("left", false).get(), RuntimeValue.Primitive.class).value().toString());
        var rightValue = peel(requireType(scope.get("right", false).get(), RuntimeValue.Primitive.class).value().toString());


        if (operator.equals("+")){
            if(isNumeric(leftValue).equals("int") && isNumeric(rightValue).equals("int")) { //both are integers
                int finalResult = Integer.parseInt(leftValue) + Integer.parseInt(rightValue);

                return new RuntimeValue.Primitive(new BigInteger("" + finalResult));

            } else if (isNumeric(leftValue).equals("double") && isNumeric(rightValue).equals("double")) { //both are doubles
                double finalResult = Double.parseDouble(leftValue) + Double.parseDouble(rightValue);

                return new RuntimeValue.Primitive(new BigDecimal("" + finalResult));
            } else{
                return new RuntimeValue.Primitive(leftValue + rightValue);
            }
        } else if (operator.equals("*")) {
            //TODO: Implement log functionality
            if(isNumeric(leftValue).equals("int") && isNumeric(rightValue).equals("int")) { //both are integers
                int finalResult = Integer.parseInt(leftValue) * Integer.parseInt(rightValue);

                return new RuntimeValue.Primitive(new BigInteger("" + finalResult));

            } else if (isNumeric(leftValue).equals("double") && isNumeric(rightValue).equals("double")) { //both are doubles
                double finalResult = Double.parseDouble(leftValue) * Double.parseDouble(rightValue);

                return new RuntimeValue.Primitive(new BigDecimal("" + finalResult));
            } else{
                throw new EvaluateException("Only Integers and Decimals are valid input");
            }

        } else if (operator.equals("-")) {
            if(isNumeric(leftValue).equals("int") && isNumeric(rightValue).equals("int")) { //both are integers
                int finalResult = Integer.parseInt(leftValue) - Integer.parseInt(rightValue);

                return new RuntimeValue.Primitive(new BigInteger("" + finalResult));

            } else if (isNumeric(leftValue).equals("double") && isNumeric(rightValue).equals("double")) { //both are doubles
                double finalResult = Double.parseDouble(leftValue) - Double.parseDouble(rightValue);

                return new RuntimeValue.Primitive(new BigDecimal("" + finalResult));
            } else{
                //TODO meant to be for the log exception, more testing needed here

                 //Environment.log(List.of(new RuntimeValue.Primitive(rightValue)));
                 throw new EvaluateException("test");
                //return Environment.log(List.of(new RuntimeValue.Primitive(rightValue)));

            }
        } else if (operator.equals("/")) {
            if(rightValue.equals("0")){ //handles the divide by zero problem
                throw new ArithmeticException("Cannot divide by zero");

            }  else if(isNumeric(leftValue).equals("int") && isNumeric(rightValue).equals("int")) { //both are integers
                int finalResult = Integer.parseInt(leftValue) / Integer.parseInt(rightValue); //will floor the results by default

                return new RuntimeValue.Primitive(new BigDecimal("" + finalResult));

            } else if (isNumeric(leftValue).equals("double") && isNumeric(rightValue).equals("double")) { //both are doubles
                double finalResult = Double.parseDouble(leftValue) / Double.parseDouble(rightValue);

                if(finalResult % 1 >= 0.5){ //checks if there is a remainder of 0.5

                    if((round(finalResult)) % 2 == 0){ //checks to see if the next number is even, if so, round
                        finalResult = round(finalResult);

                    } else{ //otherwise, floor the result
                        finalResult = floor(finalResult);

                    }
                }

                return new RuntimeValue.Primitive(new BigDecimal("" + finalResult));
            }
        } else if (operator.equals("==")) {
            if (Objects.equals(leftValue, rightValue)){ //uses Objects.equals to compare

                return new RuntimeValue.Primitive(true);
            } else{

                return new RuntimeValue.Primitive(false);
            }
        } else if (operator.equals("!=")) { //checks for not equal to
            if (Objects.equals(leftValue, rightValue)){ //uses Objects.equals to compare
                return new RuntimeValue.Primitive(false);

            } else{
                return new RuntimeValue.Primitive(true);

            }
        } else if (operator.equals("<")) { //checks for less than
            if(isNumeric(leftValue).equals("int") && isNumeric(rightValue).equals("int")) { //both are integers
                if(Integer.parseInt(leftValue) < Integer.parseInt(rightValue)){
                    return new RuntimeValue.Primitive(true);

                } else {
                    return new RuntimeValue.Primitive(false);

                }

            } else if (isNumeric(leftValue).equals("double") && isNumeric(rightValue).equals("double")) { //both are doubles
                if(Double.parseDouble(leftValue) < Double.parseDouble(rightValue)){
                    return new RuntimeValue.Primitive(true);

                } else {
                    return new RuntimeValue.Primitive(false);

                }
            } else{
                throw new UnsupportedOperationException("Cannot compare values of different types");
            }
        } else if (operator.equals("<=")) { //checks for less than or equal to
            if(isNumeric(leftValue).equals("int") && isNumeric(rightValue).equals("int")) { //both are integers
                if((Integer.parseInt(leftValue) <= Integer.parseInt(rightValue))){
                    return new RuntimeValue.Primitive(true);

                } else {
                    return new RuntimeValue.Primitive(false);

                }

            } else if (isNumeric(leftValue).equals("double") && isNumeric(rightValue).equals("double")) { //both are doubles
                if(Double.parseDouble(leftValue) <= Double.parseDouble(rightValue)){
                    return new RuntimeValue.Primitive(true);

                } else {
                    return new RuntimeValue.Primitive(false);

                }
            } else{
                throw new UnsupportedOperationException("Cannot compare values of different types");
            }
        } else if (operator.equals(">")) { //checks for greater than
            if(isNumeric(leftValue).equals("int") && isNumeric(rightValue).equals("int")) { //both are integers
                if((Integer.parseInt(leftValue) > Integer.parseInt(rightValue))){
                    return new RuntimeValue.Primitive(true);

                } else {
                    return new RuntimeValue.Primitive(false);

                }

            } else if (isNumeric(leftValue).equals("double") && isNumeric(rightValue).equals("double")) { //both are doubles
                if(Double.parseDouble(leftValue) > Double.parseDouble(rightValue)){
                    return new RuntimeValue.Primitive(true);

                } else {
                    return new RuntimeValue.Primitive(false);

                }
            } else{
                throw new UnsupportedOperationException("Cannot compare values of different types");
            }
        } else if (operator.equals(">=")) { //checks for greater than or equal to
            if(isNumeric(leftValue).equals("int") && isNumeric(rightValue).equals("int")) { //both are integers
                if((Integer.parseInt(leftValue) >= Integer.parseInt(rightValue))){
                    return new RuntimeValue.Primitive(true);

                } else {
                    return new RuntimeValue.Primitive(false);

                }

            } else if (isNumeric(leftValue).equals("double") && isNumeric(rightValue).equals("double")) { //both are doubles
                if(Double.parseDouble(leftValue) >= Double.parseDouble(rightValue)){
                    return new RuntimeValue.Primitive(true);

                } else {
                    return new RuntimeValue.Primitive(false);

                }
            } else{
                throw new UnsupportedOperationException("Cannot compare values of different types");
            }
        } else if (operator.equals("AND")) {
            //if any of the values are not boolean values, throw an exception
            if(!(leftValue.equals("true") || leftValue.equals("false")) || !(rightValue.equals("true") || rightValue.equals("false"))){
                throw new UnsupportedOperationException("Cannot have an AND expression without a boolean value");
            }

            if(leftValue.equals("true") && rightValue.equals("true")){ //makes sure both values are true
                return new RuntimeValue.Primitive(true);

            } else{ //or it fails
                return new RuntimeValue.Primitive(false);

            }
        } else if (operator.equals("OR")) {
            //if any of the values are not boolean values, throw an exception
            if(!(leftValue.equals("true") || leftValue.equals("false")) || !(rightValue.equals("true") || rightValue.equals("false"))){
                throw new UnsupportedOperationException("Cannot have an OR expression without a boolean value");
            }

            if(leftValue.equals("true") && rightValue.equals("true")){ //makes sure both values are true
                return new RuntimeValue.Primitive(true);

            } else if(leftValue.equals("true") && rightValue.equals("false")){ //makes sure both values are true
                return new RuntimeValue.Primitive(true);

            } else if(leftValue.equals("false") && rightValue.equals("true")){ //makes sure both values are true
                return new RuntimeValue.Primitive(true);

            }else{ //or it fails
                return new RuntimeValue.Primitive(false);

            }
        }


        throw new UnsupportedOperationException("TODO"); //TODO
    }

    @Override
    public RuntimeValue visit(Ast.Expr.Variable ast) throws EvaluateException {
        if(scope.get(ast.name(), true).equals(Optional.empty())){ //already been defined
            return new RuntimeValue.Primitive(peel(scope.get(ast.name(), false).get().toString()));
        } else{ //will define it then return its value
            scope.define(ast.name(), new RuntimeValue.Primitive(ast.name()));
            return new RuntimeValue.Primitive(peel(scope.get(ast.name(), false).get().toString()));
        }
    }

    @Override
    public RuntimeValue visit(Ast.Expr.Property ast) throws EvaluateException {
        throw new UnsupportedOperationException("TODO"); //TODO
    }

    @Override
    public RuntimeValue visit(Ast.Expr.Function ast) throws EvaluateException {
        /*try{ //tries to get the variable if it already exists
            scope.get(ast.name(), false).get();

        } catch (NoSuchElementException e){ //if the variable does not exist, define it
            scope.define(ast.name(), new RuntimeValue.Primitive(ast.arguments()));
        }*/

        var allValues = scope.collect(false);

        if(allValues.get(ast.name()) == null){
            //scope.define(ast.name(), new RuntimeValue.Function(ast.name(), new RuntimeValue.Function.Definition(ast.arguments())));
        }

        var value = scope.get(ast.name(), false).get(); //gets the value of the variable
        var function = requireType(value, RuntimeValue.Function.class); //gets the runtime value of the function

        if(ast.arguments().isEmpty()){ //if there are no arguments, return it as a primitive with no arguments
            return new RuntimeValue.Primitive(List.of());

        } else { //if there are arguments, go in and return them
            var argument = ast.arguments().getFirst();

            var evaluated = visit(argument);

            return function.definition().invoke(List.of(evaluated));
        }
    }

    @Override
    public RuntimeValue visit(Ast.Expr.Method ast) throws EvaluateException {
        throw new UnsupportedOperationException("TODO"); //TODO
    }

    @Override
    public RuntimeValue visit(Ast.Expr.ObjectExpr ast) throws EvaluateException {
        throw new UnsupportedOperationException("TODO"); //TODO
    }

    public String peel(String wholeExpr){
        String actual = "";

        for(int i = wholeExpr.indexOf('=') + 1; i < wholeExpr.length(); i++){
            if(wholeExpr.charAt(i) == ']')
                break;

            actual += wholeExpr.charAt(i);

            if(wholeExpr.charAt(i) == '='){
                return peel(wholeExpr.substring(actual.length() - 1, wholeExpr.length() - 1));
            }

        }
        return actual;
    }

    public String isNumeric(String str){
        try{
            Integer.parseInt(str);
            return "int";
        } catch(NumberFormatException e){
            try{
                Double.parseDouble(str);
                return "double";
            } catch (NumberFormatException f){
                return "string";
            }
        }
    }



    /**
     * Helper function for extracting RuntimeValues of specific types. If the
     * type is subclass of {@link RuntimeValue} the check applies to the value
     * itself, otherwise the value is expected to be a {@link RuntimeValue.Primitive}
     * and the check applies to the primitive value.
     */
    private static <T> T requireType(RuntimeValue value, Class<T> type) throws EvaluateException {
        //To be discussed in lecture 3/5.
        if (RuntimeValue.class.isAssignableFrom(type)) {
            if (!type.isInstance(value)) {
                throw new EvaluateException("Expected value to be of type " + type + ", received " + value.getClass() + ".");
            }
            return (T) value;
        } else {
            var primitive = requireType(value, RuntimeValue.Primitive.class);
            if (!type.isInstance(primitive.value())) {
                var received = primitive.value() != null ? primitive.value().getClass() : null;
                throw new EvaluateException("Expected value to be of type " + type + ", received " + received + ".");
            }
            return (T) primitive.value();
        }
    }

}
