package ir.iau.library.controller;

import ir.iau.library.dto.BookLoanDto;
import ir.iau.library.dto.BookLoanFilterDto;
import ir.iau.library.dto.CreateLoanRequestDto;
import ir.iau.library.service.BookLoanService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/loan")
public class BookLoanController {

    @Autowired
    private BookLoanService loanService;

    @GetMapping
    public Page<BookLoanDto> listLoans(BookLoanFilterDto filter, @PageableDefault(sort = "id", direction = org.springframework.data.domain.Sort.Direction.DESC) Pageable pageable) {
        return loanService.findAllFiltered(filter, pageable);
    }

    @PostMapping
    public ResponseEntity<BookLoanDto> createLoan(@Valid @RequestBody CreateLoanRequestDto request) {
        BookLoanDto createdLoan = loanService.createLoan(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdLoan);
    }

    @PutMapping("/{id}/return")
    public ResponseEntity<BookLoanDto> returnBook(@PathVariable Long id) {
        BookLoanDto updatedLoan = loanService.returnBook(id);
        return ResponseEntity.ok(updatedLoan);
    }
}