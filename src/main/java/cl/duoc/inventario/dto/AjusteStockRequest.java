package cl.duoc.inventario.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Ajuste de stock: positivo para ingresos, negativo para salidas.
 */
public record AjusteStockRequest(
        @NotNull(message = "La cantidad es obligatoria")
        Integer cantidad
) {
}
