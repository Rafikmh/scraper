package com.ontometrics.scraper;

import static com.ontometrics.scraper.HtmlSample.PagedListingTable;
import static com.ontometrics.scraper.extraction.HtmlExtractor.html;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;

import java.net.MalformedURLException;
import java.net.URL;
import java.text.MessageFormat;
import java.util.List;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ontometrics.scraper.extraction.DefaultFieldExtractor;
import com.ontometrics.scraper.extraction.HtmlExtractor;
import com.ontometrics.scraper.extraction.Link;
import com.ontometrics.scraper.extraction.LinkExtractor;
import java.util.ListIterator;

public class ListingDetailScraperTest {

	private static final Logger log = LoggerFactory.getLogger(ListingDetailScraperTest.class);

	private static final String listingTableKeyword = "Opportunity Title";
	private static final String linkPattern = "mode=VIEW";

	@Test
	public void canExtractLinksFromListingPage() {
		List<Link> foundLinks = new LinkExtractor()
				.source(html().url(PagedListingTable.getUrl()))
				.getLinks();

		log.info("found {} links: {}", foundLinks.size(), foundLinks);
		assertThat(foundLinks.size(), is(greaterThan(0)));
	}

	@Test
	public void canExtractLinksFromMultiplePagesThenFollowToDetailsPage() throws MalformedURLException {
		Iterator pageIterator = new Iterator() {

			List<Link> foundLinks = new LinkExtractor()
                                                .source(html().url(PagedListingTable.getUrl()))
                                                .getLinks();
                        ListIterator foundLinksItertator= foundLinks.listIterator();
                        
			@Override
			public URL next() {
                            Link currentLink =(Link)foundLinksItertator.next();                            
                            log.debug("current iterating page = {}", currentLink.getHref());
                            
                            URL currentURL = TestUtil.getFileAsURL(currentLink.getHref());
                            
                            return currentURL; 
			}

			@Override 
			public boolean hasNext() {
                            Link nextLink =(Link)foundLinks.get(foundLinksItertator.nextIndex());                            
                            log.debug("next page to iterate = {}", nextLink.getHref());

                            return foundLinksItertator.hasNext();
			}

			@Override
			public URL getBaseUrl() {
				// TODO Auto-generated method stub
				return null;
			}
		};
                HtmlExtractor htmlExtractor = html().url(PagedListingTable.getUrl()).table().matching(listingTableKeyword);
		
                List<Record> records = new ListingDetailScraper()
				.setConvertURLs(false)
				.iterator(pageIterator)
				.listing(new LinkExtractor().source(htmlExtractor))
				.details(new DefaultFieldExtractor())
				.getRecords();

		//assertThat(records.size(), is(0)); // this is not going to find any
											// records because the URLs are all
											// invalid
		log.debug("fields = {}", records);

	}

}
