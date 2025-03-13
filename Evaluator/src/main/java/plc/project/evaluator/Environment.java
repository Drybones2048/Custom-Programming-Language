package plc.project.evaluator;

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

    private static RuntimeValue range(List<RuntimeValue> arguments) {
        throw new UnsupportedOperationException("TODO"); //TODO
    }

    private static RuntimeValue function(List<RuntimeValue> arguments) {
        return new RuntimeValue.Primitive(arguments);
    }

    private static RuntimeValue method(List<RuntimeValue> arguments) {
        return new RuntimeValue.Primitive(arguments.subList(1, arguments.size()));
    }

}
