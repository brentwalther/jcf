package net.brentwalther.jcf.screen;

import com.google.common.collect.ImmutableList;
import com.google.protobuf.CodedOutputStream;
import net.brentwalther.jcf.TerminalProvider;
import net.brentwalther.jcf.model.Account;
import net.brentwalther.jcf.model.JcfModel;
import net.brentwalther.jcf.model.Model;
import net.brentwalther.jcf.model.Split;
import net.brentwalther.jcf.model.Transaction;
import net.brentwalther.jcf.prompt.NoticePrompt;
import net.brentwalther.jcf.prompt.PromptEvaluator;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

/** A screen to export a model to the JCF proto format. */
public class JcfExportScreen {
  public static void start(Model model, File file) {
    try {
      JcfModel.Model.Builder jcfModel = JcfModel.Model.newBuilder();
      for (Account account : model.accountsById.values()) {
        jcfModel.addAccount(
            JcfModel.Account.newBuilder()
                .setId(account.id)
                .setName(account.name)
                .setParentId(account.parentId)
                .setType(account.type));
      }
      for (Transaction transaction : model.transactionsById.values()) {
        jcfModel.addTransaction(
            JcfModel.Transaction.newBuilder()
                .setId(transaction.id)
                .setDescription(transaction.description)
                .setPostDateEpochSecond(transaction.postDate.getEpochSecond()));
      }
      for (Split split : model.splitsByTransactionId.values()) {
        jcfModel.addSplit(
            JcfModel.Split.newBuilder()
                .setAccountId(split.accountId)
                .setTransactionId(split.transactionId)
                .setValueNumerator(split.valueNumerator)
                .setValueDenominator(split.valueDenominator));
      }
      jcfModel.build().writeTo(CodedOutputStream.newInstance(new FileOutputStream(file)));
    } catch (IOException e) {
      PromptEvaluator.showAndGetResult(
          TerminalProvider.get(),
          NoticePrompt.withMessages(
              ImmutableList.of(
                  "Failed to export model " + model,
                  "  to file: " + file.getAbsolutePath(),
                  "  due to exception: " + e)));
    }
  }
}
