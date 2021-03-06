/**
 * Copyright (C) 2010 The UAPI Authors
 * You may not use this file except in compliance with the License.
 * You may obtain a copy of the License at the LICENSE file.
 *
 * You must gained the permission from the authors if you want to
 * use the project into a commercial product
 */

package uapi.web.internal;

import com.google.auto.service.AutoService;
import com.google.common.collect.ArrayTable;
import com.google.common.collect.Table;
import freemarker.template.Template;
import rx.Observable;
import uapi.KernelException;
import uapi.annotation.*;
import uapi.helper.*;
import uapi.service.IServiceHandlerHelper;
import uapi.service.annotation.Exposure;
import uapi.service.annotation.Service;
import uapi.service.web.*;
import uapi.web.*;
import uapi.web.annotation.FromHeader;
import uapi.web.annotation.FromParam;
import uapi.web.annotation.FromUri;
import uapi.web.annotation.Restful;

import javax.lang.model.element.*;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import java.lang.annotation.Annotation;
import java.util.*;

/**
 * A annotation handler for Restful annotation handling
 */
@AutoService(IAnnotationsHandler.class)
public class RestfulHandler extends AnnotationsHandler {

    private static final String TEMPLATE_GET_METHOD_ARGUMENTS_INFO  = "template/getMethodArgumentsInfo_method.ftl";
    private static final String TEMPLATE_GET_RETURN_TYPE_NAME       = "template/getReturnTypeName_method.ftl";
    private static final String TEMPLATE_INVOKE                     = "template/invoke_method.ftl";
    private static final String HTTP_TO_METHOD_ARGS_MAPPING         = "HttpToMethodArgumentsMapping";
    private static final String EXPOSED_NAME                        = "ExposedName";
    private static final String INTERFACE_METHOD_MAPPING            = "InterfaceMethodMapping";

    @SuppressWarnings("unchecked")
    private static final Class<? extends Annotation>[] orderedAnnotations =
            new Class[] { Restful.class };

    @Override
    protected Class<? extends Annotation>[] getOrderedAnnotations() {
        return orderedAnnotations;
    }

    @Override
    protected void handleAnnotatedElements(
            final IBuilderContext builderCtx,
            final Class<? extends Annotation> annotationType,
            final Set<? extends Element> elements
    ) throws KernelException {
        ArgumentChecker.notNull(annotationType, "annotationType");

        Observable.from(elements).subscribe(methodElement -> {
            if (methodElement.getKind() != ElementKind.METHOD) {
                throw new KernelException(
                        "The Restful annotation only can be applied on field",
                        methodElement.getSimpleName().toString());
            }
            Element classElement = methodElement.getEnclosingElement();
            checkAnnotations(classElement, Service.class);

            builderCtx.checkModifiers(methodElement, Restful.class, Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL);
            Element classElemt = methodElement.getEnclosingElement();
            builderCtx.checkModifiers(classElemt, Restful.class, Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL);

            ClassMeta.Builder clsBuilder = builderCtx.findClassBuilder(classElemt);

            Exposure exposure = classElement.getAnnotation(Exposure.class);
            String exposedName = exposure == null ? classElement.getSimpleName().toString() : exposure.value();
            ArgumentChecker.notEmpty(exposedName, "exposedName");
            clsBuilder.putTransience(EXPOSED_NAME, exposedName);

            String methodName = methodElement.getSimpleName().toString();
            String returnTypeName = ((ExecutableElement) methodElement).getReturnType().toString();
            Restful restful = methodElement.getAnnotation(Restful.class);
            HttpMethod[] httpMethods = HttpMethod.parse(restful.value());

            Map<String, MethodArgumentsMapping> httpMethodArgMappings =
                    clsBuilder.createTransienceIfAbsent(HTTP_TO_METHOD_ARGS_MAPPING, HashMap::new);
            HttpMethod found = MapHelper.findKey(httpMethodArgMappings, httpMethods);
            if (found != null) {
                throw new KernelException("Found multiple methods are mapped to same http method: {}", found);
            }

            ExecutableElement execElem = (ExecutableElement) methodElement;
            MethodArgumentsMapping methodArgMapping = new MethodArgumentsMapping(methodName, returnTypeName);
            Observable.from(execElem.getParameters())
                    .map(this::handleFromAnnotation)
                    .subscribe(methodArgMapping::addArgumentMapping);
            Observable.from(httpMethods)
                    .subscribe(httpMethod -> httpMethodArgMappings.put(httpMethod.toString(), methodArgMapping));

            scanImplementation((TypeElement) classElement, methodArgMapping, clsBuilder, builderCtx);
        }, t -> builderCtx.getLogger().error(t));

        implementIRestfulService(builderCtx);
    }

    private void scanImplementation(
            final TypeElement classElement,
            final MethodArgumentsMapping methodArgsMapping,
            final ClassMeta.Builder clsBuilder,
            final IBuilderContext builderContext) {
        TripleMap<String, MethodInfo, MethodArgumentsMapping> intfMethodMap =
                clsBuilder.createTransienceIfAbsent(INTERFACE_METHOD_MAPPING, TripleMap::new);
        if (intfMethodMap.size() == 0) {
            // Scan interface methods
            List<TypeMirror> intfs = (List<TypeMirror>) classElement.getInterfaces();

            if (intfs.size() == 0) {
                // No interface is implemented, do nothing
            } else if (intfs.size() > 0) {
                Observable.from(intfs)
                        .map(intf -> (DeclaredType) intf)
                        .subscribe(dType -> {
                            String intfName = dType.toString();
                            List<MethodInfo> methods = getInterfaceMethods(dType, builderContext);
                            intfMethodMap.put(intfName, methods);
                        });

//            } else if (intfs.size() == 1) {
//                DeclaredType dType = (DeclaredType) intfs.get(0);
//                List<Element> elements = (List<Element>) dType.asElement().getEnclosedElements();
//                Observable.from(elements)
//                        .filter(element -> element.getKind() == ElementKind.METHOD)
//                        .map(this::fetchMethodInfo)
//                        //.doOnNext(methodInfo -> builderContext.getLogger().info(" -->> {}", methodInfo.toString()))
//                        .subscribe(methodInfo -> intfMethodMappings.put(methodInfo, null), t -> builderContext.getLogger().error(t));
//            } else if (intfs.size() > 1) {
//                // Found more then 1 interface so we need indicate which one should be exposed
            } else {
                throw new KernelException(
                        "Invalid interface implementation for class - {}",
                        classElement.getSimpleName().toString());
            }
        }
        // Add matched method argument mapping
        Observable.from(intfMethodMap.entrySet())
                .subscribe(intfEntry -> {
                    Observable.from(intfEntry.getValue().entrySet())
                            .filter(methodEntry -> isSameMethod(methodEntry.getKey(), methodArgsMapping))
                            .subscribe(methodEntry -> methodEntry.setValue(methodArgsMapping));
                });
    }

    private List<MethodInfo> getInterfaceMethods(DeclaredType intfType, IBuilderContext builderCtx) {
        List<Element> elements = (List<Element>) intfType.asElement().getEnclosedElements();
        List<MethodInfo> methods = new ArrayList<>();
        Observable.from(elements)
                .filter(element -> element.getKind() == ElementKind.METHOD)
                .map(this::fetchMethodInfo)
                //.doOnNext(methodInfo -> builderContext.getLogger().info(" -->> {}", methodInfo.toString()))
                .subscribe(methods::add, t -> builderCtx.getLogger().error(t));
        return methods;
    }

    private boolean isSameMethod(MethodInfo methodInfo, MethodArgumentsMapping methodArgsMapping) {
        if (! methodInfo.getName().equals(methodArgsMapping.getName())) {
            return false;
        }
        String[] argTypes = methodInfo.getArgumentTypes();
        List<ArgumentMapping> argMappings = methodArgsMapping.getArgumentMappings();
        if (argTypes.length != argMappings.size()) {
            return false;
        }
        if (argTypes.length == 0 && argMappings.size() == 0) {
            return true;
        }
        for (int i = 0; i < argTypes.length; i++) {
            if (! argTypes[i].equals(argMappings.get(i).getType())) {
                return false;
            }
        }
        return true;
    }

    private MethodInfo fetchMethodInfo(Element methodElement) {
        String methodName = methodElement.getSimpleName().toString();
        ExecutableElement execMethod = (ExecutableElement) methodElement;
        List<VariableElement> argElements = (List<VariableElement>) execMethod.getParameters();
        List<String> argTypes = new ArrayList<>();
        Observable.from(argElements)
                .filter(argElement -> argElement.getKind() == ElementKind.PARAMETER)
                .map(argElement -> argElement.asType().toString())
                .subscribe(argTypes::add);
        String rtnType = execMethod.getReturnType().toString();
        return new MethodInfo(methodName, argTypes, rtnType);
    }

    private void implementIRestfulService(
            final IBuilderContext builderCtx
    ) {
        Template tempGetArgs = builderCtx.loadTemplate(TEMPLATE_GET_METHOD_ARGUMENTS_INFO);
        Template tempGetRtnType = builderCtx.loadTemplate(TEMPLATE_GET_RETURN_TYPE_NAME);
        Template tempInvoke = builderCtx.loadTemplate(TEMPLATE_INVOKE);
        IServiceHandlerHelper svcHelper = (IServiceHandlerHelper) builderCtx.getHelper(IServiceHandlerHelper.name);
        if (svcHelper == null) {
            throw new KernelException("No service handler helper was found");
        }

        Observable.from(builderCtx.getBuilders())
                .filter(clsBuilder -> clsBuilder.getTransience(HTTP_TO_METHOD_ARGS_MAPPING) != null)
                .subscribe(clsBuilder -> {
                    Map<HttpMethod, MethodArgumentsMapping> httpMethodMappings = clsBuilder.getTransience(HTTP_TO_METHOD_ARGS_MAPPING);
                    String codeGetId = StringHelper.makeString("return \"{}\";", clsBuilder.getTransience(EXPOSED_NAME).toString());
                    Map<String, Object> model = new HashMap<>();
                    model.put("model", httpMethodMappings);

                    svcHelper.addServiceId(clsBuilder, IRestfulService.class.getCanonicalName());

                    clsBuilder.addImplement(IRestfulService.class.getCanonicalName())
                            // implement getId method
                            .addMethodBuilder(MethodMeta.builder()
                                    .addAnnotationBuilder(AnnotationMeta.builder().setName(AnnotationMeta.OVERRIDE))
                                    .addModifier(Modifier.PUBLIC)
                                    .setName("getId")
                                    .setReturnTypeName(String.class.getCanonicalName())
                                    .addCodeBuilder(CodeMeta.builder()
                                            .addRawCode(codeGetId)))
                            // implement getMethodArgumentsInfo method
                            .addMethodBuilder(MethodMeta.builder()
                                    .addAnnotationBuilder(AnnotationMeta.builder().setName(AnnotationMeta.OVERRIDE))
                                    .addModifier(Modifier.PUBLIC)
                                    .setName("getMethodArgumentsInfo")
                                    .setReturnTypeName("uapi.service.web.ArgumentMapping[]")
                                    .addParameterBuilder(ParameterMeta.builder()
                                            .setName("method")
                                            .setType(HttpMethod.class.getCanonicalName()))
                                    .addCodeBuilder(CodeMeta.builder()
                                            .setModel(model)
                                            .setTemplate(tempGetArgs)))
                            // implement invoke method
                            .addMethodBuilder(MethodMeta.builder()
                                    .addAnnotationBuilder(AnnotationMeta.builder().setName(AnnotationMeta.OVERRIDE))
                                    .addModifier(Modifier.PUBLIC)
                                    .setName("invoke")
                                    .setReturnTypeName(Object.class.getCanonicalName())
                                    .addParameterBuilder(ParameterMeta.builder()
                                            .setName("method")
                                            .setType(HttpMethod.class.getCanonicalName()))
                                    .addParameterBuilder(ParameterMeta.builder()
                                            .setName("args")
                                            .setType("java.util.List<Object>"))
                                    .addCodeBuilder(CodeMeta.builder()
                                            .setModel(model)
                                            .setTemplate(tempInvoke)))
                            .addMethodBuilder(MethodMeta.builder()
                                    .addAnnotationBuilder(AnnotationMeta.builder().setName(AnnotationMeta.OVERRIDE))
                                    .addModifier(Modifier.PUBLIC)
                                    .setName("getReturnTypeName")
                                    .setReturnTypeName(String.class.getCanonicalName())
                                    .addParameterBuilder(ParameterMeta.builder()
                                            .setName("method")
                                            .setType(HttpMethod.class.getCanonicalName()))
                                    .addCodeBuilder(CodeMeta.builder()
                                            .setModel(model)
                                            .setTemplate(tempGetRtnType)));

                    // Check whether there are interface need to exposed
                    TripleMap<String, MethodInfo, MethodArgumentsMapping> intfMethodMap =
                            clsBuilder.getTransience(INTERFACE_METHOD_MAPPING);
                    if (intfMethodMap != null) {
                        Observable.from(intfMethodMap.entrySet())
                                .filter(intfEntry -> ! intfMethodMap.hasEmptyValue(intfEntry.getKey()))
                                .subscribe(intfEntry -> {
                                    String intfName = intfEntry.getKey();

                                });

                    }
                }, t -> builderCtx.getLogger().error(t));
    }

    private ArgumentMapping handleFromAnnotation(
            final Element paramElem
    ) {
        checkParamAnnotation(paramElem);
        String paramType = paramElem.asType().toString();
        FromUri fromUri = paramElem.getAnnotation(FromUri.class);
        FromHeader fromHeader = paramElem.getAnnotation(FromHeader.class);
        FromParam fromParam = paramElem.getAnnotation(FromParam.class);
        ArgumentMapping argMapping = null;
        if (fromUri != null) {
            argMapping = new IndexedArgumentMapping(ArgumentFrom.Uri, paramType, fromUri.value());
        } else if (fromHeader != null) {
            argMapping = new NamedArgumentMapping(ArgumentFrom.Header, paramType, fromHeader.value());
        } else if (fromParam != null) {
            argMapping = new NamedArgumentMapping(ArgumentFrom.Param, paramType, fromParam.value());
        }
        return argMapping;
    }

    private void checkParamAnnotation(Element paramElem) {
        FromUri fromUri = paramElem.getAnnotation(FromUri.class);
        FromHeader fromHeader = paramElem.getAnnotation(FromHeader.class);
        FromParam fromParam = paramElem.getAnnotation(FromParam.class);
        boolean valid = true;
        if (fromUri != null) {
            if (fromHeader != null || fromParam != null) {
                valid = false;
            }
        }
        if (fromHeader != null) {
            if (fromUri != null || fromParam != null) {
                valid = false;
            }
        }
        if (fromParam != null) {
            if (fromUri != null || fromHeader != null) {
                valid = false;
            }
        }
        if (! valid) {
            throw new KernelException(
                    "The restful method parameter {} allow only one FromUri/FromHeader/FromParam Annotation",
                    paramElem.getSimpleName().toString());
        }
        if (fromUri == null && fromHeader == null && fromParam == null) {
            throw new KernelException(
                    "The restful method parameter {} has to define one of FromUri/FromHeader/FromParam Annotation",
                    paramElem.getSimpleName().toString());
        }
    }
}
