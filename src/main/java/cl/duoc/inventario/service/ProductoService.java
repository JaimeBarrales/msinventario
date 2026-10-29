package cl.duoc.inventario.service;

import cl.duoc.inventario.dto.ProductoRequest;
import cl.duoc.inventario.dto.ProductoResponse;
import cl.duoc.inventario.exception.ProductoNoEncontradoException;
import cl.duoc.inventario.exception.SkuDuplicadoException;
import cl.duoc.inventario.exception.StockInsuficienteException;
import cl.duoc.inventario.model.Producto;
import cl.duoc.inventario.repository.ProductoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class ProductoService {

    private final ProductoRepository repositorio;

    public ProductoService(ProductoRepository repositorio) {
        this.repositorio = repositorio;
    }

    @Transactional(readOnly = true)
    public List<ProductoResponse> listar() {
        return repositorio.findAll().stream()
                .map(ProductoResponse::desde)
                .toList();
    }

    @Transactional(readOnly = true)
    public ProductoResponse obtener(Long id) {
        return ProductoResponse.desde(buscar(id));
    }

    public ProductoResponse crear(ProductoRequest req) {
        String sku = normalizarSku(req.sku());
        if (repositorio.existsBySku(sku)) {
            throw new SkuDuplicadoException(sku);
        }
        Producto nuevo = new Producto(sku, req.nombre().trim(), req.descripcion(), req.precio(), req.stock());
        return ProductoResponse.desde(repositorio.save(nuevo));
    }

    public ProductoResponse actualizar(Long id, ProductoRequest req) {
        Producto existente = buscar(id);
        String sku = normalizarSku(req.sku());
        if (repositorio.existsBySkuAndIdNot(sku, id)) {
            throw new SkuDuplicadoException(sku);
        }
        existente.actualizarDatos(sku, req.nombre().trim(), req.descripcion(), req.precio(), req.stock());
        return ProductoResponse.desde(repositorio.save(existente));
    }

    public ProductoResponse ajustarStock(Long id, int cantidad) {
        Producto producto = buscar(id);
        int stockActual = producto.getStock();
        if (!producto.ajustarStock(cantidad)) {
            throw new StockInsuficienteException(id, stockActual, cantidad);
        }
        return ProductoResponse.desde(repositorio.save(producto));
    }

    public void eliminar(Long id) {
        if (!repositorio.existsById(id)) {
            throw new ProductoNoEncontradoException(id);
        }
        repositorio.deleteById(id);
    }

    private Producto buscar(Long id) {
        return repositorio.findById(id)
                .orElseThrow(() -> new ProductoNoEncontradoException(id));
    }

    private static String normalizarSku(String sku) {
        return sku.trim().toUpperCase();
    }
}
