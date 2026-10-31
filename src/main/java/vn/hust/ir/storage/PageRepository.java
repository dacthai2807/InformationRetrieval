package vn.hust.ir.storage;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface PageRepository
        extends MongoRepository<PageDocument, String> {
}