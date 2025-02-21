package com.ontometrics.scraper.legacy;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;

import net.htmlparser.jericho.Element;
import net.htmlparser.jericho.HTMLElementName;
import net.htmlparser.jericho.Source;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ontometrics.scraper.Iterator;
import com.ontometrics.scraper.Record;
import com.ontometrics.scraper.ScrapedRecord;
import com.ontometrics.scraper.extraction.Extractor;
import com.ontometrics.scraper.extraction.Field;
import com.ontometrics.scraper.util.ScraperUtil;

/**
 * Provides a mechanism for extracting items from pages or feeds.
 * <p>
 * Uses a fluent builder pattern in a fashion that does border on being a DSL.
 * The idea is that a {@link #url} is provided, then through a set of
 * manipulator methods, the operations to be performed are framed. All scrapings
 * require a url call and then the execute thread at the end to perform the
 * scraping.
 * <p>
 * Internally, the manipulators are triggered by basic conditions right now. We
 * will need a more sophisticated architecture as more operations pile up
 * (perhaps something like a Chain of Responsibility Pattern).
 * 
 * @author Rob
 */
public class Scraper {

	private static final Logger log = LoggerFactory.getLogger(Scraper.class);

	public static final String sessionIdKeyword = "$SESSION_ID$";;

	/**
	 * Remote resource we will be extracting content from.
	 */
	private URL url;

	/**
	 * Does the work of actually extracting the desired content. If there is an
	 * {@link #iterator}, the scraper iterates through the {@link #url}s and
	 * collects the results.
	 */
	private Extractor extractor;

	/**
	 * Usually Builders have a single product. We support two kinds of products:
	 * a single string that represents a scrape and a set of extracted elements.
	 */
	private List<String> results;

	/**
	 * Scraper will call this to get the URL of the next page.
	 */
	private Iterator iterator;

	/**
	 * This variable dictates how many total pages will be iterated.
	 */
	private int pages = 0;

	private List<Field> extractedFields;

	private List<Record> records;

	/**
	 * Used for relative links. At the moment, this will be just the host name.
	 */
	private URL baseUrl;

	private String sessionIDName;

	public Scraper() {
		this.extractor = new Extractor();
	}

	/**
	 * Provides a means of just extracting links.
	 * 
	 * @return a list of the links that were valid {@link URL}s.
	 * @throws IOException
	 */
	public List<URL> getLinks() throws IOException {
		List<URL> links = new ArrayList<URL>();
		
		Source source = new Source(url);
		source.fullSequentialParse();
		List<Element> linkElements = source.getAllElements(HTMLElementName.A);
		for (Element linkElement : linkElements) {
			String href = linkElement.getAttributeValue("href");
			if (href == null)
				continue;
			// A element can contain other tags so need to extract the text from
			// it:
			String label = linkElement.getContent().getTextExtractor().toString();
			log.debug(MessageFormat.format("{0} <{1}>", label, href));
			URL currentUrl;
			try {
				currentUrl = new URL(href);
				links.add(currentUrl);
			} catch (MalformedURLException e) {
				e.printStackTrace();
			}
		}
		return links;
	}

	/**
	 * Provides means of asking that just the text of the page be extracted.
	 */
	public Scraper asText() {
		extractor.asText();
		return this;
	}

	public Scraper usesSessionId(String sessionIdName) {
		this.sessionIDName = sessionIdName;
		return this;
	}

	// ---- Builder-style Interface
	public Scraper sessionIDName(String sessionIDName) {
		this.sessionIDName = sessionIDName;
		return this;
	}

	public Scraper url(String url) throws MalformedURLException {
		return url(new URL(url));
	}

	/**
	 * Sets the url to scrape.<br>
	 * If {@link sessionIDName} is set, we will extract the session id from the
	 * page source based on the given keyword. <br>
	 * A base url will be saved inside {@link baseUrl} to handle relative links.
	 * 
	 * @param url
	 * @return
	 * @throws MalformedURLException
	 */
	public Scraper url(URL url) throws MalformedURLException {
		this.url = url;
		this.extractedFields = null;
		extractor.url(url);
		if (this.extractedFields != null) {
			this.extractedFields.clear();
		}
		if (this.sessionIDName != null) {
			try {
				this.sessionIDName = ScraperUtil.extractSessionId(this.url, sessionIDName);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		String hostName = new String().concat("http://").concat(url.getHost());
		baseUrl = new URL(hostName);
		return this;
	}

	public Extractor extractor() {
		return this.extractor;
	}

	public Scraper iterator(Iterator iterator) {
		log.debug("setting iterator: {}", iterator);
		this.iterator = iterator;
		return this;
	}

	public Scraper pages(int i) {
		this.pages = i;
		return this;
	}

	public Scraper extract(String extraction) {
		return this;
	}

	public String getResult() throws IOException {
		return extractor.execute();
	}

	public List<String> getResults() {
		return this.results;
	}

	public List<Field> getFields() {
		if (this.extractedFields == null) {
			this.extractedFields = new ArrayList<Field>();
			try {
				List<Field> fields = extractor.getFields();
				this.extractedFields.addAll(fields);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		return extractedFields;
	}

	public List<Record> getRecords() {
		return this.records;
	}

	public Scraper listing(List<Field> fields) throws IOException {
		// this method is going to get the list of strings and store them as
		// links
		pages -= 1; // when we get results, we will have parsed the first page
					// already
		extract(fields);
		log.debug("extracted links: {}", fields);
		return this;
	}

	public Scraper detail(Scraper detailScraper) {
		this.records = new ArrayList<Record>();
		// all we have to do here is loop through the links extracted in listing
		// and perform the operations here, collecting all the fields into
		// records..
		String builtUrl = null;
		for (Field link : this.extractedFields) {
			try {
				if (isRelativeUrl(link.getValue())) {
					builtUrl = convertToAbsoluteUrl(link.getValue());
				}
				log.debug("Using link = {}", builtUrl);
				List<Field> fields = new ArrayList<Field>(detailScraper.url(new URL(builtUrl)).getFields());
				log.debug("returned fields = {}", fields);
				records.add(new ScrapedRecord(fields));
			} catch (MalformedURLException e) {
				log.info("Bad URL in looping detail page for listing links: {}", e.toString());
			}
		}
		return this;
	}

	private boolean isRelativeUrl(String url) {
		boolean result = false;
		if (url != null && !url.startsWith("http://")) {
			result = true;
		}
		return result;
	}

	private String convertToAbsoluteUrl(String link) {
		String absoluteUrlString = (baseUrl.toString()).concat(link);
		return absoluteUrlString;
	}

	public void setExtractor(Extractor extractor) {
		this.extractor = extractor;
	}

	public Scraper extractStrings(List<String> results) throws IOException {
		log.debug("extractor return these results: {}", results);
		this.results = results;
		if (iterator != null) {
			for (int i = 0; i < pages; i++) {
				URL nextUrl = iterator.next();

				if (nextUrl.toString().contains(sessionIdKeyword)) {
					String urlString = nextUrl.toString().replace(sessionIdKeyword, sessionIDName);
					nextUrl = new URL(urlString);
				}
				log.debug("next url = {}", nextUrl);
				extractor.url(nextUrl);
				results.addAll(extractor.getResults());
			}
		}
		return this;
	}

	/**
	 * Supports injecting session id into the URL. We will search for a keyword
	 * and if it exists, it will replace it.
	 * 
	 * @param results
	 * @return
	 * @throws IOException
	 */
	public Scraper extract(List<Field> results) throws IOException {

		this.extractedFields = results;

		if (iterator != null) {
			for (int i = 0; i < pages; i++) {
				URL nextUrl = iterator.next();

				if (nextUrl.toString().contains(sessionIdKeyword)) {
					String urlString = nextUrl.toString().replace(sessionIdKeyword, sessionIDName);
					nextUrl = new URL(urlString);
				}
				log.debug("next url = {}", nextUrl);
				extractor.url(nextUrl);
				this.extractedFields.addAll(extractor.getFields());
			}
		}
		return this;
	}

}
