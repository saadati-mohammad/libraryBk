package ir.iau.library.dto;

import ir.iau.library.entity.BookLoan;
import java.time.LocalDate;

public record MemberLoanDto(Long id, String title, String author, LocalDate loanDate,
                            LocalDate dueDate, LocalDate returnDate, String status) {
    public static MemberLoanDto from(BookLoan loan) {
        return new MemberLoanDto(loan.getId(), loan.getBook().getTitle(), loan.getBook().getAuthor(),
                loan.getLoanDate(), loan.getDueDate(), loan.getReturnDate(), loan.getStatus().name());
    }
}
