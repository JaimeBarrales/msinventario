package cl.duoc.inventario;

import cl.duoc.inventario.repository.ProductoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Prueba de integración: levanta el contexto completo de Spring con H2 en memoria
 * y recorre el ciclo de vida de un producto de punta a punta.
 */
@SpringBootTest
@DisplayName("Integración - ciclo de vida completo de un producto")
class ProductoIntegracionTest {

    @Autowired
    private WebApplicationContext contexto;

    @Autowired
    private ProductoRepository repositorio;

    private MockMvc mvc;

    @BeforeEach
    void configurar() {
        mvc = MockMvcBuilders.webAppContextSetup(contexto).build();
        repositorio.deleteAll();
    }

    @Test
    @DisplayName("Crear, consultar, ajustar stock y eliminar un producto")
    void cicloDeVida() throws Exception {
        MvcResult creado = mvc.perform(post("/api/v1/productos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sku":"pa-77","nombre":"Pastillas de freno","precio":15990.50,"stock":8}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sku").value("PA-77"))
                .andReturn();

        String location = creado.getResponse().getHeader("Location");
        assertThat(location).startsWith("/api/v1/productos/");
        assertThat(repositorio.findBySku("PA-77")).isPresent();

        mvc.perform(get(location))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Pastillas de freno"));

        mvc.perform(patch(location + "/stock")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cantidad\":-3}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stock").value(5));

        mvc.perform(patch(location + "/stock")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cantidad\":-100}"))
                .andExpect(status().isConflict());

        mvc.perform(delete(location)).andExpect(status().isNoContent());
        mvc.perform(get(location)).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("La base de datos rechaza SKU duplicados vía la API (409)")
    void skuDuplicado() throws Exception {
        String cuerpo = """
                {"sku":"DUP-1","nombre":"Bujía","precio":2990,"stock":4}
                """;
        mvc.perform(post("/api/v1/productos").contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/v1/productos").contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isConflict());

        assertThat(repositorio.count()).isEqualTo(1);
    }
}
