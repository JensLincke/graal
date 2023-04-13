/*
 * Copyright (c) 2019, 2022, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.dsl.processor;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.ServiceConfigurationError;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.FilerException;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic.Kind;
import javax.tools.FileObject;
import javax.tools.StandardLocation;

import com.oracle.truffle.dsl.processor.generator.GeneratorUtils;
import com.oracle.truffle.dsl.processor.java.ElementUtils;
import com.oracle.truffle.dsl.processor.java.compiler.CompilerFactory;
import com.oracle.truffle.dsl.processor.java.compiler.JDTCompiler;
import com.oracle.truffle.dsl.processor.java.model.CodeAnnotationMirror;
import com.oracle.truffle.dsl.processor.java.model.CodeExecutableElement;
import com.oracle.truffle.dsl.processor.java.model.CodeTreeBuilder;
import com.oracle.truffle.dsl.processor.java.model.CodeTypeElement;
import com.oracle.truffle.dsl.processor.java.model.CodeTypeMirror.DeclaredCodeTypeMirror;
import com.oracle.truffle.dsl.processor.java.transform.FixWarningsVisitor;
import com.oracle.truffle.dsl.processor.java.transform.GenerateOverrideVisitor;
import com.oracle.truffle.dsl.processor.model.Template;

abstract class AbstractRegistrationProcessor extends AbstractProcessor {

    private final Map<String, Element> registrations = new HashMap<>();

    @Override
    public final SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latest();
    }

    @SuppressWarnings({"deprecation", "unchecked"})
    @Override
    public final boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        try (ProcessorContext context = ProcessorContext.enter(processingEnv)) {
            String providerServiceBinName = processingEnv.getElementUtils().getBinaryName(context.getTypeElement(getProviderClass())).toString();
            if (roundEnv.processingOver()) {
                generateServicesRegistration(providerServiceBinName, registrations);
                registrations.clear();
                return true;
            }
            String[] supportedAnnotations = this.getClass().getAnnotation(SupportedAnnotationTypes.class).value();
            TypeElement supportedAnnotation = processingEnv.getElementUtils().getTypeElement(supportedAnnotations[0]);
            if (supportedAnnotation == null) {
                throw new IllegalStateException("Cannot resolve " + supportedAnnotations[0]);
            }
            Set<? extends Element> annotatedElements = roundEnv.getElementsAnnotatedWith(supportedAnnotation);
            if (!annotatedElements.isEmpty()) {
                for (Element e : annotatedElements) {
                    AnnotationMirror mirror = ElementUtils.findAnnotationMirror(e, supportedAnnotation.asType());
                    if (mirror != null && e.getKind() == ElementKind.CLASS) {
                        if (validateRegistration(e, mirror)) {
                            TypeElement annotatedElement = (TypeElement) e;
                            String providerImplBinName = generateProvider(annotatedElement);
                            registrations.put(providerImplBinName, annotatedElement);
                            if (shouldGenerateProviderFiles(annotatedElement)) {
                                generateProviderFile(processingEnv, providerImplBinName, providerServiceBinName, annotatedElement);
                            }
                        }
                    }
                }
            }
            return true;
        }
    }

    abstract boolean validateRegistration(Element annotatedElement, AnnotationMirror registrationMirror);

    abstract DeclaredType getProviderClass();

    abstract Iterable<AnnotationMirror> getProviderAnnotations(TypeElement annotatedElement);

    abstract void implementMethod(TypeElement annotatedElement, CodeExecutableElement methodToImplement);

    static void assertNoErrorExpected(Element e) {
        ExpectError.assertNoErrorExpected(e);
    }

    final void emitError(String msg, Element e) {
        if (ExpectError.isExpectedError(e, msg)) {
            return;
        }
        processingEnv.getMessager().printMessage(Kind.ERROR, msg, e);
    }

    final void emitError(String msg, Element e, AnnotationMirror mirror, AnnotationValue value) {
        if (ExpectError.isExpectedError(e, msg)) {
            return;
        }
        processingEnv.getMessager().printMessage(Kind.ERROR, msg, e, mirror, value);
    }

    final void emitWarning(String msg, Element e) {
        if (ExpectError.isExpectedError(e, msg)) {
            return;
        }
        processingEnv.getMessager().printMessage(Kind.WARNING, msg, e);
    }

    final void emitWarning(String msg, Element e, AnnotationMirror mirror, AnnotationValue value) {
        if (ExpectError.isExpectedError(e, msg)) {
            return;
        }
        processingEnv.getMessager().printMessage(Kind.WARNING, msg, e, mirror, value);
    }

    static CodeAnnotationMirror copyAnnotations(AnnotationMirror mirror, Predicate<ExecutableElement> filter) {
        CodeAnnotationMirror res = new CodeAnnotationMirror(mirror.getAnnotationType());
        for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> e : mirror.getElementValues().entrySet()) {
            ExecutableElement executable = e.getKey();
            AnnotationValue value = e.getValue();
            if (filter.test(executable)) {
                res.setElementValue(executable, value);
            }
        }
        return res;
    }

    private String generateProvider(TypeElement annotatedElement) {
        ProcessorContext context = ProcessorContext.getInstance();
        Template model = new Template(context, annotatedElement, null) {
        };
        TypeElement providerElement = context.getTypeElement(getProviderClass());
        CodeTypeElement providerClass = GeneratorUtils.createClass(model, null, EnumSet.of(Modifier.PUBLIC),
                        createProviderSimpleName(annotatedElement), providerElement.asType());
        providerClass.getModifiers().add(Modifier.FINAL);
        for (ExecutableElement method : ElementFilter.methodsIn(providerElement.getEnclosedElements())) {
            CodeExecutableElement implementedMethod = CodeExecutableElement.clone(method);
            implementedMethod.getModifiers().remove(Modifier.ABSTRACT);
            implementMethod(annotatedElement, implementedMethod);
            providerClass.add(implementedMethod);
        }

        for (AnnotationMirror annotationMirror : getProviderAnnotations(annotatedElement)) {
            providerClass.addAnnotationMirror(annotationMirror);
        }
        DeclaredType overrideType = (DeclaredType) context.getType(Override.class);
        providerClass.accept(new GenerateOverrideVisitor(overrideType), null);
        providerClass.accept(new FixWarningsVisitor(overrideType), null);
        providerClass.accept(new CodeWriter(context.getEnvironment(), annotatedElement), null);
        return providerClass.getQualifiedName().toString();
    }

    private static String createProviderSimpleName(TypeElement annotatedElement) {
        StringBuilder nameBuilder = new StringBuilder();
        List<Element> hierarchy = ElementUtils.getElementHierarchy(annotatedElement);
        for (ListIterator<Element> it = hierarchy.listIterator(hierarchy.size()); it.hasPrevious();) {
            Element enc = it.previous();
            if (enc.getKind().isClass() || enc.getKind().isInterface()) {
                nameBuilder.append(enc.getSimpleName());
            }
        }
        nameBuilder.append("Provider");
        return nameBuilder.toString();
    }

    static void generateProviderFile(ProcessingEnvironment env, String providerClassName, String serviceClassName, Element... originatingElements) {
        assert originatingElements.length > 0;
        String filename = "META-INF/truffle-registrations/" + providerClassName;
        try {
            FileObject file = env.getFiler().createResource(StandardLocation.CLASS_OUTPUT, "", filename, originatingElements);
            PrintWriter writer = new PrintWriter(new OutputStreamWriter(file.openOutputStream(), "UTF-8"));
            writer.println(serviceClassName);
            writer.close();
        } catch (IOException e) {
            handleIOError(e, env, originatingElements[0]);
        }
    }

    static void generateGetServicesClassNames(AnnotationMirror registration, CodeTreeBuilder builder, ProcessorContext context) {
        List<TypeMirror> services = ElementUtils.getAnnotationValueList(TypeMirror.class, registration, "services");
        if (services.isEmpty()) {
            builder.startReturn().startStaticCall(context.getType(Collections.class), "emptySet").end().end();
        } else {
            builder.startReturn();
            builder.startStaticCall(context.getType(Arrays.class), "asList");
            for (TypeMirror service : services) {
                builder.startGroup().doubleQuote(binaryName(service, context)).end();
            }
            builder.end(2);
        }
    }

    static void generateLoadService(AnnotationMirror registration, CodeTreeBuilder builder, ProcessorContext context, Map<String, DeclaredType> registrationAttrToServiceType) {
        Map<String, List<TypeMirror>> registrationAttrToImpls = new HashMap<>();
        for (String registrationAttr : registrationAttrToServiceType.keySet()) {
            List<TypeMirror> impls = ElementUtils.getAnnotationValueList(TypeMirror.class, registration, registrationAttr);
            if (!impls.isEmpty()) {
                registrationAttrToImpls.put(registrationAttr, impls);
            }
        }
        DeclaredType stream = context.getDeclaredType(Stream.class);
        if (registrationAttrToImpls.isEmpty()) {
            builder.startReturn().startStaticCall(stream, "empty").end(2);
        } else {
            TypeMirror strStream = new DeclaredCodeTypeMirror((TypeElement) stream.asElement(), List.of(context.getDeclaredType(String.class)));
            builder.declaration(strStream, "implFqns", (String) null);
            String paramName = builder.findMethod().getParameters().get(0).getSimpleName().toString();
            builder.startSwitch().startCall(paramName, "getName").end(2).startBlock();
            for (Map.Entry<String, List<TypeMirror>> entry : registrationAttrToImpls.entrySet()) {
                builder.startCase().doubleQuote(binaryName(registrationAttrToServiceType.get(entry.getKey()), context)).end().startCaseBlock();
                builder.startStatement().string("implFqns", " = ").startStaticCall(stream, "of");
                for (TypeMirror impl : entry.getValue()) {
                    builder.doubleQuote(binaryName(impl, context));
                }
                builder.end(2);
                builder.startStatement().string("break").end(2);
            }
            builder.caseDefault().startCaseBlock();
            builder.startStatement().string("implFqns", " = ").startStaticCall(stream, "empty");
            builder.end(4);
            builder.startReturn().startCall("implFqns", "map").startGroup().string("(fqn) -> ").startBlock();
            builder.startTryBlock();
            DeclaredType clz = context.getDeclaredType(Class.class);
            builder.declaration(clz, "clazz", builder.create().startStaticCall(clz, "forName").string("fqn").end().build());
            builder.declaration(context.getDeclaredType(Constructor.class), "constructor", builder.create().startCall("clazz", "getDeclaredConstructor").end().build());
            builder.startReturn().startCall(paramName, "cast").startCall("constructor", "newInstance").end(3);
            builder.end();
            builder.startCatchBlock(context.getDeclaredType(ReflectiveOperationException.class), "e");
            builder.startThrow().startNew(context.getDeclaredType(ServiceConfigurationError.class)).startGroup().doubleQuote("Failed to instantiate ").string(" + ", "fqn").end().string("e").end(2);
            builder.end(5);
        }
    }

    private static String binaryName(TypeMirror type, ProcessorContext context) {
        Elements elements = context.getEnvironment().getElementUtils();
        Types types = context.getEnvironment().getTypeUtils();
        return elements.getBinaryName((TypeElement) ((DeclaredType) types.erasure(type)).asElement()).toString();
    }

    /**
     * Determines if a given exception is (most likely) caused by
     * <a href="https://bugs.eclipse.org/bugs/show_bug.cgi?id=367599">Bug 367599</a>.
     */
    private static boolean isBug367599(Throwable t) {
        if (t instanceof FilerException) {
            for (StackTraceElement ste : t.getStackTrace()) {
                if (ste.toString().contains("org.eclipse.jdt.internal.apt.pluggable.core.filer.IdeFilerImpl.create")) {
                    // See: https://bugs.eclipse.org/bugs/show_bug.cgi?id=367599
                    return true;
                }
            }
        }
        return t.getCause() != null && isBug367599(t.getCause());
    }

    static void generateServicesRegistration(String providerBinName, Map<String, Element> providerRegistrations) {
        ProcessorContext context = ProcessorContext.getInstance();
        ProcessingEnvironment env = context.getEnvironment();
        Elements elements = env.getElementUtils();
        String filename = "META-INF/services/" + providerBinName;
        List<String> providerClassNames = new ArrayList<>(providerRegistrations.size());
        for (String providerFqn : providerRegistrations.keySet()) {
            TypeElement te = ElementUtils.getTypeElement(providerFqn);
            if (te == null) {
                providerClassNames.add(providerFqn);
            } else {
                providerClassNames.add(elements.getBinaryName(te).toString());
            }
        }
        Collections.sort(providerClassNames);
        if (!providerClassNames.isEmpty()) {
            try {
                FileObject file = env.getFiler().createResource(StandardLocation.CLASS_OUTPUT, "", filename, providerRegistrations.values().toArray(new Element[providerRegistrations.size()]));
                try (PrintWriter out = new PrintWriter(new OutputStreamWriter(file.openOutputStream(), "UTF-8"))) {
                    for (String providerClassName : providerClassNames) {
                        out.println(providerClassName);
                    }
                }
            } catch (IOException e) {
                handleIOError(e, env, providerRegistrations.values().iterator().next());
            }
        }
    }

    boolean validateDefaultExportProviders(Element annotatedElement, AnnotationMirror mirror, ProcessorContext context) {
        return validateLookupRegistration(annotatedElement, mirror, "defaultExportProviders", context.getTypes().DefaultExportProvider, context);
    }

    boolean validateEagerExportProviders(Element annotatedElement, AnnotationMirror mirror, ProcessorContext context) {
        return validateLookupRegistration(annotatedElement, mirror, "eagerExportProviders", context.getTypes().EagerExportProvider, context);
    }

    boolean validateLookupRegistration(Element annotatedElement, AnnotationMirror mirror, String attributeName,
                    DeclaredType serviceType, ProcessorContext context) {
        AnnotationValue value = ElementUtils.getAnnotationValue(mirror, attributeName, true);
        Types types = context.getEnvironment().getTypeUtils();
        for (TypeMirror serviceImpl : ElementUtils.getAnnotationValueList(TypeMirror.class, mirror, attributeName)) {
            if (!types.isSubtype(serviceImpl, serviceType)) {
                TypeElement serviceTypeElement = ElementUtils.fromTypeMirror(serviceType);
                emitError(String.format("Registered %s must be subclass of %s. To resolve this, implement %s.",
                                attributeName, serviceTypeElement.getQualifiedName(), serviceTypeElement.getSimpleName()),
                                annotatedElement, mirror, value);
                return false;
            }
            TypeElement serviceImplElement = ElementUtils.fromTypeMirror(serviceImpl);
            PackageElement targetPackage = ElementUtils.findPackageElement(annotatedElement);
            boolean samePackage = targetPackage.equals(ElementUtils.findPackageElement(serviceImplElement));
            Set<Modifier> modifiers = serviceImplElement.getModifiers();
            if (samePackage ? modifiers.contains(Modifier.PRIVATE) : !modifiers.contains(Modifier.PUBLIC)) {
                emitError(String.format("The %s must be a public class or package protected class in %s package. To resolve this, make the %s public or move it to %s.",
                                serviceImplElement.getQualifiedName(), targetPackage.getQualifiedName(), serviceImplElement.getSimpleName(), targetPackage.getQualifiedName()),
                                annotatedElement, mirror, value);
                return false;
            }
            if (serviceImplElement.getEnclosingElement().getKind() != ElementKind.PACKAGE && !modifiers.contains(Modifier.STATIC)) {
                emitError(String.format("The %s must be a static inner-class or a top-level class. To resolve this, make the %s static or top-level class.",
                                serviceImplElement.getQualifiedName(), serviceImplElement.getSimpleName()), annotatedElement, mirror, value);
                return false;
            }
            boolean foundConstructor = false;
            for (ExecutableElement constructor : ElementFilter.constructorsIn(serviceImplElement.getEnclosedElements())) {
                modifiers = constructor.getModifiers();
                if (samePackage ? modifiers.contains(Modifier.PRIVATE) : !modifiers.contains(Modifier.PUBLIC)) {
                    continue;
                }
                if (!constructor.getParameters().isEmpty()) {
                    continue;
                }
                foundConstructor = true;
                break;
            }
            if (!foundConstructor) {
                emitError(String.format("The %s must have a no argument public constructor. To resolve this, add public %s() constructor.",
                                serviceImplElement.getQualifiedName(), serviceImplElement.getSimpleName()), annotatedElement, mirror, value);
                return false;
            }
        }
        return true;
    }

    private static void handleIOError(IOException e, ProcessingEnvironment env, Element element) {
        if (e instanceof FilerException) {
            if (e.getMessage().startsWith("Source file already created") || e.getMessage().startsWith("Resource already created")) {
                // ignore source file already created errors
                return;
            }
        }
        env.getMessager().printMessage(isBug367599(e) ? Kind.NOTE : Kind.ERROR, e.getMessage(), element);
    }

    static boolean shouldGenerateProviderFiles(Element currentElement) {
        return CompilerFactory.getCompiler(currentElement) instanceof JDTCompiler;
    }

}
