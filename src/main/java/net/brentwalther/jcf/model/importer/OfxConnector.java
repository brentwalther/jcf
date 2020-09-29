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
import net.brentwalther.jcf.model.JcfModel;
import net.brentwalther.jcf.model.JcfModel.Account;
import net.brentwalther.jcf.model.JcfModel.Split;
import net.brentwalther.jcf.model.JcfModel.Transaction;
import net.brentwalther.jcf.model.ModelGenerator;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

public class OfxConnector implements JcfModelImporter {

  private final File ofxFile;

  public OfxConnector(File ofxFile) {
    this.ofxFile = ofxFile;
  }

  public JcfModel.Model get() {
    if (!this.ofxFile.exists() || !this.ofxFile.isFile()) {
      return ModelGenerator.empty();
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
                  Account.newBuilder()
                      .setId(creditCardAccountDetails.getAccountNumber())
                      .setName(creditCardAccountDetails.getAccountNumber())
                      .setType(Account.Type.LIABILITY)
                      .build();
              accounts.put(account.getId(), account);
              for (com.webcohesion.ofx4j.domain.data.common.Transaction transaction :
                  statementTransactions.getMessage().getTransactionList().getTransactions()) {
                BigDecimal amount = transaction.getBigDecimalAmount();
                Transaction jfcTransaction =
                    Transaction.newBuilder()
                        .setId(transaction.getId())
                        .setPostDateEpochSecond(
                            transaction.getDatePosted().toInstant().getEpochSecond())
                        .setDescription(transaction.getName())
                        .build();
                Split split =
                    Split.newBuilder()
                        .setAccountId(account.getId())
                        .setTransactionId(jfcTransaction.getId())
                        .setValueNumerator(amount.unscaledValue().intValue())
                        .setValueDenominator((int) Math.pow(10, amount.scale()))
                        .build();
                transactions.put(jfcTransaction.getId(), jfcTransaction);
                splits.put(split.getTransactionId(), split);
              }
            }
            break;
          case banking:
            BankingResponseMessageSet bankingResponse = (BankingResponseMessageSet) set;
            for (BankStatementResponseTransaction statementTransactions :
                bankingResponse.getStatementResponses()) {
              BankAccountDetails details = statementTransactions.getMessage().getAccount();
              Account account =
                  Account.newBuilder()
                      .setId(details.getAccountNumber())
                      .setName(details.getAccountKey())
                      .setType(Account.Type.ASSET)
                      .build();
              accounts.put(account.getId(), account);
              for (com.webcohesion.ofx4j.domain.data.common.Transaction transaction :
                  statementTransactions.getMessage().getTransactionList().getTransactions()) {
                BigDecimal amount = transaction.getBigDecimalAmount();
                Transaction jfcTransaction =
                    Transaction.newBuilder()
                        .setId(transaction.getId())
                        .setPostDateEpochSecond(
                            transaction.getDatePosted().toInstant().getEpochSecond())
                        .setDescription(transaction.getName())
                        .build();
                Split split =
                    Split.newBuilder()
                        .setAccountId(account.getId())
                        .setTransactionId(jfcTransaction.getId())
                        .setValueNumerator(amount.unscaledValue().intValue())
                        .setValueDenominator((int) Math.pow(10, amount.scale()))
                        .build();
                transactions.put(jfcTransaction.getId(), jfcTransaction);
                splits.put(split.getTransactionId(), split);
              }
            }
            break;
          default:
            System.out.format("No OFX handler for OFX data type %s\n", set.getType());
            break;
        }
      }
      return ModelGenerator.create(accounts.values(), transactions.values(), splits.values());
    } catch (IOException e) {
      return ModelGenerator.empty();
    } catch (OFXParseException e) {
      e.printStackTrace();
    }

    return ModelGenerator.empty();
  }
}
