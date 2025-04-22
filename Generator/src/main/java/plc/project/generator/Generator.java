package plc.project.generator;

import plc.project.analyzer.Ir;
import plc.project.analyzer.Type;

import java.math.BigDecimal;
import java.math.BigInteger;

public class Generator implements Ir.Visitor<StringBuilder, RuntimeException> {

    private final StringBuilder builder = new StringBuilder();
    private int indent = 0;

    private void newline(int indent) {
        builder.append("\n");
        builder.append("    ".repeat(indent));
    }

    @Override
    public StringBuilder visit(Ir.Source ir) {
        builder.append(Environment.imports()).append("\n\n");
        builder.append("public final class Main {").append("\n\n");
        builder.append(Environment.definitions()).append("\n");
        //Java doesn't allow for nested functions, but we will pretend it does.
        //To support simple programs involving functions, we will "hoist" any
        //variable/function declaration at the start of the program to allow
        //these functions to be used as valid Java.
        indent = 1;
        boolean main = false;
        for (var statement : ir.statements()) {
            newline(indent);
            if (!main) {
                if (statement instanceof Ir.Stmt.Let || statement instanceof Ir.Stmt.Def) {
                    builder.append("static ");
                } else {
                    builder.append("public static void main(String[] args) {");
                    main = true;
                    indent = 2;
                    newline(indent);
                }
            }
            visit(statement);
        }
        if (main) {
            builder.append("\n").append("    }");
        }
        indent = 0;
        builder.append("\n\n").append("}");
        return builder;
    }

    @Override
    public StringBuilder visit(Ir.Stmt.Let ir) {
        if (ir.type() instanceof Type.Object) { // If it is type Object
            builder.append("var ");
            builder.append(ir.name());

            if (ir.value().isPresent()) { // If it has a value
                builder.append(" = ");
                visit(ir.value().get());

            }
        } else { // Else it is not an Object
            builder.append(ir.type().jvmName());
            builder.append(" ");
            builder.append(ir.name());

            if (ir.value().isPresent()) { // If it has a value
                builder.append(" = ");
                visit(ir.value().get());
            }
        }

        // Finish statement and return
        builder.append(";");
        return builder;
    }

    @Override
    public StringBuilder visit(Ir.Stmt.Def ir) {
        // Start off by making multiple appends for formatting
        builder.append(ir.returns().jvmName());
        builder.append(" ");
        builder.append(ir.name());
        builder.append("(");

        // Handle parameters
        for (int i = 0; i < ir.parameters().size(); i++) {
            Ir.Stmt.Def.Parameter param = ir.parameters().get(i);
            builder.append(param.type().jvmName());
            builder.append(" ");
            builder.append(param.name());

            if (i < ir.parameters().size() - 1) {
                builder.append(", ");

            }
        }

        builder.append(") {"); // Close parameters

        // Handle body
        if (!ir.body().isEmpty()) {
            indent++;

            for (Ir.Stmt stmt : ir.body()) {
                newline(indent);
                visit(stmt);

            }
            indent--;
            newline(indent);

        } else {
            // Add newline before closing brace even if body is empty
            newline(indent);
        }

        // Finish and return
        builder.append("}");
        return builder;
    }

    @Override
    public StringBuilder visit(Ir.Stmt.If ir) {
        // Starting format for an If statment
        builder.append("if (");
        visit(ir.condition());
        builder.append(") {");

        // Handle then body
        if (!ir.thenBody().isEmpty()) {
            indent++;

            for (Ir.Stmt stmt : ir.thenBody()) {
                newline(indent);
                visit(stmt);

            }
            indent--;
            newline(indent);

        } else {
            newline(indent); // Add newline before closing brace even if then body is empty

        }

        // Handle else body if present
        if (!ir.elseBody().isEmpty()) {
            builder.append("} else {");
            indent++;

            for (Ir.Stmt stmt : ir.elseBody()) {
                newline(indent);
                visit(stmt);

            }
            indent--;
            newline(indent);

        }

        // Finish and return
        builder.append("}");
        return builder;
    }

    @Override
    public StringBuilder visit(Ir.Stmt.For ir) {
        // Set up syntax for the for-loop
        builder.append("for (");
        builder.append(ir.type().jvmName());
        builder.append(" ");
        builder.append(ir.name());
        builder.append(" : ");
        visit(ir.expression());
        builder.append(") {");

        // Handle body
        if (!ir.body().isEmpty()) {
            indent++;

            for (Ir.Stmt stmt : ir.body()) {
                newline(indent);
                visit(stmt);

            }
            indent--;
            newline(indent);

        }

        // Finish and return
        builder.append("}");
        return builder;
    }

    @Override
    public StringBuilder visit(Ir.Stmt.Return ir) {
        builder.append("return ");

        if (ir.value().isEmpty()) { // Return null
            builder.append("null");

        } else { // If return has a value
            visit(ir.value().get());

        }

        //Finish and return
        builder.append(";");
        return builder;
    }

    @Override
    public StringBuilder visit(Ir.Stmt.Expression ir) {
        // Uses visit and then adds a semicolon and returns
        visit(ir.expression());

        builder.append(";");
        return builder;
    }

    @Override
    public StringBuilder visit(Ir.Stmt.Assignment.Variable ir) {
        // Uses visit and then adds a semicolon and returns
        visit(ir.variable());
        builder.append(" = ");
        visit(ir.value());

        builder.append(";");
        return builder;
    }

    @Override
    public StringBuilder visit(Ir.Stmt.Assignment.Property ir) {
        // Uses visit and then adds a semicolon and returns
        visit(ir.property());
        builder.append(" = ");
        visit(ir.value());

        builder.append(";");
        return builder;
    }

    @Override
    public StringBuilder visit(Ir.Expr.Literal ir) {
        var literal = switch (ir.value()) {
            case null -> "null";
            case Boolean b -> b.toString();
            case BigInteger i -> "new BigInteger(\"" + i + "\")";
            case BigDecimal d -> "new BigDecimal(\"" + d + "\")";
            case String s -> "\"" + s + "\""; //TODO: Escape characters?
            //If the IR value isn't one of the above types, the Parser/Analyzer
            //is returning an incorrect IR - this is an implementation issue,
            //hence throw AssertionError rather than a "standard" exception.
            default -> throw new AssertionError(ir.value().getClass());
        };
        builder.append(literal);
        return builder;
    }

    @Override
    public StringBuilder visit(Ir.Expr.Group ir) {
        // Uses visit and then adds a semicolon and returns
        builder.append("(");
        visit(ir.expression());

        builder.append(")");
        return builder;
    }

    @Override
    public StringBuilder visit(Ir.Expr.Binary ir) {
        String operator = ir.operator(); // Grabs the operator for switch case

        // Switch case depending on what kind of operator is present
        switch (operator) {
            case "+" -> { // If addition
                if (ir.type() == Type.STRING) { // If we are appending strings
                    visit(ir.left());
                    builder.append(" + ");
                    visit(ir.right());

                } else { // Regular addition
                    builder.append("(");
                    visit(ir.left());
                    builder.append(").add(");
                    visit(ir.right());
                    builder.append(")");

                }
            }
            case "-" -> { // If subtraction
                builder.append("(");
                visit(ir.left());
                builder.append(").subtract(");
                visit(ir.right());
                builder.append(")");

            }
            case "*" -> { // If multiplication
                builder.append("(");
                visit(ir.left());
                builder.append(").multiply(");
                visit(ir.right());
                builder.append(")");

            }
            case "/" -> { // If division
                builder.append("(");
                visit(ir.left());
                builder.append(").divide(");
                visit(ir.right());
                if (ir.type() == Type.DECIMAL) {
                    builder.append(", RoundingMode.HALF_EVEN");

                }
                builder.append(")");

            }
            case "<", "<=", ">", ">=" -> { // If any sort of comparison
                builder.append("(");
                visit(ir.left());
                builder.append(").compareTo(");
                visit(ir.right());
                builder.append(") ");
                builder.append(operator);
                builder.append(" 0");

            }
            case "==" -> { // If equals to
                builder.append("Objects.equals(");
                visit(ir.left());
                builder.append(", ");
                visit(ir.right());
                builder.append(")");

            }
            case "!=" -> { // If not equals to
                builder.append("!Objects.equals(");
                visit(ir.left());
                builder.append(", ");
                visit(ir.right());
                builder.append(")");

            }
            case "AND" -> { // If AND operation
                // If multiple operations within AND
                if (ir.left() instanceof Ir.Expr.Binary binary && binary.operator().equals("OR")) {
                    builder.append("(");
                    visit(ir.left());
                    builder.append(")");

                } else {
                    visit(ir.left());

                }
                builder.append(" && ");
                visit(ir.right());
            }
            case "OR" -> { // If OR operation
                visit(ir.left());
                builder.append(" || ");
                visit(ir.right());

            }
        }

        return builder;
    }

    @Override
    public StringBuilder visit(Ir.Expr.Variable ir) {
        builder.append(ir.name());
        return builder;

    }

    @Override
    public StringBuilder visit(Ir.Expr.Property ir) {
        // Formats for a property then returns
        visit(ir.receiver());
        builder.append(".");
        builder.append(ir.name());
        return builder;
    }

    @Override
    public StringBuilder visit(Ir.Expr.Function ir) {
        // Formats for function name and opening parenthesis
        builder.append(ir.name());
        builder.append("(");

        // Handle arguments
        for (int i = 0; i < ir.arguments().size(); i++) {
            visit(ir.arguments().get(i));
            if (i < ir.arguments().size() - 1) {
                builder.append(", ");
            }
        }

        // Close parenthesis and then return
        builder.append(")");
        return builder;
    }

    @Override
    public StringBuilder visit(Ir.Expr.Method ir) {
        // Formats for method and opening parenthesis
        visit(ir.receiver());
        builder.append(".");
        builder.append(ir.name());
        builder.append("(");

        // Handle arguments
        for (int i = 0; i < ir.arguments().size(); i++) {
            visit(ir.arguments().get(i));
            if (i < ir.arguments().size() - 1) {
                builder.append(", ");
            }
        }

        // Close parenthesis and return
        builder.append(")");
        return builder;
    }

    @Override
    public StringBuilder visit(Ir.Expr.ObjectExpr ir) {
        // Format for Object declaration
        builder.append("new Object() {");

        // Handle fields and methods
        if (!ir.fields().isEmpty() || !ir.methods().isEmpty()) {
            indent++;

            // Fields
            for (Ir.Stmt.Let field : ir.fields()) {
                newline(indent);
                visit(field);

            }

            // Methods
            for (Ir.Stmt.Def method : ir.methods()) {
                newline(indent);
                visit(method);

            }

            indent--;
            newline(indent);

        } else {
            // Add newline before closing brace even if there are no fields or methods
            newline(indent);

        }

        // Close brackets and return
        builder.append("}");
        return builder;
    }

}
