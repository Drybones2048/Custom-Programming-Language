package plc.project.evaluator;

import plc.project.parser.Ast;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class Evaluator implements Ast.Visitor<RuntimeValue, EvaluateException> {

    private Scope scope;

    public Evaluator(Scope scope) {
        this.scope = scope;
    }

    @Override
    public RuntimeValue visit(Ast.Source ast) throws EvaluateException {
        RuntimeValue value = new RuntimeValue.Primitive(null);

        try{
            for (var stmt : ast.statements()) {
                value = visit(stmt);
            }
        } catch (UnsupportedOperationException e){ //if RETURN is called outside of a function
            return value;
        }

        return value;
    }

    @Override
    public RuntimeValue visit(Ast.Stmt.Let ast) throws EvaluateException {
        if (scope.get(ast.name(), true).isPresent()) { // Check if name is already defined in current scope
            throw new EvaluateException("Variable '" + ast.name() + "' is already defined in current scope");
        }

        // Define the variable with initial value (or NIL if no value present)
        RuntimeValue value = ast.value().isPresent() ? visit(ast.value().get()) : new RuntimeValue.Primitive(null);
        scope.define(ast.name(), value);

        return value; // Return the value for REPL/testing
    }

    @Override
    public RuntimeValue visit(Ast.Stmt.Def ast) throws EvaluateException {
        if (scope.get(ast.name(), true).isPresent()) { // Ensure name is not already defined in current scope
            throw new EvaluateException("Function '" + ast.name() + "' is already defined in current scope");
        }

        // Gets a list of parameters and ensures parameter names are unique
        List<String> params = ast.parameters();
        for (int i = 0; i < params.size(); i++) {
            for (int j = i + 1; j < params.size(); j++) {
                if (params.get(i).equals(params.get(j))) {
                    throw new EvaluateException("Duplicate parameter name: " + params.get(i));
                }
            }
        }

        // Capture the current scope for static scoping of the function
        Scope capturedScope = this.scope;

        // Create function definition
        RuntimeValue.Function function = new RuntimeValue.Function(ast.name(), arguments -> {
            // Verify argument count
            if (arguments.size() != ast.body().size()) {
                throw new EvaluateException("Expected " + ast.parameters().size() +
                        " arguments but got " + arguments.size());
            }

            // Store original scope for restoration
            Scope originalScope = this.scope;

            try {
                // Create new scope as child of captured scope (static scoping)
                this.scope = new Scope(capturedScope);

                // Define parameters with argument values
                for (int i = 0; i < ast.parameters().size(); i++) {
                    scope.define(ast.parameters().get(i), arguments.get(i));
                }

                // Evaluate function body
                RuntimeValue result = new RuntimeValue.Primitive(null); // Default return value is NIL
                try {
                    for (Ast.Stmt stmt : ast.body()) {
                        result = visit(stmt);
                    }
                    return result;
                } catch (EvaluateException e) {
                    // Handle RETURN inside the function
                    return result;
                }
            } finally {
                // Restore original scope, even if an exception occurred
                this.scope = originalScope;
            }
        });

        // Define the function in the current scope
        scope.define(ast.name(), function);

        // Return the function value for REPL/testing
        return function;
    }

    @Override
    public RuntimeValue visit(Ast.Stmt.If ast) throws EvaluateException {
        Boolean condition = requireType(visit(ast.condition()), Boolean.class); // Evaluate condition and ensure it's a Boolean

        // Create a new scope
        Scope originalScope = this.scope;
        this.scope = new Scope(originalScope);

        try { //Try catch statement will act as an abstraction from the original scope
            RuntimeValue result = new RuntimeValue.Primitive(null); // Default to NIL

            // Recreate execution order of an if statement and execute appropriate branch based on condition
            if (condition) {
                for (Ast.Stmt stmt : ast.thenBody()) {
                    result = visit(stmt);
                }
            } else {
                for (Ast.Stmt stmt : ast.elseBody()) {
                    result = visit(stmt);
                }
            }

            return result;

        } finally { // Restore original scope when finished, even if an exception occurred
            this.scope = originalScope;
        }
    }

    @Override
    public RuntimeValue visit(Ast.Stmt.For ast) throws EvaluateException {
        Object iterable = requireType(visit(ast.expression()), Object.class); // Evaluate expression and ensure it's iterable

        if (!(iterable instanceof Iterable<?>)) {
            throw new EvaluateException("Expected Iterable, but got " + iterable.getClass());
        }

        // Iterate through elements
        for (Object element : (Iterable<?>) iterable) {
            if (!(element instanceof RuntimeValue)) {
                throw new EvaluateException("Expected RuntimeValue in iterable, but got " + element.getClass());
            }

            // Create new scope for each iteration
            Scope originalScope = this.scope;
            this.scope = new Scope(originalScope);

            try {
                // Define variable with element value
                scope.define(ast.name(), (RuntimeValue) element);

                // Execute body
                for (Ast.Stmt stmt : ast.body()) {
                    visit(stmt);
                }
            } finally {
                // Restore original scope
                this.scope = originalScope;
            }
        }

        // Return NIL
        return new RuntimeValue.Primitive(null);
    }

    @Override
    public RuntimeValue visit(Ast.Stmt.Return ast) throws EvaluateException {
        // Evaluate the return value (or use NIL if not present)
        RuntimeValue value = ast.value().isPresent() ? visit(ast.value().get()) : new RuntimeValue.Primitive(null);

        // Throw exception to change control flow
        throw new EvaluateException("" + value);
    }

    @Override
    public RuntimeValue visit(Ast.Stmt.Expression ast) throws EvaluateException {
        return visit(ast.expression());
    }

    @Override
    public RuntimeValue visit(Ast.Stmt.Assignment ast) throws EvaluateException {
        RuntimeValue value = visit(ast.value()); // Evaluate right-hand side value

        // Switch statement to handle different types of expressions that can be assigned
        switch (ast.expression()) {
            case Ast.Expr.Variable variable -> { //If the expression is a variable
                if (scope.get(variable.name(), false).isEmpty()) { // Check if variable is not defined
                    throw new EvaluateException("Variable '" + variable.name() + "' is not defined");
                }

                // Set the variable value
                scope.set(variable.name(), value);
                return value;
            }
            case Ast.Expr.Property property -> { //If the expression is a property
                RuntimeValue.ObjectValue receiver = requireType(visit(property.receiver()), RuntimeValue.ObjectValue.class); // Evaluate receiver and ensure it's an object

                if (receiver.scope().get(property.name(), true).isEmpty()) { // Check if property is already defined
                    throw new EvaluateException("Property '" + property.name() + "' is not defined on the object");
                }

                // Set the property value
                receiver.scope().set(property.name(), value);
                return value;
            }
            //If the expression is neither a variable nor a property, throw an error
            default -> throw new EvaluateException("Cannot assign to " + ast.expression().getClass());
        }
    }

    @Override
    public RuntimeValue visit(Ast.Expr.Literal ast) throws EvaluateException {
        return new RuntimeValue.Primitive(ast.value());
    }

    @Override
    public RuntimeValue visit(Ast.Expr.Group ast) throws EvaluateException {
        return visit(ast.expression()); //Treat a group as an expression and use visit
    }

    @Override
    public RuntimeValue visit(Ast.Expr.Binary ast) throws EvaluateException {
        String operator = ast.operator(); // Grab the operator

        // AND/OR and their short-circuit evaluation
        if ("AND".equals(operator) || "OR".equals(operator)) {
            Boolean left = requireType(visit(ast.left()), Boolean.class); //Grabs the left side

            if ("AND".equals(operator) && !left) { // Short-circuit for AND if left is FALSE
                return new RuntimeValue.Primitive(false);
            }

            if ("OR".equals(operator) && left) { // Short-circuit for OR if left is TRUE
                return new RuntimeValue.Primitive(true);
            }

            Boolean right = requireType(visit(ast.right()), Boolean.class); // Now grab the right side

            // Return the result based on operator
            if ("AND".equals(operator)) { // Result of AND operation
                return new RuntimeValue.Primitive(left && right);
            } else { // Result of OR operation
                return new RuntimeValue.Primitive(left || right);
            }
        }

        // For all other operators, grab the operands (left to right)
        RuntimeValue leftValue = visit(ast.left());
        RuntimeValue rightValue = visit(ast.right());

        // Switch statement to handle the different operators
        switch (operator) {
            case "+" -> { // If we are doing addition
                if (leftValue instanceof RuntimeValue.Primitive leftPrim && rightValue instanceof RuntimeValue.Primitive rightPrim) { // If either operand is a String, concatenate
                    if (leftPrim.value() instanceof String || rightPrim.value() instanceof String) {
                        return new RuntimeValue.Primitive(leftPrim.value().toString() + rightPrim.value().toString());

                    } else if ((leftPrim.value() instanceof BigInteger || leftPrim.value() instanceof BigDecimal) && rightPrim.value().getClass().equals(leftPrim.value().getClass())) { // If it is integer or decimal addition and both share the same class
                        if (leftPrim.value() instanceof BigInteger) { // Addition for integers
                            BigInteger leftInt = (BigInteger) leftPrim.value();
                            BigInteger rightInt = (BigInteger) rightPrim.value();
                            return new RuntimeValue.Primitive(leftInt.add(rightInt));

                        } else { // Addition for decimals
                            BigDecimal leftDec = (BigDecimal) leftPrim.value();
                            BigDecimal rightDec = (BigDecimal) rightPrim.value();
                            return new RuntimeValue.Primitive(leftDec.add(rightDec));

                        }
                    } else { // Else we cannot do addition
                        throw new EvaluateException("Incompatible types for '+' operator: " + leftPrim.value().getClass() + " and " + rightPrim.value().getClass());

                    }
                } else { // If they are not both primitives, we cannot do addition
                    throw new EvaluateException("Expected primitives for '+' operator");

                }
            }
            case "-" -> { // If the operator has us doing subtraction
                if (leftValue instanceof RuntimeValue.Primitive leftPrim && rightValue instanceof RuntimeValue.Primitive rightPrim) { // Check if they are both primitives
                    if ((leftPrim.value() instanceof BigInteger || leftPrim.value() instanceof BigDecimal) && rightPrim.value().getClass().equals(leftPrim.value().getClass())) { // If it is integer or decimal addition and both share the same class
                        if (leftPrim.value() instanceof BigInteger) { // If integer
                            BigInteger leftInt = (BigInteger) leftPrim.value();
                            BigInteger rightInt = (BigInteger) rightPrim.value();
                            return new RuntimeValue.Primitive(leftInt.subtract(rightInt));

                        } else { // If decimal
                            BigDecimal leftDec = (BigDecimal) leftPrim.value();
                            BigDecimal rightDec = (BigDecimal) rightPrim.value();
                            return new RuntimeValue.Primitive(leftDec.subtract(rightDec));

                        }
                    } else { // If they do not share the same types, throw an error
                        throw new EvaluateException("Incompatible types for '-' operator: " + leftPrim.value().getClass() + " and " + rightPrim.value().getClass());
                    }
                } else { // If they are not primitives, throw an error
                    throw new EvaluateException("Expected primitives for '-' operator");
                }
            }
            case "*" -> { // If we are doing multiplication
                if (leftValue instanceof RuntimeValue.Primitive leftPrim && rightValue instanceof RuntimeValue.Primitive rightPrim) { // Check if they are both primitives
                    if ((leftPrim.value() instanceof BigInteger || leftPrim.value() instanceof BigDecimal) && rightPrim.value().getClass().equals(leftPrim.value().getClass())) { // If it is integer or decimal addition and both share the same class
                        if (leftPrim.value() instanceof BigInteger) { // If integer
                            BigInteger leftInt = (BigInteger) leftPrim.value();
                            BigInteger rightInt = (BigInteger) rightPrim.value();
                            return new RuntimeValue.Primitive(leftInt.multiply(rightInt));

                        } else { // If decimal
                            BigDecimal leftDec = (BigDecimal) leftPrim.value();
                            BigDecimal rightDec = (BigDecimal) rightPrim.value();
                            return new RuntimeValue.Primitive(leftDec.multiply(rightDec));

                        }
                    } else { // If they do not share the same types, throw an error
                        throw new EvaluateException("Incompatible types for '*' operator: " +
                                leftPrim.value().getClass() + " and " + rightPrim.value().getClass());

                    }
                } else { // If they are not primitives, throw an error
                    throw new EvaluateException("Expected primitives for '*' operator");

                }
            }
            case "/" -> { // If we are doing division
                if (leftValue instanceof RuntimeValue.Primitive leftPrim && rightValue instanceof RuntimeValue.Primitive rightPrim) { // Check if they are both primitives
                    if ((leftPrim.value() instanceof BigInteger || leftPrim.value() instanceof BigDecimal) && rightPrim.value().getClass().equals(leftPrim.value().getClass())) { // If it is integer or decimal addition and both share the same class
                        if (leftPrim.value() instanceof BigInteger) { // If integer
                            BigInteger leftInt = (BigInteger) leftPrim.value();
                            BigInteger rightInt = (BigInteger) rightPrim.value();

                            if (rightInt.equals(BigInteger.ZERO)) { // Check for division by zero
                                throw new EvaluateException("Division by zero");
                            }

                            // Use floor division for BigInteger
                            return new RuntimeValue.Primitive(leftInt.divide(rightInt));

                        } else { // If decimal
                            BigDecimal leftDec = (BigDecimal) leftPrim.value();
                            BigDecimal rightDec = (BigDecimal) rightPrim.value();

                            if (rightDec.compareTo(BigDecimal.ZERO) == 0) { // Check for division by zero
                                throw new EvaluateException("Division by zero");
                            }


                            return new RuntimeValue.Primitive(leftDec.divide(rightDec, RoundingMode.HALF_EVEN)); // Return while using HALF_EVEN rounding mode
                        }
                    } else { // If they do not share the same types, throw an error
                        throw new EvaluateException("Incompatible types for '/' operator: " +
                                leftPrim.value().getClass() + " and " + rightPrim.value().getClass());

                    }
                } else { // If they are not primitives, throw an error
                    throw new EvaluateException("Expected primitives for '/' operator");

                }
            }
            case "==" -> { // If we are testing equals
                if (leftValue instanceof RuntimeValue.Primitive leftPrim && rightValue instanceof RuntimeValue.Primitive rightPrim) { // Check if they are both primitives and equal
                    return new RuntimeValue.Primitive(Objects.equals(leftPrim.value(), rightPrim.value()));

                } else { // Test if the objects are the same
                    return new RuntimeValue.Primitive(Objects.equals(leftValue, rightValue));

                }
            }
            case "!=" -> { // If we are testing not equals
                if (leftValue instanceof RuntimeValue.Primitive leftPrim && rightValue instanceof RuntimeValue.Primitive rightPrim) { // Check if they are both primitives and equal
                    return new RuntimeValue.Primitive(!Objects.equals(leftPrim.value(), rightPrim.value()));

                } else { //Otherwise check to see if the objects are the same
                    return new RuntimeValue.Primitive(!Objects.equals(leftValue, rightValue));

                }
            }
            case "<", "<=", ">", ">=" -> { // If we are testing any sort of comparison
                if (leftValue instanceof RuntimeValue.Primitive leftPrim && rightValue instanceof RuntimeValue.Primitive rightPrim) { // Check if they are both primitives and equal
                    if (!(leftPrim.value() instanceof Comparable)) { // Comparison operators require comparable left side
                        throw new EvaluateException("Left operand must be Comparable for comparison operators");

                    }

                    if (!leftPrim.value().getClass().equals(rightPrim.value().getClass())) { // Checks if types match
                        throw new EvaluateException("Incompatible types for comparison: " + leftPrim.value().getClass() + " and " + rightPrim.value().getClass());

                    }

                    // Uses the comparable class and gets the values associated
                    Comparable<Object> left = (Comparable<Object>) leftPrim.value();
                    Object right = rightPrim.value();
                    int comparison = left.compareTo(right);

                    return new RuntimeValue.Primitive(switch (operator) { //returns the result of the switch case with the results from comparison
                        case "<" -> comparison < 0;
                        case "<=" -> comparison <= 0;
                        case ">" -> comparison > 0;
                        case ">=" -> comparison >= 0;
                        default -> throw new IllegalStateException("Unexpected operator: " + operator);
                    });

                } else { // If we are not given primitives to compare
                    throw new EvaluateException("Expected primitives for comparison operators");

                }
            }
            default -> throw new EvaluateException("Unknown operator: " + operator); // Not an operation that we recognize

        }
    }

    @Override
    public RuntimeValue visit(Ast.Expr.Variable ast) throws EvaluateException {
        Optional<RuntimeValue> value = scope.get(ast.name(), false); // Get the variable from the current scope

        if (value.isEmpty()) { // Check to see the variable is defined
            throw new EvaluateException("Variable '" + ast.name() + "' is not defined");

        }

        return value.get(); // Return variable value
    }

    @Override
    public RuntimeValue visit(Ast.Expr.Property ast) throws EvaluateException {
        // Evaluate receiver
        RuntimeValue receiverValue = visit(ast.receiver());

        // Ensure receiver is an Object
        if (!(receiverValue instanceof RuntimeValue.ObjectValue object)) {
            throw new EvaluateException("Expected an object, but got " + receiverValue.getClass());
        }

        // Get property from object's scope
        Optional<RuntimeValue> property = object.scope().get(ast.name(), true);

        // Ensure property is defined
        if (property.isEmpty()) {
            throw new EvaluateException("Property '" + ast.name() + "' is not defined on the object");
        }

        return property.get();
    }

    @Override
    public RuntimeValue visit(Ast.Expr.Function ast) throws EvaluateException {
        Optional<RuntimeValue> functionObj = scope.get(ast.name(), false); // Get function from scope

        if (functionObj.isEmpty()) { // Check to see if function is defined
            throw new EvaluateException("Function '" + ast.name() + "' is not defined");

        }

        if (!(functionObj.get() instanceof RuntimeValue.Function function)) { // Check to see if value is a Function
            throw new EvaluateException("'" + ast.name() + "' is not a function");

        }

        // Evaluate all arguments sequentially
        List<RuntimeValue> arguments = new ArrayList<>();
        for (Ast.Expr arg : ast.arguments()) {
            arguments.add(visit(arg));
        }

        try { // Invoke the function with arguments
            return function.definition().invoke(arguments);

        } catch (EvaluateException e) { // If there was an error in invoking the function
            throw new EvaluateException("Error invoking function '" + ast.name() + "': " + e.getMessage());

        }
    }

    @Override
    public RuntimeValue visit(Ast.Expr.Method ast) throws EvaluateException {
        // Evaluate receiver
        RuntimeValue receiverValue = visit(ast.receiver());

        // Ensure receiver is an Object
        if (!(receiverValue instanceof RuntimeValue.ObjectValue object)) {
            throw new EvaluateException("Expected an object for method call, but got " + receiverValue.getClass());
        }

        // Get method from object's scope
        Optional<RuntimeValue> methodObj = object.scope().get(ast.name(), true);

        // Ensure method is defined
        if (methodObj.isEmpty()) {
            throw new EvaluateException("Method '" + ast.name() + "' is not defined on the object");
        }

        // Ensure value is a Function
        if (!(methodObj.get() instanceof RuntimeValue.Function method)) {
            throw new EvaluateException("'" + ast.name() + "' is not a method");
        }

        // Evaluate all arguments sequentially
        List<RuntimeValue> arguments = new ArrayList<>();

        // Add receiver as first argument (this is the method receiver convention)
        arguments.add(receiverValue);

        // Add explicitly provided arguments
        for (Ast.Expr arg : ast.arguments()) {
            arguments.add(visit(arg));
        }

        // Invoke the method with receiver and arguments
        try {
            return method.definition().invoke(arguments);
        } catch (EvaluateException e) {
            throw new EvaluateException("Error invoking method '" + ast.name() + "': " + e.getMessage());
        }
    }

    @Override
    //TODO ObjectExpr needs more work to finish test cases
    public RuntimeValue visit(Ast.Expr.ObjectExpr ast) throws EvaluateException {
        // Create a new scope as child of current scope
        Scope objectScope = new Scope(this.scope);

        // Create the object with the new scope
        RuntimeValue.ObjectValue object = new RuntimeValue.ObjectValue(ast.name(), objectScope);

        // Process fields
        for (var field : ast.fields()) {
            // Ensure field name is not already defined
            if (objectScope.get(field.name(), true).isPresent()) {
                throw new EvaluateException("Field '" + field.name() + "' is already defined in object");
            }

            // Evaluate field value if present, otherwise use NIL
            RuntimeValue value = field.value().isPresent() ?
                    visit(field.value().get()) :
                    new RuntimeValue.Primitive(null);

            // Define field in object's scope
            objectScope.define(field.name(), value);
        }

        // Process methods
        for (var method : ast.methods()) {
            // Ensure method name is not already defined
            if (objectScope.get(method.name(), true).isPresent()) {
                throw new EvaluateException("Method '" + method.name() + "' is already defined in object");
            }

            // Ensure parameter names are unique
            List<String> params = method.parameters();
            for (int i = 0; i < params.size(); i++) {
                for (int j = i + 1; j < params.size(); j++) {
                    if (params.get(i).equals(params.get(j))) {
                        throw new EvaluateException("Duplicate parameter name: " + params.get(i));
                    }
                }
            }

            // Capture current scope for static scoping
            Scope capturedScope = this.scope;

            // Create method definition
            RuntimeValue.Function function = new RuntimeValue.Function(method.name(), arguments -> {
                // Verify argument count (including receiver)
                if (arguments.size() != method.parameters().size() + 1) {
                    throw new EvaluateException("Expected " + (method.parameters().size() + 1) +
                            " arguments but got " + arguments.size());
                }

                // Store original scope for restoration
                Scope originalScope = this.scope;

                try {
                    // Create new scope as child of captured scope (static scoping)
                    this.scope = new Scope(capturedScope);

                    // Define 'this' variable as the method receiver (first argument)
                    scope.define("this", arguments.get(0));

                    // Define parameters with argument values (skip first argument which is the receiver)
                    for (int i = 0; i < method.parameters().size(); i++) {
                        scope.define(method.parameters().get(i), arguments.get(i + 1));
                    }

                    // Evaluate method body
                    RuntimeValue result = new RuntimeValue.Primitive(null); // Default return value is NIL
                    try {
                        for (Ast.Stmt stmt : method.body()) {
                            result = visit(stmt);
                        }
                        return result;
                    } catch (EvaluateException e) {
                        // Handle RETURN inside the method
                        return result;
                    }
                } finally {
                    // Restore original scope, even if an exception occurred
                    this.scope = originalScope;
                }
            });

            // Define the method in object's scope
            objectScope.define(method.name(), function);
        }

        return object;
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
