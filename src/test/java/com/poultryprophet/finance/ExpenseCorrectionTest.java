package com.poultryprophet.finance;

import com.poultryprophet.batch.BatchService;
import com.poultryprophet.common.BadRequestException;
import com.poultryprophet.common.ConflictException;
import com.poultryprophet.finance.dto.CorrectExpenseRequest;
import com.poultryprophet.incubation.IncubationService;
import com.poultryprophet.inventory.InventoryMovementRepository;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ExpenseCorrectionTest {
 @Test void correctionVoidsOriginalAndCreatesOneLinkedReplacement() {
  var repo=mock(FinancialTransactionRepository.class); var batch=mock(BatchService.class);
  var service=new FinanceService(repo,batch,mock(IncubationService.class),mock(InventoryMovementRepository.class));
  var original=new FinancialTransaction(); original.setId(8L); original.setFarmId(1L); original.setBatchId(3L); original.setType(FinanceTransactionType.EXPENSE); original.setUpdatedAt(Instant.parse("2026-01-01T00:00:00Z"));
  when(repo.findByIdAndFarmIdForUpdate(8L,1L)).thenReturn(Optional.of(original));
  when(repo.save(any())).thenAnswer(call -> { FinancialTransaction t=call.getArgument(0); if(t.getId()==null)t.setId(9L); return t; });
  var request=new CorrectExpenseRequest(LocalDate.of(2026,1,2),"Transport",new BigDecimal("25.00"),"Wrong amount",original.getUpdatedAt(),UUID.randomUUID());
  var result=service.correctExpense(8L,1L,2L,request);
  assertThat(original.getStatus()).isEqualTo(FinanceTransactionStatus.VOIDED);
  assertThat(original.getVoidReason()).isEqualTo("Wrong amount");
  assertThat(result.amount()).isEqualByComparingTo("25.00");
  assertThat(result.correctionOfId()).isEqualTo(8L);
 }
 @Test void refusesInventoryPurchasesAndStaleChanges() {
  var repo=mock(FinancialTransactionRepository.class);
  var service=new FinanceService(repo,mock(BatchService.class),mock(IncubationService.class),mock(InventoryMovementRepository.class));
  var original=new FinancialTransaction(); original.setId(8L); original.setFarmId(1L); original.setBatchId(3L); original.setType(FinanceTransactionType.EXPENSE); original.setSourceType("INVENTORY_PURCHASE");
  when(repo.findByIdAndFarmIdForUpdate(8L,1L)).thenReturn(Optional.of(original));
  var request=new CorrectExpenseRequest(LocalDate.now(),"Feed",new BigDecimal("10"),"Correction",original.getUpdatedAt(),UUID.randomUUID());
  assertThatThrownBy(() -> service.correctExpense(8L,1L,2L,request)).isInstanceOf(BadRequestException.class);
  original.setSourceType("MANUAL"); original.setUpdatedAt(Instant.parse("2026-02-01T00:00:00Z"));
  assertThatThrownBy(() -> service.correctExpense(8L,1L,2L,request)).isInstanceOf(ConflictException.class);
  verify(repo, never()).save(any());
 }
 @Test void correctionReplayRequiresOriginalActor() {
  var repo=mock(FinancialTransactionRepository.class);
  var service=new FinanceService(repo,mock(BatchService.class),mock(IncubationService.class),mock(InventoryMovementRepository.class));
  var original=new FinancialTransaction(); original.setId(8L); original.setFarmId(1L); original.setVoidReason("Wrong amount");
  var at=Instant.parse("2026-01-01T00:00:00Z"); var id=UUID.randomUUID();
  var request=new CorrectExpenseRequest(LocalDate.of(2026,1,2),"Transport",new BigDecimal("25.00"),"Wrong amount",at,id);
  var replacement=new FinancialTransaction(); replacement.setId(9L); replacement.setFarmId(1L); replacement.setType(FinanceTransactionType.EXPENSE); replacement.setCorrectionOfId(8L);
  replacement.setCorrectionExpectedUpdatedAt(at); replacement.setTransactionDate(request.transactionDate()); replacement.setCategory(request.category());
  replacement.setAmount(request.amount()); replacement.setEnteredBy(2L);
  when(repo.findByIdAndFarmIdForUpdate(8L,1L)).thenReturn(Optional.of(original));
  when(repo.findByFarmIdAndSourceOperationId(1L,id.toString())).thenReturn(Optional.of(replacement));
  assertThat(service.correctExpense(8L,1L,2L,request).id()).isEqualTo(9L);
  assertThatThrownBy(() -> service.correctExpense(8L,1L,4L,request)).isInstanceOf(ConflictException.class);
  replacement.setEnteredBy(null);
  assertThatThrownBy(() -> service.correctExpense(8L,1L,2L,request)).isInstanceOf(ConflictException.class);
  verify(repo, never()).save(any());
 }
}
