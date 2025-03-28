package plc.project.evaluator;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class Environment {

    public static Scope scope() {
        var scope = new Scope(null);
        scope.define("print", new RuntimeValue.Function("print", Environment::print));
        scope.define("log", new RuntimeValue.Function("log", Environment::log));
        scope.define("list", new RuntimeValue.Function("list", Environment::list));
        scope.define("range", new RuntimeValue.Function("range", Environment::range));
        scope.define("variable", new RuntimeValue.Primitive("variable"));
        scope.define("function", new RuntimeValue.Function("function", Environment::function));
        var object = new RuntimeValue.ObjectValue(Optional.of("Object"), new Scope(null));
        scope.define("object", object);
        object.scope().define("property", new RuntimeValue.Primitive("property"));
        object.scope().define("method", new RuntimeValue.Function("method", Environment::method));
        return scope;
    }

    private static RuntimeValue print(List<RuntimeValue> arguments) throws EvaluateException {
        if (arguments.size() != 1) {
            throw new EvaluateException("Expected print to be called with 1 argument.");
        }
        System.out.println(arguments.getFirst().print());
        return new RuntimeValue.Primitive(null);
    }

    static RuntimeValue log(List<RuntimeValue> arguments) throws EvaluateException {
        if (arguments.size() != 1) {
            throw new EvaluateException("Expected log to be called with 1 argument.");
        }
        System.out.println("log: " + arguments.getFirst().print());
        return arguments.getFirst(); //size validated by print
    }

    private static RuntimeValue list(List<RuntimeValue> arguments) {
        return new RuntimeValue.Primitive(arguments);
    }

    private static RuntimeValue range(List<RuntimeValue> arguments) throws EvaluateException {
        if (arguments.size() != 2) { // Check that exactly two arguments are provided
            throw new EvaluateException("range function expects exactly 2 arguments");

        }

        // Extract and validate the start argument
        RuntimeValue startValue = arguments.get(0);
        if (!(startValue instanceof RuntimeValue.Primitive primitive) ||
                !(primitive.value() instanceof java.math.BigInteger start)) {
            throw new EvaluateException("First argument to range must be an integer");

        }

        // Extract and validate the end argument
        RuntimeValue endValue = arguments.get(1);
        if (!(endValue instanceof RuntimeValue.Primitive primitive2) ||
                !(primitive2.value() instanceof java.math.BigInteger end)) {
            throw new EvaluateException("Second argument to range must be an integer");

        }

        // Check that start is less than or equal to end
        if (start.compareTo(end) > 0) {
            throw new EvaluateException("Start value must be less than or equal to end value");

        }

        // Create the list of integers from start (inclusive) to end (exclusive)
        List<RuntimeValue> rangeList = new ArrayList<>();
        for (java.math.BigInteger i = start; i.compareTo(end) < 0; i = i.add(java.math.BigInteger.ONE)) {
            rangeList.add(new RuntimeValue.Primitive(i));
        }

        return new RuntimeValue.Primitive(rangeList);
    }

    private static RuntimeValue function(List<RuntimeValue> arguments) {
        return new RuntimeValue.Primitive(arguments);
    }

    private static RuntimeValue method(List<RuntimeValue> arguments) {
        return new RuntimeValue.Primitive(arguments.subList(1, arguments.size()));
    }

}
