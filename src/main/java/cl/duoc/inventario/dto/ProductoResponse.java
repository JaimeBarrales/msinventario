package cl.duoc.inventario.dto;

import cl.duoc.inventario.model.Producto;

import java.math.BigDecimal;
import java.time.Instant;

public record ProductoResponse(
        Long id,
        String sku,
        String nombre,
        String descripcion,
        BigDecimal precio,
        Integer stock,
        Instant creadoEn,
        Instant actualizadoEn
) {
    public static ProductoResponse desde(Producto p) {
        return new ProductoResponse(
                p.getId(),
                p.getSku(),
                p.getNombre(),
                p.getDescripcion(),
                p.getPrecio(),
                p.getStock(),
                p.getCreadoEn(),
                p.getActualizadoEn()
        );
    }
}
