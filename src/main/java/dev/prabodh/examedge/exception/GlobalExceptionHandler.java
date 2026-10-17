package dev.prabodh.examedge.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@ControllerAdvice(basePackages = "dev.prabodh.examedge.controller")
@Order(Ordered.LOWEST_PRECEDENCE)
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(AccessDeniedException.class)
    public void rethrowAccessDenied(AccessDeniedException exception) {
        throw exception;
    }

    @ExceptionHandler({ResourceNotFoundException.class, NoResourceFoundException.class})
    public ModelAndView notFound(Exception exception) {
        return errorView(HttpStatus.NOT_FOUND, "Page not found",
                "The page or record you asked for does not exist or is no longer available.");
    }

    @ExceptionHandler(BusinessRuleException.class)
    public ModelAndView businessRule(BusinessRuleException exception) {
        return errorView(HttpStatus.CONFLICT, "Action not allowed", exception.getMessage());
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ModelAndView methodNotAllowed(HttpRequestMethodNotSupportedException exception) {
        return errorView(HttpStatus.METHOD_NOT_ALLOWED, "Method not allowed", "That action is not supported for this page.");
    }

    @ExceptionHandler({MissingServletRequestParameterException.class, MethodArgumentTypeMismatchException.class})
    public ModelAndView badRequest(Exception exception) {
        return errorView(HttpStatus.BAD_REQUEST, "Invalid request", "The request could not be understood.");
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ModelAndView uploadTooLarge(MaxUploadSizeExceededException exception) {
        return errorView(HttpStatus.PAYLOAD_TOO_LARGE, "File too large", "Uploads are limited to 1 MB.");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ModelAndView integrity(DataIntegrityViolationException exception, HttpServletRequest request) {
        log.warn("Data integrity violation on {}", request.getRequestURI());
        return errorView(HttpStatus.CONFLICT, "Conflict",
                "That change conflicts with existing data. Please go back and try again.");
    }

    @ExceptionHandler(Exception.class)
    public ModelAndView unexpected(Exception exception, HttpServletRequest request) {
        log.error("Unhandled error on {}", request.getRequestURI(), exception);
        return errorView(HttpStatus.INTERNAL_SERVER_ERROR, "Something went wrong",
                "An unexpected error occurred. Please try again in a moment.");
    }

    private ModelAndView errorView(HttpStatus status, String title, String message) {
        ModelAndView view = new ModelAndView("error/generic");
        view.setStatus(status);
        view.addObject("status", status.value());
        view.addObject("title", title);
        view.addObject("message", message);
        return view;
    }
}
