package tn.esprit.devops.controller;
import tn.esprit.devops.model.Product;
import tn.esprit.devops.repository.ProductRepository;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.util.List;
@RestController @RequestMapping("/api/products")
public class ProductController {
    private final ProductRepository repository;
    public ProductController(ProductRepository repository) { this.repository=repository; }
    @GetMapping public List<Product> findAll() { return repository.findAll(); }
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public Product create(@RequestBody CreateProductRequest request) {
        if (request.name()==null || request.name().isBlank() || request.price()==null || request.price().compareTo(BigDecimal.ZERO)<0)
            throw new IllegalArgumentException("A product name and a non-negative price are required");
        return repository.save(new Product(request.name(),request.price()));
    }
    public record CreateProductRequest(String name, BigDecimal price) {}
}
