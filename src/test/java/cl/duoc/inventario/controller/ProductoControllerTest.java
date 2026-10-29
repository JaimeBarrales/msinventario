package cl.duoc.inventario.controller;

import cl.duoc.inventario.dto.ProductoRequest;
import cl.duoc.inventario.dto.ProductoResponse;
import cl.duoc.inventario.exception.ManejadorErrores;
import cl.duoc.inventario.exception.ProductoNoEncontradoException;
import cl.duoc.inventario.exception.StockInsuficienteException;
import cl.duoc.inventario.service.ProductoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("ProductoController - contrato HTTP")
class ProductoControllerTest {

    private ProductoService servicio;
    private MockMvc mvc;

    private static final ProductoResponse PRODUCTO = new ProductoResponse(
            1L, "FA-001", "Filtro de aceite", "Filtro estándar",
            new BigDecimal("4990.00"), 10, Instant.now(), Instant.now());

    @BeforeEach
    void configurar() {
        servicio = mock(ProductoService.class);
        mvc = MockMvcBuilders.standaloneSetup(new ProductoController(servicio))
                .setControllerAdvice(new ManejadorErrores())
                .build();
    }

    @Test
    @DisplayName("GET /api/v1/productos responde 200 con la lista")
    void listar() throws Exception {
        when(servicio.listar()).thenReturn(List.of(PRODUCTO));

        mvc.perform(get("/api/v1/productos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].sku").value("FA-001"))
                .andExpect(jsonPath("$[0].stock").value(10));
    }

    @Test
    @DisplayName("POST válido responde 201 con cabecera Location")
    void crearValido() throws Exception {
        when(servicio.crear(any(ProductoRequest.class))).thenReturn(PRODUCTO);

        mvc.perform(post("/api/v1/productos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sku":"FA-001","nombre":"Filtro de aceite","precio":4990,"stock":10}
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/productos/1"))
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    @DisplayName("POST con datos inválidos responde 400 y no llama al servicio")
    void crearInvalido() throws Exception {
        mvc.perform(post("/api/v1/productos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sku":"","nombre":"Filtro","precio":-1,"stock":-3}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Datos inválidos"));

        verify(servicio, never()).crear(any());
    }

    @Test
    @DisplayName("GET de un id inexistente responde 404")
    void obtenerInexistente() throws Exception {
        when(servicio.obtener(99L)).thenThrow(new ProductoNoEncontradoException(99L));

        mvc.perform(get("/api/v1/productos/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Producto no encontrado"));
    }

    @Test
    @DisplayName("PATCH de stock que deja saldo negativo responde 409")
    void ajustarStockInsuficiente() throws Exception {
        when(servicio.ajustarStock(1L, -50)).thenThrow(new StockInsuficienteException(1L, 10, -50));

        mvc.perform(patch("/api/v1/productos/1/stock")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cantidad\":-50}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Stock insuficiente"));
    }

    @Test
    @DisplayName("DELETE existente responde 204")
    void eliminar() throws Exception {
        mvc.perform(delete("/api/v1/productos/1"))
                .andExpect(status().isNoContent());

        verify(servicio).eliminar(1L);
    }

    @Test
    @DisplayName("DELETE inexistente responde 404")
    void eliminarInexistente() throws Exception {
        doThrow(new ProductoNoEncontradoException(5L)).when(servicio).eliminar(5L);

        mvc.perform(delete("/api/v1/productos/5"))
                .andExpect(status().isNotFound());
    }
}
