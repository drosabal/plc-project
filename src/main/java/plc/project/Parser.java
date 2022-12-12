package plc.project;

import java.math.BigInteger;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The parser takes the sequence of tokens emitted by the lexer and turns that
 * into a structured representation of the program, called the Abstract Syntax
 * Tree (AST).
 *
 * The parser has a similar architecture to the lexer, just with {@link Token}s
 * instead of characters. As before, {@link #peek(Object...)} and {@link
 * #match(Object...)} are helpers to make the implementation easier.
 *
 * This type of parser is called <em>recursive descent</em>. Each rule in our
 * grammar will have it's own function, and reference to other rules correspond
 * to calling that functions.
 */
public final class Parser {

    private final TokenStream tokens;

    public Parser(List<Token> tokens) {
        this.tokens = new TokenStream(tokens);
    }

    /**
     * Parses the {@code source} rule.
     */
    public Ast.Source parseSource() throws ParseException {
        List<Ast.Global> globals = new ArrayList<>();
        List<Ast.Function> functions = new ArrayList<>();
        while(peek("LIST") || peek("VAR") || peek("VAL")) {
            globals.add(parseGlobal());
        }
        while(peek("FUN")) {
            functions.add(parseFunction());
        }
        if (tokens.has(0)) {
            throwParseException("Not a global or function.");
        }
        return new Ast.Source(globals, functions);
    }

    /**
     * Parses the {@code field} rule. This method should only be called if the
     * next tokens start a global, aka {@code LIST|VAL|VAR}.
     */
    public Ast.Global parseGlobal() throws ParseException {
        Ast.Global global;
        if (match("LIST")) {
            global = parseList();
        } else if (match("VAR")) {
            global = parseMutable();
        } else {
            match("VAL");
            global = parseImmutable();
        }
        if (!match(";")) {
            throwParseException("Invalid global.");
        }
        return global;
    }

    /**
     * Parses the {@code list} rule. This method should only be called if the
     * next token declares a list, aka {@code LIST}.
     */
    public Ast.Global parseList() throws ParseException {
        List<Ast.Expression> values = new ArrayList<>();
        if (!match(Token.Type.IDENTIFIER)) {
            throwParseException("Invalid list.");
        }
        String name = tokens.get(-1).getLiteral();
        if (!match(":")) {
            throwParseException("Invalid list.");
        }
        if (!match(Token.Type.IDENTIFIER)) {
            throwParseException("Invalid list.");
        }
        String typeName = tokens.get(-1).getLiteral();
        if (!match("=")) {
            throwParseException("Invalid list.");
        }
        if (!match("[")) {
            throwParseException("Invalid list.");
        }
        values.add(parseExpression());
        while (match(",")) {
            values.add(parseExpression());
        }
        if (!match("]")) {
            throwParseException("Invalid list.");
        }
        return new Ast.Global(name, typeName, true, Optional.of(new Ast.Expression.PlcList(values)));
    }

    /**
     * Parses the {@code mutable} rule. This method should only be called if the
     * next token declares a mutable global variable, aka {@code VAR}.
     */
    public Ast.Global parseMutable() throws ParseException {
        Ast.Global mutable;
        if (!match(Token.Type.IDENTIFIER)) {
            throwParseException("Invalid mutable.");
        }
        String name = tokens.get(-1).getLiteral();
        if (!match(":")) {
            throwParseException("Invalid mutable.");
        }
        if (!match(Token.Type.IDENTIFIER)) {
            throwParseException("Invalid mutable.");
        }
        String typeName = tokens.get(-1).getLiteral();
        if (match("=")) {
            mutable = new Ast.Global(name, typeName, true, Optional.of(parseExpression()));
        } else {
            mutable = new Ast.Global(name, typeName, true, Optional.empty());
        }
        return mutable;
    }

    /**
     * Parses the {@code immutable} rule. This method should only be called if the
     * next token declares an immutable global variable, aka {@code VAL}.
     */
    public Ast.Global parseImmutable() throws ParseException {
        if (!match(Token.Type.IDENTIFIER)) {
            throwParseException("Invalid immutable.");
        }
        String name = tokens.get(-1).getLiteral();
        if (!match(":")) {
            throwParseException("Invalid immutable.");
        }
        if (!match(Token.Type.IDENTIFIER)) {
            throwParseException("Invalid immutable.");
        }
        String typeName = tokens.get(-1).getLiteral();
        if (!match("=")) {
            throwParseException("Invalid immutable.");
        }
        return new Ast.Global(name, typeName, false, Optional.of(parseExpression()));
    }

    /**
     * Parses the {@code function} rule. This method should only be called if the
     * next tokens start a method, aka {@code FUN}.
     */
    public Ast.Function parseFunction() throws ParseException {
        List<String> parameters = new ArrayList<>();
        List<String> parameterTypeNames = new ArrayList<>();
        Optional<String> returnTypeName = Optional.empty();
        List<Ast.Statement> statements;
        match("FUN");
        if (!match(Token.Type.IDENTIFIER)) {
            throwParseException("Invalid function.");
        }
        String name = tokens.get(-1).getLiteral();
        if (!match("(")) {
            throwParseException("Invalid function.");
        }
        if (match(Token.Type.IDENTIFIER)) {
            parameters.add(tokens.get(-1).getLiteral());
            if (!match(":")) {
                throwParseException("Invalid function.");
            }
            if (!match(Token.Type.IDENTIFIER)) {
                throwParseException("Invalid function.");
            }
            parameterTypeNames.add(tokens.get(-1).getLiteral());
            while (match(",")) {
                if (!match(Token.Type.IDENTIFIER)) {
                    throwParseException("Invalid function.");
                }
                parameters.add(tokens.get(-1).getLiteral());
                if (!match(":")) {
                    throwParseException("Invalid function.");
                }
                if (!match(Token.Type.IDENTIFIER)) {
                    throwParseException("Invalid function.");
                }
                parameterTypeNames.add(tokens.get(-1).getLiteral());
            }
        }
        if (!match(")")) {
            throwParseException("Invalid function.");
        }
        if (match(":")) {
            if (!match(Token.Type.IDENTIFIER)) {
                throwParseException("Invalid function.");
            }
            returnTypeName = Optional.of(tokens.get(-1).getLiteral());
        }
        if (!match("DO")) {
            throwParseException("Invalid function.");
        }
        statements = parseBlock();
        if (!match("END")) {
            throwParseException("Invalid function.");
        }
        return new Ast.Function(name, parameters, parameterTypeNames, returnTypeName, statements);
    }

    /**
     * Parses the {@code block} rule. This method should only be called if the
     * preceding token indicates the opening a block.
     */
    public List<Ast.Statement> parseBlock() throws ParseException {
        List<Ast.Statement> block = new ArrayList<>();
        while (!(peek("END") || peek("ELSE") || peek("CASE") || peek("DEFAULT"))) {
            block.add(parseStatement());
        }
        return block;
    }

    /**
     * Parses the {@code statement} rule and delegates to the necessary method.
     * If the next tokens do not start a declaration, if, while, or return
     * statement, then it is an expression/assignment statement.
     */
    public Ast.Statement parseStatement() throws ParseException {
        Ast.Statement statement;
        if (match("LET")) {
            statement = parseDeclarationStatement();
        } else if (match("SWITCH")) {
            statement = parseSwitchStatement();
        } else if (match("IF")) {
            statement = parseIfStatement();
        } else if (match("WHILE")) {
            statement = parseWhileStatement();
        } else if (match("RETURN")) {
            statement = parseReturnStatement();
        } else {
            Ast.Expression left = parseExpression();
            if (match("=")) {
                statement = new Ast.Statement.Assignment(left, parseExpression());
            } else {
                statement = new Ast.Statement.Expression(left);
            }
            if (!match(";")) {
                throwParseException("Invalid statement.");
            }
        }
        return statement;
    }

    /**
     * Parses a declaration statement from the {@code statement} rule. This
     * method should only be called if the next tokens start a declaration
     * statement, aka {@code LET}.
     */
    public Ast.Statement.Declaration parseDeclarationStatement() throws ParseException {
        Ast.Statement.Declaration declarationStatement;
        Optional<String> typeName = Optional.empty();
        if (!match(Token.Type.IDENTIFIER)) {
            throwParseException("Invalid declaration statement.");
        }
        String name = tokens.get(-1).getLiteral();
        if (match(":")) {
            if (!match(Token.Type.IDENTIFIER)) {
                throwParseException("Invalid declaration statement.");
            }
            typeName = Optional.of(tokens.get(-1).getLiteral());
        }
        if (match("=")) {
            declarationStatement = new Ast.Statement.Declaration(name, typeName, Optional.of(parseExpression()));
        } else {
            declarationStatement = new Ast.Statement.Declaration(name, typeName, Optional.empty());
        }
        if (!match(";")) {
            throwParseException("Invalid declaration statement.");
        }
        return declarationStatement;
    }

    /**
     * Parses an if statement from the {@code statement} rule. This method
     * should only be called if the next tokens start an if statement, aka
     * {@code IF}.
     */
    public Ast.Statement.If parseIfStatement() throws ParseException {
        Ast.Expression condition = parseExpression();
        List<Ast.Statement> thenStatements;
        List<Ast.Statement> elseStatements = new ArrayList<>();
        if (!match("DO")) {
            throwParseException("Invalid if statement.");
        }
        thenStatements = parseBlock();
        if (match("ELSE")) {
            elseStatements = parseBlock();
        }
        if (!match("END")) {
            throwParseException("Invalid if statement.");
        }
        return new Ast.Statement.If(condition, thenStatements, elseStatements);
    }

    /**
     * Parses a switch statement from the {@code statement} rule. This method
     * should only be called if the next tokens start a switch statement, aka
     * {@code SWITCH}.
     */
    public Ast.Statement.Switch parseSwitchStatement() throws ParseException {
        Ast.Expression condition = parseExpression();
        List<Ast.Statement.Case> cases = new ArrayList<>();
        while (match("CASE")) {
            cases.add(parseCaseStatement());
        }
        if (!match("DEFAULT")) {
            throwParseException("Invalid switch statement.");
        }
        cases.add(new Ast.Statement.Case(Optional.empty(), parseBlock()));
        if (!match("END")) {
            throwParseException("Invalid switch statement.");
        }
        return new Ast.Statement.Switch(condition, cases);
    }

    /**
     * Parses a case or default statement block from the {@code switch} rule. 
     * This method should only be called if the next tokens start the case or 
     * default block of a switch statement, aka {@code CASE} or {@code DEFAULT}.
     */
    public Ast.Statement.Case parseCaseStatement() throws ParseException {
        Ast.Expression value = parseExpression();
        List<Ast.Statement> statements;
        if (!match(":")) {
            throwParseException("Invalid case statement.");
        }
        statements = parseBlock();
        return new Ast.Statement.Case(Optional.of(value), statements);
    }

    /**
     * Parses a while statement from the {@code statement} rule. This method
     * should only be called if the next tokens start a while statement, aka
     * {@code WHILE}.
     */
    public Ast.Statement.While parseWhileStatement() throws ParseException {
        Ast.Expression condition = parseExpression();
        List<Ast.Statement> statements;
        if (!match("DO")) {
            throwParseException("Invalid while statement.");
        }
        statements = parseBlock();
        if (!match("END")) {
            throwParseException("Invalid while statement.");
        }
        return new Ast.Statement.While(condition, statements);
    }

    /**
     * Parses a return statement from the {@code statement} rule. This method
     * should only be called if the next tokens start a return statement, aka
     * {@code RETURN}.
     */
    public Ast.Statement.Return parseReturnStatement() throws ParseException {
        Ast.Expression value = parseExpression();
        if (!match(";")) {
            throwParseException("Invalid return statement.");
        }
        return new Ast.Statement.Return(value);
    }

    /**
     * Parses the {@code expression} rule.
     */
    public Ast.Expression parseExpression() throws ParseException {
        return parseLogicalExpression();
    }

    /**
     * Parses the {@code logical-expression} rule.
     */
    public Ast.Expression parseLogicalExpression() throws ParseException {
        Ast.Expression logicalExpression = parseComparisonExpression();
        while (peek("&&") || peek("||")) {
            match(Token.Type.OPERATOR);
            logicalExpression = new Ast.Expression.Binary(
                    tokens.get(-1).getLiteral(), logicalExpression, parseComparisonExpression()
            );
        }
        return logicalExpression;
    }

    /**
     * Parses the {@code equality-expression} rule.
     */
    public Ast.Expression parseComparisonExpression() throws ParseException {
        Ast.Expression comparisonExpression = parseAdditiveExpression();
        while (peek("<") || peek(">") || peek("==") || peek("!=")) {
            match(Token.Type.OPERATOR);
            comparisonExpression = new Ast.Expression.Binary(
                    tokens.get(-1).getLiteral(), comparisonExpression, parseAdditiveExpression()
            );
        }
        return comparisonExpression;
    }

    /**
     * Parses the {@code additive-expression} rule.
     */
    public Ast.Expression parseAdditiveExpression() throws ParseException {
        Ast.Expression additiveExpression = parseMultiplicativeExpression();
        while (peek("+") || peek("-")) {
            match(Token.Type.OPERATOR);
            additiveExpression = new Ast.Expression.Binary(
                    tokens.get(-1).getLiteral(), additiveExpression, parseMultiplicativeExpression()
            );
        }
        return additiveExpression;
    }

    /**
     * Parses the {@code multiplicative-expression} rule.
     */
    public Ast.Expression parseMultiplicativeExpression() throws ParseException {
        Ast.Expression multiplicativeExpression = parsePrimaryExpression();
        while (peek("*") || peek("/") || peek("^")) {
            match(Token.Type.OPERATOR);
            multiplicativeExpression = new Ast.Expression.Binary(
                    tokens.get(-1).getLiteral(), multiplicativeExpression, parsePrimaryExpression()
            );
        }
        return multiplicativeExpression;
    }

    /**
     * Parses the {@code primary-expression} rule. This is the top-level rule
     * for expressions and includes literal values, grouping, variables, and
     * functions. It may be helpful to break these up into other methods but is
     * not strictly necessary.
     */
    public Ast.Expression parsePrimaryExpression() throws ParseException {
        Ast.Expression primaryExpression;
        if (match("NIL")) {
            primaryExpression = new Ast.Expression.Literal(null);
        } else if (match("TRUE")) {
            primaryExpression = new Ast.Expression.Literal(Boolean.TRUE);
        } else if (match("FALSE")) {
            primaryExpression = new Ast.Expression.Literal(Boolean.FALSE);
        } else if (match(Token.Type.INTEGER)) {
             primaryExpression = new Ast.Expression.Literal(new BigInteger(tokens.get(-1).getLiteral()));
        } else if (match(Token.Type.DECIMAL)) {
            primaryExpression = new Ast.Expression.Literal(new BigDecimal(tokens.get(-1).getLiteral()));
        } else if (match(Token.Type.CHARACTER)) {
            Character c;
            if (tokens.get(-1).getLiteral().charAt(1) == '\\') {
                if (tokens.get(-1).getLiteral().charAt(2) == 'b') {
                    c = '\b';
                } else if (tokens.get(-1).getLiteral().charAt(2) == 'n') {
                    c = '\n';
                } else if (tokens.get(-1).getLiteral().charAt(2) == 'r') {
                    c = '\r';
                } else if (tokens.get(-1).getLiteral().charAt(2) == 't') {
                    c = '\t';
                } else if (tokens.get(-1).getLiteral().charAt(2) == '\'') {
                    c = '\'';
                } else if (tokens.get(-1).getLiteral().charAt(2) == '\"') {
                    c = '\"';
                } else {
                    c = '\\';
                }
            } else {
                c = tokens.get(-1).getLiteral().charAt(1);
            }
            primaryExpression = new Ast.Expression.Literal(c);
        } else if (match(Token.Type.STRING)) {
            String s = tokens.get(-1).getLiteral();
            s = s.substring(1, s.length() - 1);
            s = s.replaceAll("\\\\b", "\b");
            s = s.replaceAll("\\\\n", "\n");
            s = s.replaceAll("\\\\r", "\r");
            s = s.replaceAll("\\\\t", "\t");
            s = s.replaceAll("\\\\'", "\'");
            s = s.replaceAll("\\\\\"", "\"");
            s = s.replaceAll("\\\\\\\\", "\\\\");
            primaryExpression = new Ast.Expression.Literal(s);
        } else if (match("(")) {
            primaryExpression = new Ast.Expression.Group(parseExpression());
            if (!match(")")) {
                throwParseException("Invalid primary expression.");
            }
        } else if (match(Token.Type.IDENTIFIER)) {
            String name = tokens.get(-1).getLiteral();
            if (match("(")) {
                List<Ast.Expression> arguments = new ArrayList<>();
                if (!match(")")) {
                    arguments.add(parseExpression());
                    while (match(",")) {
                        arguments.add(parseExpression());
                    }
                    if (!match(")")) {
                        throwParseException("Invalid primary expression.");
                    }
                }
                primaryExpression = new Ast.Expression.Function(name, arguments);
            } else if (match("[")) {
                primaryExpression = new Ast.Expression.Access(Optional.of(parseExpression()), name);
                if (!match("]")) {
                    throwParseException("Invalid primary expression.");
                }
            } else {
                primaryExpression = new Ast.Expression.Access(Optional.empty(), name);
            }
        } else {
            primaryExpression = null;
            throwParseException("Invalid primary expression.");
        }
        return primaryExpression;
    }

    private void throwParseException(String message) throws ParseException {
        if (tokens.has(0)) {
            throw new ParseException(message, tokens.get(0).getIndex());
        } else {
            throw new ParseException(message, tokens.get(-1).getIndex() + tokens.get(-1).getLiteral().length());
        }
    }

    /**
     * As in the lexer, returns {@code true} if the current sequence of tokens
     * matches the given patterns. Unlike the lexer, the pattern is not a regex;
     * instead it is either a {@link Token.Type}, which matches if the token's
     * type is the same, or a {@link String}, which matches if the token's
     * literal is the same.
     *
     * In other words, {@code Token(IDENTIFIER, "literal")} is matched by both
     * {@code peek(Token.Type.IDENTIFIER)} and {@code peek("literal")}.
     */
    private boolean peek(Object... patterns) {
        for (int i = 0; i < patterns.length; i++) {
            if (!tokens.has(i)) {
                return false;
            } else if (patterns[i] instanceof Token.Type) {
                if (patterns[i] != tokens.get(i).getType()) {
                    return false;
                }
            } else if (patterns[i] instanceof String) {
                if (!patterns[i].equals(tokens.get(i).getLiteral())) {
                    return false;
                }
            } else {
                throw new AssertionError("Invalid pattern object: " + patterns[i].getClass());
            }
        }
        return true;
    }

    /**
     * As in the lexer, returns {@code true} if {@link #peek(Object...)} is true
     * and advances the token stream.
     */
    private boolean match(Object... patterns) {
        boolean peek = peek(patterns);
        if (peek) {
            for (int i = 0; i < patterns.length; i++) {
                tokens.advance();
            }
        }
        return peek;
    }

    private static final class TokenStream {

        private final List<Token> tokens;
        private int index = 0;

        private TokenStream(List<Token> tokens) {
            this.tokens = tokens;
        }

        /**
         * Returns true if there is a token at index + offset.
         */
        public boolean has(int offset) {
            return index + offset < tokens.size();
        }

        /**
         * Gets the token at index + offset.
         */
        public Token get(int offset) {
            return tokens.get(index + offset);
        }

        /**
         * Advances to the next token, incrementing the index.
         */
        public void advance() {
            index++;
        }

    }

}
