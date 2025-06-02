package com.distributed.pitest.util;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 代码变异分析工具，用于比较原始代码和变异代码的差异
 */
public class MutationAnalyzer {
    private static final Logger logger = LoggerFactory.getLogger(MutationAnalyzer.class);

    /**
     * 分析变异描述并生成变异后的代码
     *
     * @param originalCode 原始代码
     * @param mutationDescription 变异描述
     * @param mutator 变异器类型
     * @return 变异后的代码
     */
    public String generateMutatedCode(String originalCode, String mutationDescription, String mutator) {
        if (originalCode == null || originalCode.isEmpty()) {
            return "";
        }

        try {
            // 根据变异类型进行不同的处理
            if (mutator.contains("CONDITIONALS_BOUNDARY")) {
                return handleConditionalBoundaryMutation(originalCode);
            } else if (mutator.contains("NEGATE_CONDITIONALS")) {
                return handleNegateConditionalsMutation(originalCode);
            } else if (mutator.contains("VOID_METHOD_CALL")) {
                return handleVoidMethodCallMutation(originalCode);
            } else if (mutator.contains("REMOVE_CONDITIONAL")) {
                return handleRemoveConditionalMutation(originalCode, mutationDescription);
            } else if (mutator.contains("MATH")) {
                return handleMathMutation(originalCode, mutator);
            } else if (mutator.contains("RETURN_VALS")) {
                return handleReturnValuesMutation(originalCode, mutationDescription);
            } else if (mutator.contains("CONSTRUCTOR_CALL")) {
                return handleConstructorCallMutation(originalCode);
            } else if (mutator.contains("INLINE_CONSTANT")) {
                return handleInlineConstantMutation(originalCode, mutationDescription);
            } else if (mutator.contains("NULL_RETURN")) {
                return handleNullReturnMutation(originalCode);
            } else {
                // 默认处理，添加注释表明已变异
                return "/* MUTATED: " + mutationDescription + " */ " + originalCode;
            }
        } catch (Exception e) {
            logger.warn("Error generating mutated code: {}", e.getMessage());
            // 如果解析失败，返回带注释的原始代码
            return "/* MUTATED (parsing failed): " + mutationDescription + " */ " + originalCode;
        }
    }

    /**
     * 处理条件边界变异（< 变成 <=，> 变成 >=，等）
     */
    private String handleConditionalBoundaryMutation(String originalCode) {
        try {
            JavaParser parser = new JavaParser();
            CompilationUnit cu = parser.parse(new StringReader(wrapInClass(originalCode))).getResult().orElse(null);

            if (cu == null) {
                return originalCode;
            }

            final List<BinaryExpr> conditionals = new ArrayList<>();

            // 查找条件表达式
            cu.accept(new VoidVisitorAdapter<Void>() {
                @Override
                public void visit(BinaryExpr n, Void arg) {
                    super.visit(n, arg);
                    BinaryExpr.Operator op = n.getOperator();
                    if (op == BinaryExpr.Operator.LESS || op == BinaryExpr.Operator.GREATER ||
                            op == BinaryExpr.Operator.LESS_EQUALS || op == BinaryExpr.Operator.GREATER_EQUALS) {
                        conditionals.add(n);
                    }
                }
            }, null);

            if (conditionals.isEmpty()) {
                return "/* No conditionals found to mutate */ " + originalCode;
            }

            // 修改第一个条件表达式
            BinaryExpr expr = conditionals.get(0);
            BinaryExpr.Operator oldOp = expr.getOperator();
            BinaryExpr.Operator newOp;

            switch (oldOp) {
                case LESS:
                    newOp = BinaryExpr.Operator.LESS_EQUALS;
                    break;
                case GREATER:
                    newOp = BinaryExpr.Operator.GREATER_EQUALS;
                    break;
                case LESS_EQUALS:
                    newOp = BinaryExpr.Operator.LESS;
                    break;
                case GREATER_EQUALS:
                    newOp = BinaryExpr.Operator.GREATER;
                    break;
                default:
                    return originalCode;
            }

            expr.setOperator(newOp);

            // 从包装类中提取修改后的语句
            Optional<MethodDeclaration> method = cu.findFirst(MethodDeclaration.class);
            if (method.isPresent() && !method.get().getBody().isPresent()) {
                Node statement = method.get().getBody().get().getStatement(0);
                return statement.toString();
            }

            return originalCode;

        } catch (Exception e) {
            logger.warn("Error handling conditional boundary mutation: {}", e.getMessage());
            return "/* MUTATED: conditional boundary */ " + originalCode;
        }
    }

    /**
     * 处理条件取反变异（== 变成 !=，< 变成 >=，等）
     */
    private String handleNegateConditionalsMutation(String originalCode) {
        try {
            JavaParser parser = new JavaParser();
            CompilationUnit cu = parser.parse(new StringReader(wrapInClass(originalCode))).getResult().orElse(null);

            if (cu == null) {
                return originalCode;
            }

            final List<BinaryExpr> conditionals = new ArrayList<>();

            // 查找条件表达式
            cu.accept(new VoidVisitorAdapter<Void>() {
                @Override
                public void visit(BinaryExpr n, Void arg) {
                    super.visit(n, arg);
                    BinaryExpr.Operator op = n.getOperator();
                    if (op == BinaryExpr.Operator.EQUALS || op == BinaryExpr.Operator.NOT_EQUALS ||
                            op == BinaryExpr.Operator.LESS || op == BinaryExpr.Operator.GREATER ||
                            op == BinaryExpr.Operator.LESS_EQUALS || op == BinaryExpr.Operator.GREATER_EQUALS) {
                        conditionals.add(n);
                    }
                }
            }, null);

            if (conditionals.isEmpty()) {
                return "/* No conditionals found to negate */ " + originalCode;
            }

            // 修改第一个条件表达式
            BinaryExpr expr = conditionals.get(0);
            BinaryExpr.Operator oldOp = expr.getOperator();
            BinaryExpr.Operator newOp;

            switch (oldOp) {
                case EQUALS:
                    newOp = BinaryExpr.Operator.NOT_EQUALS;
                    break;
                case NOT_EQUALS:
                    newOp = BinaryExpr.Operator.EQUALS;
                    break;
                case LESS:
                    newOp = BinaryExpr.Operator.GREATER_EQUALS;
                    break;
                case GREATER:
                    newOp = BinaryExpr.Operator.LESS_EQUALS;
                    break;
                case LESS_EQUALS:
                    newOp = BinaryExpr.Operator.GREATER;
                    break;
                case GREATER_EQUALS:
                    newOp = BinaryExpr.Operator.LESS;
                    break;
                default:
                    return originalCode;
            }

            expr.setOperator(newOp);

            // 从包装类中提取修改后的语句
            Optional<MethodDeclaration> method = cu.findFirst(MethodDeclaration.class);
            if (method.isPresent() && !method.get().getBody().isPresent()) {
                Node statement = method.get().getBody().get().getStatement(0);
                return statement.toString();
            }

            return originalCode;

        } catch (Exception e) {
            logger.warn("Error handling negate conditionals mutation: {}", e.getMessage());
            return "/* MUTATED: negated conditional */ " + originalCode;
        }
    }

    /**
     * 处理移除void方法调用的变异
     */
    private String handleVoidMethodCallMutation(String originalCode) {
        try {
            JavaParser parser = new JavaParser();
            CompilationUnit cu = parser.parse(new StringReader(wrapInClass(originalCode))).getResult().orElse(null);

            if (cu == null) {
                return originalCode;
            }

            final List<MethodCallExpr> methodCalls = new ArrayList<>();

            // 查找方法调用
            cu.accept(new VoidVisitorAdapter<Void>() {
                @Override
                public void visit(MethodCallExpr n, Void arg) {
                    super.visit(n, arg);
                    methodCalls.add(n);
                }
            }, null);

            if (methodCalls.isEmpty()) {
                return "/* No method calls found to remove */ " + originalCode;
            }

            // 创建一个方法调用表达式的副本，以便查找原位置
            MethodCallExpr methodToRemove = methodCalls.get(0);
            String methodName = methodToRemove.getNameAsString();

            // 手动处理字符串替换，将方法调用替换为空
            String mutatedCode = originalCode;
            String methodCallPattern = methodName + "\\s*\\([^)]*\\)\\s*;";
            mutatedCode = mutatedCode.replaceFirst(methodCallPattern, "/* removed call to " + methodName + "() */;");

            return mutatedCode;

        } catch (Exception e) {
            logger.warn("Error handling void method call mutation: {}", e.getMessage());
            return "/* MUTATED: removed void method call */ " + originalCode;
        }
    }

    /**
     * 处理移除条件变异（替换为true或false）
     */
    private String handleRemoveConditionalMutation(String originalCode, String description) {
        boolean replaceWithTrue = description.contains("true");
        String replacement = replaceWithTrue ? "true" : "false";

        try {
            JavaParser parser = new JavaParser();
            CompilationUnit cu = parser.parse(new StringReader(wrapInClass(originalCode))).getResult().orElse(null);

            if (cu == null) {
                return originalCode;
            }

            final List<BinaryExpr> conditionals = new ArrayList<>();

            // 查找条件表达式
            cu.accept(new VoidVisitorAdapter<Void>() {
                @Override
                public void visit(BinaryExpr n, Void arg) {
                    super.visit(n, arg);
                    conditionals.add(n);
                }
            }, null);

            if (conditionals.isEmpty()) {
                return "/* No conditionals found to replace */ " + originalCode;
            }

            // 找到第一个条件表达式并获取其文本
            BinaryExpr expr = conditionals.get(0);
            String exprStr = expr.toString();

            // 手动替换条件表达式
            String mutatedCode = originalCode.replace(exprStr, "(" + replacement + " /* was: " + exprStr + " */)");

            return mutatedCode;

        } catch (Exception e) {
            logger.warn("Error handling remove conditional mutation: {}", e.getMessage());
            return "/* MUTATED: replaced condition with " + replacement + " */ " + originalCode;
        }
    }

    /**
     * 处理数学运算变异（+ 变成 -，* 变成 /，等）
     */
    private String handleMathMutation(String originalCode, String mutator) {
        try {
            JavaParser parser = new JavaParser();
            CompilationUnit cu = parser.parse(new StringReader(wrapInClass(originalCode))).getResult().orElse(null);

            if (cu == null) {
                return originalCode;
            }

            final List<BinaryExpr> expressions = new ArrayList<>();

            // 查找数学表达式
            cu.accept(new VoidVisitorAdapter<Void>() {
                @Override
                public void visit(BinaryExpr n, Void arg) {
                    super.visit(n, arg);
                    BinaryExpr.Operator op = n.getOperator();
                    if (op == BinaryExpr.Operator.PLUS || op == BinaryExpr.Operator.MINUS ||
                            op == BinaryExpr.Operator.MULTIPLY || op == BinaryExpr.Operator.DIVIDE) {
                        expressions.add(n);
                    }
                }
            }, null);

            if (expressions.isEmpty()) {
                return "/* No math expressions found to mutate */ " + originalCode;
            }

            // 修改第一个数学表达式
            BinaryExpr expr = expressions.get(0);
            BinaryExpr.Operator oldOp = expr.getOperator();
            BinaryExpr.Operator newOp;

            if (mutator.contains("PLUS")) {
                newOp = BinaryExpr.Operator.MINUS;
            } else if (mutator.contains("MINUS")) {
                newOp = BinaryExpr.Operator.PLUS;
            } else if (mutator.contains("MULTIPLY")) {
                newOp = BinaryExpr.Operator.DIVIDE;
            } else if (mutator.contains("DIVIDE")) {
                newOp = BinaryExpr.Operator.MULTIPLY;
            } else {
                // 默认反转操作
                switch (oldOp) {
                    case PLUS:
                        newOp = BinaryExpr.Operator.MINUS;
                        break;
                    case MINUS:
                        newOp = BinaryExpr.Operator.PLUS;
                        break;
                    case MULTIPLY:
                        newOp = BinaryExpr.Operator.DIVIDE;
                        break;
                    case DIVIDE:
                        newOp = BinaryExpr.Operator.MULTIPLY;
                        break;
                    default:
                        return originalCode;
                }
            }

            expr.setOperator(newOp);

            // 从包装类中提取修改后的语句
            Optional<MethodDeclaration> method = cu.findFirst(MethodDeclaration.class);
            if (method.isPresent() && !method.get().getBody().isPresent()) {
                Node statement = method.get().getBody().get().getStatement(0);
                return statement.toString();
            }

            return originalCode;

        } catch (Exception e) {
            logger.warn("Error handling math mutation: {}", e.getMessage());
            return "/* MUTATED: math operation */ " + originalCode;
        }
    }

    /**
     * 处理返回值变异
     */
    private String handleReturnValuesMutation(String originalCode, String description) {
        // 简化实现，只处理返回值的替换
        if (originalCode.contains("return ")) {
            if (description.contains("null")) {
                return originalCode.replaceFirst("return [^;]+;", "return null; /* was: " +
                        originalCode.substring(originalCode.indexOf("return "),
                                originalCode.indexOf(";", originalCode.indexOf("return ")) + 1) +
                        " */");
            } else if (description.contains("true")) {
                return originalCode.replaceFirst("return [^;]+;", "return true; /* was: " +
                        originalCode.substring(originalCode.indexOf("return "),
                                originalCode.indexOf(";", originalCode.indexOf("return ")) + 1) +
                        " */");
            } else if (description.contains("false")) {
                return originalCode.replaceFirst("return [^;]+;", "return false; /* was: " +
                        originalCode.substring(originalCode.indexOf("return "),
                                originalCode.indexOf(";", originalCode.indexOf("return ")) + 1) +
                        " */");
            } else if (description.contains("0")) {
                return originalCode.replaceFirst("return [^;]+;", "return 0; /* was: " +
                        originalCode.substring(originalCode.indexOf("return "),
                                originalCode.indexOf(";", originalCode.indexOf("return ")) + 1) +
                        " */");
            } else if (description.contains("empty")) {
                return originalCode.replaceFirst("return [^;]+;", "return \"\"; /* was: " +
                        originalCode.substring(originalCode.indexOf("return "),
                                originalCode.indexOf(";", originalCode.indexOf("return ")) + 1) +
                        " */");
            }
        }

        return "/* MUTATED: return value */ " + originalCode;
    }

    /**
     * 处理构造函数调用变异
     */
    private String handleConstructorCallMutation(String originalCode) {
        // 查找 new ClassName(...) 模式
        if (originalCode.contains("new ")) {
            int newIndex = originalCode.indexOf("new ");
            int openParenIndex = originalCode.indexOf("(", newIndex);
            int closeParenIndex = findMatchingCloseParen(originalCode, openParenIndex);

            if (openParenIndex > newIndex && closeParenIndex > openParenIndex) {
                String className = originalCode.substring(newIndex + 4, openParenIndex).trim();
                String constructorCall = originalCode.substring(newIndex, closeParenIndex + 1);

                // 替换为null
                return originalCode.replace(constructorCall, "null /* was: " + constructorCall + " */");
            }
        }

        return "/* MUTATED: constructor call */ " + originalCode;
    }

    /**
     * 处理内联常量变异
     */
    private String handleInlineConstantMutation(String originalCode, String description) {
        // 根据描述查找要替换的常量
        if (description.contains("Replaced ")) {
            String[] parts = description.substring(description.indexOf("Replaced ") + 9).split(" with ");
            if (parts.length >= 2) {
                String original = parts[0].trim();
                String replacement = parts[1].trim().replaceAll("[.;].*$", ""); // 移除结尾的标点

                // 替换常量
                return originalCode.replace(original, replacement + " /* was: " + original + " */");
            }
        }

        return "/* MUTATED: inline constant */ " + originalCode;
    }

    /**
     * 处理null返回值变异
     */
    private String handleNullReturnMutation(String originalCode) {
        // 查找return语句
        if (originalCode.contains("return ") && !originalCode.contains("return null")) {
            int returnIndex = originalCode.indexOf("return ");
            int semicolonIndex = originalCode.indexOf(";", returnIndex);

            if (semicolonIndex > returnIndex) {
                String returnExpr = originalCode.substring(returnIndex, semicolonIndex + 1);

                // 替换为null返回
                return originalCode.replace(returnExpr, "return null; /* was: " + returnExpr + " */");
            }
        }

        return "/* MUTATED: null return */ " + originalCode;
    }

    /**
     * 查找匹配的右括号
     */
    private int findMatchingCloseParen(String code, int openParenIndex) {
        if (openParenIndex < 0 || openParenIndex >= code.length() || code.charAt(openParenIndex) != '(') {
            return -1;
        }

        int level = 1;
        for (int i = openParenIndex + 1; i < code.length(); i++) {
            char c = code.charAt(i);
            if (c == '(') {
                level++;
            } else if (c == ')') {
                level--;
                if (level == 0) {
                    return i;
                }
            }
        }

        return -1;
    }

    /**
     * 将代码片段包装在类和方法中以便解析
     */
    private String wrapInClass(String code) {
        return "class Wrapper { void method() { " + code + " } }";
    }
}