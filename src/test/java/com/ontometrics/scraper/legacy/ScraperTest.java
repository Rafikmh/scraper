package com.ontometrics.scraper.legacy;

import static com.ontometrics.scraper.HtmlSample.DetailPage;
import static com.ontometrics.scraper.HtmlSample.PagedListingTable;
import static com.ontometrics.scraper.HtmlSample.ProgramDetailPage;
import static com.ontometrics.scraper.HtmlSample.TableWithMultipleValuesOnMultipleRows;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.MessageFormat;
import java.util.List;

import net.htmlparser.jericho.HTMLElementName;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ontometrics.scraper.Iterator;
import com.ontometrics.scraper.Record;
import com.ontometrics.scraper.TestUtil;
import com.ontometrics.scraper.extraction.Field;
import com.ontometrics.scraper.legacy.Scraper;
import com.ontometrics.scraper.util.ScraperUtil;

public class ScraperTest {

	private static final Logger log = LoggerFactory.getLogger(ScraperTest.class);

	private Scraper scraper;

	private String eligibilityCodeId = "dnf_class_values_cfda__applicant_eligibility__widget";

	private String eligibilityClassName = "fld_applicant_eligibility";

	@Before
	public void setup() {
		scraper = new Scraper();
	}

	@Test
	public void scrapeUrlReturnsHtml() throws IOException {
		String pageText = new Scraper().url(PagedListingTable.getUrl()).getResult();
		assertThat(pageText.length(), is(greaterThan(0)));
		assertThat(pageText.contains("<html>"), is(true));
		log.debug("pageText = {}", pageText);
	}

	@Test
	public void extractPageText() throws IOException {
		String pageContent = new Scraper().url(PagedListingTable.getUrl()).asText().getResult();
		assertThat(pageContent.contains("<html>"), is(false));
		log.info("Content: {}", pageContent);
	}

	@Test
	public void extractTableFromPage() throws Exception {
		log.info("HtmlElementName.TABLE: {}", HTMLElementName.TABLE);
		String pageText = scraper
				.url(PagedListingTable.getUrl())
				.extract(scraper.extractor().table(3).execute())
				.getResult();
		log.debug("table extracted: {}", pageText);
		assertThat(pageText.startsWith("<table"), is(true));
		log.info(pageText);

	}

	@Test
	public void extractLinksFromTableOnPage() throws Exception {
		List<Field> urls = scraper
				.url(PagedListingTable.getUrl())
				.extract(scraper.extractor().setUseDefaultFieldExtractor(false).table(3).links().getFields())
				.getFields();

		log.info("links extracted: {}", urls);
		assertThat(urls.size(), is(greaterThan(0)));

	}

	@Test
	public void extractContentsOfElementWithId() throws Exception {
		String tagText = scraper
				.url(ProgramDetailPage.getUrl())
				.extract(scraper.extractor().id(eligibilityCodeId).execute())
				.getResult();
		log.info("tag text: {}", tagText);
		assertThat(tagText.contains("nonprofit institutions of higher education"), is(true));
	}

	@Test
	public void extractContentsByClassAndOccurrence() throws Exception {
		String tagText = scraper
				.url(ProgramDetailPage.getUrl())
				.extract(scraper.extractor().ofClass(eligibilityClassName, 1).execute())
				.getResult();
		log.info("tag text: {}", tagText);
		assertThat(tagText.contains("39"), is(true));
		assertThat(tagText.contains("52"), is(true));
	}

	@Test
	public void extractParameterFromLinksInTable() throws Exception {
		Scraper scraper = new Scraper();
		List<String> ids = scraper
				.url(PagedListingTable.getUrl())
				.extractStrings(scraper.extractor().table(3).links().parameter("oppId").getResults())
				.getResults();

		assertThat(ids.size(), is(greaterThan(0)));
		log.info("ids found: {}", ids);
		assertThat(ids, hasItems("40034", "40158", "40095", "40790", "40821", "40849", "40315"));
		assertThat(ids, hasItems("40967", "41255", "41282", "40458", "41599", "41734", "40667", "41771"));
		assertThat(ids, hasItems("41898", "41032", "41896", "42394", "42445"));
	}

	@Test
	public void extractLinksFromTableContainingString() throws Exception {
		Scraper scraper = new Scraper();
		String table = scraper
				.url(DetailPage.getUrl())
				.extract(scraper.extractor().table("Document Type").execute())
				.getResult();

		log.info("table matching {} : {}", "Document Type", table);
		assertThat(table.toString().contains("Document Type"), is(true));

	}

	@Test
	public void extractParameterFromLinksInIteratedTables() throws Exception {
		Scraper scraper = new Scraper();
		Iterator pageIterator = new Iterator() {
			private int currentPage = 2;

			@Override
			public URL getBaseUrl() {
				return null;
			}
			
			@Override
			public URL next() {
				String nextPageUrl = MessageFormat.format("/testpages/ids-page-{0}.html", currentPage++);
				log.debug("next page to iterate to: {}", nextPageUrl);
				return TestUtil.getFileAsURL(nextPageUrl);
			}

			@Override
			public boolean hasNext() {
				return true;
			}

		};
		List<String> ids = scraper
				.url(PagedListingTable.getUrl())
				.pages(1)
				.iterator(pageIterator)
				.extractStrings(scraper.extractor().table(3).links().parameter("oppId").getResults())
				.getResults();

		assertThat(ids.size(), is(40));
		log.info("ids {} found: {}", ids.size(), ids);
	}

	@Test
	public void extractFieldsFromTable() throws IOException {
		Scraper scraper = new Scraper();
		List<Field> opportunities = scraper
				.url(DetailPage.getUrl())
				.extract(scraper.extractor().field("title", HTMLElementName.H1).getFields())
				.getFields();

		assertThat(opportunities.size(), is(greaterThan(0)));
		log.debug("fields = {}", opportunities);

	}

	@Test
	public void extractFieldsFromTableAndTitleFromH1() throws IOException {
		Scraper scraper = new Scraper();
		List<Field> opportunities = scraper
				.url(DetailPage.getUrl())
				.extract(scraper.extractor().field("title", HTMLElementName.H1).getFields())
				.extract(scraper.extractor().table(4).getFields())
				.getFields();

		assertThat(opportunities.size(), is(greaterThan(0)));
		assertThat(opportunities.get(0), is(notNullValue()));
		log.debug("fields = {}", opportunities);

	}

	@Test
	public void extractFieldsAfterTablePairedTags() throws MalformedURLException, IOException {
		Scraper scraper = new Scraper();
		List<Field> fields = scraper
				.url(DetailPage.getUrl())
				.extract(
						scraper.extractor()
								.after(HTMLElementName.TABLE, 5)
								.pair(HTMLElementName.H4, HTMLElementName.DD)
								.getFields())
				.getFields();

		assertThat(fields.size(), is(greaterThan(0)));
		assertThat(ScraperUtil.getFieldValue(fields, "description").startsWith("The focus of this two-year program"),
				is(true));
		log.debug("fields = {}", fields);

	}

	@Test
	public void extractFieldsBasedOnPairedTags() throws MalformedURLException, IOException {
		Scraper scraper = new Scraper();
		List<Field> fields = scraper
				.url(DetailPage.getUrl())
				.extract(scraper.extractor().pair(HTMLElementName.H4, HTMLElementName.DD).getFields())
				.getFields();

		assertThat(fields.size(), is(greaterThan(0)));
		log.debug("fields = {}", fields);

	}

	@Test
	public void extractFieldWithMultipleValues() throws MalformedURLException, IOException {
		Scraper scraper = new Scraper();
		List<Field> fields = scraper
				.url(DetailPage.getUrl())
				.extract(scraper.extractor().pair(HTMLElementName.H4, HTMLElementName.DD).getFields())
				.getFields();

		assertThat(fields.size(), is(greaterThan(0)));

		String[] eligibilityCodes = ScraperUtil.getFieldValue(fields, "Eligible Applicants").split(";");
		assertThat(eligibilityCodes.length, is(greaterThan(1)));
		for (int i = 0; i < eligibilityCodes.length; i++) {
			log.debug("eligibility code: {}", eligibilityCodes[i]);
		}

		log.debug("eligibility codes: {}", eligibilityCodes);

		fields = scraper
				.url(TableWithMultipleValuesOnMultipleRows.getUrl())
				.extract(scraper.extractor().getFields())
				.getFields();

		log.info("fields from table with multiple values on rows: {}", fields);

		Field cfdaNumbers = null;
		for (Field field : fields) {
			if (field.getLabel().equals("CFDA Number(s)")) {
				cfdaNumbers = field;
			}
		}

		assertThat(fields.size(), is(1));
		assertThat(cfdaNumbers, is(notNullValue()));
		assertThat(cfdaNumbers.getValue().contains(";"), is(true));

	}

	@Test
	public void extractFieldFromLargeDetailPage() throws MalformedURLException {
		Scraper scraper = new Scraper();
		List<Field> fields = scraper.url(DetailPage.getUrl()).getFields();

		log.info("fields in detail page: {}", fields.size());
		assertThat(fields.size(), is(greaterThan(0)));

		fields = scraper.url(PagedListingTable.getUrl()).getFields();

		assertThat(fields.size(), is(1)); // for now don't support extracting
											// fields from listing tables

	}

	@Test
	public void extractFieldsBasedOnPairedTagsAfterAnotherTag() throws MalformedURLException, IOException {
		Scraper scraper = new Scraper();
		List<Field> fields = scraper
				.url(DetailPage.getUrl())
				.extract(
						scraper.extractor()
								.after(HTMLElementName.TABLE, 5)
								.pair(HTMLElementName.H4, HTMLElementName.DD)
								.getFields())
				.getFields();

		assertThat(fields.size(), is(greaterThan(0)));
		log.debug("paired tags returned fields: {}", fields);
		assertThat(ScraperUtil.getFieldValue(fields, "Eligible Applicants"), is(notNullValue()));

		assertThat(fields.get(fields.size() - 1).getValue().contains("mailto"), is(true));

	}

	@Test
	@Ignore
	public void useIteratedListingAndDetailInterface() throws IOException {
		String listingTableKeyword = "Opportunity Title";
		String linkPattern = "mode=VIEW";
		Scraper scraper = new Scraper();
		Iterator pageIterator = new Iterator() {
			private int currentPage = 2;

			@Override
			public URL next() {
				String nextPageUrl = MessageFormat.format("/testpages/ids-page-{0}.html", currentPage ++);
				log.debug("next page to iterate to: {}", nextPageUrl);
				return TestUtil.getFileAsURL(nextPageUrl);
			}

			@Override
			public boolean hasNext() {
				return true;
			}

			@Override
			public URL getBaseUrl() {
				return null;
			}
		};
		Scraper detailScraper = new Scraper();
		detailScraper.extractor().setUseDefaultFieldExtractor(false);
		List<Record> records = scraper
				.url(PagedListingTable.getUrl())
				.pages(2)
				.iterator(pageIterator)
				.listing(
						scraper.extractor()
								.setUseDefaultFieldExtractor(false)
								.table(listingTableKeyword)
								.links()
								.matching(linkPattern)
								.getFields())
				.detail(detailScraper)
				.getRecords();

		assertThat(records.size(), is(greaterThan(0)));
		log.debug("fields = {}", records);

	}

	@Test
	public void extractBuyerAndOfficeInformation() throws MalformedURLException, IOException {
		Scraper scraper = new Scraper();
		List<Field> fields = scraper
				.url(ProgramDetailPage.getUrl())
				.extract(scraper.extractor().ofClass("agency-name").getFields())
				.getFields();

		String agency = ScraperUtil.getFieldValue(fields, "Agency");
		String office = ScraperUtil.getFieldValue(fields, "Office");

		log.info("agency: {}", agency);
		log.info("office: {}", office);

		assertThat(agency, is("Department of Agriculture"));
		assertThat(office, is("Agricultural Research Service"));

	}

	@Test
	public void extractContactInfoFromClass() throws MalformedURLException, IOException {
		String officeInfoID = "dnf_class_values_cfda__hq_office_info__widget";
		String contactInfoFromPage = "Kathleen S. Townson, 5601 Sunnyside Ave, MS-5110, Betsville, Maryland 20705 Email: kathleen.townson@ars.usda.gov Phone: (301) 504-1702";
		Scraper scraper = new Scraper();
		String contactInfo = scraper
				.url(ProgramDetailPage.getUrl())
				.extract(scraper.extractor().id(officeInfoID).execute())
				.getResult();

		log.info("contactinfo: {}", contactInfo);
		assertThat(contactInfo.contains(contactInfoFromPage), is(true));
	}
}
