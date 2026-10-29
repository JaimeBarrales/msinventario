package cl.duoc.inventario.exception;

public class StockInsuficienteException extends RuntimeException {

    public StockInsuficienteException(Long id, int stockActual, int cantidad) {
        super("Stock insuficiente para el producto " + id
                + ": stock actual " + stockActual + ", ajuste solicitado " + cantidad);
    }
}
