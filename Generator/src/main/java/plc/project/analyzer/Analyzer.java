package plc.project.analyzer;

import plc.project.parser.Ast;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class Analyzer implements Ast.Visitor<Ir, AnalyzeException> {

    private Scope scope;

    public Analyzer(Scope scope) {
        this.scope = scope;
    }

    @Override
    public Ir.Source visit(Ast.Source ast) throws AnalyzeException {
        var statements = new ArrayList<Ir.Stmt>();
        for (var statement : ast.statements()) {
            statements.add(visit(statement));
        }
        return new Ir.Source(statements);
    }

    private Ir.Stmt visit(Ast.Stmt ast) throws AnalyzeException {
        return (Ir.Stmt) visit((Ast) ast); //helper to cast visit(Ast.Stmt) to Ir.Stmt
    }

    @Override
    public Ir.Stmt.Let visit(Ast.Stmt.Let ast) throws AnalyzeException {
        // The Scope.define() method will throw an IllegalStateException if the variable
        // is already defined, so we need to catch that and throw our AnalyzeException instead

        // Define the variable with inferred type
        Type type;
        Optional<Ir.Expr> value = Optional.empty();

        if (ast.value().isPresent()) { // If value present, analyze it first to get its type
            Ir.Expr valueExpr = visit(ast.value().get());
            value = Optional.of(valueExpr);

            if (ast.type().isEmpty()) { // Use value's type if no explicit type is given
                type = valueExpr.type();

            } else { // If explicit type is given, ensure it exists in Environment.TYPES
                String typeName = ast.type().get();

                if (!Environment.TYPES.containsKey(typeName)) {
                    throw new AnalyzeException("Type " + typeName + " is not defined");

                }
                type = Environment.TYPES.get(typeName);


                requireSubtype(valueExpr.type(), type); // Check that value's type is a subtype of the variable type
            }
        } else { // No value, so use explicit type
            if (ast.type().isPresent()) {
                String typeName = ast.type().get();

                if (!Environment.TYPES.containsKey(typeName)) {
                    throw new AnalyzeException("Type " + typeName + " is not defined");

                }
                type = Environment.TYPES.get(typeName);

            } else { // Or default to Any
                type = Type.ANY;

            }
        }

        try { // Define the variable
            scope.define(ast.name(), type);

        } catch (IllegalStateException e) { // Throws IllegalStateException if already defined
            throw new AnalyzeException("Variable " + ast.name() + " is already defined in this scope");

        }

        return new Ir.Stmt.Let(ast.name(), type, value); // Return the value
    }

    @Override
    public Ir.Stmt.Def visit(Ast.Stmt.Def ast) throws AnalyzeException {
        // Determine parameter types
        var parameters = new ArrayList<Ir.Stmt.Def.Parameter>();
        var parameterNames = new ArrayList<String>(ast.parameters());

        // Check for duplicate parameter names
        for (int i = 0; i < parameterNames.size(); i++) {
            for (int j = i + 1; j < parameterNames.size(); j++) {
                if (parameterNames.get(i).equals(parameterNames.get(j))) {
                    throw new AnalyzeException("Duplicate parameter name: " + parameterNames.get(i));

                }
            }
        }

        // Determine parameter types from annotations or default to Any
        for (int i = 0; i < ast.parameters().size(); i++) {
            String paramName = ast.parameters().get(i);
            String typeName = i < ast.parameterTypes().size() && ast.parameterTypes().get(i).isPresent() ?
                    ast.parameterTypes().get(i).get() : "Any";

            if (!Environment.TYPES.containsKey(typeName)) { // If Environment does not contain the key, throw an exception
                throw new AnalyzeException("Type " + typeName + " is not defined");

            }

            Type paramType = Environment.TYPES.get(typeName);
            parameters.add(new Ir.Stmt.Def.Parameter(paramName, paramType));

        }

        // Determine return type
        Type returnType = ast.returnType().isPresent() ?
                Environment.TYPES.getOrDefault(ast.returnType().get(), Type.ANY) : Type.ANY;

        // Define the function in the current scope
        Type.Function functionType = new Type.Function(
                parameters.stream().map(Ir.Stmt.Def.Parameter::type).toList(),
                returnType
        );
        scope.define(ast.name(), functionType);

        // Create a new scope for the function body
        Scope prevScope = scope;
        scope = new Scope(scope);

        // Define parameters in the new scope
        for (var param : parameters) {
            scope.define(param.name(), param.type());

        }

        // Define $RETURNS to store return type
        scope.define("$RETURNS", returnType);

        // Analyze all body statements
        var statements = new ArrayList<Ir.Stmt>();
        for (var stmt : ast.body()) {
            statements.add(visit(stmt));

        }

        // Restore previous scope
        scope = prevScope;

        return new Ir.Stmt.Def(ast.name(), parameters, returnType, statements);
    }

    @Override
    public Ir.Stmt.If visit(Ast.Stmt.If ast) throws AnalyzeException {
        Ir.Expr condition = visit(ast.condition()); // Analyze the condition

        requireSubtype(condition.type(), Type.BOOLEAN); // Condition must be a subtype of Boolean

        // Analyze then statements in a new scope
        Scope prevScope = scope;
        scope = new Scope(scope);

        var thenStatements = new ArrayList<Ir.Stmt>();
        for (var stmt : ast.thenBody()) {
            thenStatements.add(visit(stmt));

        }

        // Restore and create another new scope for else statements
        scope = prevScope;
        scope = new Scope(scope);

        var elseStatements = new ArrayList<Ir.Stmt>();
        for (var stmt : ast.elseBody()) {
            elseStatements.add(visit(stmt));

        }

        scope = prevScope; // Restore original scope

        return new Ir.Stmt.If(condition, thenStatements, elseStatements);
    }

    @Override
    public Ir.Stmt.For visit(Ast.Stmt.For ast) throws AnalyzeException {
        // Analyze the expression, which must be a subtype of Iterable
        Ir.Expr expression = visit(ast.expression());
        requireSubtype(expression.type(), Type.ITERABLE);

        // Create new scope for loop body
        Scope prevScope = scope;
        scope = new Scope(scope);

        // Define loop variable with type Integer
        Type variableType = Type.INTEGER;
        scope.define(ast.name(), variableType);

        // Analyze body statements
        var statements = new ArrayList<Ir.Stmt>();
        for (var stmt : ast.body()) {
            statements.add(visit(stmt));

        }

        scope = prevScope; // Restore original scope

        return new Ir.Stmt.For(ast.name(), variableType, expression, statements); // Pass the loop variable type to the constructor
    }

    @Override
    public Ir.Stmt.Return visit(Ast.Stmt.Return ast) throws AnalyzeException {
        // Ensure we're returning inside a function
        var returnType = scope.get("$RETURNS", false);
        if (returnType.isEmpty()) {
            throw new AnalyzeException("Return statement outside of a function");

        }

        // Analyze the return value (if present)
        Optional<Ir.Expr> value = Optional.empty();
        Type valueType = Type.NIL;

        if (ast.value().isPresent()) {
            Ir.Expr valueExpr = visit(ast.value().get());
            value = Optional.of(valueExpr);
            valueType = valueExpr.type();

        }

        requireSubtype(valueType, returnType.get()); // Verify return value type is a subtype of expected return type

        return new Ir.Stmt.Return(value);
    }

    @Override
    public Ir.Stmt.Expression visit(Ast.Stmt.Expression ast) throws AnalyzeException {
        var expression = visit(ast.expression());
        return new Ir.Stmt.Expression(expression);
    }

    @Override
    public Ir.Stmt.Assignment visit(Ast.Stmt.Assignment ast) throws AnalyzeException {
        if (!(ast.expression() instanceof Ast.Expr.Variable) && !(ast.expression() instanceof Ast.Expr.Property)) { // The receiver must be a Variable or Property
            throw new AnalyzeException("Assignment receiver must be a variable or property");

        }

        Ir.Expr value = visit(ast.value()); // Analyze value first

        if (ast.expression() instanceof Ast.Expr.Variable) {
            // Handle variable assignment
            Ast.Expr.Variable variable = (Ast.Expr.Variable) ast.expression();
            var variableType = scope.get(variable.name(), false);

            if (variableType.isEmpty()) {
                throw new AnalyzeException("Variable " + variable.name() + " is not defined");
            }

            requireSubtype(value.type(), variableType.get()); // Value must be a subtype of variable's type

            Ir.Expr.Variable irVariable = (Ir.Expr.Variable) visit(variable);
            return new Ir.Stmt.Assignment.Variable(irVariable, value);

        } else {
            // Handle property assignment
            Ast.Expr.Property property = (Ast.Expr.Property) ast.expression();
            Ir.Expr.Property irProperty = (Ir.Expr.Property) visit(property);

            requireSubtype(value.type(), irProperty.type()); // Value must be a subtype of property's type

            return new Ir.Stmt.Assignment.Property(irProperty, value);
        }
    }

    private Ir.Expr visit(Ast.Expr ast) throws AnalyzeException {
        return (Ir.Expr) visit((Ast) ast); //helper to cast visit(Ast.Expr) to Ir.Expr

    }

    @Override
    public Ir.Expr.Literal visit(Ast.Expr.Literal ast) throws AnalyzeException {
        var type = switch (ast.value()) {
            case null -> Type.NIL;
            case Boolean _ -> Type.BOOLEAN;
            case BigInteger _ -> Type.INTEGER;
            case BigDecimal _ -> Type.DECIMAL;
            case String _ -> Type.STRING;
            //If the AST value isn't one of the above types, the Parser is
            //returning an incorrect AST - this is an implementation issue,
            //hence throw AssertionError rather than AnalyzeException.
            default -> throw new AssertionError(ast.value().getClass());

        };
        return new Ir.Expr.Literal(ast.value(), type);

    }

    @Override
    public Ir.Expr.Group visit(Ast.Expr.Group ast) throws AnalyzeException {
        // Simply analyze the contained expression
        Ir.Expr expression = visit(ast.expression());
        return new Ir.Expr.Group(expression);

    }

    @Override
    public Ir.Expr.Binary visit(Ast.Expr.Binary ast) throws AnalyzeException {
        // Analyze both sides of the expression
        Ir.Expr left = visit(ast.left());
        Ir.Expr right = visit(ast.right());

        Type resultType;

        switch (ast.operator()) { // Switch case to check what kind of operation we will be performing
            case "+" -> {
                if (left.type().equals(Type.STRING) || right.type().equals(Type.STRING)) { // String concatenation if either operand is a String
                    resultType = Type.STRING;

                } else { // Otherwise, both must be numeric and the same type
                    if (left.type().equals(Type.INTEGER) || left.type().equals(Type.DECIMAL)) {
                        requireSubtype(right.type(), left.type());
                        resultType = left.type();

                    } else {
                        throw new AnalyzeException("Left operand of + must be String, Integer, or Decimal");

                    }
                }
            }

            case "-", "*", "/" -> { // If we are dealing with subtraction, multiplication, or division
                if (left.type().equals(Type.INTEGER) || left.type().equals(Type.DECIMAL)) { // Both operands must be numeric and the same type
                    requireSubtype(right.type(), left.type());
                    resultType = left.type();

                } else {
                    throw new AnalyzeException("Operands of " + ast.operator() + " must be Integer or Decimal");

                }
            }

            case "<", "<=", ">", ">=" -> { // If we are dealing with a comparison operation
                requireSubtype(left.type(), Type.COMPARABLE); // Left operand must be Comparable

                requireSubtype(right.type(), left.type()); // Right operand must be same type as left

                resultType = Type.BOOLEAN;

            }

            case "==", "!=" -> { // Checking to see if the operands are equivalent or not equivalent
                requireSubtype(left.type(), Type.EQUATABLE); // Both operands must be Equatable

                requireSubtype(right.type(), left.type());

                resultType = Type.BOOLEAN;
            }

            case "AND", "OR" -> { // Check to see if we are dealing with AND or OR operations
                requireSubtype(left.type(), Type.BOOLEAN); // Both operands must be Boolean

                requireSubtype(right.type(), Type.BOOLEAN);

                resultType = Type.BOOLEAN;

            }

            default -> throw new AnalyzeException("Unknown binary operator: " + ast.operator()); // If the operator is none of the above, throw an error as it is not covered by the program

        }

        return new Ir.Expr.Binary(ast.operator(), left, right, resultType);

    }

    @Override
    public Ir.Expr.Variable visit(Ast.Expr.Variable ast) throws AnalyzeException {
        // Ensure variable is defined
        var variableType = scope.get(ast.name(), false);
        if (variableType.isEmpty()) {
            throw new AnalyzeException("Variable not defined: " + ast.name());

        }

        return new Ir.Expr.Variable(ast.name(), variableType.get());

    }

    @Override
    public Ir.Expr.Property visit(Ast.Expr.Property ast) throws AnalyzeException {
        Ir.Expr receiver = visit(ast.receiver()); // Analyze the receiver

        if (!(receiver.type() instanceof Type.Object)) { // Receiver must be an Object
            throw new AnalyzeException("Property access requires an object receiver");

        }

        Type.Object objectType = (Type.Object) receiver.type(); // Cast the receiver type to be an object type

        // Ensure property exists in object's scope
        var propertyType = objectType.scope().get(ast.name(), false);
        if (propertyType.isEmpty()) {
            throw new AnalyzeException("Property not defined: " + ast.name());

        }

        return new Ir.Expr.Property(receiver, ast.name(), propertyType.get());

    }

    @Override
    public Ir.Expr.Function visit(Ast.Expr.Function ast) throws AnalyzeException {
        // Ensure function is defined
        var functionType = scope.get(ast.name(), false);
        if (functionType.isEmpty()) {
            throw new AnalyzeException("Function not defined: " + ast.name());

        }

        if (!(functionType.get() instanceof Type.Function)) { // If function type is not actually a function
            throw new AnalyzeException(ast.name() + " is not a function");

        }

        Type.Function function = (Type.Function) functionType.get(); // Cast the receiver type to be an function type

        // Analyze all arguments
        var arguments = new ArrayList<Ir.Expr>();
        for (int i = 0; i < ast.arguments().size(); i++) {
            Ir.Expr argument = visit(ast.arguments().get(i));
            arguments.add(argument);

            // Ensure argument count matches parameter count
            if (i >= function.parameters().size()) {
                throw new AnalyzeException("Too many arguments for function " + ast.name());

            }


            requireSubtype(argument.type(), function.parameters().get(i)); // Ensure argument type is a subtype of parameter type

        }

        if (arguments.size() < function.parameters().size()) { // Ensure we have enough arguments
            throw new AnalyzeException("Not enough arguments for function " + ast.name());
        }

        return new Ir.Expr.Function(ast.name(), arguments, function.returns());

    }

    @Override
    public Ir.Expr.Method visit(Ast.Expr.Method ast) throws AnalyzeException {
        Ir.Expr receiver = visit(ast.receiver()); // Analyze the receiver

        if (!(receiver.type() instanceof Type.Object)) { // Receiver must be an Object
            throw new AnalyzeException("Method call requires an object receiver");

        }

        Type.Object objectType = (Type.Object) receiver.type(); // Cast the receiver type to be an object type

        // Ensure method exists in object's scope
        var methodType = objectType.scope().get(ast.name(), false);
        if (methodType.isEmpty()) {
            throw new AnalyzeException("Method not defined: " + ast.name());

        }

        if (!(methodType.get() instanceof Type.Function)) {
            throw new AnalyzeException(ast.name() + " is not a method");

        }

        Type.Function function = (Type.Function) methodType.get();

        // Analyze all arguments
        var arguments = new ArrayList<Ir.Expr>();
        for (int i = 0; i < ast.arguments().size(); i++) {
            Ir.Expr argument = visit(ast.arguments().get(i));
            arguments.add(argument);

            if (i >= function.parameters().size()) { // Ensure argument count matches parameter count
                throw new AnalyzeException("Too many arguments for method " + ast.name());

            }

            requireSubtype(argument.type(), function.parameters().get(i)); // Ensure argument type is a subtype of parameter type

        }

        if (arguments.size() < function.parameters().size()) { // Ensure we have enough arguments
            throw new AnalyzeException("Not enough arguments for method " + ast.name());

        }

        return new Ir.Expr.Method(receiver, ast.name(), arguments, function.returns());

    }

    @Override
    public Ir.Expr.ObjectExpr visit(Ast.Expr.ObjectExpr ast) throws AnalyzeException {
        if (ast.name().isPresent() && Environment.TYPES.containsKey(ast.name().get())) { // Object name must not be a type in Environment.TYPES
            throw new AnalyzeException("Object name cannot be a type: " + ast.name().get());

        }

        // Create a new scope for the object
        Scope objectScope = new Scope(null);
        Type.Object objectType = new Type.Object(objectScope);

        // Store current scope to restore later
        Scope prevScope = scope;

        // List to collect Let and Def statements for the object
        List<Ir.Stmt.Let> letStatements = new ArrayList<>();
        List<Ir.Stmt.Def> defStatements = new ArrayList<>();

        // Process fields (Let statements)
        for (Ast.Stmt.Let field : ast.fields()) {
            for (Ir.Stmt.Let existingField : letStatements) { // Check for duplicate field names
                if (existingField.name().equals(field.name())) {
                    throw new AnalyzeException("Duplicate field name: " + field.name());

                }
            }

            // Similar to LET statement analysis
            Type fieldType;
            Optional<Ir.Expr> fieldValue = Optional.empty();


            if (field.value().isPresent()) { // If value present, analyze it first
                Ir.Expr valueExpr = visit(field.value().get());
                fieldValue = Optional.of(valueExpr);

                if (field.type().isEmpty()) { // Use value's type if no explicit type is given
                    fieldType = valueExpr.type();

                } else {
                    // If explicit type is given, ensure it exists
                    String typeName = field.type().get();
                    if (!Environment.TYPES.containsKey(typeName)) {
                        throw new AnalyzeException("Type " + typeName + " is not defined");

                    }
                    fieldType = Environment.TYPES.get(typeName);

                    requireSubtype(valueExpr.type(), fieldType); // Check that value's type is a subtype of the field type
                }

            } else { // No value, so use explicit type
                if (field.type().isPresent()) {
                    String typeName = field.type().get();
                    if (!Environment.TYPES.containsKey(typeName)) {
                        throw new AnalyzeException("Type " + typeName + " is not defined");

                    }
                    fieldType = Environment.TYPES.get(typeName);

                } else { // Default to Any
                    fieldType = Type.ANY;

                }
            }

            objectScope.define(field.name(), fieldType); // Define the field in the object's scope

            letStatements.add(new Ir.Stmt.Let(field.name(), fieldType, fieldValue)); // Create Let statement for the field

        }

        // Process methods (Def statements)
        for (Ast.Stmt.Def method : ast.methods()) {
            String methodName = method.name();

            // Check for duplicate method names and conflicts with field names
            for (Ir.Stmt.Let field : letStatements) {
                if (field.name().equals(methodName)) {
                    throw new AnalyzeException("Method name conflicts with field name: " + methodName);

                }
            }

            for (Ir.Stmt.Def existingMethod : defStatements) {
                if (existingMethod.name().equals(methodName)) {
                    throw new AnalyzeException("Duplicate method name: " + methodName);

                }
            }

            List<Ir.Stmt.Def.Parameter> parameters = new ArrayList<>(); // Similar to DEF statement analysis

            // Check for duplicate parameter names
            for (int i = 0; i < method.parameters().size(); i++) {
                for (int j = i + 1; j < method.parameters().size(); j++) {
                    if (method.parameters().get(i).equals(method.parameters().get(j))) {
                        throw new AnalyzeException("Duplicate parameter name: " + method.parameters().get(i));

                    }
                }
            }

            // Process parameters
            List<Type> parameterTypes = new ArrayList<>();
            for (int i = 0; i < method.parameters().size(); i++) {
                String paramName = method.parameters().get(i);
                String typeName = i < method.parameterTypes().size() && method.parameterTypes().get(i).isPresent() ?
                        method.parameterTypes().get(i).get() : "Any";

                if (!Environment.TYPES.containsKey(typeName)) {
                    throw new AnalyzeException("Type " + typeName + " is not defined");
                }

                Type paramType = Environment.TYPES.get(typeName);
                parameters.add(new Ir.Stmt.Def.Parameter(paramName, paramType));
                parameterTypes.add(paramType);
            }

            // Get return type
            Type returnType = method.returnType().isPresent() ?
                    Environment.TYPES.getOrDefault(method.returnType().get(), Type.ANY) : Type.ANY;

            // Define the method in the object's scope
            Type.Function functionType = new Type.Function(parameterTypes, returnType);
            objectScope.define(methodName, functionType);

            scope = new Scope(null); // Create a new scope for the method body

            scope.define("this", objectType); // Define "this" in method scope with the object's type


            for (int i = 0; i < method.parameters().size(); i++) { // Define parameters in the method scope
                scope.define(method.parameters().get(i), parameters.get(i).type());

            }

            scope.define("$RETURNS", returnType); // Define $RETURNS to store return type

            // Analyze method body
            List<Ir.Stmt> statements = new ArrayList<>();
            for (var stmt : method.body()) {
                statements.add(visit(stmt));

            }


            defStatements.add(new Ir.Stmt.Def(methodName, parameters, returnType, statements)); // Create Def statement for the method

        }

        // Restore original scope
        scope = prevScope;

        return new Ir.Expr.ObjectExpr(ast.name(), letStatements, defStatements, objectType);

    }

    public static void requireSubtype(Type type, Type other) throws AnalyzeException {
        if (other.equals(Type.ANY)) { // All types are subtypes of Any
            return;

        }

        if (type.equals(other)) { // All types are subtypes of themselves
            return;

        }

        // Nil, Comparable (and all subtypes), Iterable are subtypes of Equatable
        if (other.equals(Type.EQUATABLE) && (
                type.equals(Type.NIL) ||
                        type.equals(Type.COMPARABLE) ||
                        type.equals(Type.BOOLEAN) ||
                        type.equals(Type.INTEGER) ||
                        type.equals(Type.DECIMAL) ||
                        type.equals(Type.STRING) ||
                        type.equals(Type.ITERABLE)
        )) {
            return;
        }

        // Boolean, Integer, Decimal, String are subtypes of Comparable
        if (other.equals(Type.COMPARABLE) && (
                type.equals(Type.BOOLEAN) ||
                        type.equals(Type.INTEGER) ||
                        type.equals(Type.DECIMAL) ||
                        type.equals(Type.STRING)
        )) {
            return;
        }

        throw new AnalyzeException("Type " + type + " is not a subtype of " + other); // If we get here, type is not a subtype of other

    }

}
