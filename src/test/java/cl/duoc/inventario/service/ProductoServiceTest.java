package cl.duoc.inventario.service;

import cl.duoc.inventario.dto.ProductoRequest;
import cl.duoc.inventario.dto.ProductoResponse;
import cl.duoc.inventario.exception.ProductoNoEncontradoException;
import cl.duoc.inventario.exception.SkuDuplicadoException;
import cl.duoc.inventario.exception.StockInsuficienteException;
import cl.duoc.inventario.model.Producto;
import cl.duoc.inventario.repository.ProductoRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProductoService - reglas de negocio")
class ProductoServiceTest {

    @Mock
    private ProductoRepository repositorio;

    @InjectMocks
    private ProductoService servicio;

    private static ProductoRequest request(String sku, int stock) {
        return new ProductoRequest(sku, "Filtro de aceite", "Filtro estándar", new BigDecimal("4990.00"), stock);
    }

    private static Producto producto(Long id, String sku, int stock) {
        Producto p = new Producto(sku, "Filtro de aceite", "Filtro estándar", new BigDecimal("4990.00"), stock);
        p.setId(id);
        return p;
    }

    @Test
    @DisplayName("Crear un producto normaliza el SKU y lo guarda")
    void crearProducto() {
        when(repositorio.existsBySku("FA-001")).thenReturn(false);
        when(repositorio.save(any(Producto.class))).thenAnswer(inv -> {
            Producto p = inv.getArgument(0);
            p.setId(1L);
            return p;
        });

        ProductoResponse creado = servicio.crear(request("  fa-001 ", 10));

        assertThat(creado.id()).isEqualTo(1L);
        assertThat(creado.sku()).isEqualTo("FA-001");
        assertThat(creado.stock()).isEqualTo(10);
    }

    @Test
    @DisplayName("No permite crear dos productos con el mismo SKU")
    void crearConSkuDuplicado() {
        when(repositorio.existsBySku("FA-001")).thenReturn(true);

        assertThatThrownBy(() -> servicio.crear(request("FA-001", 5)))
                .isInstanceOf(SkuDuplicadoException.class)
                .hasMessageContaining("FA-001");
        verify(repositorio, never()).save(any());
    }

    @Test
    @DisplayName("Listar devuelve todos los productos")
    void listarProductos() {
        when(repositorio.findAll()).thenReturn(List.of(producto(1L, "A-1", 1), producto(2L, "B-2", 2)));

        assertThat(servicio.listar()).extracting(ProductoResponse::sku).containsExactly("A-1", "B-2");
    }

    @Test
    @DisplayName("Obtener un producto inexistente lanza excepción")
    void obtenerInexistente() {
        when(repositorio.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.obtener(99L))
                .isInstanceOf(ProductoNoEncontradoException.class);
    }

    @Test
    @DisplayName("Actualizar falla si el nuevo SKU pertenece a otro producto")
    void actualizarConSkuDeOtroProducto() {
        when(repositorio.findById(1L)).thenReturn(Optional.of(producto(1L, "A-1", 3)));
        when(repositorio.existsBySkuAndIdNot("B-2", 1L)).thenReturn(true);

        assertThatThrownBy(() -> servicio.actualizar(1L, request("b-2", 3)))
                .isInstanceOf(SkuDuplicadoException.class);
    }

    @Test
    @DisplayName("Actualizar modifica los datos del producto")
    void actualizarProducto() {
        when(repositorio.findById(1L)).thenReturn(Optional.of(producto(1L, "A-1", 3)));
        when(repositorio.existsBySkuAndIdNot("A-1", 1L)).thenReturn(false);
        when(repositorio.save(any(Producto.class))).thenAnswer(inv -> inv.getArgument(0));

        ProductoResponse actualizado = servicio.actualizar(1L, request("a-1", 25));

        assertThat(actualizado.stock()).isEqualTo(25);
    }

    @Test
    @DisplayName("Ajustar stock suma ingresos y resta salidas")
    void ajustarStock() {
        when(repositorio.findById(1L)).thenReturn(Optional.of(producto(1L, "A-1", 10)));
        when(repositorio.save(any(Producto.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThat(servicio.ajustarStock(1L, -4).stock()).isEqualTo(6);
    }

    @Test
    @DisplayName("Caso crítico: el stock nunca puede quedar negativo")
    void ajustarStockInsuficiente() {
        when(repositorio.findById(1L)).thenReturn(Optional.of(producto(1L, "A-1", 2)));

        assertThatThrownBy(() -> servicio.ajustarStock(1L, -5))
                .isInstanceOf(StockInsuficienteException.class);
        verify(repositorio, never()).save(any());
    }

    @Test
    @DisplayName("Eliminar un producto inexistente lanza excepción")
    void eliminarInexistente() {
        when(repositorio.existsById(7L)).thenReturn(false);

        assertThatThrownBy(() -> servicio.eliminar(7L))
                .isInstanceOf(ProductoNoEncontradoException.class);
        verify(repositorio, never()).deleteById(any());
    }

    @Test
    @DisplayName("Eliminar un producto existente lo borra")
    void eliminarExistente() {
        when(repositorio.existsById(7L)).thenReturn(true);

        servicio.eliminar(7L);

        verify(repositorio).deleteById(7L);
    }
}
