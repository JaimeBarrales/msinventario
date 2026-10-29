package cl.duoc.inventario.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Traduce las excepciones de negocio a respuestas HTTP con formato RFC 9457 (Problem Details).
 */
@RestControllerAdvice
public class ManejadorErrores {

    @ExceptionHandler(ProductoNoEncontradoException.class)
    public ProblemDetail noEncontrado(ProductoNoEncontradoException ex) {
        return problema(HttpStatus.NOT_FOUND, "Producto no encontrado", ex.getMessage());
    }

    @ExceptionHandler(SkuDuplicadoException.class)
    public ProblemDetail skuDuplicado(SkuDuplicadoException ex) {
        return problema(HttpStatus.CONFLICT, "SKU duplicado", ex.getMessage());
    }

    @ExceptionHandler(StockInsuficienteException.class)
    public ProblemDetail stockInsuficiente(StockInsuficienteException ex) {
        return problema(HttpStatus.CONFLICT, "Stock insuficiente", ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail validacion(MethodArgumentNotValidException ex) {
        Map<String, String> errores = new LinkedHashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            errores.putIfAbsent(error.getField(), error.getDefaultMessage());
        }
        ProblemDetail detalle = problema(HttpStatus.BAD_REQUEST, "Datos inválidos",
                "La solicitud contiene campos inválidos");
        detalle.setProperty("errores", errores);
        return detalle;
    }

    private static ProblemDetail problema(HttpStatus estado, String titulo, String mensaje) {
        ProblemDetail detalle = ProblemDetail.forStatusAndDetail(estado, mensaje);
        detalle.setTitle(titulo);
        return detalle;
    }
}
