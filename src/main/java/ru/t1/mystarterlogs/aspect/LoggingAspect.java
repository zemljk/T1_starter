package ru.t1.mystarterlogs.aspect;

import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.*;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import ru.t1.mystarterlogs.component.HttpLoggingProperties;


import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

@Aspect
@Component
public class LoggingAspect {

    private final Logger logger = LoggerFactory.getLogger(LoggingAspect.class);
    private final HttpLoggingProperties properties;

    public LoggingAspect(HttpLoggingProperties properties) {
        this.properties = properties;
    }

    @Pointcut("annotation(Loggable)")
    public void methodsAnnotatedWithLoggable() {}


    @Before("methodsAnnotatedWithLoggable()")
    public void logBefore(JoinPoint joinPoint) {
        if (!properties.enabled()) {
            return;
        }
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String className = signature.getDeclaringType().getSimpleName();
        String methodName = signature.getName();
        Object[] args = joinPoint.getArgs();

        StringBuilder logMessage = new StringBuilder();
        logMessage.append(">>> METHOD EXECUTION START <<<\n");
        logMessage.append(String.format("Class: %s, Method: %s\n", className, methodName));
        logMessage.append(String.format("Arguments: %s\n", Arrays.toString(args)));

        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes != null) {
            HttpServletRequest request = attributes.getRequest();
            logMessage.append("Request Details:\n");
            logMessage.append(String.format("  Method: %s, URI: %s\n", request.getMethod(), request.getRequestURI()));
            logMessage.append("  Headers: ").append(getHeaders(request)).append("\n");
        }
        logMessage.append("----------------------------------");
        generateLog(logMessage.toString());
    }

    @AfterReturning(pointcut = "methodsAnnotatedWithLoggable()", returning = "result")
    public void logAfterReturning(JoinPoint joinPoint, Object result) {
        if (!properties.enabled()) {
            return;
        }
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String className = signature.getDeclaringType().getSimpleName();
        String methodName = signature.getName();

        StringBuilder logMessage = new StringBuilder();
        logMessage.append(">>> METHOD EXECUTION END (SUCCESS) <<<\n");
        logMessage.append(String.format("Class: %s, Method: %s\n", className, methodName));
        logMessage.append(String.format("Return Value: %s\n", (result != null ? result.toString() : "void")));

        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes != null && attributes.getResponse() != null) {
            logMessage.append("Response Status: ").append(attributes.getResponse().getStatus()).append("\n");
        }
        logMessage.append("----------------------------------");
        generateLog(logMessage.toString());
    }

    @AfterThrowing(pointcut = "methodsAnnotatedWithLoggable()", throwing = "exception")
    public void logAfterThrowing(JoinPoint joinPoint, Throwable exception) {
        if (!properties.enabled()) {
            return;
        }
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String className = signature.getDeclaringType().getSimpleName();
        String methodName = signature.getName();

        StringBuilder logMessage = new StringBuilder();
        logMessage.append(">>> METHOD EXECUTION END (FAILURE) <<<\n");
        logMessage.append(String.format("Class: %s, Method: %s\n", className, methodName));
        logMessage.append(String.format("Exception: %s\n", exception.getClass().getSimpleName()));
        logMessage.append(String.format("Error Message: %s\n", exception.getMessage()));
        logMessage.append("----------------------------------");
        generateLog(logMessage.toString());

        if (logger.isDebugEnabled() || logger.isTraceEnabled()) {
            logger.error("Full exception stack trace for {}.{}:", className, methodName, exception);
        }
    }

    @Around("methodsAnnotatedWithCustomTimeTracking()")
    public Object logAround(ProceedingJoinPoint joinPoint) throws Throwable {
        if (!properties.enabled()) {
            return joinPoint.proceed();
        }

        long startTime = System.currentTimeMillis();
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String className = signature.getDeclaringType().getSimpleName();
        String methodName = signature.getName();

        Object result;
        try {
            result = joinPoint.proceed();
            long endTime = System.currentTimeMillis();
            generateLog(String.format("Method %s.%s completed successfully in %d ms", className, methodName, endTime - startTime));
        } catch (Throwable e) {
            long endTime = System.currentTimeMillis();
            generateLog(String.format("Method %s.%s completed with error in %d ms: %s", className, methodName, endTime - startTime, e.getMessage()));
            throw e;
        }
        return result;
    }


    private void generateLog(String message) {
        String level = properties.level() != null ? properties.level().toUpperCase() : "INFO";

        switch (level) {
            case "DEBUG": logger.debug(message); break;
            case "WARN": logger.warn(message); break;
            case "ERROR": logger.error(message); break;
            case "TRACE": logger.trace(message); break;
            default: logger.info(message);
        }
    }

    private Map<String, String> getHeaders(HttpServletRequest request) {
        Map<String, String> headers = new HashMap<>();
        Enumeration<String> headerNames = request.getHeaderNames();
        while (headerNames.hasMoreElements()) {
            String headerName = headerNames.nextElement();
            headers.put(headerName, request.getHeader(headerName));
        }
        return headers;
    }
}