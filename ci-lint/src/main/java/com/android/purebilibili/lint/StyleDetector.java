package com.android.purebilibili.lint;

import com.android.tools.lint.client.api.UElementHandler;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.android.tools.lint.detector.api.SourceSetType;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMember;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiType;
import com.intellij.psi.util.PsiTreeUtil;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.jetbrains.kotlin.psi.KtBlockExpression;
import org.jetbrains.kotlin.psi.KtClassBody;
import org.jetbrains.kotlin.psi.KtClassOrObject;
import org.jetbrains.kotlin.psi.KtExpression;
import org.jetbrains.kotlin.psi.KtFile;
import org.jetbrains.kotlin.psi.KtImportDirective;
import org.jetbrains.kotlin.psi.KtLambdaExpression;
import org.jetbrains.kotlin.psi.KtNamedFunction;
import org.jetbrains.kotlin.psi.KtParameter;
import org.jetbrains.kotlin.psi.KtProperty;
import org.jetbrains.kotlin.psi.KtReturnExpression;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UExpression;
import org.jetbrains.uast.ULiteralExpression;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.UNamedExpression;
import org.jetbrains.uast.UParenthesizedExpression;
import org.jetbrains.uast.UPrefixExpression;
import org.jetbrains.uast.UQualifiedReferenceExpression;
import org.jetbrains.uast.USimpleNameReferenceExpression;
import org.jetbrains.uast.UastPrefixOperator;
import org.jetbrains.uast.UastContextKt;
import org.jetbrains.uast.UastUtils;

/** Resolved Compose APIs in the six migrated feature packages, not source-text patterns. */
public final class StyleDetector extends Detector implements SourceCodeScanner {
    private static final Implementation IMPLEMENTATION =
            new Implementation(StyleDetector.class, Scope.JAVA_FILE_SCOPE);
    private static final String FEATURE = "com.android.purebilibili.feature.";
    private static final Set<String> MIGRATED =
            Set.of("live", "home", "dynamic", "following", "list", "watchlater");
    private static final Set<String> NAMED_COLORS = Set.of(
            "Black", "White", "Red", "Blue", "Green", "Yellow", "Gray", "LightGray", "DarkGray");
    private static final Set<String> STYLE_VALUE_TYPES = Set.of(
            "float", "double", "int", "long", "boolean",
            "androidx.compose.ui.unit.Dp", "androidx.compose.ui.unit.TextUnit",
            "androidx.compose.ui.graphics.Color", "androidx.compose.ui.graphics.Shape",
            "androidx.compose.animation.core.AnimationSpec",
            "androidx.compose.animation.core.FiniteAnimationSpec",
            "androidx.compose.animation.core.SpringSpec", "androidx.compose.animation.core.TweenSpec");

    public static final Issue COLOR = issue("BiliPaiHardcodedColor", "Literal Compose color",
            "Use theme roles or an explicitly named Palette/Policy token instead of a numeric "
                    + "Compose Color or one of the fixed named colors. Color.Transparent is allowed.");
    public static final Issue DIMENSION = issue("BiliPaiHardcodedDimension", "Literal Compose dimension",
            "Use a named dimension token or a value-producing style policy. This includes size, "
                    + "spacing, offset and effect radius; it is not a claim of a layout defect. "
                    + "Zero-valued identities and lower bounds are allowed in their resolved API roles.");
    public static final Issue TYPOGRAPHY = issue("BiliPaiHardcodedTypography", "Literal Compose text unit",
            "Use theme typography or a named typography token instead of converting a numeric literal to sp.");
    public static final Issue SHAPE = issue("BiliPaiHardcodedShape", "Literal rounded corner dimension",
            "Use a named shape token instead of supplying a numeric dp literal to Compose RoundedCornerShape.");
    public static final Issue MOTION = issue("BiliPaiHardcodedMotion", "Literal animation parameter",
            "Use a named motion token instead of supplying a numeric literal to Compose tween or spring.");
    public static final Issue SURFACE = issue("BiliPaiHardcodedSurface", "Direct base theme surface read",
            "Use a named surface role instead of directly reading MaterialTheme.colorScheme.surface or background.");
    public static final List<Issue> ISSUES = List.of(COLOR, DIMENSION, TYPOGRAPHY, SHAPE, MOTION, SURFACE);

    private static Issue issue(String id, String title, String explanation) {
        return Issue.create(id, title, explanation, Category.USABILITY, 6, Severity.ERROR, IMPLEMENTATION);
    }

    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return List.of(UCallExpression.class, USimpleNameReferenceExpression.class);
    }

    @Override
    public UElementHandler createUastHandler(JavaContext context) {
        if (context.getSourceSetType() != SourceSetType.MAIN || context.getUastFile() == null
                || !migratedPackage(context.getUastFile().getPackageName())) {
            return null;
        }
        Map<KtNamedFunction, Boolean> valueFactories = new IdentityHashMap<>();
        return new UElementHandler() {
            @Override
            public void visitCallExpression(UCallExpression node) {
                if (namedTokenInitializer(node) || namedValueFactory(context, node, valueFactories)) return;
                PsiMethod method = node.resolve();
                if (method == null) return;
                String owner = owner(method);
                String name = unmangled(method.getName());
                if (("androidx.compose.ui.graphics.ColorKt".equals(owner) && "Color".equals(name))
                        || ("androidx.compose.ui.graphics.Color".equals(owner) && method.isConstructor())) {
                    if (hasLiteralArgument(node, false)) {
                        report(context, COLOR, node, "Numeric literal supplied to Compose Color; use a named color token.");
                    }
                } else if (("androidx.compose.foundation.shape.RoundedCornerShapeKt".equals(owner)
                        && "RoundedCornerShape".equals(name))
                        || ("androidx.compose.foundation.shape.RoundedCornerShape".equals(owner)
                        && method.isConstructor())) {
                    if (hasLiteralArgument(node, true)) {
                        report(context, SHAPE, node, "Numeric dp literal supplied to RoundedCornerShape; use a named shape token.");
                    }
                } else if ("androidx.compose.animation.core.AnimationSpecKt".equals(owner)
                        && ("tween".equals(name) || "spring".equals(name))
                        && hasLiteralArgument(node, false)) {
                    report(context, MOTION, node, "Numeric literal supplied to Compose " + name + "; use a named motion token.");
                }
            }

            @Override
            public void visitSimpleNameReferenceExpression(USimpleNameReferenceExpression node) {
                if (namedTokenInitializer(node) || namedValueFactory(context, node, valueFactories)) return;
                PsiElement source = node.getSourcePsi();
                if (source != null
                        && PsiTreeUtil.getParentOfType(source, KtImportDirective.class, false) != null) return;
                PsiElement resolved = resolvedPsi(node.resolve());
                String name = propertyName(resolved);
                String owner = owner(resolved);
                UElement parent = node.getUastParent();
                if (parent instanceof UQualifiedReferenceExpression qualified
                        && Objects.equals(qualified.getSelector(), node)) {
                    if ("dp".equals(name) && "androidx.compose.ui.unit.DpKt".equals(owner)
                            && numericLiteral(qualified.getReceiver())
                            && !roundedCornerArgument(qualified)
                            && !zeroUnitIdentity(qualified, false)) {
                        report(context, DIMENSION, qualified,
                                "Numeric Compose dimension in a UI use site; use a named dimension token or style policy.");
                    } else if ("sp".equals(name) && "androidx.compose.ui.unit.TextUnitKt".equals(owner)
                            && numericLiteral(qualified.getReceiver())
                            && !zeroUnitIdentity(qualified, true)) {
                        report(context, TYPOGRAPHY, qualified,
                                "Numeric Compose text unit in a UI use site; use theme typography or a named typography policy.");
                    } else if (("surface".equals(name) || "background".equals(name))
                            && "androidx.compose.material3.ColorScheme".equals(owner)
                            && materialColorScheme(qualified.getReceiver())
                            && !contrastBackgroundInput(qualified)) {
                        report(context, SURFACE, qualified,
                                "Direct MaterialTheme.colorScheme." + name + " read; use a named surface role.");
                    }
                }
                if (NAMED_COLORS.contains(name)
                        && ("androidx.compose.ui.graphics.Color.Companion".equals(owner)
                        || "androidx.compose.ui.graphics.Color".equals(owner))) {
                    report(context, COLOR, node, "Fixed Compose Color." + name + " read; use a named color token.");
                }
            }
        };
    }

    private static void report(JavaContext context, Issue issue, UElement node, String message) {
        context.report(issue, node, context.getLocation(node), message);
    }

    private static boolean migratedPackage(String packageName) {
        if (!packageName.startsWith(FEATURE)) return false;
        int start = FEATURE.length();
        int end = packageName.indexOf('.', start);
        return MIGRATED.contains(packageName.substring(start, end < 0 ? packageName.length() : end));
    }

    private static PsiElement resolvedPsi(PsiElement element) {
        if (element == null || element instanceof PsiMember) return element;
        PsiElement declaration = element.getNavigationElement();
        // Constructor properties can resolve to a light value parameter. Read the resolved
        // Kotlin declaration directly: value-class getter lookup can be absent or mangled.
        if (declaration instanceof KtParameter parameter && parameter.hasValOrVar()
                || declaration instanceof KtProperty) {
            return declaration;
        }
        return element;
    }

    private static String owner(PsiElement element) {
        if (element instanceof PsiMember member) {
            PsiClass containingClass = member.getContainingClass();
            return containingClass == null ? "" : containingClass.getQualifiedName();
        }
        if (element instanceof KtParameter || element instanceof KtProperty) {
            KtClassOrObject containingClass = PsiTreeUtil.getParentOfType(
                    element, KtClassOrObject.class, false);
            return containingClass == null || containingClass.getFqName() == null
                    ? "" : containingClass.getFqName().asString();
        }
        return "";
    }

    private static String unmangled(String name) {
        int separator = name.indexOf('-');
        return separator < 0 ? name : name.substring(0, separator);
    }

    private static String propertyName(PsiElement element) {
        if (element instanceof KtParameter parameter) return Objects.toString(parameter.getName(), "");
        if (element instanceof KtProperty property) return Objects.toString(property.getName(), "");
        if (!(element instanceof PsiMember member)) return "";
        String name = member.getName();
        if (name == null) return "";
        name = unmangled(name);
        if (element instanceof PsiMethod && name.startsWith("get") && name.length() > 3) {
            String property = name.substring(3);
            // Color's named properties deliberately start with an uppercase letter.
            if (NAMED_COLORS.contains(property)) return property;
            return Character.toLowerCase(property.charAt(0)) + property.substring(1);
        }
        return name;
    }

    private static UExpression unwrap(UExpression expression) {
        while (true) {
            if (expression instanceof UNamedExpression named) expression = named.getExpression();
            else if (expression instanceof UParenthesizedExpression parentheses) expression = parentheses.getExpression();
            else return expression;
        }
    }

    private static boolean numericLiteral(UExpression expression) {
        expression = unwrap(expression);
        if (expression instanceof UPrefixExpression prefix
                && (prefix.getOperator() == UastPrefixOperator.UNARY_MINUS
                || prefix.getOperator() == UastPrefixOperator.UNARY_PLUS)) {
            expression = unwrap(prefix.getOperand());
        }
        return expression instanceof ULiteralExpression literal && literal.getValue() instanceof Number;
    }

    private static boolean hasLiteralArgument(UCallExpression call, boolean dp) {
        for (UExpression argument : call.getValueArguments()) {
            if (dp ? literalDp(argument) && !zeroLiteral(
                    ((UQualifiedReferenceExpression) unwrap(argument)).getReceiver())
                    : numericLiteral(argument)) return true;
        }
        return false;
    }

    private static boolean literalDp(UExpression expression) {
        expression = unwrap(expression);
        if (!(expression instanceof UQualifiedReferenceExpression qualified)) return false;
        PsiElement resolved = resolvedPsi(qualified.resolve());
        return "androidx.compose.ui.unit.DpKt".equals(owner(resolved))
                && "dp".equals(propertyName(resolved)) && numericLiteral(qualified.getReceiver());
    }

    private static boolean materialColorScheme(UExpression expression) {
        expression = unwrap(expression);
        if (!(expression instanceof UQualifiedReferenceExpression qualified)) return false;
        PsiElement resolved = resolvedPsi(qualified.resolve());
        return "androidx.compose.material3.MaterialTheme".equals(owner(resolved))
                && "colorScheme".equals(propertyName(resolved));
    }

    /**
     * A named factory may define returned style values, not arbitrary code in a Policy file.
     * UI callbacks, local function bodies and non-returned statements keep their diagnostics.
     */
    private static boolean namedValueFactory(JavaContext context, UElement node,
            Map<KtNamedFunction, Boolean> factories) {
        PsiElement source = node.getSourcePsi();
        if (source == null) return false;
        KtNamedFunction function = null;
        for (PsiElement parent = source; parent != null; parent = parent.getParent()) {
            if (parent instanceof KtLambdaExpression) return false;
            if (parent instanceof KtNamedFunction named) {
                function = named;
                break;
            }
        }
        if (function == null || !factoryName(function.getName())) return false;
        Boolean factory = factories.get(function);
        if (factory == null) {
            UMethod method = UastContextKt.toUElement(function, UMethod.class);
            factory = method != null
                    && context.getEvaluator().findAnnotation(method,
                            "androidx.compose.runtime.Composable") == null
                    && styleValueType(context, method.getReturnType())
                    && (tokenContainer(function) || tokenName(function.getName())
                            || function.getName().endsWith("Spring")
                            || function.getName().endsWith("Tween"));
            factories.put(function, factory);
        }
        if (!factory) return false;
        for (KtParameter parameter : function.getValueParameters()) {
            KtExpression defaultValue = parameter.getDefaultValue();
            if (defaultValue != null && PsiTreeUtil.isAncestor(defaultValue, source, false)) return true;
        }
        KtExpression body = function.getBodyExpression();
        if (body == null) return false;
        if (!(body instanceof KtBlockExpression)) {
            return PsiTreeUtil.isAncestor(body, source, false);
        }
        for (PsiElement parent = source; parent != null && parent != function;
                parent = parent.getParent()) {
            if (parent instanceof KtReturnExpression returned
                    && returned.getLabelName() == null) return true;
        }
        return false;
    }

    private static boolean styleValueType(JavaContext context, PsiType type) {
        if (type == null) return false;
        String name = type.getCanonicalText();
        // Compose value classes can be exposed as their JVM primitives by UAST.
        if (STYLE_VALUE_TYPES.contains(name)) return true;
        PsiClass valueClass = context.getEvaluator().getTypeClass(type);
        if (valueClass == null) return false;
        String qualifiedName = valueClass.getQualifiedName();
        if (qualifiedName == null) return false;
        return STYLE_VALUE_TYPES.contains(qualifiedName)
                || context.getEvaluator().implementsInterface(valueClass,
                        "androidx.compose.ui.graphics.Shape", false)
                || context.getEvaluator().inheritsFrom(valueClass,
                        "androidx.compose.animation.core.AnimationSpec", false)
                || context.getEvaluator().isData(valueClass) && styleName(valueClass.getName());
    }

    private static boolean factoryName(String name) {
        return name != null && (name.startsWith("resolve") || name.startsWith("create")
                || tokenName(name) || name.endsWith("Spring") || name.endsWith("Tween"));
    }

    private static boolean styleName(String name) {
        return name != null && (name.endsWith("Style") || name.endsWith("Spec")
                || name.endsWith("Palette") || name.endsWith("Policy") || name.endsWith("Geometry")
                || name.endsWith("Tuning") || name.endsWith("Metrics") || name.endsWith("Padding"));
    }

    private static boolean tokenContainer(PsiElement declaration) {
        PsiElement container = declaration.getParent();
        if (container instanceof KtClassBody && container.getParent() instanceof KtClassOrObject named) {
            return tokenName(named.getName());
        }
        if (container instanceof KtFile file) {
            String name = file.getName();
            int extension = name.lastIndexOf('.');
            return tokenName(extension < 0 ? name : name.substring(0, extension));
        }
        return false;
    }

    private static UCallExpression argumentCall(UExpression expression) {
        UElement parent = expression.getUastParent();
        while (parent instanceof UNamedExpression || parent instanceof UParenthesizedExpression) {
            parent = parent.getUastParent();
        }
        return parent instanceof UCallExpression call ? call : null;
    }

    private static boolean roundedCornerArgument(UExpression expression) {
        UCallExpression call = argumentCall(expression);
        PsiMethod method = call == null ? null : call.resolve();
        return method != null && "RoundedCornerShape".equals(unmangled(method.getName()))
                && ("androidx.compose.foundation.shape.RoundedCornerShapeKt".equals(owner(method))
                        || "androidx.compose.foundation.shape.RoundedCornerShape".equals(owner(method)));
    }

    private static String argumentName(UCallExpression call, UExpression expression) {
        PsiParameter parameter = UastUtils.getParameterForArgument(call, expression);
        return parameter == null ? "" : parameter.getName();
    }

    private static boolean zeroLiteral(UExpression expression) {
        expression = unwrap(expression);
        if (expression instanceof UPrefixExpression prefix
                && (prefix.getOperator() == UastPrefixOperator.UNARY_MINUS
                        || prefix.getOperator() == UastPrefixOperator.UNARY_PLUS)) {
            expression = unwrap(prefix.getOperand());
        }
        return expression instanceof ULiteralExpression literal
                && literal.getValue() instanceof Number number && number.doubleValue() == 0;
    }

    private static boolean zeroUnitIdentity(UQualifiedReferenceExpression expression, boolean text) {
        if (!zeroLiteral(expression.getReceiver())) return false;
        UCallExpression call = argumentCall(expression);
        PsiMethod method = call == null ? null : call.resolve();
        if (method == null) return false;
        String owner = owner(method);
        String name = unmangled(method.getName());
        String parameter = argumentName(call, expression);
        if (!text && owner.startsWith("kotlin.ranges.")
                && ("coerceAtLeast".equals(name) || "coerceIn".equals(name))) {
            return "minimumValue".equals(parameter);
        }
        if (!owner.startsWith("androidx.compose.")
                && !owner.startsWith("com.android.purebilibili.core.ui.components.")
                && !owner.startsWith("top.yukonga.miuix.kmp.basic.")) return false;
        if (text) return "letterSpacing".equals(parameter);
        return parameter.endsWith("Elevation") || "elevation".equals(parameter)
                || "padding".equals(name) || "PaddingValues".equals(name)
                || "offset".equals(name) || "absoluteOffset".equals(name)
                || "blur".equals(name) || ("widthIn".equals(name) || "heightIn".equals(name))
                        && "min".equals(parameter);
    }

    private static boolean contrastBackgroundInput(UQualifiedReferenceExpression expression) {
        UCallExpression call = argumentCall(expression);
        PsiMethod method = call == null ? null : call.resolve();
        return method != null && "resolveAccessibleContainerColors".equals(method.getName())
                && "com.android.purebilibili.core.theme.ThemeContrastPolicyKt".equals(owner(method))
                && "backgroundColor".equals(argumentName(call, expression));
    }

    /** Only immutable property initializers are token definitions; UI methods in the same file are not. */
    private static boolean namedTokenInitializer(UElement node) {
        PsiElement source = node.getSourcePsi();
        if (source == null) return false;
        KtProperty property = PsiTreeUtil.getParentOfType(source, KtProperty.class, false);
        if (property == null || property.isVar() || property.getName() == null
                || property.getInitializer() == null
                || !PsiTreeUtil.isAncestor(property.getInitializer(), source, false)) return false;
        // An initializer that stores UI code or a function is not a literal token definition.
        for (PsiElement ancestor = source; ancestor != null && ancestor != property;
                ancestor = ancestor.getParent()) {
            if (ancestor instanceof KtLambdaExpression || ancestor instanceof KtNamedFunction) return false;
        }
        return tokenContainer(property);
    }

    private static boolean tokenName(String name) {
        return name != null && (name.endsWith("Spec") || name.endsWith("Palette") || name.endsWith("Policy"));
    }
}
