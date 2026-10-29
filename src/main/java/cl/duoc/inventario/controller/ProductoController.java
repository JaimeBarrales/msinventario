package cl.duoc.inventario.controller;

import cl.duoc.inventario.dto.AjusteStockRequest;
import cl.duoc.inventario.dto.ProductoRequest;
import cl.duoc.inventario.dto.ProductoResponse;
import cl.duoc.inventario.service.ProductoService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/v1/productos")
public class ProductoController {

    private final ProductoService servicio;

    public ProductoController(ProductoService servicio) {
        this.servicio = servicio;
    }

    @GetMapping
    public List<ProductoResponse> listar() {
        return servicio.listar();
    }

    @GetMapping("/{id}")
    public ProductoResponse obtener(@PathVariable Long id) {
        return servicio.obtener(id);
    }

    @PostMapping
    public ResponseEntity<ProductoResponse> crear(@Valid @RequestBody ProductoRequest req) {
        ProductoResponse creado = servicio.crear(req);
        return ResponseEntity
                .created(URI.create("/api/v1/productos/" + creado.id()))
                .body(creado);
    }

    @PutMapping("/{id}")
    public ProductoResponse actualizar(@PathVariable Long id, @Valid @RequestBody ProductoRequest req) {
        return servicio.actualizar(id, req);
    }

    @PatchMapping("/{id}/stock")
    public ProductoResponse ajustarStock(@PathVariable Long id, @Valid @RequestBody AjusteStockRequest req) {
        return servicio.ajustarStock(id, req.cantidad());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        servicio.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
