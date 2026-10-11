package ir.iau.library.specification;

import ir.iau.library.dto.BookFilterDto;
import ir.iau.library.entity.Book;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

public class BookSpecification {
    public static Specification<Book> filter(BookFilterDto criteria) {
        return (Root<Book> root, CriteriaQuery<?> query, CriteriaBuilder cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // Active status is almost always applied
            if (criteria.getActive() != null) {
                predicates.add(cb.equal(root.get("active"), criteria.getActive()));
            }

            if (StringUtils.hasText(criteria.getTitle())) {
                predicates.add(cb.like(cb.lower(root.get("title")), "%" + criteria.getTitle().toLowerCase() + "%"));
            }
            if (StringUtils.hasText(criteria.getAuthor())) {
                predicates.add(cb.like(cb.lower(root.get("author")), "%" + criteria.getAuthor().toLowerCase() + "%"));
            }
            if (StringUtils.hasText(criteria.getTranslator())) {
                predicates.add(cb.like(cb.lower(root.get("translator")), "%" + criteria.getTranslator().toLowerCase() + "%"));
            }
            if (StringUtils.hasText(criteria.getPublisher())) {
                predicates.add(cb.like(cb.lower(root.get("publisher")), "%" + criteria.getPublisher().toLowerCase() + "%"));
            }
            if (StringUtils.hasText(criteria.getIsbn10())) {
                predicates.add(cb.like(cb.lower(root.get("isbn10")), "%" + criteria.getIsbn10().toLowerCase() + "%"));
            }
            if (StringUtils.hasText(criteria.getIsbn13())) {
                predicates.add(cb.like(cb.lower(root.get("isbn13")), "%" + criteria.getIsbn13().toLowerCase() + "%"));
            }
            if (StringUtils.hasText(criteria.getDescription())) {
                predicates.add(cb.like(cb.lower(root.get("description")), "%" + criteria.getDescription().toLowerCase() + "%"));
            }
            if (StringUtils.hasText(criteria.getDeweyDecimal())) {
                predicates.add(cb.like(cb.lower(root.get("deweyDecimal")), "%" + criteria.getDeweyDecimal().toLowerCase() + "%"));
            }
            if (StringUtils.hasText(criteria.getCongressClassification())) {
                predicates.add(cb.like(cb.lower(root.get("congressClassification")), "%" + criteria.getCongressClassification().toLowerCase() + "%"));
            }
            if (StringUtils.hasText(criteria.getSubject())) {
                // For subject, you might want an exact match or like, depending on requirements
                predicates.add(cb.like(cb.lower(root.get("subject")), "%" + criteria.getSubject().toLowerCase() + "%"));
            }
            if (StringUtils.hasText(criteria.getSummary())) {
                predicates.add(cb.like(cb.lower(root.get("summary")), "%" + criteria.getSummary().toLowerCase() + "%"));
            }
            if (criteria.getPublicationDate() != null) {
                predicates.add(cb.equal(root.get("publicationDate"), criteria.getPublicationDate()));
            }
            if (criteria.getPageCount() != null && criteria.getPageCount() > 0) {
                predicates.add(cb.equal(root.get("pageCount"), criteria.getPageCount()));
            }
            if (StringUtils.hasText(criteria.getLanguage())) {
                predicates.add(cb.like(cb.lower(root.get("language")), "%" + criteria.getLanguage().toLowerCase() + "%"));
            }
            if (StringUtils.hasText(criteria.getEdition())) {
                predicates.add(cb.like(cb.lower(root.get("edition")), "%" + criteria.getEdition().toLowerCase() + "%"));
            }
            if (criteria.getCopyCount() != null && criteria.getCopyCount() > 0) {
                predicates.add(cb.equal(root.get("copyCount"), criteria.getCopyCount()));
            }
            if (StringUtils.hasText(criteria.getLibrarySection())) {
                predicates.add(cb.like(cb.lower(root.get("librarySection")), "%" + criteria.getLibrarySection().toLowerCase() + "%"));
            }
            if (StringUtils.hasText(criteria.getShelfCode())) {
                predicates.add(cb.like(cb.lower(root.get("shelfCode")), "%" + criteria.getShelfCode().toLowerCase() + "%"));
            }
            if (StringUtils.hasText(criteria.getRowNumbers())) {
                predicates.add(cb.like(cb.lower(root.get("rowNumbers")), "%" + criteria.getRowNumbers().toLowerCase() + "%"));
            }
            if (StringUtils.hasText(criteria.getColumnNumber())) {
                predicates.add(cb.like(cb.lower(root.get("columnNumber")), "%" + criteria.getColumnNumber().toLowerCase() + "%"));
            }
            if (StringUtils.hasText(criteria.getPositionNote())) {
                predicates.add(cb.like(cb.lower(root.get("positionNote")), "%" + criteria.getPositionNote().toLowerCase() + "%"));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
