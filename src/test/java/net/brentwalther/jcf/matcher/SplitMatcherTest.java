package net.brentwalther.jcf.matcher;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import java.util.List;
import net.brentwalther.jcf.matcher.SplitMatcher.Match;
import net.brentwalther.jcf.matcher.SplitMatcher.MatchResult;
import net.brentwalther.jcf.matcher.SplitMatcher.ShouldExcludePredicate;
import net.brentwalther.jcf.model.JcfModel.Account;
import net.brentwalther.jcf.model.JcfModel.Model;
import net.brentwalther.jcf.model.JcfModel.Split;
import net.brentwalther.jcf.model.JcfModel.Transaction;
import org.junit.Test;

public class SplitMatcherTest {

  private static final Account DEFAULT_ACCOUNT =
      Account.newBuilder().setId("test").setName("Assets:Test").build();
  private static final Transaction DEFAULT_TRANSACTION =
      Transaction.newBuilder().setId("t1").setDescription("A common transaction").build();
  private static final Split DEFAULT_SPLIT =
      Split.newBuilder()
          .setTransactionId(DEFAULT_TRANSACTION.getId())
          .setAccountId(DEFAULT_ACCOUNT.getId())
          .setValueNumerator(1000)
          .setValueDenominator(100)
          .build();

  private static final ShouldExcludePredicate EXCLUDE_NO_ACCOUNTS = account -> false;

  @Test
  public void testExclusionPredicate() {
    SplitMatcher matcher =
        SplitMatcher.create(
            Model.newBuilder()
                .addAccount(DEFAULT_ACCOUNT)
                .addTransaction(DEFAULT_TRANSACTION)
                .addSplit(DEFAULT_SPLIT)
                .build());
    ImmutableList<Match> topMatches =
        matcher.getTopMatches(
            DEFAULT_TRANSACTION,
            ImmutableList.of(),
            /* shouldExcludePredicate= */ DEFAULT_ACCOUNT::equals);
    assertThat(topMatches).isEmpty();
  }

  @Test
  public void testFullExactMatch_noExclusions() {
    SplitMatcher matcher =
        SplitMatcher.create(
            Model.newBuilder()
                .addAccount(DEFAULT_ACCOUNT)
                .addTransaction(DEFAULT_TRANSACTION)
                .addSplit(DEFAULT_SPLIT)
                .build());
    ImmutableList<Match> topMatches =
        matcher.getTopMatches(DEFAULT_TRANSACTION, ImmutableList.of(), EXCLUDE_NO_ACCOUNTS);
    assertThat(topMatches).hasSize(1);
    Match match = topMatches.get(0);
    assertThat(match.result()).isEqualTo(MatchResult.PARTIAL_CONFIDENCE);
    assertThat(match.matches()).hasSize(1);
    assertThat(match.matches().get(0).account()).isEqualTo(DEFAULT_ACCOUNT);
    assertThat(match.matches().get(0).transaction()).isEqualTo(DEFAULT_TRANSACTION);
  }

  @Test
  public void testPartialMatch_noExclusions() {
    List<String> tokens = Splitter.on(' ').splitToList(DEFAULT_TRANSACTION.getDescription());
    assertThat(tokens.size()).isGreaterThan(1);
    SplitMatcher matcher =
        SplitMatcher.create(
            Model.newBuilder()
                .addAccount(DEFAULT_ACCOUNT)
                .addTransaction(DEFAULT_TRANSACTION)
                .addSplit(DEFAULT_SPLIT)
                .build());
    // The transaction we're passing in to match contains only one of the tokens that's in the
    // description of the transaction in the input model. It should still match.
    ImmutableList<Match> topMatches =
        matcher.getTopMatches(
            DEFAULT_TRANSACTION.toBuilder().setDescription(tokens.get(1)).build(),
            ImmutableList.of(),
            EXCLUDE_NO_ACCOUNTS);
    assertThat(topMatches).hasSize(1);
    Match match = topMatches.get(0);
    assertThat(match.result()).isEqualTo(MatchResult.PARTIAL_CONFIDENCE);
    assertThat(match.matches()).hasSize(1);
    assertThat(match.matches().get(0).account()).isEqualTo(DEFAULT_ACCOUNT);
    assertThat(match.matches().get(0).transaction()).isEqualTo(DEFAULT_TRANSACTION);
  }

  @Test
  public void testLongIdSanitizer() {
    Transaction transaction =
            DEFAULT_TRANSACTION.toBuilder().setDescription("AmazonOrder123456789").build();
    SplitMatcher matcher =
            SplitMatcher.create(
                    Model.newBuilder()
                            .addAccount(DEFAULT_ACCOUNT)
                            .addTransaction(transaction)
                            .addSplit(DEFAULT_SPLIT)
                            .build());
    // Amazon and possibly many other merchants concatenate a long ID to the description. To work
    // around this, we have a sanitizer that strips long groups of digits.
    ImmutableList<Match> topMatches =
            matcher.getTopMatches(
                    DEFAULT_TRANSACTION.toBuilder().setDescription("AmazonOrder987654321").build(),
                    ImmutableList.of(),
                    EXCLUDE_NO_ACCOUNTS);
    assertThat(topMatches).hasSize(1);
    Match match = topMatches.get(0);
    assertThat(match.result()).isEqualTo(MatchResult.PARTIAL_CONFIDENCE);
    assertThat(match.matches()).hasSize(1);
    assertThat(match.matches().get(0).account()).isEqualTo(DEFAULT_ACCOUNT);
    assertThat(match.matches().get(0).transaction()).isEqualTo(transaction);
  }

  @Test
  public void testDotComSanitizer() {
    Transaction transaction =
            DEFAULT_TRANSACTION.toBuilder().setDescription("Amazon.com order").build();
    SplitMatcher matcher =
            SplitMatcher.create(
                    Model.newBuilder()
                            .addAccount(DEFAULT_ACCOUNT)
                            .addTransaction(transaction)
                            .addSplit(DEFAULT_SPLIT)
                            .build());
    ImmutableList<Match> topMatches =
            matcher.getTopMatches(
                    DEFAULT_TRANSACTION.toBuilder().setDescription("Amazon order").build(),
                    ImmutableList.of(),
                    EXCLUDE_NO_ACCOUNTS);
    assertThat(topMatches).hasSize(1);
    Match match = topMatches.get(0);
    assertThat(match.result()).isEqualTo(MatchResult.PARTIAL_CONFIDENCE);
    assertThat(match.matches()).hasSize(1);
    assertThat(match.matches().get(0).account()).isEqualTo(DEFAULT_ACCOUNT);
    assertThat(match.matches().get(0).transaction()).isEqualTo(transaction);
  }

  @Test
  public void testNonAlphaNumericSanitizer() {
    Transaction transaction =
            DEFAULT_TRANSACTION.toBuilder().setDescription("~~Google~~").build();
    SplitMatcher matcher =
            SplitMatcher.create(
                    Model.newBuilder()
                            .addAccount(DEFAULT_ACCOUNT)
                            .addTransaction(transaction)
                            .addSplit(DEFAULT_SPLIT)
                            .build());
    ImmutableList<Match> topMatches =
            matcher.getTopMatches(
                    DEFAULT_TRANSACTION.toBuilder().setDescription("!!Google!!").build(),
                    ImmutableList.of(),
                    EXCLUDE_NO_ACCOUNTS);
    assertThat(topMatches).hasSize(1);
    Match match = topMatches.get(0);
    assertThat(match.result()).isEqualTo(MatchResult.PARTIAL_CONFIDENCE);
    assertThat(match.matches()).hasSize(1);
    assertThat(match.matches().get(0).account()).isEqualTo(DEFAULT_ACCOUNT);
    assertThat(match.matches().get(0).transaction()).isEqualTo(transaction);
  }
}
