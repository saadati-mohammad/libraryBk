package ir.iau.library.repository;

import ir.iau.library.entity.Book;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Page;

import java.util.Optional;
import java.util.Collection;
import java.util.List;

@Repository
public interface BookRepository extends JpaRepository<Book, Long>, JpaSpecificationExecutor<Book> {

    interface SubjectCount {
        String getSubject();
        Long getTitleCount();
    }

    @Query("SELECT b.subject AS subject, COUNT(b) AS titleCount FROM Book b " +
            "WHERE b.active = true AND b.subject IS NOT NULL AND b.subject <> '' " +
            "GROUP BY b.subject ORDER BY COUNT(b) DESC")
    java.util.List<SubjectCount> countActiveSubjects();

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

    /**
     * Books that are not soft-deleted. Used by the dashboard: a deactivated book is gone
     * from the catalogue's point of view, so it must not be counted.
     */
    long countByActiveTrue();

    /** Batch-load existing catalog matches for safe spreadsheet deduplication. */
    List<Book> findByIsbn10In(Collection<String> isbn10Values);
    List<Book> findByIsbn13In(Collection<String> isbn13Values);
}
