package plc.project;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class Interpreter implements Ast.Visitor<Environment.PlcObject> {

    private Scope scope = new Scope(null);

    public Interpreter(Scope parent) {
        scope = new Scope(parent);
        scope.defineFunction("print", 1, args -> {
            System.out.println(args.get(0).getValue());
            return Environment.NIL;
        });
        scope.defineFunction("logarithm", 1, args -> {
            BigDecimal n = requireType(BigDecimal.class, Environment.create(args.get(0).getValue()));
            BigDecimal result = BigDecimal.valueOf(Math.log(n.doubleValue()));
            return Environment.create(result);
        });
        scope.defineFunction("converter", 2, args -> {
            String number = new String();
            int i, n = 0;
            ArrayList<BigInteger> quotients = new ArrayList<>();
            ArrayList<BigInteger> remainders = new ArrayList<>();
            BigInteger base10 = requireType(BigInteger.class, Environment.create(args.get(0).getValue()));
            BigInteger base = requireType(BigInteger.class, Environment.create(args.get(1).getValue()));
            quotients.add(base10);
            do {
                quotients.add(quotients.get(n).divide(base));
                remainders.add(quotients.get(n).subtract(quotients.get(n + 1).multiply(base)));
                n++;
            } while (quotients.get(n).compareTo(BigInteger.ZERO) > 0);
            for (i = 0; i < remainders.size(); i++) {
                number = remainders.get(i).toString() + number;
            }
            return Environment.create(number);
        });
    }

    public Scope getScope() {
        return scope;
    }

    @Override
    public Environment.PlcObject visit(Ast.Source ast) {
        for (Ast.Global global : ast.getGlobals()) {
            visit(global);
        }
        for (Ast.Function function : ast.getFunctions()) {
            visit(function);
        }
        return scope.lookupFunction("main", 0).invoke(new ArrayList<>());
    }

    @Override
    public Environment.PlcObject visit(Ast.Global ast) {
        if (ast.getValue().isPresent()) {
            scope.defineVariable(ast.getName(), ast.getMutable(), visit(ast.getValue().get()));
        } else {
            scope.defineVariable(ast.getName(), ast.getMutable(), Environment.NIL);
        }
        return Environment.NIL;
    }

    @Override
    public Environment.PlcObject visit(Ast.Function ast) {
        Scope parent = scope;
        scope.defineFunction(ast.getName(), ast.getParameters().size(), args -> {
            Scope previous = scope;
            scope = new Scope(parent);
            try {
                for (int i = 0; i < ast.getParameters().size(); i++) {
                    scope.defineVariable(ast.getParameters().get(i), true, args.get(i));
                }
                for (Ast.Statement stmt : ast.getStatements()) {
                    visit(stmt);
                }
                return Environment.NIL;
            } catch (Return r) {
                return r.value;
            } finally {
                scope = previous;
            }
        });
        return Environment.NIL;
    }

    @Override
    public Environment.PlcObject visit(Ast.Statement.Expression ast) {
        visit(ast.getExpression());
        return Environment.NIL;
    }

    @Override
    public Environment.PlcObject visit(Ast.Statement.Declaration ast) {
        if (ast.getValue().isPresent()) {
            scope.defineVariable(ast.getName(), true, visit(ast.getValue().get()));
        } else {
            scope.defineVariable(ast.getName(), true, Environment.NIL);
        }
        return Environment.NIL;
    }

    @Override
    public Environment.PlcObject visit(Ast.Statement.Assignment ast) {
        if (ast.getReceiver() instanceof Ast.Expression.Access) {
            Ast.Expression.Access receiver = (Ast.Expression.Access) ast.getReceiver();
            if (scope.lookupVariable(receiver.getName()).getMutable()) {
                if (receiver.getOffset().isPresent()) {
                    List<Object> vals = requireType(List.class, scope.lookupVariable(receiver.getName()).getValue());
                    BigInteger offset = requireType(BigInteger.class, visit(receiver.getOffset().get()));
                    vals.set(offset.intValueExact(), visit(ast.getValue()).getValue());
                    scope.lookupVariable(receiver.getName()).setValue(Environment.create(vals));
                } else {
                    scope.lookupVariable(receiver.getName()).setValue(visit(ast.getValue()));
                }
                return Environment.NIL;
            } else {
                throw new RuntimeException("Invalid assignment statement.");
            }
        } else {
            throw new RuntimeException("Invalid assignment statement.");
        }
    }

    @Override
    public Environment.PlcObject visit(Ast.Statement.If ast) {
        if (requireType(Boolean.class, visit(ast.getCondition()))) {
            try {
                scope = new Scope(scope);
                for (Ast.Statement stmt : ast.getThenStatements()) {
                    visit(stmt);
                }
            } finally {
                scope = scope.getParent();
            }
        } else {
            try {
                scope = new Scope(scope);
                for (Ast.Statement stmt : ast.getElseStatements()) {
                    visit(stmt);
                }
            } finally {
                scope = scope.getParent();
            }
        }
        return Environment.NIL;
    }

    @Override
    public Environment.PlcObject visit(Ast.Statement.Switch ast) {
        boolean caseTaken = false;
        for (Ast.Statement.Case c : ast.getCases()) {
            try {
                scope = new Scope(scope);
                if (c.getValue().isPresent()) {
                    if (visit(c.getValue().get()).getValue().equals(visit(ast.getCondition()).getValue())) {
                        visit(c);
                        caseTaken = true;
                    }
                }
            } finally {
                scope = scope.getParent();
            }
        }
        if (!caseTaken) {
            for (Ast.Statement.Case c : ast.getCases()) {
                try {
                    scope = new Scope(scope);
                    if (!c.getValue().isPresent()) {
                        visit(c);
                    }
                } finally {
                    scope = scope.getParent();
                }
            }
        }
        return Environment.NIL;
    }

    @Override
    public Environment.PlcObject visit(Ast.Statement.Case ast) {
        for (Ast.Statement stmt : ast.getStatements()) {
            visit(stmt);
        }
        return Environment.NIL;
    }

    @Override
    public Environment.PlcObject visit(Ast.Statement.While ast) {
        while (requireType(Boolean.class, visit(ast.getCondition()))) {
            try {
                scope = new Scope(scope);
                for (Ast.Statement stmt : ast.getStatements()) {
                    visit(stmt);
                }
            } finally {
                scope = scope.getParent();
            }
        }
        return Environment.NIL;
    }

    @Override
    public Environment.PlcObject visit(Ast.Statement.Return ast) {
        throw new Return(visit(ast.getValue()));
    }

    @Override
    public Environment.PlcObject visit(Ast.Expression.Literal ast) {
        if (ast.getLiteral() == null) {
            return Environment.NIL;
        } else {
            return Environment.create(ast.getLiteral());
        }
    }

    @Override
    public Environment.PlcObject visit(Ast.Expression.Group ast) {
        return visit(ast.getExpression());
    }

    @Override
    public Environment.PlcObject visit(Ast.Expression.Binary ast) {
        Environment.PlcObject left = visit(ast.getLeft());
        switch (ast.getOperator()) {
            case "&&": {
                Boolean l = requireType(Boolean.class, left);
                return Environment.create(l && requireType(Boolean.class, visit(ast.getRight())));
            } case "||": {
                Boolean l = requireType(Boolean.class, left);
                return Environment.create(l || requireType(Boolean.class, visit(ast.getRight())));
            } case "<": {
                Environment.PlcObject right = visit(ast.getRight());
                if (left.getValue() instanceof Comparable) {
                    Comparable<Object> l = requireType(Comparable.class, left);
                    Object r = requireType(left.getValue().getClass(), right);
                    return Environment.create(l.compareTo(r) < 0);
                } else {
                    throw new RuntimeException("Invalid binary operation.");
                }
            } case ">": {
                Environment.PlcObject right = visit(ast.getRight());
                if (left.getValue() instanceof Comparable) {
                    Comparable<Object> l = requireType(Comparable.class, left);
                    Object r = requireType(left.getValue().getClass(), right);
                    return Environment.create(l.compareTo(r) > 0);
                } else {
                    throw new RuntimeException("Invalid binary operation.");
                }
            } case "==": {
                Environment.PlcObject right = visit(ast.getRight());
                return Environment.create(java.util.Objects.equals(left.getValue(), right.getValue()));
            } case "!=": {
                Environment.PlcObject right = visit(ast.getRight());
                return Environment.create(!java.util.Objects.equals(left.getValue(), right.getValue()));
            } case "+": {
                Environment.PlcObject right = visit(ast.getRight());
                if (left.getValue() instanceof String || right.getValue() instanceof String) {
                    String l = String.valueOf(left.getValue());
                    String r = String.valueOf(right.getValue());
                    return Environment.create(l + r);
                } else if (left.getValue() instanceof BigInteger) {
                    BigInteger l = requireType(BigInteger.class, left);
                    BigInteger r = requireType(BigInteger.class, right);
                    return Environment.create(l.add(r));
                } else if (left.getValue() instanceof BigDecimal) {
                    BigDecimal l = requireType(BigDecimal.class, left);
                    BigDecimal r = requireType(BigDecimal.class, right);
                    return Environment.create(l.add(r));
                } else {
                    throw new RuntimeException("Invalid binary operation.");
                }
            } case "-": {
                Environment.PlcObject right = visit(ast.getRight());
                if (left.getValue() instanceof BigInteger) {
                    BigInteger l = requireType(BigInteger.class, left);
                    BigInteger r = requireType(BigInteger.class, right);
                    return Environment.create(l.subtract(r));
                } else if (left.getValue() instanceof BigDecimal) {
                    BigDecimal l = requireType(BigDecimal.class, left);
                    BigDecimal r = requireType(BigDecimal.class, right);
                    return Environment.create(l.subtract(r));
                } else {
                    throw new RuntimeException("Invalid binary operation.");
                }
            } case "*": {
                Environment.PlcObject right = visit(ast.getRight());
                if (left.getValue() instanceof BigInteger) {
                    BigInteger l = requireType(BigInteger.class, left);
                    BigInteger r = requireType(BigInteger.class, right);
                    return Environment.create(l.multiply(r));
                } else if (left.getValue() instanceof BigDecimal) {
                    BigDecimal l = requireType(BigDecimal.class, left);
                    BigDecimal r = requireType(BigDecimal.class, right);
                    return Environment.create(l.multiply(r));
                } else {
                    throw new RuntimeException("Invalid binary operation.");
                }
            } case "/": {
                Environment.PlcObject right = visit(ast.getRight());
                if (left.getValue() instanceof BigInteger) {
                    BigInteger l = requireType(BigInteger.class, left);
                    BigInteger r = requireType(BigInteger.class, right);
                    if (r.compareTo(BigInteger.ZERO) != 0) {
                        return Environment.create(l.divide(r));
                    } else {
                        throw new RuntimeException("Invalid binary operation.");
                    }
                } else if (left.getValue() instanceof BigDecimal) {
                    BigDecimal l = requireType(BigDecimal.class, left);
                    BigDecimal r = requireType(BigDecimal.class, right);
                    if (r.compareTo(BigDecimal.ZERO) != 0) {
                        return Environment.create(l.divide(r, RoundingMode.HALF_EVEN));
                    } else {
                        throw new RuntimeException("Invalid binary operation.");
                    }
                } else {
                    throw new RuntimeException("Invalid binary operation.");
                }
            } case "^": {
                Environment.PlcObject right = visit(ast.getRight());
                if (left.getValue() instanceof BigInteger) {
                    BigInteger l = requireType(BigInteger.class, left);
                    BigInteger r = requireType(BigInteger.class, right);
                    if (r.compareTo(BigInteger.valueOf(Integer.MAX_VALUE)) > 0) {
                        BigInteger remainder = r.subtract(BigInteger.valueOf(Integer.MAX_VALUE));
                        BigInteger n = l.pow(Integer.MAX_VALUE);
                        for (BigInteger i = BigInteger.ZERO; i.compareTo(remainder) < 0; i = i.add(BigInteger.ONE)) {
                            n = n.multiply(n);
                        }
                        return Environment.create(n);
                    } else {
                        return Environment.create(l.pow(r.intValueExact()));
                    }
                } else {
                    throw new RuntimeException("Invalid binary operation.");
                }
            } default: {
                throw new RuntimeException("Invalid binary operation.");
            }
        }
    }

    @Override
    public Environment.PlcObject visit(Ast.Expression.Access ast) {
        if (ast.getOffset().isPresent()) {
            List<Object> vals = requireType(List.class, scope.lookupVariable(ast.getName()).getValue());
            BigInteger offset = requireType(BigInteger.class, visit(ast.getOffset().get()));
            return Environment.create(vals.get(offset.intValueExact()));
        } else {
            return scope.lookupVariable(ast.getName()).getValue();
        }
    }

    @Override
    public Environment.PlcObject visit(Ast.Expression.Function ast) {
        List<Environment.PlcObject> args = new ArrayList<>();
        for (Ast.Expression expr : ast.getArguments()) {
            args.add(visit(expr));
        }
        return scope.lookupFunction(ast.getName(), ast.getArguments().size()).invoke(args);
    }

    @Override
    public Environment.PlcObject visit(Ast.Expression.PlcList ast) {
        List<Object> list = new ArrayList<>();
        for (Ast.Expression expr : ast.getValues()) {
            list.add(visit(expr).getValue());
        }
        return Environment.create(list);
    }

    /**
     * Helper function to ensure an object is of the appropriate type.
     */
    private static <T> T requireType(Class<T> type, Environment.PlcObject object) {
        if (type.isInstance(object.getValue())) {
            return type.cast(object.getValue());
        } else {
            throw new RuntimeException("Expected type " + type.getName() + ", received " + object.getValue().getClass().getName() + ".");
        }
    }

    /**
     * Exception class for returning values.
     */
    private static class Return extends RuntimeException {

        private final Environment.PlcObject value;

        private Return(Environment.PlcObject value) {
            this.value = value;
        }

    }

}
