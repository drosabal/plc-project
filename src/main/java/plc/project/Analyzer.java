package plc.project;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * See the specification for information about what the different visit
 * methods should do.
 */
public final class Analyzer implements Ast.Visitor<Void> {

    public Scope scope;
    private Ast.Global global = null;
    private Ast.Function function = null;

    public Analyzer(Scope parent) {
        scope = new Scope(parent);
        scope.defineFunction("print", "System.out.println", Arrays.asList(Environment.Type.ANY), Environment.Type.NIL, args -> Environment.NIL);
    }

    public Scope getScope() {
        return scope;
    }

    @Override
    public Void visit(Ast.Source ast) {
        for (Ast.Global global : ast.getGlobals()) {
            visit(global);
        }
        for (Ast.Function function : ast.getFunctions()) {
            visit(function);
        }
        if (!scope.lookupFunction("main", 0).getReturnType().equals(Environment.Type.INTEGER)) {
            throw new RuntimeException("Invalid source.");
        }
        return null;
    }

    @Override
    public Void visit(Ast.Global ast) {
        global = ast;
        if (ast.getValue().isPresent()) {
            visit(ast.getValue().get());
            requireAssignable(Environment.getType(ast.getTypeName()), ast.getValue().get().getType());
        }
        global = null;
        Environment.Variable v = scope.defineVariable(ast.getName(), ast.getName(), Environment.getType(ast.getTypeName()), ast.getMutable(), Environment.NIL);
        ast.setVariable(v);
        return null;
    }

    @Override
    public Void visit(Ast.Function ast) {
        List<Environment.Type> parameterTypes = new ArrayList<>();
        for (String parameterTypeName : ast.getParameterTypeNames()) {
            parameterTypes.add(Environment.getType(parameterTypeName));
        }
        Environment.Type returnType = Environment.Type.NIL;
        if (ast.getReturnTypeName().isPresent()) {
            returnType = Environment.getType(ast.getReturnTypeName().get());
        }
        Environment.Function f = scope.defineFunction(ast.getName(), ast.getName(), parameterTypes, returnType, args -> Environment.NIL);
        ast.setFunction(f);

        function = ast;
        scope = new Scope(scope);
        for (int i = 0; i < ast.getParameters().size(); i++) {
            scope.defineVariable(ast.getParameters().get(i), ast.getParameters().get(i), parameterTypes.get(i), true, Environment.NIL);
        }
        for (Ast.Statement stmt : ast.getStatements()) {
            visit(stmt);
        }
        scope = scope.getParent();
        function = null;

        return null;
    }

    @Override
    public Void visit(Ast.Statement.Expression ast) {
        visit(ast.getExpression());
        if (!(ast.getExpression() instanceof Ast.Expression.Function)) {
            throw new RuntimeException("Invalid expression statement.");
        }
        return null;
    }

    @Override
    public Void visit(Ast.Statement.Declaration ast) {
        if (ast.getValue().isPresent()) {
            visit(ast.getValue().get());
            if (ast.getTypeName().isPresent()) {
                requireAssignable(Environment.getType(ast.getTypeName().get()), ast.getValue().get().getType());
                Environment.Variable v = scope.defineVariable(ast.getName(), ast.getName(), Environment.getType(ast.getTypeName().get()), true, Environment.NIL);
                ast.setVariable(v);
            } else {
                Environment.Variable v = scope.defineVariable(ast.getName(), ast.getName(), ast.getValue().get().getType(), true, Environment.NIL);
                ast.setVariable(v);
            }
        } else {
            if (!ast.getTypeName().isPresent()) {
                throw new RuntimeException("Invalid declaration statement");
            }
            Environment.Variable v = scope.defineVariable(ast.getName(), ast.getName(), Environment.getType(ast.getTypeName().get()), true, Environment.NIL);
            ast.setVariable(v);
        }
        return null;
    }

    @Override
    public Void visit(Ast.Statement.Assignment ast) {
        if (!(ast.getReceiver() instanceof Ast.Expression.Access)) {
            throw new RuntimeException("Invalid assignment statement.");
        }
        visit(ast.getReceiver());
        visit(ast.getValue());
        requireAssignable(ast.getReceiver().getType(), ast.getValue().getType());
        return null;
    }

    @Override
    public Void visit(Ast.Statement.If ast) {
        visit(ast.getCondition());
        if (!ast.getCondition().getType().equals(Environment.Type.BOOLEAN) || ast.getThenStatements().isEmpty()) {
            throw new RuntimeException("Invalid if statement.");
        }
        scope = new Scope(scope);
        for (Ast.Statement stmt : ast.getThenStatements()) {
            visit(stmt);
        }
        scope = scope.getParent();
        scope = new Scope(scope);
        for (Ast.Statement stmt : ast.getElseStatements()) {
            visit(stmt);
        }
        scope = scope.getParent();
        return null;
    }

    @Override
    public Void visit(Ast.Statement.Switch ast) {
        visit(ast.getCondition());
        for (int i = 0; i < ast.getCases().size(); i++) {
            if (ast.getCases().get(i).getValue().isPresent()) {
                if (i == ast.getCases().size() - 1) {
                    throw new RuntimeException("Invalid switch statement");
                }
                visit(ast.getCases().get(i).getValue().get());
                requireAssignable(ast.getCondition().getType(), ast.getCases().get(i).getValue().get().getType());
            }
            visit(ast.getCases().get(i));
        }
        return null;
    }

    @Override
    public Void visit(Ast.Statement.Case ast) {
        scope = new Scope(scope);
        for (Ast.Statement stmt: ast.getStatements()) {
            visit(stmt);
        }
        scope = scope.getParent();
        return null;
    }

    @Override
    public Void visit(Ast.Statement.While ast) {
        visit(ast.getCondition());
        if (!ast.getCondition().getType().equals(Environment.Type.BOOLEAN)) {
            throw new RuntimeException("Invalid while statement.");
        }
        scope = new Scope(scope);
        for (Ast.Statement stmt : ast.getStatements()) {
            visit(stmt);
        }
        scope = scope.getParent();
        return null;
    }

    @Override
    public Void visit(Ast.Statement.Return ast) {
        if (function == null) {
            throw new RuntimeException("Invalid return statement.");
        }
        visit(ast.getValue());
        requireAssignable(function.getFunction().getReturnType(), ast.getValue().getType());
        return null;
    }

    @Override
    public Void visit(Ast.Expression.Literal ast) {
        if (ast.getLiteral() == null) {
            ast.setType(Environment.Type.NIL);
        } else if (ast.getLiteral() instanceof Boolean) {
            ast.setType(Environment.Type.BOOLEAN);
        } else if (ast.getLiteral() instanceof Character) {
            ast.setType(Environment.Type.CHARACTER);
        } else if (ast.getLiteral() instanceof String) {
            ast.setType(Environment.Type.STRING);
        } else if (ast.getLiteral() instanceof BigInteger) {
            ast.setType(Environment.Type.INTEGER);
            if (((BigInteger) ast.getLiteral()).compareTo(BigInteger.valueOf(Integer.MAX_VALUE)) > 0) {
                throw new RuntimeException("Invalid literal expression.");
            } else if (((BigInteger) ast.getLiteral()).compareTo(BigInteger.valueOf(Integer.MIN_VALUE)) < 0) {
                throw new RuntimeException("Invalid literal expression.");
            }
        } else if (ast.getLiteral() instanceof BigDecimal) {
            ast.setType(Environment.Type.DECIMAL);
            if (((BigDecimal) ast.getLiteral()).doubleValue() == Double.POSITIVE_INFINITY) {
                throw new RuntimeException("Invalid literal expression.");
            } else if (((BigDecimal) ast.getLiteral()).doubleValue() == Double.NEGATIVE_INFINITY) {
                throw new RuntimeException("Invalid literal expression.");
            }
        }
        return null;
    }

    @Override
    public Void visit(Ast.Expression.Group ast) {
        visit(ast.getExpression());
        if (!(ast.getExpression() instanceof Ast.Expression.Binary)) {
            throw new RuntimeException("Invalid group expression.");
        }
        ast.setType(ast.getExpression().getType());
        return null;
    }

    @Override
    public Void visit(Ast.Expression.Binary ast) {
        visit(ast.getLeft());
        visit(ast.getRight());
        switch (ast.getOperator()) {
            case "&&": case "||": {
                if (!ast.getLeft().getType().equals(Environment.Type.BOOLEAN) || !ast.getRight().getType().equals(Environment.Type.BOOLEAN)) {
                    throw new RuntimeException("Invalid binary expression.");
                }
                ast.setType(Environment.Type.BOOLEAN);
                break;
            } case "<": case ">": case "==": case "!=": {
                requireAssignable(Environment.Type.COMPARABLE, ast.getLeft().getType());
                requireAssignable(Environment.Type.COMPARABLE, ast.getRight().getType());
                if (!ast.getLeft().getType().equals(ast.getRight().getType())) {
                    throw new RuntimeException("Invalid binary expression.");
                }
                ast.setType(Environment.Type.BOOLEAN);
                break;
            } case "+": {
                if (ast.getLeft().getType().equals(Environment.Type.STRING) || ast.getRight().getType().equals(Environment.Type.STRING)) {
                    ast.setType(Environment.Type.STRING);
                } else if (ast.getLeft().getType().equals(Environment.Type.INTEGER) && ast.getRight().getType().equals(Environment.Type.INTEGER)) {
                    ast.setType(Environment.Type.INTEGER);
                } else if (ast.getLeft().getType().equals(Environment.Type.DECIMAL) && ast.getRight().getType().equals(Environment.Type.DECIMAL)) {
                    ast.setType(Environment.Type.DECIMAL);
                } else {
                    throw new RuntimeException("Invalid binary expression.");
                }
                break;
            } case "-": case "*": case "/": {
                if (ast.getLeft().getType().equals(Environment.Type.INTEGER) && ast.getRight().getType().equals(Environment.Type.INTEGER)) {
                    ast.setType(Environment.Type.INTEGER);
                } else if (ast.getLeft().getType().equals(Environment.Type.DECIMAL) && ast.getRight().getType().equals(Environment.Type.DECIMAL)) {
                    ast.setType(Environment.Type.DECIMAL);
                } else {
                    throw new RuntimeException("Invalid binary expression.");
                }
                break;
            } case "^": {
                if (!ast.getLeft().getType().equals(Environment.Type.INTEGER) || !ast.getRight().getType().equals(Environment.Type.INTEGER)) {
                    throw new RuntimeException("Invalid binary expression.");
                }
                ast.setType(Environment.Type.INTEGER);
                break;
            } default: {
                throw new RuntimeException("Invalid binary expression.");
            }
        }
        return null;
    }

    @Override
    public Void visit(Ast.Expression.Access ast) {
        if (ast.getOffset().isPresent()) {
            visit(ast.getOffset().get());
            if (!ast.getOffset().get().getType().equals(Environment.Type.INTEGER)) {
                throw new RuntimeException("Invalid access expression.");
            }
        }
        ast.setVariable(scope.lookupVariable(ast.getName()));
        return null;
    }

    @Override
    public Void visit(Ast.Expression.Function ast) {
        ast.setFunction(scope.lookupFunction(ast.getName(), ast.getArguments().size()));
        for (int i = 0; i < ast.getArguments().size(); i++) {
            visit(ast.getArguments().get(i));
            requireAssignable(ast.getFunction().getParameterTypes().get(i), ast.getArguments().get(i).getType());
        }
        return null;
    }

    @Override
    public Void visit(Ast.Expression.PlcList ast) {
        ast.setType(Environment.getType(global.getTypeName()));
        for (Ast.Expression expr : ast.getValues()) {
            visit(expr);
            requireAssignable(ast.getType(), expr.getType());
        }
        return null;
    }

    public static void requireAssignable(Environment.Type target, Environment.Type type) {
        if (target.equals(type)) {
            return;
        } else if (target.equals(Environment.Type.ANY)) {
            return;
        } else if (target.equals(Environment.Type.COMPARABLE) && (type.equals(Environment.Type.INTEGER) ||
                                                             type.equals(Environment.Type.DECIMAL) ||
                                                             type.equals(Environment.Type.CHARACTER) ||
                                                             type.equals(Environment.Type.STRING))) {
            return;
        } else {
            throw new RuntimeException("Invalid type.");
        }
    }

}
