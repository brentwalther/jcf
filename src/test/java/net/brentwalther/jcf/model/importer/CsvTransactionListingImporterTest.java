package net.brentwalther.jcf.model.importer;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import net.brentwalther.jcf.JcfEnvironment;
import net.brentwalther.jcf.SettingsProto.SettingsProfile.DataField;
import net.brentwalther.jcf.model.JcfModel.Account;
import net.brentwalther.jcf.model.JcfModel.Model;
import net.brentwalther.jcf.model.ModelGenerators;
import net.brentwalther.jcf.model.ModelTransforms;
import net.brentwalther.jcf.testing.Correspondences;
import org.junit.Before;
import org.junit.Test;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAccessor;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class CsvTransactionListingImporterTest {

  private static final DateTimeFormatter DEFAULT_DATE_TIME_FORMATTER =
      DateTimeFormatter.ofPattern("yyyy-MM-dd");
  private static final TemporalAccessor DEFAULT_DATE =
      DEFAULT_DATE_TIME_FORMATTER.parse("2020-12-31");
  private static final ImmutableList<String> DEFAULT_CSV_LINES =
      ImmutableList.of(
          "Transaction Date,Card No,Description,Category,Amount",
          DEFAULT_DATE_TIME_FORMATTER.format(DEFAULT_DATE)
              + ",1234,An example restaurant,Dining,56.91");
  private static final ImmutableMap<DataField, Integer> DEFAULT_CSV_FIELD_MAPPINGS =
      ImmutableMap.of(DataField.DATE, 0, DataField.DESCRIPTION, 2, DataField.AMOUNT, 4);
  private static final Account DEFAULT_ACCOUNT =
      ModelGenerators.simpleAccount("Assets:Current Assets:Checking 1234");

  private JcfEnvironment mockEnvironment;

  @Before
  public void setUp() {
    mockEnvironment = mock(JcfEnvironment.class);
    when(mockEnvironment.getInputCsvLines()).thenReturn(DEFAULT_CSV_LINES);
    when(mockEnvironment.getCsvFieldMappings()).thenReturn(DEFAULT_CSV_FIELD_MAPPINGS);
    when(mockEnvironment.getCsvAccount()).thenReturn(Optional.of(DEFAULT_ACCOUNT));
    when(mockEnvironment.getCsvDateFormat()).thenReturn(Optional.of(DEFAULT_DATE_TIME_FORMATTER));
  }

  @Test
  public void testBasicEnvironment() {
    Model model = CsvTransactionListingImporter.create(mockEnvironment).get();
    assertThat(model.getAccountList()).containsExactly(DEFAULT_ACCOUNT);
    assertThat(model.getTransactionList())
        .comparingElementsUsing(Correspondences.TRANSACTION_DESCRIPTION_CORRESPONDENCE)
        .containsExactly("An example restaurant");
    assertThat(
            model.getSplitList().stream()
                .map(ModelTransforms::bigDecimalAmountForSplit)
                .collect(Collectors.toList()))
        .comparingElementsUsing(Correspondences.BIGDECIMAL_COMPARETO_CORRESPONDENCE)
        .containsExactly(new BigDecimal("56.91"));
  }

  @Test
  public void testEmptyInput() {
    when(mockEnvironment.getInputCsvLines()).thenReturn(ImmutableList.of());
    Model model = CsvTransactionListingImporter.create(mockEnvironment).get();

    // We consider empty input an early exit condition and return an importer that does nothing.
    assertThat(model.getAccountList()).isEmpty();
    assertThat(model.getTransactionList()).isEmpty();
    assertThat(model.getSplitList()).isEmpty();
  }

  @Test
  public void testEmptyInputAccount() {
    when(mockEnvironment.getCsvAccount()).thenReturn(Optional.empty());
    Model model = CsvTransactionListingImporter.create(mockEnvironment).get();

    // We consider a missing input account as an early exit condition and return an importer that
    // does nothing.
    assertThat(model.getAccountList()).isEmpty();
    assertThat(model.getTransactionList()).isEmpty();
    assertThat(model.getSplitList()).isEmpty();
  }

  @Test
  public void testEmptyDateFormat() {
    when(mockEnvironment.getCsvDateFormat()).thenReturn(Optional.empty());
    Model model = CsvTransactionListingImporter.create(mockEnvironment).get();

    // We consider a missing date format as an early exit condition and return an importer that
    // does nothing.
    assertThat(model.getAccountList()).isEmpty();
    assertThat(model.getTransactionList()).isEmpty();
    assertThat(model.getSplitList()).isEmpty();
  }

  @Test
  public void testEmptyFieldMappings() {
    when(mockEnvironment.getCsvFieldMappings()).thenReturn(ImmutableMap.of());
    Model model = CsvTransactionListingImporter.create(mockEnvironment).get();

    // Insufficient field mappings is as an early exit condition and return an importer that
    // does nothing.
    assertThat(model.getAccountList()).isEmpty();
    assertThat(model.getTransactionList()).isEmpty();
    assertThat(model.getSplitList()).isEmpty();
  }

  @Test
  public void testInsufficientFieldMappings() {
    when(mockEnvironment.getCsvFieldMappings()).thenReturn(ImmutableMap.of(DataField.DATE, 0));
    Model model = CsvTransactionListingImporter.create(mockEnvironment).get();

    // Insufficient field mappings is as an early exit condition and return an importer that
    // does nothing.
    assertThat(model.getAccountList()).isEmpty();
    assertThat(model.getTransactionList()).isEmpty();
    assertThat(model.getSplitList()).isEmpty();
  }

  @Test
  public void testCsvWithOnlyHeader() {
    when(mockEnvironment.getInputCsvLines()).thenReturn(DEFAULT_CSV_LINES.subList(0, 1));

    Model model = CsvTransactionListingImporter.create(mockEnvironment).get();
    assertThat(model.getAccountList()).containsExactly(DEFAULT_ACCOUNT);
    assertThat(model.getTransactionList()).isEmpty();
    assertThat(model.getSplitList()).isEmpty();
  }

  @Test
  public void testCsvWithEmptyFirstField() {
    when(mockEnvironment.getInputCsvLines())
        .thenReturn(
            ImmutableList.of(
                "Card,Date,Desc,Amt",
                "," + DEFAULT_DATE_TIME_FORMATTER.format(DEFAULT_DATE) + ",Foobar,50.00"));
    when(mockEnvironment.getCsvFieldMappings())
        .thenReturn(
            ImmutableMap.of(DataField.DATE, 1, DataField.DESCRIPTION, 2, DataField.AMOUNT, 3));

    Model model = CsvTransactionListingImporter.create(mockEnvironment).get();
    assertThat(model.getAccountList()).containsExactly(DEFAULT_ACCOUNT);
    assertThat(model.getTransactionList())
        .comparingElementsUsing(Correspondences.TRANSACTION_DESCRIPTION_CORRESPONDENCE)
        .containsExactly("Foobar");
    assertThat(
            model.getSplitList().stream()
                .map(ModelTransforms::bigDecimalAmountForSplit)
                .collect(Collectors.toList()))
        .comparingElementsUsing(Correspondences.BIGDECIMAL_COMPARETO_CORRESPONDENCE)
        .containsExactly(new BigDecimal("50.00"));
  }

  @Test
  public void testCsvWithEmptyMiddleField() {
    when(mockEnvironment.getInputCsvLines())
        .thenReturn(
            ImmutableList.of(
                "Date,Card,Desc,Amt",
                DEFAULT_DATE_TIME_FORMATTER.format(DEFAULT_DATE) + ",,Foobar,50.00"));
    when(mockEnvironment.getCsvFieldMappings())
        .thenReturn(
            ImmutableMap.of(DataField.DATE, 0, DataField.DESCRIPTION, 2, DataField.AMOUNT, 3));

    Model model = CsvTransactionListingImporter.create(mockEnvironment).get();
    assertThat(model.getAccountList()).containsExactly(DEFAULT_ACCOUNT);
    assertThat(model.getTransactionList())
        .comparingElementsUsing(Correspondences.TRANSACTION_DESCRIPTION_CORRESPONDENCE)
        .containsExactly("Foobar");
    assertThat(
            model.getSplitList().stream()
                .map(ModelTransforms::bigDecimalAmountForSplit)
                .collect(Collectors.toList()))
        .comparingElementsUsing(Correspondences.BIGDECIMAL_COMPARETO_CORRESPONDENCE)
        .containsExactly(new BigDecimal("50.00"));
  }

  @Test
  public void testCsvWithEmptyLastField() {
    when(mockEnvironment.getInputCsvLines())
        .thenReturn(
            ImmutableList.of(
                "Date,Desc,Amt,Card",
                DEFAULT_DATE_TIME_FORMATTER.format(DEFAULT_DATE) + ",Foobar,50.00,"));
    when(mockEnvironment.getCsvFieldMappings())
        .thenReturn(
            ImmutableMap.of(DataField.DATE, 0, DataField.DESCRIPTION, 1, DataField.AMOUNT, 2));

    Model model = CsvTransactionListingImporter.create(mockEnvironment).get();
    assertThat(model.getAccountList()).containsExactly(DEFAULT_ACCOUNT);
    assertThat(model.getTransactionList())
        .comparingElementsUsing(Correspondences.TRANSACTION_DESCRIPTION_CORRESPONDENCE)
        .containsExactly("Foobar");
    assertThat(
            model.getSplitList().stream()
                .map(ModelTransforms::bigDecimalAmountForSplit)
                .collect(Collectors.toList()))
        .comparingElementsUsing(Correspondences.BIGDECIMAL_COMPARETO_CORRESPONDENCE)
        .containsExactly(new BigDecimal("50.00"));
  }
}
