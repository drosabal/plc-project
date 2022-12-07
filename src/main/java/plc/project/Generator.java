package plc.project;

import java.io.PrintWriter;

public final class Generator implements Ast.Visitor<Void> {

    private final PrintWriter writer;
    private int indent = 0;

    public Generator(PrintWriter writer) {
        this.writer = writer;
    }

    private void print(Object... objects) {
        for (Object object : objects) {
            if (object instanceof Ast) {
                visit((Ast) object);
            } else {
                writer.write(object.toString());
            }
        }
    }

    private void newline(int indent) {
        writer.println();
        for (int i = 0; i < indent; i++) {
            writer.write("    ");
        }
    }

    @Override
    public Void visit(Ast.Source ast) {
        print("public class Main {");
        newline(0);
        ++indent;

        if (ast.getGlobals().size() > 0) {
            for (Ast.Global global : ast.getGlobals()) {
                newline(indent);
                print(global);
            }
            newline(0);
        }

        newline(indent);
        print("public static void main(String[] args) {");
        newline(++indent);
        print("System.exit(new Main().main());");
        newline(--indent);
        print("}");
        newline(0);

        for (Ast.Function function : ast.getFunctions()) {
            newline(indent);
            print(function);
            newline(0);
        }

        newline(--indent);
        print("}");

        return null;
    }

    @Override
    public Void visit(Ast.Global ast) {
        if (!ast.getMutable()) {
            print("final ");
        }
        print(ast.getVariable().getType().getJvmName());
        if (ast.getValue().isPresent() && ast.getValue().get() instanceof Ast.Expression.PlcList) {
            print("[]");
        }
        print(" ", ast.getVariable().getJvmName());
        if (ast.getValue().isPresent()) {
            print(" = ", ast.getValue().get());
        }
        print(";");
        return null;
    }

    @Override
    public Void visit(Ast.Function ast) {
        print(ast.getFunction().getReturnType().getJvmName(), " ", ast.getFunction().getJvmName(), "(");
        for (int i = 0; i < ast.getFunction().getArity(); i++) {
            print(ast.getFunction().getParameterTypes().get(i).getJvmName(), " ", ast.getParameters().get(i));
            if (i != ast.getFunction().getArity() - 1) {
                print(", ");
            }
        }
        print(") {");
        if (!ast.getStatements().isEmpty()) {
            ++indent;
            for (Ast.Statement stmt : ast.getStatements()) {
                newline(indent);
                print(stmt);
            }
            newline(--indent);
        }
        print("}");
        return null;
    }

    @Override
    public Void visit(Ast.Statement.Expression ast) {
        print(ast.getExpression(), ";");
        return null;
    }

    @Override
    public Void visit(Ast.Statement.Declaration ast) {
        print(ast.getVariable().getType().getJvmName(), " ", ast.getVariable().getJvmName());
        if (ast.getValue().isPresent()) {
            print(" = ", ast.getValue().get());
        }
        print(";");
        return null;
    }

    @Override
    public Void visit(Ast.Statement.Assignment ast) {
        print(ast.getReceiver(), " = ", ast.getValue(), ";");
        return null;
    }

    @Override
    public Void visit(Ast.Statement.If ast) {
        print("if (", ast.getCondition(), ") {");
        ++indent;
        for (Ast.Statement stmt : ast.getThenStatements()) {
            newline(indent);
            print(stmt);
        }
        newline(--indent);
        print("}");
        if (!ast.getElseStatements().isEmpty()) {
            print(" else {");
            ++indent;
            for (Ast.Statement stmt : ast.getElseStatements()) {
                newline(indent);
                print(stmt);
            }
            newline(--indent);
            print("}");
        }
        return null;
    }

    @Override
    public Void visit(Ast.Statement.Switch ast) {
        print("switch (", ast.getCondition(), ") {");
        ++indent;
        for (Ast.Statement.Case c : ast.getCases()) {
            newline(indent);
            print(c);
        }
        newline(--indent);
        print("}");
        return null;
    }

    @Override
    public Void visit(Ast.Statement.Case ast) {
        if (ast.getValue().isPresent()) {
            print("case ", ast.getValue().get(), ":");
        } else {
            print("default:");
        }
        ++indent;
        for (Ast.Statement stmt : ast.getStatements()) {
            newline(indent);
            print(stmt);
        }
        if (ast.getValue().isPresent()) {
            newline(indent);
            print("break;");
        }
        --indent;
        return null;
    }

    @Override
    public Void visit(Ast.Statement.While ast) {
        print("while (", ast.getCondition(), ") {");
        if (!ast.getStatements().isEmpty()) {
            ++indent;
            for (Ast.Statement stmt : ast.getStatements()) {
                newline(indent);
                print(stmt);
            }
            newline(--indent);
        }
        print("}");
        return null;
    }

    @Override
    public Void visit(Ast.Statement.Return ast) {
        print("return ", ast.getValue(), ";");
        return null;
    }

    @Override
    public Void visit(Ast.Expression.Literal ast) {
        if (ast.getLiteral() instanceof Character) {
            print("'", ast.getLiteral(), "'");
        } else if (ast.getLiteral() instanceof String) {
            print("\"", ast.getLiteral(), "\"");
        } else {
            print(ast.getLiteral());
        }
        return null;
    }

    @Override
    public Void visit(Ast.Expression.Group ast) {
        print("(", ast.getExpression(), ")");
        return null;
    }

    @Override
    public Void visit(Ast.Expression.Binary ast) {
        if (ast.getOperator().equals("^")) {
            print("Math.pow(", ast.getLeft(), ", ", ast.getRight(), ")");
        } else {
            print(ast.getLeft(), " ", ast.getOperator(), " ", ast.getRight());
        }
        return null;
    }

    @Override
    public Void visit(Ast.Expression.Access ast) {
        print(ast.getVariable().getJvmName());
        if (ast.getOffset().isPresent()) {
            print("[", ast.getOffset().get(), "]");
        }
        return null;
    }

    @Override
    public Void visit(Ast.Expression.Function ast) {
        print(ast.getFunction().getJvmName(), "(");
        for (int i = 0; i < ast.getArguments().size(); i++) {
            print(ast.getArguments().get(i));
            if (i != ast.getArguments().size() - 1) {
                print(", ");
            }
        }
        print(")");
        return null;
    }

    @Override
    public Void visit(Ast.Expression.PlcList ast) {
        print("{");
        for (int i = 0; i < ast.getValues().size(); i++){
            print(ast.getValues().get(i));
            if (i != ast.getValues().size() - 1) {
                print(", ");
            }
        }
        print("}");
        return null;
    }

}
