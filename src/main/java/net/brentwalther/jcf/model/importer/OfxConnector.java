package net.brentwalther.jcf.model.importer;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import com.webcohesion.ofx4j.domain.data.ResponseEnvelope;
import com.webcohesion.ofx4j.domain.data.ResponseMessageSet;
import com.webcohesion.ofx4j.domain.data.banking.BankAccountDetails;
import com.webcohesion.ofx4j.domain.data.banking.BankStatementResponseTransaction;
import com.webcohesion.ofx4j.domain.data.banking.BankingResponseMessageSet;
import com.webcohesion.ofx4j.domain.data.creditcard.CreditCardAccountDetails;
import com.webcohesion.ofx4j.domain.data.creditcard.CreditCardResponseMessageSet;
import com.webcohesion.ofx4j.domain.data.creditcard.CreditCardStatementResponseTransaction;
import com.webcohesion.ofx4j.io.AggregateUnmarshaller;
import com.webcohesion.ofx4j.io.OFXHandler;
import com.webcohesion.ofx4j.io.OFXParseException;
import com.webcohesion.ofx4j.io.OFXReader;
import com.webcohesion.ofx4j.io.OFXSyntaxException;
import com.webcohesion.ofx4j.io.nanoxml.NanoXMLOFXReader;
import net.brentwalther.jcf.model.Account;
import net.brentwalther.jcf.model.Model;
import net.brentwalther.jcf.model.Split;
import net.brentwalther.jcf.model.Transaction;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

public class OfxConnector {

  private final File ofxFile;

  public OfxConnector(File ofxFile) {
    this.ofxFile = ofxFile;
  }

  public Model extract() {
    if (!this.ofxFile.exists() || !this.ofxFile.isFile()) {
      return Model.empty();
    }

    OFXReader ofxReader = new NanoXMLOFXReader();
    AggregateUnmarshaller<ResponseEnvelope> transactionList =
        new AggregateUnmarshaller<>(ResponseEnvelope.class);
    ofxReader.setContentHandler(
        new OFXHandler() {
          @Override
          public void onHeader(String s, String s1) throws OFXSyntaxException {
            System.out.format("h: %s %s\n", s, s1);
          }

          @Override
          public void onElement(String s, String s1) throws OFXSyntaxException {
            System.out.format("e: %s %s\n", s, s1);
          }

          @Override
          public void startAggregate(String s) throws OFXSyntaxException {
            System.out.format("....... %s\n", s);
          }

          @Override
          public void endAggregate(String s) throws OFXSyntaxException {
            System.out.format("^^^^^ %s\n", s);
          }
        });
    try {
      ResponseEnvelope response = transactionList.unmarshal(new FileInputStream(this.ofxFile));
      System.out.format("UID %s\n", response.getUID());

      Map<String, Account> accounts = new HashMap<>();
      Map<String, Transaction> transactions = new HashMap<>();
      Multimap<String, Split> splits = ArrayListMultimap.create();
      for (ResponseMessageSet set : response.getMessageSets()) {
        switch (set.getType()) {
          case creditcard:
            CreditCardResponseMessageSet creditCardResponse = (CreditCardResponseMessageSet) set;
            for (CreditCardStatementResponseTransaction statementTransactions :
                creditCardResponse.getStatementResponses()) {
              CreditCardAccountDetails creditCardAccountDetails =
                  statementTransactions.getMessage().getAccount();
              Account account =
                  new Account(
                      creditCardAccountDetails.getAccountNumber(),
                      creditCardAccountDetails.getAccountNumber(),
                      Account.Type.LIABILITY,
                      null);
              accounts.put(account.id, account);
              for (com.webcohesion.ofx4j.domain.data.common.Transaction transaction :
                  statementTransactions.getMessage().getTransactionList().getTransactions()) {
                BigDecimal amount = transaction.getBigDecimalAmount();
                Transaction jfcTransaction =
                    new Transaction(
                        Transaction.DataSource.OFX,
                        transaction.getId(),
                        transaction.getDatePosted().toInstant(),
                        transaction.getName());
                Split split =
                    new Split(
                        account.id,
                        jfcTransaction.id,
                        amount.unscaledValue().intValue(),
                        (int) Math.pow(10, amount.scale()));
                transactions.put(jfcTransaction.id, jfcTransaction);
                splits.put(split.transactionId, split);
              }
            }
            break;
          case banking:
            BankingResponseMessageSet bankingResponse = (BankingResponseMessageSet) set;
            for (BankStatementResponseTransaction statementTransactions :
                bankingResponse.getStatementResponses()) {
              BankAccountDetails details = statementTransactions.getMessage().getAccount();
              Account account =
                  new Account(
                      details.getAccountNumber(),
                      details.getAccountKey(),
                      Account.Type.ASSET,
                      null);
              accounts.put(account.id, account);
              for (com.webcohesion.ofx4j.domain.data.common.Transaction transaction :
                  statementTransactions.getMessage().getTransactionList().getTransactions()) {
                BigDecimal amount = transaction.getBigDecimalAmount();
                Transaction jfcTransaction =
                    new Transaction(
                        Transaction.DataSource.OFX,
                        transaction.getId(),
                        transaction.getDatePosted().toInstant(),
                        transaction.getName());
                Split split =
                    new Split(
                        account.id,
                        jfcTransaction.id,
                        amount.unscaledValue().intValue(),
                        (int) Math.pow(10, amount.scale()));
                transactions.put(jfcTransaction.id, jfcTransaction);
                splits.put(split.transactionId, split);
              }
            }
            break;
          default:
            System.out.format("No OFX handler for OFX data type %s\n", set.getType());
            break;
        }
      }
      return new Model(accounts, transactions, splits);
    } catch (IOException e) {
      return Model.empty();
    } catch (OFXParseException e) {
      e.printStackTrace();
    }

    return Model.empty();
  }
}
