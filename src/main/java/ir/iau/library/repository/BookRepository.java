package ir.iau.library.repository;

import ir.iau.library.entity.Book;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface BookRepository extends JpaRepository<Book, Long>, JpaSpecificationExecutor<Book> {

    /**
     * Load a book with a pessimistic write lock.
     *
     * <p>Used when creating a loan: the "is this book already on loan?" check and the
     * subsequent insert must be atomic, otherwise two concurrent requests for the same
     * book can both pass the check and both create a loan (double-lending race).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM Book b WHERE b.id = :id")
    Optional<Book> findByIdForUpdate(@Param("id") Long id);
}
