package sg.edu.nus.iss.c2csectrade.repository;

import sg.edu.nus.iss.c2csectrade.document.ProductDocument;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProductSearchRepository extends ElasticsearchRepository<ProductDocument, String> {
}

