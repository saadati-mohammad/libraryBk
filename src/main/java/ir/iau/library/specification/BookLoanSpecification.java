package ir.iau.library.specification;

import ir.iau.library.dto.BookLoanFilterDto;
import ir.iau.library.entity.BookLoan;
import ir.iau.library.service.Isbn;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

public class BookLoanSpecification {
    public static Specification<BookLoan> filter(BookLoanFilterDto criteria) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // To avoid n+1 problem in count queries for pagination
            if (query.getResultType() != Long.class && query.getResultType() != long.class) {
                root.fetch("person", JoinType.LEFT);
                root.fetch("book", JoinType.LEFT);
            }


            if (StringUtils.hasText(criteria.getPersonNationalId())) {
                predicates.add(cb.equal(root.get("person").get("nationalId"), criteria.getPersonNationalId()));
            }

            if (StringUtils.hasText(criteria.getBookIsbn())) {
                String scannedIsbn = Isbn.normalize(criteria.getBookIsbn());
                String isbn10 = Isbn.toIsbn10(scannedIsbn);
                String isbn13 = Isbn.toIsbn13(scannedIsbn);
                List<Predicate> isbnMatches = new ArrayList<>(List.of(
                        cb.equal(root.get("book").get("isbn10"), scannedIsbn),
                        cb.equal(root.get("book").get("isbn13"), scannedIsbn)));
                if (!isbn10.isBlank()) isbnMatches.add(cb.equal(root.get("book").get("isbn10"), isbn10));
                if (!isbn13.isBlank()) isbnMatches.add(cb.equal(root.get("book").get("isbn13"), isbn13));
                predicates.add(cb.or(isbnMatches.toArray(Predicate[]::new)));
            }

            if (criteria.getStatus() != null) {
                predicates.add(cb.equal(root.get("status"), criteria.getStatus()));
            }

            if (criteria.getDueDateFrom() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("dueDate"), criteria.getDueDateFrom()));
            }

            if (criteria.getDueDateTo() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("dueDate"), criteria.getDueDateTo()));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
