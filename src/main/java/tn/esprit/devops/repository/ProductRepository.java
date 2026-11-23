package tn.esprit.devops.repository;
import tn.esprit.devops.model.Product;
import org.springframework.data.jpa.repository.JpaRepository;
public interface ProductRepository extends JpaRepository<Product,Long> {}
